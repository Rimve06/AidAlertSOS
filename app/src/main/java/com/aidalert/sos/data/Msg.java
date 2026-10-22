package com.aidalert.sos.data;

import org.json.JSONObject;

/** One chat message (center chat, community forum or direct message). */
public class Msg {
    public String key = "";
    public String from = "";   // CENTER or USER (center chat), otherwise empty
    public String uid = "";
    public String name = "";
    public String text = "";
    public long ts;

    public static Msg from(String key, JSONObject o) {
        Msg m = new Msg();
        m.key = key;
        m.from = o.optString("from", "");
        m.uid = o.optString("uid", "");
        m.name = o.optString("name", "");
        m.text = o.optString("text", "");
        m.ts = o.optLong("ts", 0);
        return m;
    }
}
