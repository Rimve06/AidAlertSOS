package com.aidalert.sos.directory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** SQLite: routes the user has marked safe or unsafe, and the last route shown (so it is still there offline). */
public class MapStore extends SQLiteOpenHelper {
    public static final class Mark {
        public long id;
        public String label = "";
        public boolean safe;
        public String points = "";
        public long ts;
    }

    public MapStore(Context c) {
        super(c.getApplicationContext(), "aid_map.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE mark(id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT, safe INTEGER, points TEXT, ts INTEGER)");
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)");
        createRoute(db);
    }

    private static void createRoute(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS route(id INTEGER PRIMARY KEY AUTOINCREMENT, from_lat REAL, from_lon REAL,"
                + " to_lat REAL, to_lon REAL, mode TEXT, dist REAL, dur REAL, points TEXT, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) createRoute(db);
    }

    /** A real road route saved while online, so the same trip can be shown again with no internet. */
    public static final class Saved {
        public double fromLat, fromLon, toLat, toLon, dist, dur;
        public String mode = "", points = "";
        public long ts;
    }

    public void saveRoute(Saved s) {
        ContentValues v = new ContentValues();
        v.put("from_lat", s.fromLat);
        v.put("from_lon", s.fromLon);
        v.put("to_lat", s.toLat);
        v.put("to_lon", s.toLon);
        v.put("mode", s.mode);
        v.put("dist", s.dist);
        v.put("dur", s.dur);
        v.put("points", s.points);
        v.put("ts", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM route WHERE ABS(to_lat-?)<0.0012 AND ABS(to_lon-?)<0.0012 AND ABS(from_lat-?)<0.01 AND ABS(from_lon-?)<0.01 AND mode=?",
                new Object[]{s.toLat, s.toLon, s.fromLat, s.fromLon, s.mode});
        db.insert("route", null, v);
        db.execSQL("DELETE FROM route WHERE id NOT IN (SELECT id FROM route ORDER BY ts DESC LIMIT 400)");
    }

    public List<Saved> routesTo(double toLat, double toLon) {
        List<Saved> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT from_lat,from_lon,to_lat,to_lon,mode,dist,dur,points,ts FROM route WHERE ABS(to_lat-?)<0.0012 AND ABS(to_lon-?)<0.0012",
                new String[]{String.valueOf(toLat), String.valueOf(toLon)})) {
            while (c.moveToNext()) {
                Saved s = new Saved();
                s.fromLat = c.getDouble(0);
                s.fromLon = c.getDouble(1);
                s.toLat = c.getDouble(2);
                s.toLon = c.getDouble(3);
                s.mode = c.getString(4) == null ? "" : c.getString(4);
                s.dist = c.getDouble(5);
                s.dur = c.getDouble(6);
                s.points = c.getString(7) == null ? "" : c.getString(7);
                s.ts = c.getLong(8);
                out.add(s);
            }
        }
        return out;
    }

    public long addMark(String label, boolean safe, String points) {
        ContentValues v = new ContentValues();
        v.put("label", label);
        v.put("safe", safe ? 1 : 0);
        v.put("points", points);
        v.put("ts", System.currentTimeMillis());
        return getWritableDatabase().insert("mark", null, v);
    }

    public void deleteMark(long id) {
        getWritableDatabase().delete("mark", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Mark> marks() {
        List<Mark> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,label,safe,points,ts FROM mark ORDER BY ts DESC", null)) {
            while (c.moveToNext()) {
                Mark m = new Mark();
                m.id = c.getLong(0);
                m.label = c.getString(1) == null ? "" : c.getString(1);
                m.safe = c.getInt(2) == 1;
                m.points = c.getString(3) == null ? "" : c.getString(3);
                m.ts = c.getLong(4);
                out.add(m);
            }
        }
        return out;
    }

    public void setMeta(String k, String v) {
        ContentValues m = new ContentValues();
        m.put("k", k);
        m.put("v", v);
        getWritableDatabase().insertWithOnConflict("meta", null, m, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String meta(String k) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{k})) {
            return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : "";
        }
    }
}
