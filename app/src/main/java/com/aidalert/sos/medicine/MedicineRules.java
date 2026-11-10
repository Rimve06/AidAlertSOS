package com.aidalert.sos.medicine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeSet;

/** Pure Java rules for medicine reminders: times, the next alarm, and reading a scanned prescription's answer. */
public final class MedicineRules {
    private MedicineRules() {}

    public static final String VISION_PROMPT = "This is a photo of a medical prescription, possibly handwritten, in English and/or Bangla. "
            + "List ONLY the medicines you can actually read. Reply with a JSON array and nothing else: "
            + "[{\"name\":\"\",\"dose\":\"\",\"frequency\":\"\",\"duration\":\"\",\"unclear\":false}]. "
            + "Copy the name exactly as written. If any word is hard to read, still copy what you see and set \"unclear\":true. "
            + "Never guess a medicine name and never add medicines that are not on the paper.";

    public static final class Scanned {
        public String name = "", dose = "", frequency = "", duration = "", form = "", food = "";
        public boolean unclear;
    }

    /** "HH:mm" with hours 0-23 and minutes 0-59. */
    public static boolean validTime(String t) {
        if (t == null || !t.matches("\\d{1,2}:\\d{2}")) return false;
        String[] p = t.split(":");
        int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
        return h <= 23 && m <= 59;
    }

    public static String normalizeTime(String t) {
        String[] p = t.trim().split(":");
        return String.format(java.util.Locale.US, "%02d:%02d", Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    /** "8:00, 14:00 20:00" -> ["08:00","14:00","20:00"], sorted, no duplicates, invalid parts dropped. */
    public static List<String> parseTimes(String text) {
        TreeSet<String> set = new TreeSet<>();
        if (text != null) {
            for (String part : text.split("[,;\\s]+")) {
                String t = part.trim();
                if (validTime(t)) set.add(normalizeTime(t));
            }
        }
        return new ArrayList<>(set);
    }

    /** True when some non-empty text was typed but no valid time came out of it. */
    public static boolean hasBadTimes(String text) {
        if (text == null) return false;
        for (String part : text.split("[,;\\s]+")) {
            if (!part.trim().isEmpty() && !validTime(part.trim())) return true;
        }
        return false;
    }

    /** The next moment strictly after nowMs when the clock shows hhmm in the given zone. */
    public static long nextTrigger(long nowMs, String hhmm, TimeZone tz) {
        String[] p = hhmm.split(":");
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(nowMs);
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
        c.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= nowMs) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    /** days == 0 means "until I stop it". */
    public static boolean hasEnded(long startMs, int days, long nowMs) {
        return days > 0 && nowMs >= startMs + days * 86_400_000L;
    }

    /** First number in text such as "5 days" or "7 din"; 0 when there is none. */
    public static int daysFrom(String duration) {
        if (duration == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})").matcher(duration);
        if (!m.find()) return 0;
        int n = Integer.parseInt(m.group(1));
        String l = duration.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("week")) n *= 7;
        else if (l.contains("month")) n *= 30;
        return Math.min(n, 365);
    }

