package com.aidalert.sos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite database (works offline): own incidents, emergency contacts, missing/wanted records
 * and the store-and-forward queue for the offline relay.
 */
public class Db extends SQLiteOpenHelper {
    private static final String NAME = "aidalert.db";
    private static final int VERSION = 3;
    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE incident (id TEXT PRIMARY KEY, user_id TEXT, user_name TEXT, phone TEXT, "
                + "type TEXT, source TEXT, status TEXT, via TEXT, lat REAL, lon REAL, acc REAL, silent INTEGER, "
                + "note TEXT, created INTEGER, updated INTEGER, synced INTEGER DEFAULT 0, mine INTEGER DEFAULT 0, "
                + "dispatched TEXT, ops_note TEXT)");
        db.execSQL("CREATE TABLE contact (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, phone TEXT, "
                + "is_primary INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE record_alert (id TEXT PRIMARY KEY, kind TEXT, name TEXT, age TEXT, "
                + "description TEXT, last_seen TEXT, ts INTEGER, authority TEXT DEFAULT '', ref TEXT DEFAULT '', "
                + "source_url TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE relay_packet (id TEXT PRIMARY KEY, payload TEXT, hops INTEGER, "
                + "created INTEGER, uploaded INTEGER DEFAULT 0)");
        createDirectoryTables(db);
    }

    /** Offline directory (plain SQLite): hotlines, facilities and a small key/value meta table. */
    private static void createDirectoryTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS hotline (id TEXT PRIMARY KEY, name TEXT, category TEXT, "
                + "number TEXT, description TEXT, verified INTEGER, source TEXT, source_url TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS facility (id TEXT PRIMARY KEY, kind TEXT, name TEXT, phone TEXT, "
                + "phone_verified INTEGER, lat REAL, lon REAL, district TEXT, source TEXT, source_url TEXT, "
                + "updated INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_facility_kind ON facility(kind)");
        db.execSQL("CREATE TABLE IF NOT EXISTS dir_meta (k TEXT PRIMARY KEY, v TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Incremental: never drop the user's own incidents, contacts or relay queue.
        if (oldV < 2) createDirectoryTables(db);
        if (oldV < 3) {
            db.execSQL("ALTER TABLE record_alert ADD COLUMN authority TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE record_alert ADD COLUMN ref TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE record_alert ADD COLUMN source_url TEXT DEFAULT ''");
        }
    }

    // ================================================================ incidents

    public void saveIncident(Incident i) {
        getWritableDatabase().insertWithOnConflict("incident", null, i.toValues(), SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Incident getIncident(String id) {
        try (Cursor c = getReadableDatabase().query("incident", null, "id=?", new String[]{id}, null, null, null)) {
            return c.moveToFirst() ? Incident.fromCursor(c) : null;
        }
    }

    public List<Incident> incidents(boolean onlyMine) {
        List<Incident> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("incident", null, onlyMine ? "mine=1" : null, null,
                null, null, "created DESC")) {
            while (c.moveToNext()) out.add(Incident.fromCursor(c));
        }
        return out;
    }

    public List<Incident> unsyncedMine() {
        List<Incident> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("incident", null, "mine=1 AND synced=0", null,
                null, null, "created ASC")) {
            while (c.moveToNext()) out.add(Incident.fromCursor(c));
        }
        return out;
    }

    public void setSynced(String id, boolean synced) {
        ContentValues v = new ContentValues();
        v.put("synced", synced ? 1 : 0);
        getWritableDatabase().update("incident", v, "id=?", new String[]{id});
    }

    // ================================================================ contacts

    public static class Contact {
        public long id;
        public String name;
        public String phone;
        public boolean primary;
    }

    public List<Contact> contacts() {
        List<Contact> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("contact", null, null, null, null, null, "is_primary DESC, _id ASC")) {
            while (c.moveToNext()) {
                Contact x = new Contact();
                x.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                x.name = c.getString(c.getColumnIndexOrThrow("name"));
                x.phone = c.getString(c.getColumnIndexOrThrow("phone"));
                x.primary = c.getInt(c.getColumnIndexOrThrow("is_primary")) == 1;
                out.add(x);
            }
        }
        return out;
    }

    public void addContact(String name, String phone) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("phone", phone);
        v.put("is_primary", contacts().isEmpty() ? 1 : 0);
        getWritableDatabase().insert("contact", null, v);
    }

    public void deleteContact(long id) {
        getWritableDatabase().delete("contact", "_id=?", new String[]{String.valueOf(id)});
    }

    public void setPrimaryContact(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE contact SET is_primary=0");
            db.execSQL("UPDATE contact SET is_primary=1 WHERE _id=" + id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ================================================================ missing / wanted records

    public static class Record {
        public String id = "";
        public String kind = "MISSING";
        public String name = "";
        public String age = "";
        public String description = "";
        public String lastSeen = "";
        public long ts;
        /** Wanted notices only: who issued it, their case reference, and the official page. */
        public String authority = "";
        public String ref = "";
        public String sourceUrl = "";
    }

    public void replaceRecords(List<Record> list) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("record_alert", null, null);
            for (Record r : list) {
                ContentValues v = new ContentValues();
                v.put("id", r.id);
                v.put("kind", r.kind);
                v.put("name", r.name);
                v.put("age", r.age);
                v.put("description", r.description);
                v.put("last_seen", r.lastSeen);
                v.put("ts", r.ts);
                v.put("authority", r.authority);
                v.put("ref", r.ref);
                v.put("source_url", r.sourceUrl);
                db.insert("record_alert", null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Record> records() {
        List<Record> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("record_alert", null, null, null, null, null, "ts DESC")) {
            while (c.moveToNext()) {
                Record r = new Record();
                r.id = c.getString(c.getColumnIndexOrThrow("id"));
                r.kind = c.getString(c.getColumnIndexOrThrow("kind"));
                r.name = c.getString(c.getColumnIndexOrThrow("name"));
                r.age = c.getString(c.getColumnIndexOrThrow("age"));
                r.description = c.getString(c.getColumnIndexOrThrow("description"));
                r.lastSeen = c.getString(c.getColumnIndexOrThrow("last_seen"));
                r.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                r.authority = c.getString(c.getColumnIndexOrThrow("authority"));
                r.ref = c.getString(c.getColumnIndexOrThrow("ref"));
                r.sourceUrl = c.getString(c.getColumnIndexOrThrow("source_url"));
                out.add(r);
            }
        }
        return out;
    }

    // ================================================================ relay queue

    public static class Packet {
        public String id;
        public String payload;
        public int hops;
        public long created;
        public boolean uploaded;
    }

    /** @return true if the packet was new (not seen before). */
    public synchronized boolean savePacket(Packet p) {
        ContentValues v = new ContentValues();
        v.put("id", p.id);
        v.put("payload", p.payload);
        v.put("hops", p.hops);
        v.put("created", p.created);
        v.put("uploaded", p.uploaded ? 1 : 0);
        return getWritableDatabase().insertWithOnConflict("relay_packet", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public synchronized List<Packet> pendingPackets() {
        List<Packet> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("relay_packet", null, "uploaded=0", null, null, null, "created ASC")) {
            while (c.moveToNext()) {
                Packet p = new Packet();
                p.id = c.getString(c.getColumnIndexOrThrow("id"));
                p.payload = c.getString(c.getColumnIndexOrThrow("payload"));
                p.hops = c.getInt(c.getColumnIndexOrThrow("hops"));
                p.created = c.getLong(c.getColumnIndexOrThrow("created"));
                out.add(p);
            }
        }
        return out;
    }

    public synchronized void markPacketUploaded(String id) {
        ContentValues v = new ContentValues();
        v.put("uploaded", 1);
        getWritableDatabase().update("relay_packet", v, "id=?", new String[]{id});
    }

    /** Delete old, already delivered packets so the table does not grow forever. */
    public synchronized void prunePackets(long olderThanMs) {
        getWritableDatabase().delete("relay_packet", "uploaded=1 AND created<?",
                new String[]{String.valueOf(olderThanMs)});
    }
}
