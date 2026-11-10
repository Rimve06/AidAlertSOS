package com.aidalert.sos.auth;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around Firebase Authentication. The SDK stores the session itself, so a user stays
 * signed in until logout (or until the account is disabled / the refresh token is revoked).
 * No password or hash is ever kept by this app.
 */
public final class AuthManager {
    private AuthManager() {}

    public static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseUser user() {
        return auth().getCurrentUser();
    }

    public static boolean hasPhone(FirebaseUser u) {
        return u != null && u.getPhoneNumber() != null && !u.getPhoneNumber().isEmpty();
    }

    public static boolean hasEmail(FirebaseUser u) {
        return u != null && u.getEmail() != null && !u.getEmail().isEmpty();
    }

    /**
     * Current ID token for the Realtime Database REST calls. The SDK refreshes it when it is about
     * to expire. Blocks, so call it from a background thread only.
     * @return the token, or "" if nobody is signed in or the refresh failed (offline)
     */
    public static String idToken() {
        return idToken(false);
    }

    /** @param force true to fetch fresh claims, e.g. right after the email was verified */
    public static String idToken(boolean force) {
        FirebaseUser u = user();
        if (u == null) return "";
        try {
            GetTokenResult r = Tasks.await(u.getIdToken(force), 10, TimeUnit.SECONDS);
            return r.getToken() == null ? "" : r.getToken();
        } catch (Exception e) {
            return "";
        }
    }

    /** Re-reads the account from Firebase (picks up emailVerified). Blocks: background thread only. */
    public static boolean reload() {
        FirebaseUser u = user();
        if (u == null) return false;
        try {
            Tasks.await(u.reload(), 10, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void signOut() {
        auth().signOut();
    }
}
