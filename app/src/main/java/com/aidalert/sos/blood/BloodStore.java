package com.aidalert.sos.blood;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Offline copy of the donor list (SQLite), so donors can be searched without internet. */
public class BloodStore extends SQLiteOpenHelper {
    public BloodStore(Context c) {
        super(c.getApplicationContext(), "aid_blood.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE donor(uid TEXT PRIMARY KEY, name TEXT, grp TEXT, district TEXT, area TEXT, available INTEGER,"
                + " last_donation INTEGER, show_phone INTEGER, phone TEXT, ts INTEGER)");
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)");
        createExternal(db);
    }

    private static void createExternal(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS external(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, grp TEXT, district TEXT,"
                + " area TEXT, phone TEXT, source TEXT, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) createExternal(db);
    }

    /** Replaces the list with the latest from the server, keeping phone numbers already fetched for the same donors. */
    public void replaceAll(List<Donor> donors) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            java.util.Map<String, String> phones = new java.util.HashMap<>();
            try (Cursor c = db.rawQuery("SELECT uid,phone FROM donor WHERE phone<>''", null)) {
                while (c.moveToNext()) phones.put(c.getString(0), c.getString(1));
            }
            db.delete("donor", null, null);
            for (Donor d : donors) {
                ContentValues v = new ContentValues();
                v.put("uid", d.uid);
                v.put("name", d.name);
                v.put("grp", d.group);
                v.put("district", d.district);
                v.put("area", d.area);
                v.put("available", d.available ? 1 : 0);
                v.put("last_donation", d.lastDonation);
                v.put("show_phone", d.showPhone ? 1 : 0);
                String ph = d.showPhone && phones.containsKey(d.uid) ? phones.get(d.uid) : "";
                v.put("phone", ph);
                v.put("ts", d.ts);
                db.insert("donor", null, v);
            }
            ContentValues m = new ContentValues();
            m.put("k", "synced");
            m.put("v", String.valueOf(System.currentTimeMillis()));
            db.insertWithOnConflict("meta", null, m, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public String meta(String k) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{k})) {
            return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : "";
        }
    }

    public void setMeta(String k, String v) {
        ContentValues m = new ContentValues();
        m.put("k", k);
        m.put("v", v);
        getWritableDatabase().insertWithOnConflict("meta", null, m, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** The donor profile of this phone's owner, kept on the phone even when the server refuses it. "" when none. */
    public String mineJson() {
        return meta("mine_json");
    }

    public String mineJson(String uid) {
        if (uid == null || uid.isEmpty()) return mineJson();
        String val = meta("mine_json_" + uid);
        return val.isEmpty() ? mineJson() : val;
    }

    public boolean minePending() {
        return "1".equals(meta("mine_pending"));
    }

    public boolean minePending(String uid) {
        if (uid == null || uid.isEmpty()) return minePending();
        return "1".equals(meta("mine_pending_" + uid));
    }

    public String minePhone() {
        return meta("mine_phone");
    }

    public String minePhone(String uid) {
        if (uid == null || uid.isEmpty()) return minePhone();
        String val = meta("mine_phone_" + uid);
        return val.isEmpty() ? minePhone() : val;
    }

    public void saveMine(String json, String phone, boolean pending) {
        setMeta("mine_json", json);
        setMeta("mine_phone", phone == null ? "" : phone);
        setMeta("mine_pending", pending ? "1" : "0");
    }

    public void saveMine(String uid, String json, String phone, boolean pending) {
        saveMine(json, phone, pending);
        if (uid != null && !uid.isEmpty()) {
            setMeta("mine_json_" + uid, json);
            setMeta("mine_phone_" + uid, phone == null ? "" : phone);
            setMeta("mine_pending_" + uid, pending ? "1" : "0");
        }
    }

    public void markMineSynced() {
        setMeta("mine_pending", "0");
    }

    public void clearMine() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("meta", "k IN ('mine_json','mine_phone','mine_pending')", null);
    }

    public void setPhone(String uid, String phone) {
        ContentValues v = new ContentValues();
        v.put("phone", phone);
        getWritableDatabase().update("donor", v, "uid=?", new String[]{uid});
    }

    public List<Donor> all() {
        List<Donor> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT uid,name,grp,district,area,available,last_donation,show_phone,phone,ts FROM donor", null)) {
            while (c.moveToNext()) {
                Donor d = new Donor();
                d.uid = c.getString(0);
                d.name = c.getString(1);
                d.group = c.getString(2);
                d.district = c.getString(3);
                d.area = c.getString(4);
                d.available = c.getInt(5) == 1;
                d.lastDonation = c.getLong(6);
                d.showPhone = c.getInt(7) == 1;
                d.phone = c.getString(8) == null ? "" : c.getString(8);
                d.ts = c.getLong(9);
                out.add(d);
            }
        }
        return out;
    }

    public long lastSync() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k='synced'", null)) {
            return c.moveToFirst() ? Long.parseLong(c.getString(0)) : 0;
        }
    }

    /** Saves a donor the person found on a partner website, so it can be searched offline and called later. */
    public long addExternal(Donor d) {
        ContentValues v = new ContentValues();
        v.put("name", d.name);
        v.put("grp", d.group);
        v.put("district", d.district);
        v.put("area", d.area);
        v.put("phone", d.phone);
        v.put("source", d.source);
        v.put("ts", System.currentTimeMillis());
        return getWritableDatabase().insert("external", null, v);
    }

    public void removeExternal(String uid) {
        if (uid != null && uid.startsWith("ext:")) {
            getWritableDatabase().delete("external", "id=?", new String[]{uid.substring(4)});
        }
    }

    public List<Donor> external() {
        List<Donor> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,grp,district,area,phone,source,ts FROM external", null)) {
            while (c.moveToNext()) {
                Donor d = new Donor();
                d.uid = "ext:" + c.getLong(0);
                d.name = c.getString(1);
                d.group = c.getString(2);
                d.district = c.getString(3);
                d.area = c.getString(4) == null ? "" : c.getString(4);
                d.phone = c.getString(5) == null ? "" : c.getString(5);
                d.source = c.getString(6) == null ? "" : c.getString(6);
                d.ts = c.getLong(7);
                d.available = true;
                d.showPhone = true;
                out.add(d);
            }
        }
        return out;
    }
}
