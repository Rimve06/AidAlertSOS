package com.aidalert.sos.net;

import android.content.Context;

import com.aidalert.sos.auth.AuthManager;
import com.aidalert.sos.blood.AccessInfo;
import com.aidalert.sos.data.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * "Server check": asks the database a few harmless questions with the signed-in account and says in plain words
 * which of the usual causes is stopping posts, comments, donors and requests. Background thread only.
 */
public final class ServerCheck {
    private ServerCheck() {}

    /** What the probes found. Plain data so the decision can be unit tested. */
    public static final class Facts {
        public boolean signedIn = true;
        public boolean tokenKnown;
        public boolean emailVerified, hasPhone;
        public String tokenProject = "";
        public String dbUrl = "";
        public long clockOffsetMs;
        public int pingCode;          // write to _ping/{uid}: only the NEWEST rules contain this node
        public String pingBody = "";
        public int communityCode, donorsCode;
        public int firestoreCode;
        public String firestoreBody = "";
    }

    public static final class Report {
        public final List<String> lines = new ArrayList<>();
        public String verdict = "";
        public boolean healthy;

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append('\n');
            sb.append('\n').append(verdict);
            return sb.toString();
        }
    }

    private static boolean refused(int code) {
        return code == 401 || code == 403;
    }

    /** The single most useful sentence about what to fix. */
    public static String decide(Facts f) {
        if (!f.signedIn) return "You are signed out. Sign in again, then run the check.";
        if (f.pingCode == -2) return "The server address is empty. Open Settings > Server and enter your Realtime Database address.";
        if (f.pingCode == -1) return "No connection to the server. Check the internet (mobile data or Wi-Fi) and run the check again.";
        if (!AccessInfo.sameProject(f.tokenProject, f.dbUrl)) {
            return "WRONG DATABASE. Your login belongs to Firebase project \"" + f.tokenProject + "\" but the database address is "
                    + f.dbUrl + ". Open Settings > Server and set the address shown at the top of Firebase console > Realtime Database "
                    + "(for this project it is usually https://" + f.tokenProject + "-default-rtdb.firebaseio.com ).";
        }
        String b = f.pingBody == null ? "" : f.pingBody.toLowerCase();
        if (f.pingCode == 404 || b.contains("ensure that you have the url") || b.contains("database lives in a different region")) {
            return "The database address is wrong or the database does not exist. Copy the exact address from Firebase console > "
                    + "Realtime Database (top of the Data tab) into Settings > Server.";
        }
        if (b.contains("auth token is invalid") || b.contains("could not parse auth token") || b.contains("auth token is expired")) {
            return "The server does not accept your login token. Sign out and sign in again. If it still fails, the app's "
                    + "google-services.json belongs to a different Firebase project than the database address.";
        }
        if (refused(f.pingCode)) {
            return "The rules that are LIVE on this database are not the newest ones (they have no \"_ping\" rule), or they were published "
                    + "to a different database/project, or you published the Firestore rules instead. In Firebase console open "
                    + "Build > Realtime Database > Rules (NOT Firestore), delete everything, paste the whole database.rules.json from the "
                    + "project, press Publish, and run this check again.";
        }
        if (!f.pingBody.isEmpty() && f.pingCode >= 400) {
            return "The server answered " + f.pingCode + ": " + f.pingBody;
        }
        if (refused(f.communityCode)) {
            return "The newest rules are live, but reading the community board is refused. Check that the \"community\" rule in the "
                    + "published rules matches database.rules.json and press Publish again.";
        }
        if (f.firestoreCode != 0 && f.firestoreCode != 200) {
            return FirestoreJson.explain("Donor list (Cloud Firestore)", f.firestoreCode, f.firestoreBody);
        }
        if (ServerClock.tooFarOff(f.clockOffsetMs)) {
            return "Everything is accepted, but your phone clock is " + ServerClock.describe(f.clockOffsetMs)
                    + ". The app corrects the time it sends, but please turn on Settings > Date and time > Automatic.";
        }
        return "All good: you are signed in, the newest rules are live, and the community board and donor list can be read and written.";
    }

    public static Report run(Context c) {
        Prefs prefs = Prefs.of(c);
        FirebaseRest rest = new FirebaseRest(c);
        Facts f = new Facts();
        Report r = new Report();
        f.dbUrl = prefs.dbUrl();
        r.lines.add("Database address: " + (f.dbUrl.isEmpty() ? "(empty)" : f.dbUrl));
        f.signedIn = prefs.isLoggedIn();
        String uid = prefs.uid();
        String token = f.signedIn ? AuthManager.idToken(true) : "";
        AccessInfo a = AccessInfo.fromToken(token);
        f.tokenKnown = a.known;
        f.emailVerified = a.emailVerified;
        f.hasPhone = a.hasPhone;
        f.tokenProject = a.project;
        r.lines.add("Signed in: " + (f.signedIn ? "yes" : "no") + (uid.isEmpty() ? "" : " (" + uid.substring(0, Math.min(6, uid.length())) + "...)"));
        r.lines.add("Login token: " + (a.known ? "read ok" : "NOT available")
                + " | email verified: " + (a.emailVerified ? "yes" : "NO") + " | phone number: " + (a.hasPhone ? "yes" : "NO")
                + " | Firebase project: " + (a.project.isEmpty() ? "unknown" : a.project));
        f.clockOffsetMs = rest.measureClock(true);
        r.lines.add("Phone clock: " + ServerClock.describe(f.clockOffsetMs));

        if (f.signedIn && !uid.isEmpty()) {
            FirebaseRest.Result ping = rest.request("PUT", "_ping/" + uid, null, String.valueOf(System.currentTimeMillis()));
            f.pingCode = ping.code;
            f.pingBody = ping.body;
            r.lines.add("Write test (newest rules only): " + show(ping));
            if (ping.ok()) rest.request("DELETE", "_ping/" + uid, null, null);
            FirebaseRest.Result com = rest.request("GET", "community", "shallow=true", null);
            f.communityCode = com.code;
            r.lines.add("Read community board: " + show(com));
            FirebaseRest.Result don = new FirestoreRest(c).list("donors");
            f.firestoreCode = don.code;
            f.firestoreBody = don.body;
            r.lines.add("Read donor list (Cloud Firestore, project " + FirestoreRest.projectOf(token, f.dbUrl) + "): " + show(don));
            FirebaseRest.Result req = rest.request("GET", "bloodRequests", "shallow=true", null);
            r.lines.add("Read blood requests: " + show(req));
        }
        r.verdict = decide(f);
        r.healthy = r.verdict.startsWith("All good");
        return r;
    }

    private static String show(FirebaseRest.Result x) {
        if (x.ok()) return "OK";
        String b = x.body.replaceAll("\\s+", " ");
        if (b.length() > 120) b = b.substring(0, 120) + "...";
        return "refused/failed (" + x.code + ") " + b;
    }
}
