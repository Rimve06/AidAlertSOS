package com.aidalert.sos.medicine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the text of a prescription photo ON THE PHONE (Google ML Kit, no internet needed, the photo never leaves the phone).
 * The Latin model reads English words and numbers; Bangla and most handwriting are not read reliably.
 */
public final class PrescriptionOcr {
    private PrescriptionOcr() {}

    /** Called on the main thread. Exactly one of text / error is non-null. */
    public interface Callback {
        void done(String text, String error);
    }

    /** Decodes the photo (at most about 2000 px on the long side). */
    public static Bitmap decode(Context c, Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open the picture");
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 2000) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open the picture");
            bmp = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bmp == null) throw new IOException("Could not read the picture");
        return bmp;
    }

    /** Runs the on-device text recognition. */
    public static void read(Context c, Uri uri, final Callback cb) {
        final Bitmap bmp;
        try {
            bmp = decode(c, uri);
        } catch (IOException | RuntimeException e) {
            cb.done(null, e.getMessage() == null ? "Could not read the picture" : e.getMessage());
            return;
        }
        final TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(result -> {
                    closeQuietly(rec);
                    cb.done(result.getText(), null);
                })
                .addOnFailureListener(e -> {
                    closeQuietly(rec);
                    cb.done(null, e.getMessage() == null ? "Text recognition failed" : e.getMessage());
                });
    }

    private static void closeQuietly(TextRecognizer rec) {
        try {
            rec.close();
        } catch (Exception ignored) {
            // nothing to do
        }
    }

    /** Keeps a smaller copy of the prescription photo inside the app. Returns its path, or "" when it could not be saved. */
    public static String saveCopy(Context c, Uri uri) {
        try {
            Bitmap bmp = decode(c, uri);
            float scale = 1600f / Math.max(bmp.getWidth(), bmp.getHeight());
            if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * scale), Math.round(bmp.getHeight() * scale), true);
            File dir = new File(c.getFilesDir(), "prescriptions");
            if (!dir.exists() && !dir.mkdirs()) return "";
            File f = new File(dir, "rx_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, out);
            }
            return f.getAbsolutePath();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
