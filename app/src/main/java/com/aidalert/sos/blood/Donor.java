package com.aidalert.sos.blood;

import org.json.JSONObject;

/** One donor in the AidAlert donor list. The phone is only known when the donor agreed to show it (showPhone). */
public class Donor {
    public String uid = "", name = "", group = "", district = "", area = "", phone = "";
    /** Empty for the AidAlert list; otherwise the partner site the person copied this donor from. */
    public String source = "";
    public boolean available, showPhone;
    public long lastDonation, ts;

    public static Donor fromJson(String uid, JSONObject o) {
        Donor d = new Donor();
        d.uid = uid;
        d.name = o.optString("name", "Donor");
        d.group = o.optString("group");
        d.district = o.optString("district");
        d.area = o.optString("area");
        d.available = o.optBoolean("available", false);
        d.showPhone = o.optBoolean("showPhone", false);
        d.lastDonation = o.optLong("lastDonation");
        d.ts = o.optLong("ts");
        return d;
    }
}
