package com.aidalert.sos.medicine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** SQLite table of the user's confirmed medicines, isolated per signed-in account. */
public class MedicineStore extends SQLiteOpenHelper {
    public static final class Med {
        public long id;
        public String userId = "";
        public String name = "", dose = "", times = "", note = "";
        public int days;           // 0 = until stopped
        public long startMs;
        public long endMs;         // 0 = no end date
        public String strength = "", unit = "", frequency = "", food = "", image = "";
        public boolean active = true;
    }

    private final Context ctx;

    public MedicineStore(Context c) {
        super(c.getApplicationContext(), "aid_medicine.db", null, 3);
        ctx = c.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE med(id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT DEFAULT '', name TEXT NOT NULL, dose TEXT, times TEXT NOT NULL,"
                + " note TEXT, days INTEGER, start_ms INTEGER, active INTEGER,"
                + " strength TEXT, unit TEXT, frequency TEXT, end_ms INTEGER, food TEXT, image TEXT)");
        createLog(db);
    }

    private static void createLog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS dose_log(med_id INTEGER NOT NULL, day TEXT NOT NULL, time TEXT NOT NULL,"
                + " PRIMARY KEY(med_id, day, time))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            try {
                db.execSQL("ALTER TABLE med ADD COLUMN strength TEXT");
                db.execSQL("ALTER TABLE med ADD COLUMN unit TEXT");
                db.execSQL("ALTER TABLE med ADD COLUMN frequency TEXT");
                db.execSQL("ALTER TABLE med ADD COLUMN end_ms INTEGER");
                db.execSQL("ALTER TABLE med ADD COLUMN food TEXT");
                db.execSQL("ALTER TABLE med ADD COLUMN image TEXT");
            } catch (Exception ignored) {}
            createLog(db);
        }
        if (o < 3) {
            try {
                db.execSQL("ALTER TABLE med ADD COLUMN user_id TEXT DEFAULT ''");
            } catch (Exception ignored) {}
        }
    }

    /** Remember that a dose was taken (or forget it again). */
    public void setTaken(long medId, String day, String time, boolean taken) {
        SQLiteDatabase db = getWritableDatabase();
        if (taken) {
            ContentValues v = new ContentValues();
            v.put("med_id", medId);
            v.put("day", day);
            v.put("time", time);
            db.insertWithOnConflict("dose_log", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } else {
            db.delete("dose_log", "med_id=? AND day=? AND time=?", new String[]{String.valueOf(medId), day, time});
        }
    }

    public boolean isTaken(long medId, String day, String time) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM dose_log WHERE med_id=? AND day=? AND time=?",
                new String[]{String.valueOf(medId), day, time})) {
            return c.moveToFirst();
        }
    }

    public long add(Med m) {
        return add(m, m.userId);
    }

    public long add(Med m, String userId) {
        return add(m, userId, true);
    }

    private long add(Med m, String userId, boolean notify) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId == null ? "" : userId);
        v.put("name", m.name);
        v.put("dose", m.dose);
        v.put("times", m.times);
        v.put("note", m.note);
        v.put("days", m.days);
        v.put("start_ms", m.startMs == 0 ? System.currentTimeMillis() : m.startMs);
        v.put("active", m.active ? 1 : 0);
        v.put("strength", m.strength);
        v.put("unit", m.unit);
        v.put("frequency", m.frequency);
        v.put("end_ms", m.endMs);
        v.put("food", m.food);
        v.put("image", m.image);
        long id = getWritableDatabase().insert("med", null, v);
        if (notify) MedicineSync.changed(ctx);
        return id;
    }

    public void setActive(long id, boolean on) {
        ContentValues v = new ContentValues();
        v.put("active", on ? 1 : 0);
        getWritableDatabase().update("med", v, "id=?", new String[]{String.valueOf(id)});
        MedicineSync.changed(ctx);
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("dose_log", "med_id=?", new String[]{String.valueOf(id)});
        db.delete("med", "id=?", new String[]{String.valueOf(id)});
        MedicineSync.changed(ctx);
    }

    public Med get(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,dose,times,note,days,start_ms,active,strength,unit,frequency,end_ms,food,image,user_id FROM med WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Medicines of the signed-in account only. */
    public List<Med> all() {
        return all(com.aidalert.sos.data.Prefs.of(ctx).uid());
    }

    /** Every account's medicines on this phone (used only to cancel alarms at sign-out). */
    public List<Med> every() {
        return query("", null);
    }

    public List<Med> all(String userId) {
        if (userId == null || userId.isEmpty()) return new ArrayList<>();      // nobody signed in: nothing to show
        return query(" WHERE user_id=?", new String[]{userId});
    }

    private List<Med> query(String where, String[] args) {
        List<Med> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,dose,times,note,days,start_ms,active,strength,unit,frequency,end_ms,food,image,user_id FROM med"
                + where + " ORDER BY name", args)) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    /** Medicines saved by older versions have no owner: the first account that opens the list takes them, once. */
    private void claimLegacy(String userId) {
        android.content.SharedPreferences sp = ctx.getSharedPreferences("med_sync", Context.MODE_PRIVATE);
        if (sp.getBoolean("legacy_claimed", false)) return;
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        getWritableDatabase().update("med", v, "user_id IS NULL OR user_id=''", null);
        sp.edit().putBoolean("legacy_claimed", true).apply();
    }

    /** Replaces this account's medicines with the server copy. */
    public void replaceFor(String userId, List<Med> list) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("dose_log", "med_id IN (SELECT id FROM med WHERE user_id=?)", new String[]{userId});
            db.delete("med", "user_id=?", new String[]{userId});
            for (Med m : list) add(m, userId, false);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Removes the medicines and dose log of one account from this phone. */
    public void wipeFor(String userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("dose_log", "med_id IN (SELECT id FROM med WHERE user_id=?)", new String[]{userId});
        db.delete("med", "user_id=?", new String[]{userId});
    }

    private static Med read(Cursor c) {
        Med m = new Med();
        m.id = c.getLong(0);
        m.name = c.getString(1);
        m.dose = c.getString(2) == null ? "" : c.getString(2);
        m.times = c.getString(3);
        m.note = c.getString(4) == null ? "" : c.getString(4);
        m.days = c.getInt(5);
        m.startMs = c.getLong(6);
        m.active = c.getInt(7) == 1;
        m.strength = c.getString(8) == null ? "" : c.getString(8);
        m.unit = c.getString(9) == null ? "" : c.getString(9);
        m.frequency = c.getString(10) == null ? "" : c.getString(10);
        m.endMs = c.getLong(11);
        m.food = c.getString(12) == null ? "" : c.getString(12);
        m.image = c.getString(13) == null ? "" : c.getString(13);
        if (c.getColumnCount() > 14) m.userId = c.getString(14) == null ? "" : c.getString(14);
        return m;
    }
}
