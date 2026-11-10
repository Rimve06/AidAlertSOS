package bd.aidalert.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text of a crime-statistics table into the app's JSON. A table row is a name followed by numbers.
 * You give the column names in the order they appear; a row is accepted only when it has exactly that many numbers.
 * Everything else is listed in {@link Result#skipped} so a person can check it. Nothing is guessed.
 */
public final class StatsRowParser {
    private StatsRowParser() {}

    private static final Pattern ROW = Pattern.compile("^\\s*(\\p{L}[\\p{L} .()'&/-]*?)\\s+((?:[\\d,]+\\s*)+)$");

    public static final class Row {
        public final String name;
        public final long[] counts;

        Row(String name, long[] counts) {
            this.name = name;
            this.counts = counts;
        }
    }

    public static final class Result {
        public final List<Row> rows = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
    }

    public static Result parse(String text, int columns) {
        Result r = new Result();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            Matcher m = ROW.matcher(t);
            if (!m.matches()) {
                if (t.matches(".*\\d.*")) r.skipped.add(t);
                continue;
            }
            String[] nums = m.group(2).trim().split("\\s+");
            if (nums.length != columns) {
                r.skipped.add(t);
                continue;
            }
            long[] c = new long[columns];
            for (int i = 0; i < columns; i++) c[i] = Long.parseLong(nums[i].replace(",", ""));
            r.rows.add(new Row(m.group(1).trim(), c));
        }
        return r;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** JSON in the format the app reads (see docs/CRIME_STATS.md). groups maps a row name to its division. */
    public static String toJson(Result res, List<String> crimeTypes, String title, String period, String source,
                                String sourceUrl, Map<String, String> groups) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"title\":\"").append(esc(title)).append("\",\"period\":\"").append(esc(period))
                .append("\",\"source\":\"").append(esc(source)).append("\",\"sourceUrl\":\"").append(esc(sourceUrl))
                .append("\",\"crimeTypes\":[");
        for (int i = 0; i < crimeTypes.size(); i++) sb.append(i > 0 ? "," : "").append('"').append(esc(crimeTypes.get(i))).append('"');
        sb.append("],\"regions\":[");
        boolean first = true;
        for (Row row : res.rows) {
            if (!first) sb.append(',');
            first = false;
            String g = groups == null ? "" : groups.getOrDefault(row.name, "");
            sb.append("{\"name\":\"").append(esc(row.name)).append("\",\"group\":\"").append(esc(g)).append("\",\"counts\":[");
            for (int i = 0; i < row.counts.length; i++) sb.append(i > 0 ? "," : "").append(row.counts[i]);
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    static Map<String, String> parseGroups(String csv) {
        Map<String, String> m = new LinkedHashMap<>();
        if (csv == null) return m;
        for (String line : csv.split("\\R")) {
            int i = line.lastIndexOf(',');
            if (i > 0) m.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
        }
        return m;
    }
}
