package com.aidalert.sos.report;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** SQLite copy of the incident reports made on this phone, isolated per signed-in user. */
public class ReportStore extends SQLiteOpenHelper {
    public static final class Report {
        public String id = "", userId = "", type = "", description = "", place = "", photo = "", video = "", status = "";
        public double lat = Double.NaN, lon = Double.NaN;
        public boolean anonymous, synced;
        public long ts;

        public boolean hasFix() {
            return !Double.isNaN(lat) && !Double.isNaN(lon);
        }
    }

    public ReportStore(Context c) {
        super(c.getApplicationContext(), "aid_reports.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE report(id TEXT PRIMARY KEY, user_id TEXT DEFAULT '', type TEXT, description TEXT, place TEXT, photo TEXT, video TEXT,"
                + " lat REAL, lon REAL, has_fix INTEGER, anonymous INTEGER, synced INTEGER, status TEXT, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            try {
                db.execSQL("ALTER TABLE report ADD COLUMN user_id TEXT DEFAULT ''");
            } catch (Exception ignored) {}
        }
    }

    public void add(Report r) {
        add(r, r.userId);
    }

    public void add(Report r, String userId) {
        ContentValues v = new ContentValues();
        v.put("id", r.id);
        v.put("user_id", userId == null ? "" : userId);
        v.put("type", r.type);
        v.put("description", r.description);
        v.put("place", r.place);
        v.put("photo", r.photo);
        v.put("video", r.video);
        v.put("lat", r.hasFix() ? r.lat : 0);
        v.put("lon", r.hasFix() ? r.lon : 0);
        v.put("has_fix", r.hasFix() ? 1 : 0);
        v.put("anonymous", r.anonymous ? 1 : 0);
        v.put("synced", r.synced ? 1 : 0);
        v.put("status", r.status);
        v.put("ts", r.ts);
        getWritableDatabase().insertWithOnConflict("report", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void setSynced(String id, boolean synced) {
        ContentValues v = new ContentValues();
        v.put("synced", synced ? 1 : 0);
        getWritableDatabase().update("report", v, "id=?", new String[]{id});
    }

    public void setStatus(String id, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        getWritableDatabase().update("report", v, "id=?", new String[]{id});
    }

    public List<Report> all() {
        return all("");
    }

    public List<Report> all(String userId) {
        List<Report> out = new ArrayList<>();
        String query;
        String[] args;
        if (userId == null || userId.isEmpty()) {
            query = "SELECT id,type,description,place,photo,video,lat,lon,has_fix,anonymous,synced,status,ts,user_id FROM report ORDER BY ts DESC";
            args = null;
        } else {
            query = "SELECT id,type,description,place,photo,video,lat,lon,has_fix,anonymous,synced,status,ts,user_id FROM report WHERE user_id=? OR user_id='' ORDER BY ts DESC";
            args = new String[]{userId};
        }
        try (Cursor c = getReadableDatabase().rawQuery(query, args)) {
            while (c.moveToNext()) {
                Report r = new Report();
                r.id = c.getString(0);
                r.type = c.getString(1) == null ? "" : c.getString(1);
                r.description = c.getString(2) == null ? "" : c.getString(2);
                r.place = c.getString(3) == null ? "" : c.getString(3);
                r.photo = c.getString(4) == null ? "" : c.getString(4);
                r.video = c.getString(5) == null ? "" : c.getString(5);
                if (c.getInt(8) == 1) {
                    r.lat = c.getDouble(6);
                    r.lon = c.getDouble(7);
                }
                r.anonymous = c.getInt(9) == 1;
                r.synced = c.getInt(10) == 1;
                r.status = c.getString(11) == null ? "" : c.getString(11);
                r.ts = c.getLong(12);
                if (c.getColumnCount() > 13) r.userId = c.getString(13) == null ? "" : c.getString(13);
                out.add(r);
            }
        }
        return out;
    }
}
