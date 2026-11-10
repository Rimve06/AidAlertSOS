package com.aidalert.sos.medicine;

import android.Manifest;
import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.aidalert.sos.ai.ChatRules;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.net.Http;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;

/**
 * Medicine reminders: today's schedule, adding a medicine by hand, or reading a prescription photo.
 * The photo is read on the phone (ML Kit). A scan only SUGGESTS: every name, strength and time is shown for the
 * user to check against the paper before anything is saved.
 */
public class MedicineActivity extends BaseActivity {
    private static final int NAVY = 0xFF0B2A5B, MUTED = 0xFF667085, INK = 0xFF111827, GREEN = 0xFF2E7D32,
            AMBER = 0xFFEF6C00, RED = 0xFFD32F2F;

    private MedicineStore store;
    private LinearLayout list;
    private TextView txtNote;
    private final Queue<MedicineRules.Scanned> pending = new ArrayDeque<>();
    private int scanTotal;
    private String scanImagePath = "";

    // state of the add form while its dialog is open
    private TextView attachLabel;
    private String attachedPath = "";

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
                if (!ok) Ui.toast(this, "Notifications are off, so reminders will not be shown.");
            });

    private final ActivityResultLauncher<String> pickScanImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::scan);

    private final ActivityResultLauncher<String> pickAttachImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::attach);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new MedicineStore(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView head = new TextView(this);
        head.setText("Your health matters.\nNever miss your scheduled medicine.");
        head.setTextSize(17);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(NAVY);
        root.addView(head);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, dp(10), 0, 0);
        buttons.addView(button("Scan Prescription", true, v -> askScan()));
        buttons.addView(button("Add Manually", false, v -> editDialog(null, 0, 0)));
        root.addView(buttons);

        txtNote = new TextView(this);
        txtNote.setTextSize(12);
        txtNote.setTextColor(MUTED);
        txtNote.setPadding(0, dp(8), 0, dp(8));
        showDisclaimer();
        root.addView(txtNote);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        setTitle("Medicine reminders");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) render();
        final android.content.Context app = getApplicationContext();
        AppExecutors.io(() -> {
            MedicineSync.sync(app);
            AppExecutors.main(() -> {
                if (isFinishing() || list == null) return;
                render();
                if (txtNote != null) txtNote.setText(MedicineSync.status(this));
            });
        });
    }

    private void showDisclaimer() {
        txtNote.setText("Reminders are only as correct as what you enter. Check names and doses against your prescription "
                + "or ask a pharmacist. This is not medical advice.");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private View button(String text, boolean filled, View.OnClickListener l) {
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setSingleLine(true);
        if (filled) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(RED));
            b.setTextColor(0xFFFFFFFF);
        } else {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(NAVY));
            b.setTextColor(0xFFFFFFFF);
        }
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(4);
        lp.leftMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(NAVY);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(com.aidalert.sos.R.drawable.bg_card);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    /** "Paracetamol 500 mg" style title of a medicine. */
    private static String title(MedicineStore.Med m) {
        String s = m.name;
        if (!m.strength.isEmpty()) s += "  " + m.strength + (m.unit.isEmpty() ? "" : " " + m.unit);
        else if (!m.dose.isEmpty()) s += "  " + m.dose;
        return s;
    }

    private static String details(MedicineStore.Med m) {
        List<String> parts = new ArrayList<>();
        if (!m.dose.isEmpty() && !m.strength.isEmpty()) parts.add(m.dose);
        if (!m.food.isEmpty() && !m.food.equals("No matter")) parts.add(m.food);
        return android.text.TextUtils.join(" · ", parts);
    }

    private void render() {
        list.removeAllViews();
        final List<MedicineStore.Med> all = store.all(com.aidalert.sos.data.Prefs.of(this).uid());
        final TimeZone tz = TimeZone.getDefault();
        final long now = System.currentTimeMillis();
        final String today = MedicineRules.dayKey(now, tz);

        // ---------------- Today's Schedule
        list.addView(heading("Today's Schedule"));
        final List<String[]> slots = new ArrayList<>();      // {time, medIndex}
        for (int i = 0; i < all.size(); i++) {
            MedicineStore.Med m = all.get(i);
            if (!m.active || MedicineRules.hasEnded(m.startMs, m.days, now)) continue;
            if (!MedicineRules.isDueToday(m.startMs, m.days, now, tz)) continue;
            for (String t : MedicineRules.parseTimes(m.times)) slots.add(new String[]{t, String.valueOf(i)});
        }
        Collections.sort(slots, (a, b) -> a[0].compareTo(b[0]));
        if (slots.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(all.isEmpty() ? "No medicines yet. Scan a prescription or add one manually."
                    : "Nothing is scheduled for today.");
            t.setTextColor(MUTED);
            t.setPadding(0, dp(8), 0, dp(8));
            list.addView(t);
        }
        for (final String[] slot : slots) {
            final MedicineStore.Med m = all.get(Integer.parseInt(slot[1]));
            final String time = slot[0];
            final boolean taken = store.isTaken(m.id, today, time);
            final boolean upcoming = MedicineRules.isUpcoming(now, time, tz);
            LinearLayout c = card();
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView tt = new TextView(this);
            tt.setText(MedicineRules.prettyTime(time));
            tt.setTextSize(15);
            tt.setTypeface(null, Typeface.BOLD);
            tt.setTextColor(NAVY);
            tt.setMinWidth(dp(78));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView n = new TextView(this);
            n.setText(title(m));
            n.setTextColor(INK);
            n.setTypeface(null, Typeface.BOLD);
            mid.addView(n);
            String det = details(m);
            if (!det.isEmpty()) {
                TextView d = new TextView(this);
                d.setText(det);
                d.setTextSize(12);
                d.setTextColor(MUTED);
                mid.addView(d);
            }
            TextView status = new TextView(this);
            status.setText(taken ? "Taken ✓" : upcoming ? "Upcoming" : "Mark taken");
            status.setTextColor(taken ? GREEN : upcoming ? NAVY : AMBER);
            status.setTypeface(null, Typeface.BOLD);
            status.setPadding(dp(8), dp(8), 0, dp(8));
            status.setOnClickListener(v -> {
                store.setTaken(m.id, today, time, !taken);
                render();
            });
            c.addView(tt);
            c.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
            c.addView(status);
            list.addView(c);
        }

        // ---------------- all medicines
        if (!all.isEmpty()) list.addView(heading("My medicines"));
        for (final MedicineStore.Med m : all) {
            LinearLayout c = card();
            TextView t = new TextView(this);
            t.setTextSize(16);
            t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(INK);
            t.setText(title(m));
            TextView d = new TextView(this);
            d.setTextColor(MUTED);
            boolean ended = MedicineRules.hasEnded(m.startMs, m.days, now);
            StringBuilder sb = new StringBuilder();
            if (!m.frequency.isEmpty()) sb.append(m.frequency).append(" · ");
            List<String> pretty = new ArrayList<>();
            for (String x : MedicineRules.parseTimes(m.times)) pretty.add(MedicineRules.prettyTime(x));
            sb.append(android.text.TextUtils.join(", ", pretty));
            String det = details(m);
            if (!det.isEmpty()) sb.append("\n").append(det);
            sb.append("\nFrom ").append(dateText(m.startMs)).append(m.endMs > 0 ? " to " + dateText(m.endMs)
                    : m.days > 0 ? " for " + m.days + " days" : ", until you stop it");
            if (ended) sb.append("  (finished)");
            else if (!m.active) sb.append("  (paused)");
            d.setText(sb.toString());
            LinearLayout row = new LinearLayout(this);
            row.addView(small(m.active ? "Pause" : "Resume", v -> {
                store.setActive(m.id, !m.active);
                m.active = !m.active;
                if (m.active) MedicineScheduler.schedule(this, m);
                else MedicineScheduler.cancel(this, m);
                render();
            }));
            if (!m.image.isEmpty()) row.addView(small("Prescription", v -> showImage(m.image)));
            row.addView(small("Delete", v -> new AlertDialog.Builder(this).setTitle("Delete " + m.name + "?")
                    .setPositiveButton("Delete", (x, w) -> {
                        MedicineScheduler.cancel(this, m);
                        store.delete(m.id);
                        render();
                    }).setNegativeButton("Cancel", null).show()));
            c.addView(t);
            c.addView(d);
            c.addView(row);
            list.addView(c);
        }
    }

    private static String dateText(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%02d/%02d/%04d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    private static long startOfDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void showImage(String path) {
        Bitmap bmp = BitmapFactory.decodeFile(path);
        if (bmp == null) {
            Ui.toast(this, "The prescription photo is no longer on this phone.");
            return;
        }
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(bmp);
        ScrollView sv = new ScrollView(this);
        sv.addView(iv);
        new AlertDialog.Builder(this).setTitle("Prescription").setView(sv).setPositiveButton("Close", null).show();
    }

    private View small(String text, View.OnClickListener l) {
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(this);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
        b.setTextColor(NAVY);
        b.setAllCaps(false);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    // ------------------------------------------------------------ add / confirm form
    private Spinner spinner(String[] items, int selected) {
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        sp.setSelection(Math.max(0, Math.min(selected, items.length - 1)));
        return sp;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    private com.google.android.material.button.MaterialButton dateButton(String text) {
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8EDF6));
        b.setTextColor(NAVY);
        return b;
    }

    /**
     * Add form. For a scanned item the fields are pre-filled from the paper; the user still checks everything and
     * presses save. Fields: name, strength, unit, dosage, frequency, times, start date, end date, food, prescription photo.
     */
    private void editDialog(final MedicineRules.Scanned s, int index, int total) {
        final long[] start = {startOfDay(System.currentTimeMillis())};
        final long[] end = {0};
        attachedPath = s == null ? "" : scanImagePath;

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        scroll.addView(box);

        String[] strengthParts = s == null ? new String[]{"", ""} : MedicineRules.splitStrength(s.dose);
        int scannedDays = s == null ? 0 : MedicineRules.daysFrom(s.duration);
        if (scannedDays > 0) end[0] = start[0] + (scannedDays - 1) * 86_400_000L;
        String scannedTimes = s == null ? "" : MedicineRules.timesForScannedFrequency(s.frequency);

        if (s != null) {
            TextView hint = new TextView(this);
            hint.setTextSize(12);
            hint.setTextColor(s.unclear ? RED : MUTED);
            hint.setText((s.unclear ? "The scan was not sure about this one. " : "")
                    + "Read on the paper: frequency \"" + s.frequency + "\", duration \"" + s.duration
                    + "\". Check every field against your prescription.");
            box.addView(hint);
        }

        final EditText name = field("Medicine name (as on the prescription)", s == null ? "" : s.name, false);
        box.addView(label("Medicine name"));
        box.addView(name);

        box.addView(label("Strength and unit"));
        LinearLayout strRow = new LinearLayout(this);
        final EditText strength = field("e.g. 500", strengthParts[0], false);
        strength.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final Spinner unit = spinner(MedicineRules.UNITS, MedicineRules.unitIndex(strengthParts[1]));
        strRow.addView(strength, new LinearLayout.LayoutParams(0, -2, 1f));
        strRow.addView(unit, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(strRow);

        box.addView(label("Dosage (how much each time)"));
        final EditText dose = field("e.g. 1 tablet", s == null || s.form.isEmpty() ? "" : "1 " + s.form.toLowerCase(Locale.ROOT), false);
        box.addView(dose);

        final List<String> freqLabels = java.util.Arrays.asList(MedicineRules.FREQUENCIES);
        int freqIdx = scannedTimes.isEmpty() ? 0 : MedicineRules.frequencyIndexForTimes(MedicineRules.parseTimes(scannedTimes).size());
        box.addView(label("Frequency"));
        final Spinner freq = spinner(MedicineRules.FREQUENCIES, freqIdx);
        box.addView(freq);

        box.addView(label("Times (24-hour, e.g. 08:00, 14:00, 20:00)"));
        final EditText times = field("08:00, 20:00", scannedTimes.isEmpty() ? MedicineRules.timesForLabel(MedicineRules.FREQUENCIES[freqIdx]).replace(",", ", ")
                : scannedTimes.replace(",", ", "), false);
        box.addView(times);
        freq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) {      // the initial selection must not overwrite what was pre-filled
                    first = false;
                    return;
                }
                String t = MedicineRules.timesForLabel(freqLabels.get(position));
                if (!t.isEmpty()) times.setText(t.replace(",", ", "));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        box.addView(label("Start date"));
        final com.google.android.material.button.MaterialButton startBtn = dateButton(dateText(start[0]));
        startBtn.setOnClickListener(v -> pickDate(start[0], ms -> {
            start[0] = ms;
            startBtn.setText(dateText(ms));
        }));
        box.addView(startBtn);

        box.addView(label("End date (optional)"));
        final com.google.android.material.button.MaterialButton endBtn = dateButton(end[0] > 0 ? dateText(end[0]) : "No end date - tap to choose");
        endBtn.setOnClickListener(v -> pickDate(end[0] > 0 ? end[0] : start[0], ms -> {
            end[0] = ms;
            endBtn.setText(dateText(ms));
        }));
        endBtn.setOnLongClickListener(v -> {
            end[0] = 0;
            endBtn.setText("No end date - tap to choose");
            return true;
        });
        box.addView(endBtn);
        TextView endHint = new TextView(this);
        endHint.setText("Long-press the end date to clear it.");
        endHint.setTextSize(11);
        endHint.setTextColor(MUTED);
        box.addView(endHint);

        box.addView(label("Food instructions"));
        final Spinner food = spinner(MedicineRules.FOODS, s == null ? 0 : MedicineRules.foodIndex(s.food));
        box.addView(food);

        box.addView(label("Prescription photo (optional)"));
        attachLabel = new TextView(this);
        attachLabel.setTextColor(NAVY);
        attachLabel.setTypeface(null, Typeface.BOLD);
        attachLabel.setPadding(0, dp(8), 0, dp(8));
        attachLabel.setText(attachedPath.isEmpty() ? "Tap to attach a photo" : "Photo attached (tap to change)");
        attachLabel.setOnClickListener(v -> pickAttachImage.launch("image/*"));
        box.addView(attachLabel);

        String title = "Add medicine";
        if (s != null) title = "Check against your prescription (" + (index + 1) + "/" + total + ")";
        final boolean scanned = s != null;
        final AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setPositiveButton("Save", null)
                .setNegativeButton(scanned ? "Skip" : "Cancel", (x, w) -> nextScanned()).create();
        d.setOnDismissListener(x -> attachLabel = null);
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = Ui.clean(name.getText());
            String tt = Ui.clean(times.getText());
            List<String> parsed = MedicineRules.parseTimes(tt);
            if (n.isEmpty() || parsed.isEmpty() || MedicineRules.hasBadTimes(tt)) {
                Ui.toast(this, "Enter a name and times like 08:00, 20:00.");
                return;
            }
            if (end[0] > 0 && end[0] < start[0]) {
                Ui.toast(this, "The end date is before the start date.");
                return;
            }
            MedicineStore.Med m = new MedicineStore.Med();
            m.name = n;
            m.strength = Ui.clean(strength.getText());
            m.unit = m.strength.isEmpty() ? "" : MedicineRules.UNITS[unit.getSelectedItemPosition()];
            m.dose = Ui.clean(dose.getText());
            m.frequency = MedicineRules.FREQUENCIES[freq.getSelectedItemPosition()];
            m.times = android.text.TextUtils.join(",", parsed);
            m.startMs = start[0];
            m.endMs = end[0];
            m.days = MedicineRules.daysBetween(start[0], end[0], TimeZone.getDefault());
            m.food = MedicineRules.FOODS[food.getSelectedItemPosition()];
            m.image = attachedPath;
            m.id = store.add(m, com.aidalert.sos.data.Prefs.of(this).uid());
            MedicineScheduler.schedule(this, m);
            d.dismiss();
            render();
            nextScanned();
        }));
        d.show();
    }

    private interface DateChosen {
        void chosen(long startOfDayMs);
    }

    private void pickDate(long initial, final DateChosen cb) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(initial);
        new DatePickerDialog(this, (view, y, mo, day) -> {
            Calendar r = Calendar.getInstance();
            r.set(y, mo, day, 0, 0, 0);
            r.set(Calendar.MILLISECOND, 0);
            cb.chosen(r.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private EditText field(String hint, String value, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(number ? android.text.InputType.TYPE_CLASS_NUMBER : android.text.InputType.TYPE_CLASS_TEXT);
        return e;
    }

    /** A photo chosen inside the add form: keep a copy in the app and show that it is attached. */
    private void attach(final Uri uri) {
        if (uri == null) return;
        AppExecutors.io(() -> {
            final String path = PrescriptionOcr.saveCopy(this, uri);
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (path.isEmpty()) {
                    Ui.toast(this, "Could not keep that photo.");
                    return;
                }
                attachedPath = path;
                if (attachLabel != null) attachLabel.setText("Photo attached (tap to change)");
            });
        });
    }

    // ------------------------------------------------------------ prescription scan
    private void askScan() {
        new AlertDialog.Builder(this).setTitle("Scan a prescription")
                .setMessage("The photo is read on your phone. If you are online, only the TEXT found in it (not the photo) is "
                        + "sent to Groq (an AI service) to pick out the medicines. Offline, a built-in reader is used.\n\n"
                        + "Printed English text works best; Bangla and handwriting are often missed. The result is only a "
                        + "suggestion: you check and save each medicine yourself.")
                .setPositiveButton("Choose photo", (d, w) -> pickScanImage.launch("image/*"))
                .setNegativeButton("Cancel", null).show();
    }

    private void scan(final Uri uri) {
        if (uri == null) return;
        txtNote.setText("Reading the prescription on your phone...");
        PrescriptionOcr.read(this, uri, (text, error) -> {
            if (isFinishing()) return;
            if (error != null) {
                showDisclaimer();
                Ui.toast(this, "Could not read the photo: " + error);
                return;
            }
            if (text == null || text.trim().isEmpty()) {
                showDisclaimer();
                Ui.toast(this, "No text was found in the photo. Try a clearer photo, or add the medicine manually.");
                return;
            }
            interpret(uri, text);
        });
    }

    /** Turns the OCR text into medicines: Groq if online, the built-in reader otherwise or when Groq fails. */
    private void interpret(final Uri uri, final String text) {
        final Prefs p = Prefs.of(this);
        final boolean tryOnline = NetUtil.isOnline(this) && !p.groqKey().isEmpty();
        if (tryOnline) txtNote.setText("Picking out the medicines...");
        AppExecutors.io(() -> {
            List<MedicineRules.Scanned> found = new ArrayList<>();
            String note = "";
            if (tryOnline) {
                String err = "";
                for (String model : ChatRules.candidates(p.groqModel())) {
                    try {
                        String json = Http.postJson(ChatRules.ENDPOINT, MedicineRules.textBody(model, text),
                                new String[][]{{"Authorization", "Bearer " + p.groqKey().trim()}}, 40000);
                        found = MedicineRules.parseScan(ChatRules.parseReply(json));
                        err = "";
                        break;
                    } catch (IOException | JSONException e) {
                        err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        if (!ChatRules.isModelProblem(err)) break;
                    }
                }
                if (!err.isEmpty()) note = "Online reading failed (" + err + "). Used the built-in reader.";
            }
            if (found.isEmpty()) found = MedicineRules.parseOcrText(text);
            final String path = PrescriptionOcr.saveCopy(this, uri);
            final List<MedicineRules.Scanned> res = found;
            final String nt = note;
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                showDisclaimer();
                if (!nt.isEmpty()) Ui.toast(this, nt);
                if (res.isEmpty()) {
                    Ui.toast(this, "No medicines were recognised in the text. Add them manually.");
                    return;
                }
                scanImagePath = path;
                pending.clear();
                pending.addAll(res);
                scanTotal = res.size();
                nextScanned();
            });
        });
    }

    private void nextScanned() {
        MedicineRules.Scanned s = pending.poll();
        if (s != null) editDialog(s, scanTotal - pending.size() - 1, scanTotal);
    }
}
