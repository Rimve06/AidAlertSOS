package com.aidalert.sos.auth;

/** Pure Java password policy used by the "set password" step. Returns an error text or null if fine. */
public final class PasswordRules {
    private PasswordRules() {}

    public static String check(String password, String confirm, String email) {
        if (password == null || password.length() < 8) return "Use at least 8 characters.";
        if (password.length() > 128) return "Use at most 128 characters.";
        if (!password.equals(confirm)) return "The two passwords do not match.";
        boolean letter = false, digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) return "Use both letters and numbers.";
        if (email != null && !email.isEmpty()) {
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            if (local.length() >= 4 && password.toLowerCase().contains(local.toLowerCase())) {
                return "The password must not contain your email name.";
            }
        }
        String lower = password.toLowerCase();
        if (lower.equals("password1") || lower.equals("12345678") || lower.equals("qwerty123")) {
            return "That password is too common.";
        }
        return null;
    }
}
