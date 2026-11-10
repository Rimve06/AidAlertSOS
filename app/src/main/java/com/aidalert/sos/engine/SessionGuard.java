package com.aidalert.sos.engine;

import android.content.Context;

import com.aidalert.sos.auth.AuthManager;
import com.aidalert.sos.data.LocalData;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.FirebaseRest;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the phone and the Firebase console in step (call from a background thread, about once a minute):
 * - an account deleted or disabled in the console signs this phone out at once;
 * - a profile removed from the database is put back from the phone.
 */
public final class SessionGuard {
    private SessionGuard() {}

    /** @return false when the account is gone and the phone was signed out */
    public static boolean check(Context app) {
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn()) return true;
        FirebaseUser u = AuthManager.user();
        if (u == null || !u.getUid().equals(p.uid())) {
            endSession(app);
            return false;
        }
        try {
            Tasks.await(u.reload(), 10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof FirebaseAuthInvalidUserException || c instanceof FirebaseAuthInvalidCredentialsException) {
                endSession(app);
                return false;
            }
            return true;                       // network trouble: keep the session
        } catch (Exception e) {
            return true;
        }
        if (AuthManager.user() == null) {
            endSession(app);
            return false;
        }
        if (!p.isCenter()) restoreProfile(app, p, u);
        return true;
    }

    private static void restoreProfile(Context app, Prefs p, FirebaseUser u) {
        try {
            FirebaseRest rest = new FirebaseRest(app);
            FirebaseRest.Result r = rest.get("users/" + u.getUid());
            if (!r.ok() || r.obj() != null) return;      // refused/offline, or the profile is there
            JSONObject o = new JSONObject();
            o.put("name", p.name().length() < 2 ? "User" : p.name());
            o.put("phone", p.phone());
            o.put("email", u.getEmail() == null ? "" : u.getEmail());
            o.put("createdAt", System.currentTimeMillis());
            o.put("setupDone", true);
            rest.put("users/" + u.getUid(), o);
        } catch (JSONException | RuntimeException ignored) {
            // retried next round
        }
    }

    private static void endSession(Context app) {
        try {
            SosEngine.get(app).stopTracking();
            SosGuardService.stop(app);
            RelayManager.get(app).stop();
        } catch (RuntimeException ignored) {
            // keep going: the sign-out matters most
        }
        LocalData.wipeAccount(app);
        AuthManager.signOut();
        Prefs.of(app).logout();
    }
}
