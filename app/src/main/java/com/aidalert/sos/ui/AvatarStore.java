package com.aidalert.sos.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** The profile picture: chosen from the gallery, cropped to a square, shrunk, and kept only on this phone. */
public final class AvatarStore {
    private AvatarStore() {}

    private static final int SIZE = 320;

    private static File file(Context c) {
        return new File(c.getFilesDir(), "avatar.jpg");
    }

    /** Pure: the sample size that brings the smaller side down to about target pixels or just above. */
    public static int sampleSize(int w, int h, int target) {
        int s = 1;
        int small = Math.min(w, h);
        while (small / (s * 2) >= target) s *= 2;
        return s;
    }

    /** Reads the picture at the uri, crops it to a centred square and saves it. @return true when saved */
    public static boolean save(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sampleSize(o.outWidth, o.outHeight, SIZE);
            Bitmap src;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                src = BitmapFactory.decodeStream(in, null, d);
            }
            if (src == null) return false;
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
            try (FileOutputStream fo = new FileOutputStream(file(c))) {
                out.compress(Bitmap.CompressFormat.JPEG, 85, fo);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** The saved picture, or null when there is none. */
    public static Bitmap load(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void remove(Context c) {
        File f = file(c);
        if (f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
    }
}
