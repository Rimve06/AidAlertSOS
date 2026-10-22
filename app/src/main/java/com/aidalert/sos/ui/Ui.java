package com.aidalert.sos.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.aidalert.sos.util.Geo;

/** Small UI helpers shared by the activities. */
public final class Ui {
    private Ui() {}

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    public static void hideKeyboard(Activity a) {
        View v = a.getCurrentFocus();
        if (v == null) return;
        InputMethodManager im = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /** Implicit intent: any installed maps app opens the position. */
    public static void openMap(Context c, double lat, double lon, String label) {
        Uri uri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(" + Uri.encode(label) + ")");
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        if (i.resolveActivity(c.getPackageManager()) == null) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(Geo.mapsLink(lat, lon)));
        }
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            toast(c, "No maps app found");
        }
    }

    public static String clean(CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }
}
