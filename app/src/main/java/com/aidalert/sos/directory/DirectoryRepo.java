package com.aidalert.sos.directory;

import android.content.Context;

import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Offline-first directory. Everything works with no login and no internet:
 * the bundled assets are copied into SQLite on first launch, and an optional refresh merges newer
 * facility data from Firebase (signed-in users only, the database is not public).
 */
public class DirectoryRepo {
    public static final String META_ASSET_VERSION = "asset_version";
    public static final String META_LAST_UPDATED = "last_updated_ms";
    public static final String META_SOURCE = "last_source";
    private static final int ASSET_VERSION = 2;

    private final Context ctx;
    private final DirectoryStore store;

    public DirectoryRepo(Context c) {
        ctx = c.getApplicationContext();
        store = new DirectoryStore(ctx);
    }

    public DirectoryStore store() {
        return store;
    }

    /** Copy the bundled hotlines + facility seed into SQLite once per asset version. Safe offline. */
    public synchronized void seedIfNeeded() {
        String have = store.meta(META_ASSET_VERSION, "0");
        if (String.valueOf(ASSET_VERSION).equals(have) && !store.hotlines().isEmpty()) return;
        try {
            store.replaceHotlines(DirectoryJson.parseHotlines(readAsset("hotlines.json")));
            DirectoryJson.FacilityFile ff = DirectoryJson.parseFacilities(readAsset("facilities_seed.json"));
            store.upsertFacilities(ff.facilities, System.currentTimeMillis());
            if (!ff.facilities.isEmpty()) {
                store.setMeta(META_LAST_UPDATED, String.valueOf(System.currentTimeMillis()));
                store.setMeta(META_SOURCE, ff.source);
            }
            store.setMeta(META_ASSET_VERSION, String.valueOf(ASSET_VERSION));
        } catch (IOException | JSONException e) {
            // Keep whatever is already cached; the screen shows the empty / stale state honestly.
        }
    }

