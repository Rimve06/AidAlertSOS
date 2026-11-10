package com.aidalert.sos.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
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
 * Two separate lists, opened with EXTRA_KIND:
 *  MISSING  missing people. Anyone can send a private sighting to the control center.
 *  WANTED   official police notices only. The control center must give the issuing authority, the
 *           case reference and the link to the official page; a notice without them is rejected.
 *           Users can never add a wanted notice: unverified accusations must not become listings.
 * Citizens read the SQLite copy on their phone (works offline). The control center reads the server.
 */
public class RecordsActivity extends BaseActivity {
    public static final String EXTRA_KIND = "kind";
    public static final String MISSING = "MISSING";
    public static final String WANTED = "WANTED";

    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private boolean center;
    private String kind = MISSING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        center = Prefs.of(this).isCenter();
        kind = WANTED.equals(getIntent().getStringExtra(EXTRA_KIND)) ? WANTED : MISSING;
        final boolean wanted = WANTED.equals(kind);
        setTitle(wanted ? "Wanted notices (official)" : "Missing persons");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.txtInfo.setVisibility(View.VISIBLE);
        b.btnAdd.setVisibility(View.VISIBLE);
        if (center) {
            b.txtInfo.setText(wanted
                    ? "Publish only notices issued by the police or another authority. Authority, case reference and the official link are required. Long-press to delete."
                    : "Records you add are delivered to every phone. Long-press to delete. Sightings sent by users are in Community > Switch view.");
            b.btnAdd.setText(wanted ? "Add official notice" : "Add missing person");
            b.btnAdd.setOnClickListener(v -> addDialog());
            adapter.setOnLongClick(r -> confirmDelete((Db.Record) r.tag));
        } else {
            b.txtInfo.setText(wanted
                    ? "Only official notices. Do not approach anyone. If you have information, call 999 or the control center."
                    : "If you may have seen one of these people, tap the person and send a private sighting. Do not post accusations publicly.");
            b.btnAdd.setText("Official lists (live pages)");
            b.btnAdd.setOnClickListener(v -> officialLists());
        }
        adapter.setOnClick(r -> show((Db.Record) r.tag));
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
                    r.authority = o.optString("authority");
                    r.ref = o.optString("ref");
                    r.sourceUrl = o.optString("sourceUrl");
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
        boolean wanted = WANTED.equals(kind);
        for (Db.Record r : list) {
            if (!kind.equals(r.kind)) continue;
            String sub = wanted
                    ? (r.authority.isEmpty() ? "Authority not stated" : r.authority) + (r.ref.isEmpty() ? "" : " · Ref " + r.ref)
                    : (r.lastSeen.isEmpty() ? r.description : "Last seen: " + r.lastSeen);
            rows.add(new RowAdapter.Row(r.name + (r.age.isEmpty() ? "" : ", " + r.age), sub,
                    wanted ? "WANTED" : "MISSING", wanted ? 0xFF6A1B9A : 0xFFEF6C00, r));
        }
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.txtEmpty.setText(wanted ? "No official notices on this phone right now." : "No missing-person records right now.");
    }

    private void show(final Db.Record r) {
        boolean wanted = WANTED.equals(r.kind);
        StringBuilder sb = new StringBuilder();
        if (!r.age.isEmpty()) sb.append("Age: ").append(r.age).append("\n");
        if (!wanted) sb.append("Last seen: ").append(r.lastSeen).append("\n");
        if (wanted) {
            sb.append("Issued by: ").append(r.authority.isEmpty() ? "not stated" : r.authority).append("\n");
            sb.append("Case reference: ").append(r.ref.isEmpty() ? "not stated" : r.ref).append("\n");
        }
        sb.append("\n").append(r.description).append("\n\nPublished ").append(TimeUtil.clock(r.ts));
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(r.name).setMessage(sb.toString())
                .setNegativeButton("Close", null);
        if (r.sourceUrl != null && r.sourceUrl.startsWith("http")) {
            d.setNeutralButton("Official page", (x, w) -> openUrl(r.sourceUrl));
        }
        if (!wanted && !center) d.setPositiveButton("Report a sighting", (x, w) -> sighting(r));
        d.show();
    }

    private void openUrl(String url) {
        startActivity(WebActivity.open(this, url, "Official page"));
    }

    /** Private report to the control center only; it is never shown to other users. */
    private void sighting(final Db.Record r) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final EditText where = field("Where did you see them?", InputType.TYPE_CLASS_TEXT);
        final EditText when = field("When?", InputType.TYPE_CLASS_TEXT);
        final EditText note = field("What did you notice? (clothes, who they were with)", InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_CLASS_TEXT);
        box.addView(where);
        box.addView(when);
        box.addView(note);
        new AlertDialog.Builder(this).setTitle("Sighting of " + r.name).setView(box)
                .setPositiveButton("Send privately", (d, w) -> {
                    String wh = Ui.clean(where.getText());
                    if (wh.length() < 3) {
                        Ui.toast(this, "Say where you saw them.");
                        return;
                    }
                    final JSONObject o = new JSONObject();
                    try {
                        o.put("type", "SIGHTING");
                        o.put("recordId", r.id);
                        o.put("recordName", r.name);
                        o.put("where", wh);
                        o.put("when", Ui.clean(when.getText()));
                        o.put("note", Ui.clean(note.getText()));
                        o.put("by", Prefs.of(this).uid());
                        o.put("byName", Prefs.of(this).name());
                        o.put("ts", System.currentTimeMillis());
                    } catch (JSONException e) {
                        return;
                    }
                    AppExecutors.io(() -> {
                        String key = new FirebaseRest(this).post("sightings", o);
                        AppExecutors.main(() -> Ui.toast(this, key != null
                                ? "Sent to the control center. Thank you."
                                : "Could not send. Sign in and check your connection."));
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void officialLists() {
        com.aidalert.sos.news.FeedConfig cfg = com.aidalert.sos.news.FeedConfig.load(this);
        java.util.List<com.aidalert.sos.news.FeedConfig.Feed> l = WANTED.equals(kind) ? cfg.wanted : cfg.missing;
        String[] n = new String[l.size()];
        String[] u = new String[l.size()];
        for (int i = 0; i < n.length; i++) {
            n[i] = l.get(i).name;
            u[i] = l.get(i).url;
        }
        if (n.length == 0) return;
        startActivity(SourceBrowserActivity.intent(this, WANTED.equals(kind) ? "Wanted notices" : "Missing persons", n, u, ""));
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
        final boolean wanted = WANTED.equals(kind);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final EditText name = field("Full name", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText age = field("Age", InputType.TYPE_CLASS_NUMBER);
        final EditText seen = field("Last seen (place / time)", InputType.TYPE_CLASS_TEXT);
        final EditText desc = field("Description, contact instructions", InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_CLASS_TEXT);
        final EditText authority = field("Issuing authority (e.g. Bangladesh Police)", InputType.TYPE_CLASS_TEXT);
        final EditText ref = field("Case / notice reference", InputType.TYPE_CLASS_TEXT);
        final EditText url = field("Link to the official notice (https://...)", InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(name);
        box.addView(age);
        if (!wanted) box.addView(seen);
        box.addView(desc);
        if (wanted) {
            box.addView(authority);
            box.addView(ref);
        }
        box.addView(url);
        new AlertDialog.Builder(this).setTitle(wanted ? "New official notice" : "New missing person").setView(box)
                .setPositiveButton("Publish", (d, w) -> {
                    final String n = Ui.clean(name.getText());
                    final String link = Ui.clean(url.getText());
                    if (n.isEmpty()) {
                        Ui.toast(this, "Name is required");
                        return;
                    }
                    if (wanted && (Ui.clean(authority.getText()).isEmpty() || Ui.clean(ref.getText()).isEmpty()
                            || !link.startsWith("http"))) {
                        Ui.toast(this, "A wanted notice needs the authority, the reference and the official link.");
                        return;
                    }
                    final JSONObject o = new JSONObject();
                    try {
                        o.put("kind", kind);
                        o.put("name", n);
                        o.put("age", Ui.clean(age.getText()));
                        o.put("lastSeen", Ui.clean(seen.getText()));
                        o.put("description", Ui.clean(desc.getText()));
                        o.put("authority", Ui.clean(authority.getText()));
                        o.put("ref", Ui.clean(ref.getText()));
                        o.put("sourceUrl", link);
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
