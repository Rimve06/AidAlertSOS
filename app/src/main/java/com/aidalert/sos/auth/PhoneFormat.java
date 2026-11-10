package com.aidalert.sos.auth;

/** Pure Java: turns what a person types into E.164 (+8801XXXXXXXXX), which Firebase Phone Auth requires. */
public final class PhoneFormat {
    private PhoneFormat() {}

    /** @return E.164 number, or "" if the input cannot be a valid number */
    public static String toE164(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[\\s\\-().]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        else if (s.startsWith("880")) s = "+" + s;
        else if (s.startsWith("0") && s.length() == 11) s = "+88" + s;     // 01XXXXXXXXX
        else if (s.length() == 10 && s.startsWith("1")) s = "+880" + s;   // 1XXXXXXXXX
        if (!s.matches("\\+[1-9][0-9]{7,14}")) return "";
        if (s.startsWith("+880") && !s.matches("\\+8801[3-9][0-9]{8}")) return ""; // Bangladesh mobile only
        return s;
    }
}
