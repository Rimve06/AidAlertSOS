package com.aidalert.sos.missing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.aidalert.sos.R;
import com.aidalert.sos.alerts.Regions;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.Http;
import com.aidalert.sos.news.FeedConfig;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Notifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the public missing-person pages and news feeds listed in assets/feeds.json and posts a notification (with
 * the photo when the page has one) for each NEW report about the user's area. Tapping it opens the original page.
 * There is no partnership or API with those sites: this reads the same public pages a person would, at most every
 * few hours, skips pages whose robots.txt asks bots to stay out, and always labels the result unverified.
 */
public final class MissingWatcher {
    private MissingWatcher() {}

    private static final String SEEN_KEY = "missing";
    private static final int MAX_PER_RUN = 3;
    private static final int MAX_IMAGE_BYTES = 1_500_000;

    /** The place to match: the area chosen in Alerts, else the area in the profile if it names a district, else everywhere. */
    public static String placeFor(Prefs p) {
        String r = p.alertRegion();
        if (r != null && !r.isEmpty()) return r;
        return Regions.districtIn(p.area());
    }

    /**
     * One check. The very first run only remembers what is already on the sites (so the phone is not flooded with old
     * reports). Call from a background thread.
     * @return number of notifications posted
     */
    public static int runOnce(Context ctx) {
        Prefs p = Prefs.of(ctx);
        if (!p.missingWatch() || !NetUtil.isOnline(ctx)) return 0;
        FeedConfig cfg = FeedConfig.load(ctx);
        List<MissingRules.Item> all = new ArrayList<>();
        for (FeedConfig.Feed f : cfg.missing) {
            try {
                if (!robotsOk(f.url)) continue;
                String html = Http.get(f.url, "text/html,application/xhtml+xml", 1024 * 1024, 15000);
                all.addAll(MissingRules.extractCases(html, f.url, f.name));
            } catch (IOException e) {
                // this site is down or slow: the others still count
            }
        }
        for (FeedConfig.Feed f : cfg.missingNews) {
            try {
                String xml = Http.get(f.url, "application/rss+xml, application/xml, text/xml", 1024 * 1024, 15000);
                all.addAll(MissingRules.parseRss(xml, f.name));
            } catch (IOException e) {
                // skipped
            }
        }
        Set<String> seen = p.seen(SEEN_KEY);
        boolean first = !p.missingBaselineDone();
        String place = placeFor(p);
        int posted = 0;
        if (!first) {
            for (MissingRules.Item it : MissingRules.toNotify(all, seen, place, MAX_PER_RUN)) {
                notifyItem(ctx, it, place);
                posted++;
            }
        }
        Set<String> now = new HashSet<>(seen);
        for (MissingRules.Item it : all) now.add(it.id);
        if (now.size() > 600) {
            // keep the set small: remember only what is on the pages today
            now.clear();
            for (MissingRules.Item it : all) now.add(it.id);
        }
        p.setSeen(SEEN_KEY, now);
        if (!all.isEmpty()) p.missingBaselineDone(true);
        p.missingLastCheck(System.currentTimeMillis());
        return posted;
    }

    private static boolean robotsOk(String pageUrl) {
        try {
            URL u = new URL(pageUrl);
            String robots = Http.get(u.getProtocol() + "://" + u.getHost() + "/robots.txt", "text/plain", 128 * 1024, 8000);
            String path = u.getPath() == null || u.getPath().isEmpty() ? "/" : u.getPath();
            return MissingRules.robotsAllows(robots, path);
        } catch (IOException e) {
            return true;       // no robots.txt answer means no restriction was published
        }
    }

    @SuppressLint("MissingPermission")
    private static void notifyItem(Context ctx, MissingRules.Item it, String place) {
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(it.link));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String body = it.title + "\nSource: " + it.source + ". Not verified: tap to open the original page.";
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, Notifier.CH_MISSING)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(MissingRules.notificationTitle(it, place))
                .setContentText(it.title)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(Notifier.activity(ctx, open, 3000 + (it.id.hashCode() & 0xFFF)));
        Bitmap pic = it.imageUrl.isEmpty() ? null : download(it.imageUrl);
        if (pic != null) {
            b.setLargeIcon(pic);
            b.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(pic).setBigContentTitle(MissingRules.notificationTitle(it, place))
                    .setSummaryText(it.title));
        } else {
            b.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }
        try {
            NotificationManagerCompat.from(ctx).notify(3000 + (it.id.hashCode() & 0xFFF), b.build());
        } catch (SecurityException ignored) {
            // notification permission not granted
        }
    }

    /** Small photo for the notification (https only, at most 1.5 MB, scaled to about 512 px). Null on any problem. */
    private static Bitmap download(String url) {
        if (!url.startsWith("https://")) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "AidAlertSOS/1.0 (Android)");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_IMAGE_BYTES) return null;
                }
                byte[] data = out.toByteArray();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int sample = 1;
                while (o.outWidth / sample > 1024 || o.outHeight / sample > 1024) sample *= 2;
                BitmapFactory.Options d = new BitmapFactory.Options();
                d.inSampleSize = sample;
                return BitmapFactory.decodeByteArray(data, 0, data.length, d);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
