package com.aidalert.sos.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Content provider exposing the local incident history (course topic: content providers).
 * Not exported: only this app reads it, through ContentResolver in the reports screen.
 *
 *   content://com.aidalert.sos.provider/incidents        all incidents
 *   content://com.aidalert.sos.provider/incidents/{id}   one incident
 */
public class IncidentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.aidalert.sos.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/incidents");

    private static final int INCIDENTS = 1;
    private static final int INCIDENT_ID = 2;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "incidents", INCIDENTS);
        MATCHER.addURI(AUTHORITY, "incidents/*", INCIDENT_ID);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private SQLiteDatabase readable() {
        return Db.get(getContext()).getReadableDatabase();
    }

    private SQLiteDatabase writable() {
        return Db.get(getContext()).getWritableDatabase();
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        Cursor c;
        switch (MATCHER.match(uri)) {
            case INCIDENTS:
                c = readable().query("incident", projection, selection, selectionArgs, null, null,
                        sortOrder == null ? "created DESC" : sortOrder);
                break;
            case INCIDENT_ID:
                c = readable().query("incident", projection, "id=?", new String[]{uri.getLastPathSegment()},
                        null, null, null);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case INCIDENTS: return "vnd.android.cursor.dir/vnd.aidalert.incident";
            case INCIDENT_ID: return "vnd.android.cursor.item/vnd.aidalert.incident";
            default: return null;
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (MATCHER.match(uri) != INCIDENTS || values == null) {
            throw new IllegalArgumentException("Unsupported insert " + uri);
        }
        writable().insertWithOnConflict("incident", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        return Uri.withAppendedPath(CONTENT_URI, values.getAsString("id"));
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int n;
        switch (MATCHER.match(uri)) {
            case INCIDENTS:
                n = writable().delete("incident", selection, selectionArgs);
                break;
            case INCIDENT_ID:
                n = writable().delete("incident", "id=?", new String[]{uri.getLastPathSegment()});
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (n > 0) getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        return n;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        if (values == null) return 0;
        int n;
        switch (MATCHER.match(uri)) {
            case INCIDENTS:
                n = writable().update("incident", values, selection, selectionArgs);
                break;
            case INCIDENT_ID:
                n = writable().update("incident", values, "id=?", new String[]{uri.getLastPathSegment()});
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (n > 0) getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        return n;
    }
}
