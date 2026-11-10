package com.aidalert.sos.directory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.aidalert.sos.data.Db;

import java.util.ArrayList;
import java.util.List;

/** Plain SQLite access (no Room) for the offline directory tables created in Db. */
public class DirectoryStore {
    private final Db db;

    public DirectoryStore(Context c) {
        db = Db.get(c);
    }

    // ------------------------------------------------------------ meta
    public String meta(String key, String def) {
        try (Cursor c = db.getReadableDatabase().query("dir_meta", new String[]{"v"}, "k=?",
                new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : def;
        }
    }

    public void setMeta(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("k", key);
        v.put("v", value);
        db.getWritableDatabase().insertWithOnConflict("dir_meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ------------------------------------------------------------ hotlines
    public void replaceHotlines(List<DirectoryJson.Hotline> list) {
        SQLiteDatabase d = db.getWritableDatabase();
        d.beginTransaction();
        try {
            d.delete("hotline", null, null);
            for (DirectoryJson.Hotline h : list) {
                ContentValues v = new ContentValues();
                v.put("id", h.id);
                v.put("name", h.name);
                v.put("category", h.category);
                v.put("number", h.number);
                v.put("description", h.description);
                v.put("verified", h.verified ? 1 : 0);
                v.put("source", h.source);
                v.put("source_url", h.sourceUrl);
                d.insert("hotline", null, v);
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    public List<DirectoryJson.Hotline> hotlines() {
        List<DirectoryJson.Hotline> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query("hotline", null, null, null, null, null, "rowid ASC")) {
            while (c.moveToNext()) {
                DirectoryJson.Hotline h = new DirectoryJson.Hotline();
                h.id = c.getString(c.getColumnIndexOrThrow("id"));
                h.name = c.getString(c.getColumnIndexOrThrow("name"));
                h.category = c.getString(c.getColumnIndexOrThrow("category"));
                h.number = c.getString(c.getColumnIndexOrThrow("number"));
                h.description = c.getString(c.getColumnIndexOrThrow("description"));
                h.verified = c.getInt(c.getColumnIndexOrThrow("verified")) == 1;
                h.source = c.getString(c.getColumnIndexOrThrow("source"));
                h.sourceUrl = c.getString(c.getColumnIndexOrThrow("source_url"));
                out.add(h);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ facilities
    public int facilityCount() {
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM facility", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Upsert in one transaction; existing rows with the same id are replaced. */
    public void upsertFacilities(List<Facility> list, long now) {
        SQLiteDatabase d = db.getWritableDatabase();
        d.beginTransaction();
        try {
            for (Facility f : list) {
                ContentValues v = new ContentValues();
                v.put("id", f.id);
                v.put("kind", f.kind);
                v.put("name", f.name);
                v.put("phone", f.phone);
                v.put("phone_verified", f.phoneVerified ? 1 : 0);
                v.put("lat", f.lat);
                v.put("lon", f.lon);
                v.put("district", f.district);
                v.put("source", f.source);
                v.put("source_url", f.sourceUrl);
                v.put("updated", f.updated > 0 ? f.updated : now);
                d.insertWithOnConflict("facility", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    public List<Facility> facilities() {
        List<Facility> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().query("facility", null, null, null, null, null, "name ASC")) {
            while (c.moveToNext()) {
                Facility f = new Facility();
                f.id = c.getString(c.getColumnIndexOrThrow("id"));
                f.kind = c.getString(c.getColumnIndexOrThrow("kind"));
                f.name = c.getString(c.getColumnIndexOrThrow("name"));
                f.phone = c.getString(c.getColumnIndexOrThrow("phone"));
                f.phoneVerified = c.getInt(c.getColumnIndexOrThrow("phone_verified")) == 1;
                f.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
                f.lon = c.getDouble(c.getColumnIndexOrThrow("lon"));
                f.district = c.getString(c.getColumnIndexOrThrow("district"));
                f.source = c.getString(c.getColumnIndexOrThrow("source"));
                f.sourceUrl = c.getString(c.getColumnIndexOrThrow("source_url"));
                f.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
                out.add(f);
            }
        }
        return out;
    }
}
