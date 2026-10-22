package com.aidalert.sos.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import com.aidalert.sos.ui.BaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Missing persons and wanted criminals.
 *  - Citizens: read the copy stored in their own SQLite database (works offline).
 *  - Control center: reads from the server, can add records (pushed to every phone) and delete them.
 */
public class RecordsActivity extends BaseActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private boolean center;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        center = Prefs.of(this).isCenter();
        setTitle("Missing persons / wanted list");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.txtInfo.setVisibility(View.VISIBLE);
        b.txtInfo.setText(center ? "Records you add are delivered to every user's phone. Long-press a record to delete it."
                : "If you see any of these people, contact the control center. This list is stored on your phone.");
        adapter.setOnClick(r -> show((Db.Record) r.tag));
        if (center) {
            b.btnAdd.setVisibility(View.VISIBLE);
            b.btnAdd.setText("Add record");
            b.btnAdd.setOnClickListener(v -> addDialog());
            adapter.setOnLongClick(r -> confirmDelete((Db.Record) r.tag));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (!center) {
            render(Db.get(this).records());
            return;
        }
        AppExecutors.io(() -> {
            JSONObject all = new FirebaseRest(this).getObj("records");
            final List<Db.Record> list = new ArrayList<>();
            if (all != null) {
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = all.optJSONObject(k);
                    if (o == null) continue;
                    Db.Record r = new Db.Record();
                    r.id = k;
                    r.kind = o.optString("kind");
                    r.name = o.optString("name");
                    r.age = o.optString("age");
                    r.description = o.optString("description");
                    r.lastSeen = o.optString("lastSeen");
                    r.ts = o.optLong("ts");
                    list.add(r);
                }
                list.sort((x, y) -> Long.compare(y.ts, x.ts));
            }
            AppExecutors.main(() -> {
                if (!isFinishing()) render(list);
            });
        });
    }

    private void render(List<Db.Record> list) {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Record r : list) {
            boolean wanted = "WANTED".equals(r.kind);
            rows.add(new RowAdapter.Row(r.name + (r.age.isEmpty() ? "" : ", " + r.age),
                    r.lastSeen.isEmpty() ? r.description : "Last seen: " + r.lastSeen,
                    wanted ? "WANTED" : "MISSING", wanted ? 0xFF6A1B9A : 0xFFEF6C00, r));
        }
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.txtEmpty.setText("No records right now.");
    }

    private void show(Db.Record r) {
        new AlertDialog.Builder(this)
                .setTitle(r.kind + ": " + r.name)
                .setMessage("Age: " + r.age + "\nLast seen: " + r.lastSeen + "\n\n" + r.description + "\n\nAdded " + TimeUtil.clock(r.ts))
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmDelete(final Db.Record r) {
        new AlertDialog.Builder(this).setTitle("Delete record?").setMessage(r.name)
                .setPositiveButton("Delete", (d, w) -> AppExecutors.io(() -> {
                    new FirebaseRest(this).delete("records/" + r.id);
                    AppExecutors.main(this::load);
                }))
                .setNegativeButton("Cancel", null).show();
    }

    private void addDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final RadioGroup kind = new RadioGroup(this);
        kind.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton missing = new RadioButton(this);
        missing.setText("Missing person");
        missing.setId(View.generateViewId());
        RadioButton wanted = new RadioButton(this);
        wanted.setText("Wanted criminal");
        wanted.setId(View.generateViewId());
        kind.addView(missing);
        kind.addView(wanted);
        kind.check(missing.getId());
        final EditText name = field("Full name", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText age = field("Age", InputType.TYPE_CLASS_NUMBER);
        final EditText seen = field("Last seen (place / time)", InputType.TYPE_CLASS_TEXT);
        final EditText desc = field("Description, contact instructions", InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_CLASS_TEXT);
        box.addView(kind);
        box.addView(name);
        box.addView(age);
        box.addView(seen);
        box.addView(desc);
        new AlertDialog.Builder(this).setTitle("New record").setView(box)
                .setPositiveButton("Publish", (d, w) -> {
                    final String n = Ui.clean(name.getText());
                    if (n.isEmpty()) {
                        Ui.toast(this, "Name is required");
                        return;
                    }
                    final JSONObject o = new JSONObject();
                    try {
                        o.put("kind", kind.getCheckedRadioButtonId() == wanted.getId() ? "WANTED" : "MISSING");
                        o.put("name", n);
                        o.put("age", Ui.clean(age.getText()));
                        o.put("lastSeen", Ui.clean(seen.getText()));
                        o.put("description", Ui.clean(desc.getText()));
                        o.put("ts", System.currentTimeMillis());
                    } catch (JSONException e) {
                        return;
                    }
                    AppExecutors.io(() -> {
                        String key = new FirebaseRest(this).post("records", o);
                        AppExecutors.main(() -> {
                            Ui.toast(this, key == null ? "Failed to publish (offline?)" : "Published to all users");
                            load();
                        });
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        return e;
    }
}
