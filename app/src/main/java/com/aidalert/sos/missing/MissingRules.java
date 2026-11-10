package com.aidalert.sos.missing;

import com.aidalert.sos.alerts.Regions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java for the missing-person watcher: pulls report cards out of a public web page or news feed, decides which
 * ones are new and about the user's area, and checks robots.txt. Everything here is a best-effort reading of
 * public pages; results are always shown as UNVERIFIED and link back to the original site.
 */
public final class MissingRules {
    private MissingRules() {}

    public static final class Item {
        public String id = "";
        public String title = "";
        public String link = "";
        public String imageUrl = "";
        public String source = "";
        public String text = "";
    }

    private static final Pattern ANCHOR = Pattern.compile("<a\\s[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMG = Pattern.compile("<img\\s[^>]*?(?:data-src|src)\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ALT = Pattern.compile("alt\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Words that mark a text as a missing-person report (English and Bangla). */
    public static boolean looksLikeMissing(String text) {
        if (text == null) return false;
        String l = text.toLowerCase(Locale.ROOT);
        return l.contains("missing") || l.contains("disappear") || l.contains("last seen") || l.contains("lost child")
                || l.contains("নিখোঁজ") || l.contains("নিখোজ") || l.contains("হারিয়ে");
    }

    /**
     * Cards from a page: every link that holds a picture and a few words, or whose own text speaks of a missing person.
     * Menu links, same-page anchors and mail / script links are skipped.
     */
    public static List<Item> extractCases(String html, String pageUrl, String sourceName) {
        List<Item> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;
        Set<String> seenLinks = new HashSet<>();
        Matcher m = ANCHOR.matcher(html);
        while (m.find() && out.size() < 60) {
            String href = m.group(1).trim();
            String inner = m.group(2);
            if (href.startsWith("#") || href.toLowerCase(Locale.ROOT).startsWith("javascript:") || href.toLowerCase(Locale.ROOT).startsWith("mailto:")
                    || href.toLowerCase(Locale.ROOT).startsWith("tel:")) continue;
            String link = absolute(pageUrl, href);
            if (link.isEmpty() || link.equals(pageUrl) || !seenLinks.add(link)) continue;
            String img = "";
            String alt = "";
            Matcher im = IMG.matcher(inner);
            if (im.find()) {
                img = absolute(pageUrl, im.group(1).trim());
                Matcher am = ALT.matcher(im.group(0));
                if (am.find()) alt = am.group(1);
            }
            String text = clean(TAGS.matcher(inner).replaceAll(" "));
            String title = text.length() >= 6 ? text : alt;
            title = clean(title);
            boolean hasPicture = !img.isEmpty();
            if (title.length() < 6) continue;
            if (!(hasPicture || looksLikeMissing(title))) continue;
            if (title.length() > 160) title = title.substring(0, 160);
            Item it = new Item();
            it.title = title;
            it.link = link;
            it.imageUrl = img;
            it.source = sourceName;
            it.text = title;
            it.id = idOf(link);
            out.add(it);
        }
        return out;
    }

    /** Items of a news RSS feed whose title speaks of a missing person. */
    public static List<Item> parseRss(String xml, String sourceName) {
        List<Item> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = ITEM.matcher(xml);
        while (m.find() && out.size() < 40) {
            String block = m.group(1);
            String title = clean(unCdata(tag(block, "title")));
            String link = clean(unCdata(tag(block, "link")));
            if (title.isEmpty() || !link.startsWith("http") || !looksLikeMissing(title)) continue;
            Item it = new Item();
            it.title = title;
            it.link = link;
            it.source = sourceName;
            it.text = title;
            it.id = idOf(link);
            out.add(it);
        }
        return out;
    }

    private static String tag(String block, String name) {
        Matcher m = Pattern.compile("<" + name + "[^>]*>(.*?)</" + name + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(block);
        return m.find() ? m.group(1) : "";
    }

    private static String unCdata(String s) {
        String t = s.trim();
        if (t.startsWith("<![CDATA[") && t.endsWith("]]>")) return t.substring(9, t.length() - 3);
        return t;
    }

    static String clean(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** Makes a link absolute against the page address. Returns "" for something that cannot be opened. */
    public static String absolute(String page, String href) {
        String h = href.trim().replace("&amp;", "&");
        if (h.startsWith("http://") || h.startsWith("https://")) return h;
        if (h.startsWith("//")) return "https:" + h;
        int schemeEnd = page.indexOf("://");
        if (schemeEnd < 0) return "";
        int hostEnd = page.indexOf('/', schemeEnd + 3);
        String origin = hostEnd < 0 ? page : page.substring(0, hostEnd);
        if (h.startsWith("/")) return origin + h;
        String base = page;
        int q = base.indexOf('?');
        if (q >= 0) base = base.substring(0, q);
        int slash = base.lastIndexOf('/');
        if (slash <= schemeEnd + 2) return origin + "/" + h;
        return base.substring(0, slash + 1) + h;
    }

    /** Stable short id of a link (ignores a trailing slash and a #fragment). */
    public static String idOf(String link) {
        String l = link == null ? "" : link.trim();
        int hash = l.indexOf('#');
        if (hash >= 0) l = l.substring(0, hash);
        while (l.endsWith("/")) l = l.substring(0, l.length() - 1);
        return Integer.toHexString(l.toLowerCase(Locale.ROOT).hashCode()) + Integer.toHexString(l.length());
    }

    /**
     * Is this report about the user's place? An empty place means "anywhere in Bangladesh". Otherwise the text must
     * name the place (a district, or a division with its districts): a text that names no place is not "near you".
     */
    public static boolean aboutPlace(String text, String place) {
        if (place == null || place.isEmpty()) return true;
        return Regions.mentionsPlace(text) && Regions.relevant(text, place);
    }

    /** New items worth a notification: not seen before, about the place, at most max. */
    public static List<Item> toNotify(List<Item> items, Set<String> seen, String place, int max) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            if (out.size() >= max) break;
            if (seen.contains(it.id)) continue;
            if (!aboutPlace(it.text, place)) continue;
            out.add(it);
        }
        return out;
    }

    public static String notificationTitle(Item it, String place) {
        String where = place == null || place.isEmpty() ? "Bangladesh" : place;
        return "Missing person report - " + where + " (unverified)";
    }

    // ------------------------------------------------------------------ robots.txt

    /**
     * Simple robots.txt reading for the "*" group: false when the path is disallowed. An Allow line that is longer
     * than the matching Disallow wins. Not a full parser, but enough to stay out of places a site asked bots to avoid.
     */
    public static boolean robotsAllows(String robots, String path) {
        if (robots == null || robots.isEmpty()) return true;
        String p = path == null || path.isEmpty() ? "/" : path;
        boolean inStar = false, groupHasAgent = false;
        int bestDisallow = -1, bestAllow = -1;
        for (String raw : robots.split("\\r?\\n")) {
            String line = raw;
            int c = line.indexOf('#');
            if (c >= 0) line = line.substring(0, c);
            line = line.trim();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String val = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (!groupHasAgent) inStar = false;
                groupHasAgent = true;
                if (val.equals("*")) inStar = true;
            } else {
                groupHasAgent = false;
                if (!inStar) continue;
                if (key.equals("disallow") && !val.isEmpty() && p.startsWith(val)) bestDisallow = Math.max(bestDisallow, val.length());
                if (key.equals("allow") && !val.isEmpty() && p.startsWith(val)) bestAllow = Math.max(bestAllow, val.length());
            }
        }
        return bestDisallow < 0 || bestAllow >= bestDisallow;
    }
}
