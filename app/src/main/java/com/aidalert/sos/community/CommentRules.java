package com.aidalert.sos.community;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Java rules for comments under a community post: short, on topic, no links or phone numbers. */
public final class CommentRules {
    private CommentRules() {}

    public static final int MIN_LEN = 3;
    public static final int MAX_LEN = 200;
    public static final long MIN_GAP_MS = 10_000;

    /** @return null if the comment may be sent, otherwise what to tell the user */
    public static String check(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() < MIN_LEN) return "Write a little more (at least " + MIN_LEN + " characters).";
        if (t.length() > MAX_LEN) return "Too long. Keep a comment under " + MAX_LEN + " characters.";
        String l = t.toLowerCase(Locale.ROOT);
        if (l.matches("(?s).*(https?://|www\\.|\\.com\\b|\\.net\\b|\\.xyz\\b|t\\.me/|bit\\.ly).*")) {
            return "Links are not allowed in comments.";
        }
        if (t.replaceAll("[^0-9০-৯]", "").length() >= 7) {
            return "Do not post phone numbers in comments. Ask the author to contact the control center instead.";
        }
        return null;
    }

    public static int waitSeconds(long last, long now) {
        long left = MIN_GAP_MS - (now - last);
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /** Comments of a post, oldest first. Each object needs a "ts". */
    public static List<JSONObject> oldestFirst(List<JSONObject> in) {
        List<JSONObject> out = new ArrayList<>(in);
        java.util.Collections.sort(out, (a, b) -> Long.compare(a.optLong("ts"), b.optLong("ts")));
        return out;
    }
}
