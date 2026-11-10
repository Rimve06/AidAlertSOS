package com.aidalert.sos.directory;

/** Pure Java: decides whether a string is plausible as a phone number. Never repairs or guesses digits. */
public final class PhoneRules {
    private PhoneRules() {}

    /** Digits, optional leading +, spaces and dashes only; 3 to 16 digits. Otherwise returns "". */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (!s.matches("\\+?[0-9][0-9 \\-]{1,20}[0-9]")) return "";
        int digits = s.replaceAll("[^0-9]", "").length();
        return digits >= 3 && digits <= 16 ? s : "";
    }
}
