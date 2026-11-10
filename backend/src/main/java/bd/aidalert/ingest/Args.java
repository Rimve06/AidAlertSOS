package bd.aidalert.ingest;

import java.util.HashMap;
import java.util.Map;

final class Args {
    private Args() {}

    /** --key value pairs; a flag with no value (like --dry-run) maps to "true". */
    static Map<String, String> parse(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) continue;
            String k = args[i].substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) m.put(k, args[++i]);
            else m.put(k, "true");
        }
        return m;
    }
}
