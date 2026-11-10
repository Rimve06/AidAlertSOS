package com.aidalert.sos.report;

import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.engine.LocationHelper;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Perms;
import com.aidalert.sos.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Report an incident that is not an emergency call: what happened, where, with an optional photo or video, and
 * optionally without your name. Every report gets a tracking ID. The report is kept on the phone first and uploaded
 * when there is internet; the control center sets the status you can check later. For an emergency use SOS instead.
 */
public class ReportIncidentActivity extends BaseActivity {
    private static final int NAVY = 0xFF0B2A5B, MUTED = 0xFF667085, INK = 0xFF111827, RED = 0xFFD32F2F, GREEN = 0xFF2E7D32;

    private ReportStore store;
    private Prefs prefs;
    private LinearLayout history;
    private Spinner type;
    private EditText description, place;
    private TextView txtPhoto, txtVideo, txtLoc;
    private CheckBox anonymous;
    private String photoPath = "", videoPath = "";
    private double lat = Double.NaN, lon = Double.NaN;
    private LocationHelper locator;
    private boolean submitting;

    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> detectLocation());
    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> attach(uri, "photo"));
    private final ActivityResultLauncher<String> pickVideo =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> attach(uri, "video"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ReportStore(this);
        prefs = Prefs.of(this);
        locator = new LocationHelper(this);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(24));
        sv.addView(root);

        TextView head = new TextView(this);
        head.setText("Report an incident");
        head.setTextSize(18);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(NAVY);
        root.addView(head);
        TextView sub = new TextView(this);
        sub.setText("Help keep your community safe. In an emergency use the SOS button instead: a report is read later, an SOS is answered at once.");
        sub.setTextSize(12);
        sub.setTextColor(MUTED);
        root.addView(sub);

        root.addView(label("Type of incident"));
        type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ReportRules.TYPES));
        root.addView(type);

        root.addView(label("What happened?"));
        description = new EditText(this);
        description.setHint("Describe the incident: what, when, who was involved (no more than 1000 characters)");
        description.setMinLines(4);
        description.setGravity(Gravity.TOP);
        description.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(description);

        root.addView(label("Photo or video (optional)"));
        LinearLayout media = new LinearLayout(this);
        media.addView(button("Add photo", v -> pickPhoto.launch("image/*")));
        media.addView(button("Add video", v -> pickVideo.launch("video/*")));
        root.addView(media);
        txtPhoto = note("No photo");
        txtVideo = note("No video (kept on this phone, shared when the control center asks)");
        root.addView(txtPhoto);
        root.addView(txtVideo);

        root.addView(label("Location"));
        txtLoc = note("Not detected yet");
        root.addView(txtLoc);
        LinearLayout locRow = new LinearLayout(this);
        locRow.addView(button("Detect my location", v -> detectLocation()));
        root.addView(locRow);
        place = new EditText(this);
        place.setHint("Adjust: type a place / landmark, or latitude, longitude");
        place.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        root.addView(place);

        anonymous = new CheckBox(this);
        anonymous.setText("Report anonymously (your name and phone are not included)");
        root.addView(anonymous);

        com.google.android.material.button.MaterialButton submit = new com.google.android.material.button.MaterialButton(this);
        submit.setText("Submit report");
        submit.setAllCaps(false);
        submit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(RED));
        submit.setTextColor(0xFFFFFFFF);
        submit.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(12);
        root.addView(submit, sp);

        TextView hh = new TextView(this);
        hh.setText("My reports and their status");
        hh.setTextSize(16);
        hh.setTypeface(null, Typeface.BOLD);
        hh.setTextColor(NAVY);
        hh.setPadding(0, dp(20), 0, dp(4));
        root.addView(hh);
        history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        root.addView(history);

        setContentView(sv);
        setTitle("Report incident");
        detectLocation();
        renderHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (history != null) retryUploads();
    }

    @Override
    protected void onDestroy() {
        locator.stop();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(12);
        v.setTextColor(MUTED);
        v.setPadding(0, dp(12), 0, 0);
        return v;
    }

    private TextView note(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(12);
        v.setTextColor(INK);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private View button(String text, View.OnClickListener l) {
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(NAVY));
        b.setTextColor(0xFFFFFFFF);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    // ------------------------------------------------------------ media
    private void attach(final Uri uri, final String kind) {
        if (uri == null) return;
        AppExecutors.io(() -> {
            String path = "";
            String err = "";
            try {
                path = ReportMedia.copyIn(this, uri, kind);
            } catch (IOException | RuntimeException e) {
                err = e.getMessage() == null ? "Could not attach the file" : e.getMessage();
            }
            final String p = path, er = err;
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (!er.isEmpty()) {
                    Ui.toast(this, er);
                    return;
                }
                if (kind.equals("photo")) {
                    photoPath = p;
                    txtPhoto.setText("Photo attached");
                } else {
                    videoPath = p;
                    txtVideo.setText("Video attached (kept on this phone, shared when the control center asks)");
                }
            });
        });
    }

    // ------------------------------------------------------------ location
    private void detectLocation() {
        if (!Perms.location(this)) {
            txtLoc.setText("Location permission is off. Allow it, or type the place below.");
            if (!locationAsked) {
                locationAsked = true;
                locationPermission.launch(Perms.locationPerms());
            }
            return;
        }
        Location last = locator.lastKnown();
        if (last != null) gotFix(last);
        txtLoc.setText(last == null ? "Looking for your position..." : txtLoc.getText());
        boolean started = locator.start(2000, this::gotFix);
        if (!started && last == null) txtLoc.setText(locator.whyNoFix() + " You can type the place below.");
    }

    private boolean locationAsked;

    private void gotFix(Location l) {
        if (l == null) return;
        lat = l.getLatitude();
        lon = l.getLongitude();
        txtLoc.setText("Detected: " + ReportRules.formatLatLon(lat, lon)
                + (l.hasAccuracy() ? "  (about " + Math.round(l.getAccuracy()) + " m)" : ""));
    }

    // ------------------------------------------------------------ submit
    private void submit() {
        if (submitting) return;
        String err = ReportRules.check(type.getSelectedItemPosition(), description.getText().toString());
        if (err != null) {
            Ui.toast(this, err);
            return;
        }
        final String typed = Ui.clean(place.getText());
        double[] manual = ReportRules.parseLatLon(typed);
        final ReportStore.Report r = new ReportStore.Report();
        r.id = ReportRules.trackingId(System.currentTimeMillis(), new Random());
        r.userId = com.aidalert.sos.data.Prefs.of(this).uid();
        r.type = ReportRules.TYPES[type.getSelectedItemPosition()];
        r.description = description.getText().toString().trim();
        if (manual != null) {          // a typed position wins over the detected one
            r.lat = manual[0];
            r.lon = manual[1];
        } else {
            r.lat = lat;
            r.lon = lon;
            r.place = typed;
        }
        r.photo = photoPath;
        r.video = videoPath;
        r.anonymous = anonymous.isChecked();
        r.ts = System.currentTimeMillis();
        r.status = "";
        store.add(r);                  // safe on the phone before anything is sent
        submitting = true;
        description.setText("");
        place.setText("");
        photoPath = "";
        videoPath = "";
        txtPhoto.setText("No photo");
        txtVideo.setText("No video (kept on this phone, shared when the control center asks)");
        renderHistory();
        upload(r, true);
    }

    private JSONObject toJson(ReportStore.Report r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("uid", prefs.uid());
        o.put("type", r.type);
        o.put("description", r.description);
        o.put("place", r.place);
        if (r.hasFix()) {
            o.put("lat", r.lat);
            o.put("lon", r.lon);
        }
        o.put("anonymous", r.anonymous);
        if (!r.anonymous) {
            o.put("name", prefs.name());
            o.put("phone", prefs.phone());
        }
        o.put("ts", r.ts);
        o.put("hasVideo", !r.video.isEmpty());
        String thumb = ReportMedia.thumbBase64(r.photo);
        if (!thumb.isEmpty()) o.put("photo", thumb);
        return o;
    }

    private void upload(final ReportStore.Report r, final boolean showDialog) {
        if (!NetUtil.isOnline(this) || prefs.uid().isEmpty()) {
            submitting = false;
            if (showDialog) trackingDialog(r, "Saved on this phone. It is uploaded automatically when you are online and signed in.");
            return;
        }
        AppExecutors.io(() -> {
            String why = "";
            boolean ok = false;
            FirebaseRest rest = new FirebaseRest(this);
            try {
                FirebaseRest.Result res = rest.request("PUT", "incidentReports/" + r.id + "/report", null, toJson(r).toString());
                ok = res.ok();
                if (!ok) {
                    why = com.aidalert.sos.blood.AccessInfo.explain("Upload", res.code, res.body, rest.configured(),
                            com.aidalert.sos.blood.AccessInfo.fromToken(com.aidalert.sos.auth.AuthManager.idToken(true)));
                }
            } catch (JSONException e) {
                why = "Upload failed: " + e.getMessage();
            }
            if (ok) store.setSynced(r.id, true);
            final boolean done = ok;
            final String reason = why;
            AppExecutors.main(() -> {
                submitting = false;
                if (isFinishing()) return;
                renderHistory();
                if (showDialog) {
                    trackingDialog(r, done ? "Sent to the control center."
                            : "Saved on this phone but not uploaded yet.\n" + reason + "\nIt will be tried again when you open this screen.");
                }
            });
        });
    }

    private void trackingDialog(ReportStore.Report r, String message) {
        new AlertDialog.Builder(this).setTitle("Report submitted")
                .setMessage("Your tracking ID:\n\n" + r.id + "\n\n" + message + "\n\nKeep the ID: it is how you check the status.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy ID", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Tracking ID", r.id));
                    Ui.toast(this, "Copied " + r.id);
                }).show();
    }

    /** Reports that could not be uploaded earlier are sent again; the status of sent ones is refreshed. */
    private void retryUploads() {
        if (!NetUtil.isOnline(this) || prefs.uid().isEmpty()) return;
        final List<ReportStore.Report> all = store.all();
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            boolean changed = false;
            for (ReportStore.Report r : all) {
                try {
                    if (!r.synced) {
                        FirebaseRest.Result res = rest.request("PUT", "incidentReports/" + r.id + "/report", null, toJson(r).toString());
                        if (res.ok()) {
                            store.setSynced(r.id, true);
                            changed = true;
                        }
                    } else {
                        JSONObject ops = rest.getObj("incidentReports/" + r.id + "/ops");
                        if (ops != null && !ops.optString("status").equals(r.status)) {
                            store.setStatus(r.id, ops.optString("status"));
                            changed = true;
                        }
                    }
                } catch (JSONException ignored) {
                    // leave this one for next time
                }
            }
            if (changed) AppExecutors.main(() -> {
                if (!isFinishing()) renderHistory();
            });
        });
    }

    // ------------------------------------------------------------ my reports
    private void renderHistory() {
        history.removeAllViews();
        List<ReportStore.Report> all = store.all(com.aidalert.sos.data.Prefs.of(this).uid());
        if (all.isEmpty()) {
            history.addView(note("No reports yet."));
            return;
        }
        for (final ReportStore.Report r : all) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setBackgroundResource(com.aidalert.sos.R.drawable.bg_card);
            c.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            c.setLayoutParams(lp);
            TextView t = new TextView(this);
            t.setText(r.type + "  ·  " + r.id);
            t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(INK);
            TextView s = new TextView(this);
            String st = r.synced ? ReportRules.statusLabel(r.status) : "Waiting to upload";
            s.setText(st + "  ·  " + TimeUtil.clock(r.ts));
            s.setTextColor(r.synced ? GREEN : 0xFFEF6C00);
            s.setTextSize(12);
            c.addView(t);
            c.addView(s);
            c.setOnClickListener(v -> detail(r));
            history.addView(c);
        }
    }

    private void detail(final ReportStore.Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tracking ID: ").append(r.id).append('\n');
        sb.append("Status: ").append(r.synced ? ReportRules.statusLabel(r.status) : "Waiting to upload (saved on this phone)").append('\n');
        sb.append("Time: ").append(TimeUtil.clock(r.ts)).append('\n');
        if (r.hasFix()) sb.append("Location: ").append(ReportRules.formatLatLon(r.lat, r.lon)).append('\n');
        if (!r.place.isEmpty()) sb.append("Place: ").append(r.place).append('\n');
        sb.append(r.anonymous ? "Sent without your name.\n" : "Sent with your name and phone.\n");
        if (!r.photo.isEmpty()) sb.append("1 photo attached\n");
        if (!r.video.isEmpty()) sb.append("1 video attached (on this phone)\n");
        sb.append("\n").append(r.description);
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(r.type).setMessage(sb.toString())
                .setPositiveButton("Close", null);
        if (r.hasFix()) d.setNeutralButton("Open map", (x, w) -> Ui.openMap(this, r.lat, r.lon, r.id));
        d.show();
    }
}
