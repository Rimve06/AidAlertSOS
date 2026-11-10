package com.aidalert.sos.ai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.aidalert.sos.data.Prefs;

import java.util.ArrayList;
import java.util.List;

/** Assistant chat history in SQLite, kept separately for every account on the phone. */
public class ChatStore extends SQLiteOpenHelper {
    private final Context ctx;

    public ChatStore(Context c) {
        super(c.getApplicationContext(), "aid_chat.db", null, 2);
        ctx = c.getApplicationContext();
    }

    private String uid() {
        return Prefs.of(ctx).uid();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE chat(id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT DEFAULT '', role TEXT NOT NULL, text TEXT NOT NULL, mode TEXT, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            try {
                db.execSQL("ALTER TABLE chat ADD COLUMN user_id TEXT DEFAULT ''");   // old rows have no owner and are never shown
            } catch (RuntimeException ignored) {
                // column already there
            }
        }
    }

    public void add(String role, String text, String mode) {
        ContentValues v = new ContentValues();
        v.put("user_id", uid());
        v.put("role", role);
        v.put("text", text);
        v.put("mode", mode);
        v.put("ts", System.currentTimeMillis());
        getWritableDatabase().insert("chat", null, v);
    }

    public List<String[]> all() {
        List<String[]> out = new ArrayList<>();
        String u = uid();
        if (u.isEmpty()) return out;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT role,text,mode FROM chat WHERE user_id=? ORDER BY id DESC LIMIT 100", new String[]{u})) {
            while (c.moveToNext()) out.add(0, new String[]{c.getString(0), c.getString(1), c.getString(2)});
        }
        return out;
    }

    /** Clears the signed-in account's chat only. */
    public void clear() {
        getWritableDatabase().delete("chat", "user_id=?", new String[]{uid()});
    }
}
