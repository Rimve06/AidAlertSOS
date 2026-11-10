package com.aidalert.sos.net;

/** Tiny HTML-to-text helper for feed descriptions (Google News descriptions are HTML). Pure Java. */
public final class Html {
    private Html() {}

    public static String strip(String s) {
        if (s == null) return "";
        String t = s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</p>|</li>", "\n")
                .replaceAll("(?s)<[^>]*>", " ");
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    /** Google News titles end with " - Publisher". Returns the publisher or "". */
    public static String publisher(String title) {
        if (title == null) return "";
        int i = title.lastIndexOf(" - ");
        return i > 0 && i < title.length() - 3 ? title.substring(i + 3).trim() : "";
    }

    public static String withoutPublisher(String title) {
        if (title == null) return "";
        int i = title.lastIndexOf(" - ");
        return i > 0 ? title.substring(0, i).trim() : title.trim();
    }
}
