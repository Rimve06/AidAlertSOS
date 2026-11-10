package com.aidalert.sos.report;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Keeps the photo / video of a report inside the app and makes a small photo copy that is small enough to upload. */
public final class ReportMedia {
    private ReportMedia() {}

    public static final long MAX_BYTES = 60L * 1024 * 1024;
    public static final int MAX_THUMB_BYTES = 90 * 1024;

    /** Copies the chosen file into the app. Returns its path. Throws when it is unreadable or over 60 MB. */
    public static String copyIn(Context c, Uri uri, String prefix) throws IOException {
        String mime = c.getContentResolver().getType(uri);
        String ext = mime == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (ext == null || ext.isEmpty()) ext = prefix.equals("video") ? "mp4" : "jpg";
        File dir = new File(c.getFilesDir(), "reports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create the folder");
        File f = new File(dir, prefix + "_" + System.currentTimeMillis() + "." + ext);
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new IOException("Could not open the file");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    out.close();
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    throw new IOException("The file is bigger than 60 MB");
                }
                out.write(buf, 0, n);
            }
        }
        return f.getAbsolutePath();
    }

    /** A small JPEG (at most about 90 KB) of the photo as base64, or "" when it cannot be read. */
    public static String thumbBase64(String path) {
        if (path == null || path.isEmpty()) return "";
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return "";
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 1280) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(path, o2);
        if (bmp == null) return "";
        float scale = 800f / Math.max(bmp.getWidth(), bmp.getHeight());
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * scale), Math.round(bmp.getHeight() * scale), true);
        for (int q = 70; q >= 25; q -= 15) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out);
            if (out.size() <= MAX_THUMB_BYTES || q <= 25) {
                if (out.size() > MAX_THUMB_BYTES * 2) return "";
                return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            }
        }
        return "";
    }
}
