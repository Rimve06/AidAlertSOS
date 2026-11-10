package com.aidalert.sos.news;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Reads assets/feeds.json: the editable list of public feeds and source pages. */
public final class FeedConfig {
    public static final class Feed {
        public final String name;
        public final String url;
        public final String kind;

        public Feed(String name, String url, String kind) {
            this.name = name;
            this.url = url;
            this.kind = kind;
        }
    }

    public final List<Feed> alerts = new ArrayList<>();
    public final List<Feed> news = new ArrayList<>();
    public final List<Feed> pages = new ArrayList<>();
    public final List<Feed> missing = new ArrayList<>();
    public final List<Feed> missingNews = new ArrayList<>();
    public final List<Feed> wanted = new ArrayList<>();
    public final List<Feed> blood = new ArrayList<>();

    public static FeedConfig parse(String json) throws JSONException {
        FeedConfig c = new FeedConfig();
        JSONObject o = new JSONObject(json);
        read(o.optJSONArray("alerts"), c.alerts);
        read(o.optJSONArray("news"), c.news);
        read(o.optJSONArray("pages"), c.pages);
        read(o.optJSONArray("missing"), c.missing);
        read(o.optJSONArray("missingNews"), c.missingNews);
        read(o.optJSONArray("wanted"), c.wanted);
        read(o.optJSONArray("blood"), c.blood);
        return c;
    }

    private static void read(JSONArray a, List<Feed> out) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject f = a.optJSONObject(i);
            if (f == null) continue;
            String url = f.optString("url");
            if (!url.startsWith("https://") && !url.startsWith("http://")) continue;
            out.add(new Feed(f.optString("name", url), url, f.optString("kind", "OTHER")));
        }
    }

    public static FeedConfig load(Context ctx) {
        try (InputStream in = ctx.getAssets().open("feeds.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return parse(out.toString("UTF-8"));
        } catch (IOException | JSONException e) {
            return new FeedConfig();
        }
    }
}
