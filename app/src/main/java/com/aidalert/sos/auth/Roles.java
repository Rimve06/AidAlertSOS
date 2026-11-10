package com.aidalert.sos.auth;

import com.aidalert.sos.data.Prefs;

/** Pure Java: which role a signed-in account may use, and whether the chosen sign-in tab matches it. */
public final class Roles {
    private Roles() {}

    public static final String ADMIN_EMAIL = "meshaidalertsos@gmail.com";
    public static final String ADMIN_PASSWORD = "meshaid@1";

    /** The ONLY account allowed to open the Control Center. */
    public static boolean isAdminEmail(String email) {
        return email != null && ADMIN_EMAIL.equalsIgnoreCase(email.trim());
    }

    /** The role comes ONLY from the server node roles/{uid}, which clients cannot write. */
    public static String fromServer(String value) {
        return Prefs.ROLE_CENTER.equals(value) ? Prefs.ROLE_CENTER : Prefs.ROLE_CITIZEN;
    }

    /** @return null if allowed, otherwise the message to show */
    public static String check(boolean wantsCenter, String actualRole) {
        boolean isCenter = Prefs.ROLE_CENTER.equals(actualRole);
        if (wantsCenter && !isCenter) return "This account is not a control center account. Ask the project owner to enable it.";
        if (!wantsCenter && isCenter) return "This is a control center account. Choose Control center to sign in.";
        return null;
    }
}
