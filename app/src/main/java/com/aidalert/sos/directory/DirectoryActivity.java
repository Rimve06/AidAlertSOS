package com.aidalert.sos.directory;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.databinding.ActivityDirectoryBinding;
import com.aidalert.sos.engine.LocationHelper;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.CallHelper;
import com.aidalert.sos.util.Perms;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Emergency directory: national hotlines and nearby hospitals / fire stations / police stations.
 * Opens without login and works fully offline. Location is read only when the user taps "Near me"
 * (one-shot, last known position; nothing is stored or sent).
 */
public class DirectoryActivity extends BaseActivity {
    private static final String ALL_HOTLINES = "HOTLINES";
    private static final String ALL_NEARBY = "ALL_NEARBY";

    private ActivityDirectoryBinding b;
    private DirectoryRepo repo;
    private final RowAdapter adapter = new RowAdapter();
    private String mode = ALL_HOTLINES;
    private Location here;
    private boolean liveOn;
    private LocationHelper live;
    private Location lastRendered;

    private final ActivityResultLauncher<String[]> locPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Perms.location(this)) useLocation();
                else Ui.toast(this, "Location not allowed. Facilities are listed A to Z instead.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDirectoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Emergency directory");
        repo = new DirectoryRepo(this);

        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> {
            if (r.tag instanceof DirectoryJson.Hotline) askCall(((DirectoryJson.Hotline) r.tag).number,
                    ((DirectoryJson.Hotline) r.tag).name);
            else if (r.tag instanceof Facility) showFacility((Facility) r.tag);
        });

        b.btnAll.setOnClickListener(v -> setMode(ALL_NEARBY));
        b.btnShelter.setOnClickListener(v -> setMode(Facility.SHELTER));
        b.btnOpenMap.setOnClickListener(v -> openMap(null));
        b.btnHotlines.setOnClickListener(v -> setMode(ALL_HOTLINES));
        b.btnHospitals.setOnClickListener(v -> setMode(Facility.HOSPITAL));
        b.btnFire.setOnClickListener(v -> setMode(Facility.FIRE));
        b.btnPolice.setOnClickListener(v -> setMode(Facility.POLICE));
        b.btnBloodCentres.setOnClickListener(v -> setMode(Facility.BLOOD));
        b.btnNear.setOnClickListener(v -> {
            if (Perms.location(this)) useLocation();
            else locPermission.launch(Perms.locationPerms());
        });
        b.btnRefresh.setOnClickListener(v -> refresh());
        AppExecutors.io(() -> {
            repo.seedIfNeeded();
            AppExecutors.main(this::render);
        });
        // Like a maps app: find the position at once and sync nearby places without a button press.
        if (Perms.location(this)) useLocation();
        else locPermission.launch(Perms.locationPerms());
    }

    private Location syncedAt;
    private boolean syncing;
    private long lastTry;
    private String syncMsg = "";

    /** Downloads places around the phone when online. Runs again after moving more than 15 km (the list covers 50 km). */
    private void syncIfNeeded(final boolean force) {
        final Location l = here;
        if (l == null || syncing) return;
        if (!force && syncedAt != null && l.distanceTo(syncedAt) < 15000f) return;
        if (!force && System.currentTimeMillis() - lastTry < 10 * 60 * 1000L && syncedAt == null) return;   // failed a moment ago: retry in 10 minutes
        lastTry = System.currentTimeMillis();
        syncing = true;
        syncMsg = "Syncing nearby places...";
        AppExecutors.io(() -> {
            String msg = repo.syncNearby(l.getLatitude(), l.getLongitude(), force);
            if (force) msg += "  " + repo.refresh();
            final String m = msg;
            AppExecutors.main(() -> {
                syncing = false;
                if (m.startsWith("Synced") || m.startsWith("Up to date")) syncedAt = l;   // a failed download must not count as synced
                syncMsg = m;
                if (!isFinishing()) render();
            });
        });
    }

    private void setMode(String m) {
        mode = m;
        render();
    }

    /**
     * "Near me": starts a fresh GPS/network fix and keeps updating while this screen is open (stops in
     * onPause). Nothing is stored or uploaded. The list is re-sorted when you move about 150 m.
     */
    private void useLocation() {
        if (live == null) live = new LocationHelper(this);
        Location cached = live.lastKnown();
        if (cached != null && here == null) {
            here = cached;
            showMe(cached, true);
            if (ALL_HOTLINES.equals(mode) && repo.store().facilityCount() > 0) mode = ALL_NEARBY;
            render();
            syncIfNeeded(false);
        }
        boolean started = liveOn;
        if (!liveOn) {
            liveOn = true;
            started = live.start(8000, l -> {
                boolean first = here == null;
                here = l;
                showMe(l, first);
                boolean moved = lastRendered == null || l.distanceTo(lastRendered) > 150f;
                if (first && ALL_HOTLINES.equals(mode) && repo.store().facilityCount() > 0) mode = ALL_NEARBY;
                if (first || moved) render();
                syncIfNeeded(false);
            });
        }
        if (!started && !live.servicesOn()) {
            liveOn = false;
            b.txtStatus.setText(live.whyNoFix());
            offerLocationSettings();
            return;
        }
        b.txtStatus.setText(here != null ? "Position found." : "Getting your position...");
        // If nothing arrives, say why instead of waiting forever.
        AppExecutors.mainDelayed(() -> {
            if (!isFinishing() && here == null && live != null) {
                b.txtStatus.setText(live.whyNoFix());
                if (!live.servicesOn()) offerLocationSettings();
            }
        }, 15000);
    }

    /** The list only needs the position; the map screen saves map tiles for offline use. */
    private void showMe(Location l, boolean first) {
        // nothing to draw here: this screen is a list
    }

    private void openMap(Facility f) {
        Intent i = new Intent(this, MapActivity.class);
        if (f != null) {
            i.putExtra(MapActivity.EXTRA_LAT, f.lat).putExtra(MapActivity.EXTRA_LON, f.lon).putExtra(MapActivity.EXTRA_NAME, f.name);
        }
        startActivity(i);
    }

    private boolean askedSettings;

    private void offerLocationSettings() {
        if (askedSettings || isFinishing()) return;
        askedSettings = true;
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Turn on Location")
                .setMessage("Near me needs the phone's Location switch on. It works with no internet; the internet is only used to download places.")
                .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Not now", null).show();
    }

    private void stopLive() {
        if (live != null && liveOn) {
            live.stop();
            liveOn = false;
        }
    }

    @Override
    protected void onPause() {
        stopLive();
        super.onPause();
    }

    private void refresh() {
        if (here == null) {
            if (Perms.location(this)) {
                useLocation();
                Ui.toast(this, "Getting your position first...");
            } else {
                locPermission.launch(Perms.locationPerms());
            }
            return;
        }
        stopLive();
        useLocation();
        syncedAt = null;
        syncIfNeeded(true);
    }

    private void render() {
        if (isFinishing()) return;
        lastRendered = here;
        AppExecutors.io(() -> {
            final List<RowAdapter.Row> rows = new ArrayList<>();
            String empty = "";
            if (ALL_HOTLINES.equals(mode)) {
                for (DirectoryJson.Hotline h : repo.store().hotlines()) {
                    rows.add(new RowAdapter.Row(h.name, h.description + (h.verified ? "" : "\nUnverified number"),
                            h.number, h.verified ? 0xFF2E7D32 : 0xFFEF6C00, h));
                }
                if (rows.isEmpty()) empty = "No hotlines saved. Dial 999 for any emergency.";
            } else if (ALL_NEARBY.equals(mode)) {
                List<Facility> core = new ArrayList<>();
                for (Facility f : repo.store().facilities()) {
                    for (String k : Facility.CORE) if (k.equals(f.kind)) core.add(f);
                }
                List<Facility> shown;
                if (here != null) {
                    shown = NearbyRanker.within(core, here.getLatitude(), here.getLongitude(), null, NearbyRanker.DIRECTORY_RADIUS_KM, 600);
                } else {
                    shown = core.size() > 600 ? new ArrayList<>(core.subList(0, 600)) : core;
                }
                for (Facility f : shown) {
                    String dist = Double.isNaN(f.distanceKm) ? "" : String.format(Locale.US, "%.1f km", f.distanceKm);
                    rows.add(new RowAdapter.Row(f.name, Facility.label(f.kind) + " · " + NearbyRanker.phoneLabel(f)
                            + (f.district.isEmpty() ? "" : "\n" + f.district), dist, Facility.color(f.kind), f));
                }
                if (rows.isEmpty()) empty = repo.store().facilityCount() == 0
                        ? "No places saved yet. Turn on location and connect to the internet: places within 50 km of you download by "
                        + "themselves and then stay available offline. Hotlines always work." : "No saved places within 50 km of you.";
            } else {
                String natNum = Facility.FIRE.equals(mode) ? "102" : "999";
                rows.add(new RowAdapter.Row(Facility.FIRE.equals(mode) ? "Fire Service and Civil Defence" : "National Emergency Service",
                        "Always works, no internet needed. Tap to call.", natNum, 0xFF2E7D32,
                        hotlineFor(natNum)));
                List<Facility> all = repo.store().facilities();
                List<Facility> shown;
                if (here != null) {
                    shown = NearbyRanker.within(all, here.getLatitude(), here.getLongitude(), mode, NearbyRanker.DIRECTORY_RADIUS_KM, 400);
                } else {
                    shown = new ArrayList<>();
                    for (Facility f : all) if (mode.equals(f.kind)) shown.add(f);
                }
                for (Facility f : shown) {
                    String dist = Double.isNaN(f.distanceKm) ? "" : String.format(Locale.US, "%.1f km", f.distanceKm);
                    rows.add(new RowAdapter.Row(f.name, NearbyRanker.phoneLabel(f)
                            + (f.district.isEmpty() ? "" : "\n" + f.district), dist,
                            f.phoneVerified ? 0xFF2E7D32 : 0xFF757575, f));
                }
                if (rows.size() <= 1) {
                    empty = repo.store().facilityCount() == 0
                            ? "No places saved yet.\nTurn on location and connect to the internet once: places near you are "
                            + "downloaded and then stay available offline. Hotlines always work."
                            : "No saved facilities of this type.";
                }
            }
            final String emptyText = empty;
            final long updated = repo.lastUpdated();
            AppExecutors.main(() -> {
                adapter.submit(rows);
                b.txtEmpty.setVisibility(emptyText.isEmpty() ? View.GONE : View.VISIBLE);
                b.txtEmpty.setText(emptyText);
                String when = updated == 0 ? "No places saved yet"
                        : "Places last updated "
                        + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(updated));
                String loc = here == null ? "Waiting for your location (allow location access to sort by distance)."
                        : "Everything within 50 km, nearest first. " + syncMsg;
                b.txtStatus.setText(ALL_HOTLINES.equals(mode)
                        ? "Works offline. Numbers marked unverified need checking before you rely on them."
                        : when + "\n" + loc);
            });
        });
    }

    private DirectoryJson.Hotline hotlineFor(String number) {
        DirectoryJson.Hotline h = new DirectoryJson.Hotline();
        h.number = number;
        h.name = "999".equals(number) ? "National Emergency Service" : "Fire Service and Civil Defence";
        h.verified = true;
        return h;
    }

    private void showFacility(Facility f) {
        StringBuilder sb = new StringBuilder();
        sb.append(NearbyRanker.phoneLabel(f));
        if (!f.hasPhone()) {
            sb.append("\n\nThis place has no phone number in the saved data. Use the national number below, or report the number if you know it.");
        }
        if (!f.district.isEmpty()) sb.append("\nArea: ").append(f.district);
        if (!Double.isNaN(f.distanceKm)) sb.append(String.format(Locale.US, "\nDistance: %.1f km", f.distanceKm));
        sb.append("\nSource: ").append(f.source.isEmpty() ? "unknown" : f.source);
        final String national = Facility.FIRE.equals(f.kind) ? "102" : "999";
        final String nationalName = Facility.FIRE.equals(f.kind) ? "Fire Service 102" : "National Emergency 999";
        final String[] items = f.hasPhone()
                ? new String[]{"Call " + f.phone, "Call " + nationalName, "Show on map and get directions", "Report a wrong number"}
                : new String[]{"Call " + nationalName, "Show on map and get directions", "I know this number: report it"};
        new AlertDialog.Builder(this).setTitle(f.name).setMessage(sb.toString())
                .setItems(items, (d, which) -> {
                    String pick = items[which];
                    if (pick.startsWith("Call " + nationalName)) CallHelper.dial(this, national);
                    else if (f.hasPhone() && which == 0) CallHelper.dial(this, f.phone);
                    else if (pick.startsWith("Show on map")) openMap(f);
                    else reportNumber(f);
                })
                .setNegativeButton("Close", null).show();
    }

    /** Sends a suggested number to the control center for review. Nothing is published automatically. */
    private void reportNumber(Facility f) {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        in.setHint("Phone number");
        new AlertDialog.Builder(this).setTitle("Report number for " + f.name).setView(in)
                .setPositiveButton("Send", (d, w) -> {
                    final String num = PhoneRules.clean(Ui.clean(in.getText()));
                    if (num.isEmpty()) {
                        Ui.toast(this, "That is not a valid phone number.");
                        return;
                    }
                    final org.json.JSONObject o = new org.json.JSONObject();
                    try {
                        o.put("facilityId", f.id);
                        o.put("kind", f.kind);
                        o.put("name", f.name);
                        o.put("lat", f.lat);
                        o.put("lon", f.lon);
                        o.put("district", f.district);
                        o.put("phone", num);
                        o.put("by", com.aidalert.sos.data.Prefs.of(this).uid());
                        o.put("ts", System.currentTimeMillis());
                    } catch (org.json.JSONException e) {
                        return;
                    }
                    AppExecutors.io(() -> {
                        String key = new com.aidalert.sos.net.FirebaseRest(this).post("facilityReports", o);
                        AppExecutors.main(() -> Ui.toast(this, key != null
                                ? "Thank you. The control center will check it."
                                : "Could not send. Sign in and check your connection."));
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void askCall(String number, String name) {
        new AlertDialog.Builder(this).setTitle(name).setMessage("Call " + number + "?")
                .setPositiveButton("Call", (x, w) -> CallHelper.dial(this, number))
                .setNegativeButton("Cancel", null).show();
    }
}