    /** JSON body for a Groq vision request: the prompt plus one JPEG sent as a data URL. */
    public static String visionBody(String model, String jpegBase64) throws JSONException {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", VISION_PROMPT));
        content.put(new JSONObject().put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + jpegBase64)));
        JSONArray msgs = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        return new JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.0)
                .put("max_tokens", 700).toString();
    }

    /** A string field of the model's answer; missing, null and the word "null" all give an empty string. */
    private static String textOf(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "").trim();
        return v.equalsIgnoreCase("null") ? "" : v;
    }

    /** Medicines from the model's reply. Anything that is not a JSON array of objects with a name gives an empty list. */
    public static List<Scanned> parseScan(String reply) {
        List<Scanned> out = new ArrayList<>();
        if (reply == null) return out;
        int a = reply.indexOf('['), b = reply.lastIndexOf(']');
        if (a < 0 || b <= a) return out;
        try {
            JSONArray arr = new JSONArray(reply.substring(a, b + 1));
            for (int i = 0; i < arr.length() && out.size() < 20; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Scanned s = new Scanned();
                s.name = o.optString("name").trim();
                if (s.name.isEmpty() || s.name.length() > 80) continue;
                s.dose = textOf(o, "dose");
                s.frequency = textOf(o, "frequency");
                s.duration = textOf(o, "duration");
                s.form = textOf(o, "form");
                s.food = textOf(o, "food");
                s.unclear = o.optBoolean("unclear", false);
                out.add(s);
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    // ------------------------------------------------------------ form choices
    public static final String[] FREQUENCIES = {"Once daily", "Twice daily", "Three times daily", "Four times daily",
            "Every 8 hours", "At bedtime", "Custom times"};
    public static final String[] UNITS = {"mg", "mcg", "g", "ml", "IU", "%", "tablet", "capsule", "drop", "puff"};
    public static final String[] FOODS = {"No matter", "Before food", "After food", "With food"};

    /** Suggested clock times for a frequency label from FREQUENCIES. Empty for custom times. */
    public static String timesForLabel(String label) {
        if (label == null) return "";
        switch (label) {
            case "Once daily": return "08:00";
            case "Twice daily": return "08:00,20:00";
            case "Three times daily": return "08:00,14:00,20:00";
            case "Four times daily": return "06:00,12:00,18:00,22:00";
            case "Every 8 hours": return "06:00,14:00,22:00";
            case "At bedtime": return "22:00";
            default: return "";
        }
    }

    /** Index in FREQUENCIES that matches the number of times, or the last (custom) entry. */
    public static int frequencyIndexForTimes(int count) {
        switch (count) {
            case 1: return 0;
            case 2: return 1;
            case 3: return 2;
            case 4: return 3;
            default: return FREQUENCIES.length - 1;
        }
    }

    // ------------------------------------------------------------ strength, dates
    private static final Pattern STRENGTH = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s?(mg|mcg|µg|ml|iu|g|%)(?![a-z])", Pattern.CASE_INSENSITIVE);

    /** "500 mg" / "500mg" -> {"500","mg"}; {"",""} when there is no strength in the text. */
    public static String[] splitStrength(String text) {
        if (text == null) return new String[]{"", ""};
        Matcher m = STRENGTH.matcher(text);
        if (!m.find()) return new String[]{"", ""};
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        if (unit.equals("iu")) unit = "IU";
        if (unit.equals("µg")) unit = "mcg";
        return new String[]{m.group(1), unit};
    }

    /** Whole days from start to end, both included. 0 when there is no end date; at least 1 otherwise. */
    public static int daysBetween(long startMs, long endMs, TimeZone tz) {
        if (endMs <= 0) return 0;
        Calendar a = Calendar.getInstance(tz), b = Calendar.getInstance(tz);
        a.setTimeInMillis(startMs);
        b.setTimeInMillis(endMs);
        clearTime(a);
        clearTime(b);
        long diff = Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000.0);
        return (int) Math.max(1, Math.min(diff + 1, 3650));
    }

    private static void clearTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    /** yyyy-MM-dd of a moment in the given zone: the key used to remember which doses were taken that day. */
    public static String dayKey(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** True when the dose time (HH:mm) of the given day is still ahead of nowMs. */
    public static boolean isUpcoming(long nowMs, String hhmm, TimeZone tz) {
        String[] p = hhmm.split(":");
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(nowMs);
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
        c.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() > nowMs;
    }

    /** Is the medicine due on the day of nowMs (started, not past its end)? */
    public static boolean isDueToday(long startMs, int days, long nowMs, TimeZone tz) {
        Calendar s = Calendar.getInstance(tz), n = Calendar.getInstance(tz);
        s.setTimeInMillis(startMs);
        n.setTimeInMillis(nowMs);
        clearTime(s);
        clearTime(n);
        if (n.getTimeInMillis() < s.getTimeInMillis()) return false;
        if (days <= 0) return true;
        long gone = Math.round((n.getTimeInMillis() - s.getTimeInMillis()) / 86_400_000.0);
        return gone < days;
    }

    // ------------------------------------------------------------ reading text (OCR) of a prescription
    public static final String TEXT_PROMPT = "Below is text that was read by OCR from a medical prescription (English, maybe with Bangla words). "
            + "It can contain mistakes. List ONLY medicines that are really in the text. Reply with a JSON array and nothing else: "
            + "[{\"name\":\"\",\"dose\":\"\",\"form\":\"\",\"frequency\":\"\",\"duration\":\"\",\"food\":\"\",\"unclear\":false}]. "
            + "name = medicine name only, dose = strength such as 500 mg, form = tablet/capsule/syrup/injection, "
            + "frequency as written (for example 1+0+1 or twice daily), duration as written (for example 7 days), "
            + "food = before food / after food / with food or empty. If a word looks damaged by OCR set \"unclear\":true. "
            + "Never invent a medicine and never add one that is not in the text.\n\nTEXT:\n";

    /** JSON body for a Groq text request that turns OCR text into a medicine list. */
    public static String textBody(String model, String ocrText) throws JSONException {
        String t = ocrText == null ? "" : ocrText;
        if (t.length() > 4000) t = t.substring(0, 4000);
        JSONArray msgs = new JSONArray().put(new JSONObject().put("role", "user").put("content", TEXT_PROMPT + t));
        JSONObject body = new JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.0)
                .put("max_tokens", 1500);
        if (model.startsWith("openai/gpt-oss")) body.put("reasoning_effort", "low");
        return body.toString();
    }

    private static final Pattern LINE_FORM = Pattern.compile(
            "^\\W*(?:\\d{1,2}\\s*[.)]\\s*)?(tab|tabs|tablet|cap|caps|capsule|syp|syrup|susp|suspension|inj|injection|drop|drops|oint|ointment|cream|gel|sachet|inh|inhaler|supp)\\b\\.?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_FREQ = Pattern.compile("(?<![\\d.])([01]\\s*[+\\-–]\\s*[01]\\s*[+\\-–]\\s*[01](?:\\s*[+\\-–]\\s*[01])?)(?![\\d])");
    private static final Pattern WORD_FREQ = Pattern.compile("\\b(OD|BD|BID|TDS|TID|QID|HS|SOS|once daily|twice daily|thrice daily|three times daily|four times daily|at night)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile("(\\d{1,3})\\s*(days?|din|weeks?|months?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOOD = Pattern.compile("\\b(before|after|with)\\s+(?:a\\s+)?(?:meal|meals|food|eating)\\b", Pattern.CASE_INSENSITIVE);

    private static String formName(String raw) {
        String l = raw.toLowerCase(Locale.ROOT);
        if (l.startsWith("tab")) return "Tablet";
        if (l.startsWith("cap")) return "Capsule";
        if (l.startsWith("syp") || l.startsWith("syr") || l.startsWith("susp")) return "Syrup";
        if (l.startsWith("inj")) return "Injection";
        if (l.startsWith("drop")) return "Drops";
        if (l.startsWith("oint")) return "Ointment";
        if (l.startsWith("cream")) return "Cream";
        if (l.startsWith("gel")) return "Gel";
        if (l.startsWith("sach")) return "Sachet";
        if (l.startsWith("inh")) return "Inhaler";
        return "Suppository";
    }

    /**
     * Offline reader for OCR text: finds lines that start with Tab./Cap./Syp./Inj. and so on, then the strength,
     * the 1+0+1 or BD style frequency, the duration and the food hint on that line or the line after it.
     * It only suggests; the user checks every result.
     */
    public static List<Scanned> parseOcrText(String text) {
        List<Scanned> out = new ArrayList<>();
        if (text == null) return out;
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length && out.size() < 20; i++) {
            Matcher lf = LINE_FORM.matcher(lines[i].trim());
            if (!lf.matches()) continue;
            String rest = lf.group(2).trim();
            if (rest.isEmpty()) continue;
            Scanned s = new Scanned();
            s.form = formName(lf.group(1));
            String block = rest;
            if (i + 1 < lines.length && !LINE_FORM.matcher(lines[i + 1].trim()).matches()) block = rest + " " + lines[i + 1];
            Matcher st = STRENGTH.matcher(rest);
            int cut = rest.length();
            if (st.find()) {
                s.dose = st.group(1) + " " + splitStrength(st.group())[1];
                cut = st.start();
            }
            Matcher pf = PATTERN_FREQ.matcher(block);
            Matcher wf = WORD_FREQ.matcher(block);
            if (pf.find()) {
                s.frequency = pf.group(1).replaceAll("\\s", "").replace('–', '+').replace('-', '+');
                if (pf.start() < cut && pf.start() < rest.length()) cut = Math.min(cut, pf.start());
            } else if (wf.find()) {
                s.frequency = wf.group(1);
                if (wf.start() < cut && wf.start() < rest.length()) cut = Math.min(cut, wf.start());
            }
            Matcher du = DURATION.matcher(block);
            if (du.find()) s.duration = du.group(1) + " " + du.group(2).toLowerCase(Locale.ROOT);
            Matcher fo = FOOD.matcher(block);
            if (fo.find()) s.food = fo.group(1).substring(0, 1).toUpperCase(Locale.ROOT) + fo.group(1).substring(1).toLowerCase(Locale.ROOT) + " food";
            String name = rest.substring(0, Math.max(0, Math.min(cut, rest.length())))
                    .replaceAll("[^\\p{L}\\p{N} \\-]", " ").replaceAll("\\s+", " ").trim();
            if (name.length() < 2 || name.length() > 60) continue;
            s.name = name;
            s.unclear = s.frequency.isEmpty();
            out.add(s);
        }
        return out;
    }

    /** Clock times for a scanned frequency such as 1+0+1, BD or "twice daily". Empty when unknown or "as needed". */
    public static String timesForScannedFrequency(String freq) {
        if (freq == null) return "";
        String f = freq.trim().toLowerCase(Locale.ROOT).replaceAll("\\s", "");
        if (f.matches("[01][+\\-][01][+\\-][01]")) {
            String[] p = f.split("[+\\-]");
            List<String> t = new ArrayList<>();
            if (p[0].equals("1")) t.add("08:00");
            if (p[1].equals("1")) t.add("14:00");
            if (p[2].equals("1")) t.add("20:00");
            return joinTimes(t);
        }
        if (f.matches("[01][+\\-][01][+\\-][01][+\\-][01]")) return "06:00,12:00,18:00,22:00";
        switch (f) {
            case "od": case "oncedaily": return "08:00";
            case "bd": case "bid": case "twicedaily": return "08:00,20:00";
            case "tds": case "tid": case "thricedaily": case "threetimesdaily": return "08:00,14:00,20:00";
            case "qid": case "fourtimesdaily": return "06:00,12:00,18:00,22:00";
            case "hs": case "atnight": return "22:00";
            default: return "";
        }
    }

    private static String joinTimes(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        return sb.toString();
    }

    /** "14:05" -> "2:05 PM". Unchanged when it is not a valid time. */
    public static String prettyTime(String hhmm) {
        if (!validTime(hhmm)) return hhmm == null ? "" : hhmm;
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]);
        String ampm = h >= 12 ? "PM" : "AM";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + p[1] + " " + ampm;
    }

    /** Index of the food choice in FOODS that matches free text such as "after meal"; 0 ("No matter") otherwise. */
    public static int foodIndex(String text) {
        if (text == null) return 0;
        String l = text.toLowerCase(Locale.ROOT);
        if (l.contains("before")) return 1;
        if (l.contains("after")) return 2;
        if (l.contains("with")) return 3;
        return 0;
    }

    /** Index of the unit in UNITS, or 0. */
    public static int unitIndex(String unit) {
        for (int i = 0; i < UNITS.length; i++) if (UNITS[i].equalsIgnoreCase(unit)) return i;
        return 0;
    }
}