    /**
     * Pull facilities from Firebase node "facilities" (id -> object). Requires a signed-in user.
     * @return a short message for the screen
     */
    public String refresh() {
        if (!NetUtil.isOnline(ctx)) return "You are offline. Showing saved data.";
        FirebaseRest fb = new FirebaseRest(ctx);
        if (!fb.configured()) return "Server is not configured. Showing saved data.";
        FirebaseRest.Result r = fb.get("facilities");
        if (r.code == 401 || r.code == 403) return "Sign in to refresh the facility list. Showing saved data.";
        if (!r.ok()) return "Could not refresh (code " + r.code + "). Showing saved data.";
        JSONObject root = r.obj();
        if (root == null) return "No newer data on the server. Showing saved data.";
        List<Facility> list = new ArrayList<>();
        int rejected = 0;
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONObject o = root.optJSONObject(k);
            if (o != null && !o.has("id")) {
                try {
                    o.put("id", k);
                } catch (JSONException ignored) {
                    // key stays missing, entry is rejected below
                }
            }
            Facility f = DirectoryJson.parseFacility(o, "Control center");
            if (f == null) rejected++;
            else list.add(f);
        }
        long now = System.currentTimeMillis();
        store.upsertFacilities(list, now);
        store.setMeta(META_LAST_UPDATED, String.valueOf(now));
        store.setMeta(META_SOURCE, "Firebase refresh");
        return "Updated " + list.size() + " facilities" + (rejected > 0 ? " (" + rejected + " incomplete entries skipped)" : "") + ".";
    }

    private static final String[] OVERPASS = {
            "https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter", "https://overpass.openstreetmap.fr/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"};
    /** Smaller areas answer faster on slow mobile networks, so try a modest radius first, then a tiny one. */
    private static final int[] RADII_M = {OsmNearby.DIRECTORY_RADIUS_M, 15000, 5000};
    private static final int[] RADII_MAP_M = {8000, 3000};
    private static final int[] TIMEOUT_MS = {40000, 25000, 20000};
    private static final long SYNC_EVERY_MS = 6L * 3600 * 1000;

    /**
     * Downloads hospitals, fire stations and police stations within a few km of the phone from OpenStreetMap and saves
     * them in SQLite. Skipped when the same area was synced in the last 6 hours, unless force is true.
     * Call from a background thread.
     * @return a one-line status for the screen
     */
    public String syncNearby(double lat, double lon, boolean force) {
        if (!NetUtil.isOnline(ctx)) return "Offline. Showing saved places.";
        String cell = "r50:" + OsmNearby.cellKey(lat, lon);      // r50: the 50 km list; an older small-radius sync does not count
        long last = 0;
        try {
            last = Long.parseLong(store.meta("osm_sync_at", "0"));
        } catch (NumberFormatException ignored) {
            // treated as never synced
        }
        if (!force && cell.equals(store.meta("osm_cell", "")) && System.currentTimeMillis() - last < SYNC_EVERY_MS) {
            return "Up to date for this area.";
        }
        // One small request per kind of place (hospitals, police, ...): a heavy single request is what timed out before.
        long now = System.currentTimeMillis();
        int total = 0, withPhone = 0, failedGroups = 0;
        String lastError = "";
        for (String group : OsmNearby.CORE_GROUPS) {
            try {
                List<Facility> list = downloadGroup(group, lat, lon);
                store.upsertFacilities(list, now);
                total += list.size();
                for (Facility f : list) if (f.hasPhone()) withPhone++;
            } catch (IOException | JSONException e) {
                failedGroups++;
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        if (failedGroups < OsmNearby.CORE_GROUPS.length) {
            store.setMeta("osm_cell", cell);
            if (failedGroups == 0) store.setMeta("osm_sync_at", String.valueOf(now));
            store.setMeta(META_LAST_UPDATED, String.valueOf(now));
            store.setMeta(META_SOURCE, OsmNearby.SOURCE);
            return "Synced " + total + " places from OpenStreetMap (" + withPhone + " with a phone number)"
                    + (failedGroups > 0 ? "; " + failedGroups + " of " + OsmNearby.CORE_GROUPS.length + " kinds did not answer, they will be retried" : "") + ".";
        }
        return "The free OpenStreetMap servers did not answer in time (" + lastError + "). Saved places and hotlines still work; it retries by itself.";
    }

    /**
     * Downloads one kind of place around a position (50 km, then 15 km and 5 km if the server is slow) and returns it.
     * Does not save; call from a background thread.
     */
    public List<Facility> downloadGroup(String group, double lat, double lon) throws IOException, JSONException {
        return downloadGroup(group, lat, lon, RADII_M);
    }

    /** For the Map screen's category chips: a smaller area (8 km, then 3 km) because there are far more restaurants than hospitals. */
    public List<Facility> downloadGroupForMap(String group, double lat, double lon) throws IOException, JSONException {
        return downloadGroup(group, lat, lon, RADII_MAP_M);
    }

    private List<Facility> downloadGroup(String group, double lat, double lon, int[] radii) throws IOException, JSONException {
        IOException last = new IOException("no answer");
        for (int i = 0; i < radii.length; i++) {
            int radius = radii[i];
            String ql = OsmNearby.groupQuery(group, lat, lon, radius);
            if (ql == null) throw new IOException("unknown group " + group);
            try {
                int timeout = radius >= 30000 ? TIMEOUT_MS[0] : radius >= 10000 ? TIMEOUT_MS[1] : TIMEOUT_MS[2];
                String body = raceGet(OVERPASS, "data=" + java.net.URLEncoder.encode(ql, "UTF-8"), 6 * 1024 * 1024, timeout);
                return OsmNearby.parse(body, System.currentTimeMillis());
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** Saves a list that was downloaded with {@link #downloadGroup}. */
    public void save(List<Facility> list) {
        long now = System.currentTimeMillis();
        store.upsertFacilities(list, now);
        store.setMeta(META_LAST_UPDATED, String.valueOf(now));
        store.setMeta(META_SOURCE, OsmNearby.SOURCE);
    }

    /** Requests every URL at once and returns the first successful body. Throws the last error when all fail. */
    static String raceGet(final String[] urls, final String form, final int maxBytes, final int timeoutMs) throws IOException {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(urls.length);
        java.util.concurrent.ExecutorCompletionService<String> done = new java.util.concurrent.ExecutorCompletionService<>(pool);
        try {
            for (final String u : urls) {
                done.submit(() -> {
                    try {
                        return com.aidalert.sos.net.Http.postForm(u, form, maxBytes, timeoutMs);
                    } catch (IOException e) {
                        String host = u.replaceFirst("https://", "").replaceFirst("/.*", "");
                        throw new IOException(host + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    }
                });
            }
            StringBuilder err = new StringBuilder();
            for (int i = 0; i < urls.length; i++) {
                try {
                    return done.take().get();
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable c = e.getCause();
                    if (err.length() > 0) err.append("; ");
                    err.append(c == null || c.getMessage() == null ? "request failed" : c.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
            throw new IOException(err.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    public long lastUpdated() {
        try {
            return Long.parseLong(store.meta(META_LAST_UPDATED, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String readAsset(String name) throws IOException {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
