package com.aidalert.sos.net;

import org.json.JSONObject;

/** Finds a Firebase uid from a phone number through phoneIndex/{hash}. Background thread only. */
public final class UserLookup {
    private UserLookup() {}

    /** @return the uid, or null if nobody registered that number (or the caller may not read the index) */
    public static String uidForPhone(FirebaseRest rest, String phone) {
        FirebaseRest.Result r = rest.get("phoneIndex/" + Crypto.phoneKey(phone));
        Object v = r.json();
        return v instanceof String ? (String) v : null;
    }
}
