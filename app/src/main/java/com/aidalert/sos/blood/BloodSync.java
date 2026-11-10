package com.aidalert.sos.blood;

import android.content.Context;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.net.FirestoreJson;
import com.aidalert.sos.net.FirestoreRest;

import org.json.JSONException;
import org.json.JSONObject;

/** Uploads the donor profile that was saved on this phone but not yet accepted by the server. Background thread only. */
public final class BloodSync {
    private BloodSync() {}

    /**
     * @return "" when nothing is pending or the upload worked, otherwise the reason it did not.
     * The profile counts as uploaded only when the donor record AND (if phone sharing is on) the contact record are accepted.
     */
    public static String pushPending(Context c) {
        BloodStore store = new BloodStore(c);
        Prefs prefs = Prefs.of(c);
        if (!store.minePending() || store.mineJson().isEmpty() || prefs.uid().isEmpty()) return "";
        FirestoreRest fs = new FirestoreRest(c);
        try {
            JSONObject mine = new JSONObject(store.mineJson());
            FirebaseRest.Result pr = fs.put("donors", prefs.uid(), mine);
            if (!pr.ok()) return fail(store, "Saving your donor profile", pr);
            if (mine.optBoolean("showPhone", false) && !store.minePhone().isEmpty()) {
                FirebaseRest.Result cr = fs.put("donorContacts", prefs.uid(), new JSONObject().put("phone", store.minePhone()));
                if (!cr.ok()) return fail(store, "Saving your phone number for donors", cr);
            } else {
                fs.delete("donorContacts", prefs.uid());
            }
            store.markMineSynced();
            store.setMeta("mine_error", "");
            return "";
        } catch (JSONException e) {
            return "Your saved donor profile could not be read.";
        }
    }

    private static String fail(BloodStore store, String what, FirebaseRest.Result r) {
        String msg = FirestoreJson.explain(what, r.code, r.body);
        store.setMeta("mine_error", msg);
        return msg;
    }
}
