package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.databinding.ActivityDisasterAdminBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.TimeUtil;
import com.aidalert.sos.view.MapView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Disaster mode, operator side. The query goes to every app; each user chooses "safe" or "need rescue".
 * Only those answers are plotted (location is never read otherwise). Distressed people can switch their
 * sharing off, which deletes their dot. The list can be handed to the national rescue system.
 */
public class DisasterAdminActivity extends AppCompatActivity {
    private ActivityDisasterAdminBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private final List<String[]> distressed = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDisasterAdminBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Disaster check-ins");
        b.listDistress.setLayoutManager(new LinearLayoutManager(this));
        b.listDistress.setAdapter(adapter);
        adapter.setOnClick(r -> {
            String[] t = (String[]) r.tag;
            Ui.openMap(this, Double.parseDouble(t[2]), Double.parseDouble(t[3]), t[0]);
        });

        b.btnSend.setOnClickListener(v -> sendQuery());
        b.btnEnd.setOnClickListener(v -> endAll());
        b.btnRefresh.setOnClickListener(v -> load());
        b.btnExport.setOnClickListener(v -> share());
        b.mapCheckins.setOnDotClickListener((index, dot) -> {
            if (dot.tag instanceof String[]) {
                String[] t = (String[]) dot.tag;
                b.txtSelected.setText(t[0] + "  [" + t[1] + "]  " + t[2] + ", " + t[3] + "  " + t[4]);
            }
        });
        load();
    }

    private void sendQuery() {
        String title = Ui.clean(b.editTitle.getText());
        String msg = Ui.clean(b.editMessage.getText());
        if (title.isEmpty()) {
            Ui.toast(this, "Enter a title");
            return;
        }
        final JSONObject o = new JSONObject();
        try {
            o.put("title", title);
            o.put("message", msg.isEmpty() ? "Please report your status." : msg);
            o.put("active", true);
            o.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        AppExecutors.io(() -> {
            String key = new FirebaseRest(this).post("disasters", o);
            AppExecutors.main(() -> Ui.toast(this, key == null ? "Failed (offline?)" : "Query sent to all apps"));
        });
    }

    private void endAll() {
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            JSONObject all = rest.getObj("disasters");
            if (all != null) {
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject d = all.optJSONObject(k);
                    if (d != null && d.optBoolean("active", false)) {
                        rest.request("PUT", "disasters/" + k + "/active", null, "false");
                    }
                }
            }
            AppExecutors.main(() -> Ui.toast(this, "All disaster alerts closed"));
        });
    }

    private void load() {
        AppExecutors.io(() -> {
            JSONObject all = new FirebaseRest(this).getObj("checkins");
            final List<MapView.Dot> dots = new ArrayList<>();
            final List<RowAdapter.Row> rows = new ArrayList<>();
            final List<String[]> bad = new ArrayList<>();
            int safe = 0;
            if (all != null) {
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    JSONObject c = all.optJSONObject(it.next());
                    if (c == null || !c.has("lat")) continue;
                    String name = c.optString("name", "?");
                    String status = c.optString("status", "");
                    double lat = c.optDouble("lat"), lon = c.optDouble("lon");
                    boolean help = "DISTRESSED".equals(status);
                    if (!help) safe++;
                    String[] t = {name, status, String.format(Locale.US, "%.5f", lat), String.format(Locale.US, "%.5f", lon),
                            TimeUtil.ago(c.optLong("ts"))};
                    MapView.Dot d = new MapView.Dot();
                    d.lat = lat;
                    d.lon = lon;
                    d.color = help ? 0xFFC62828 : 0xFF2E7D32;
                    d.radiusDp = help ? 9 : 6;
                    d.label = help ? name : "";
                    d.tag = t;
                    dots.add(d);
                    if (help) {
                        bad.add(t);
                        rows.add(new RowAdapter.Row(name, Geo.pretty(lat, lon) + "  |  " + t[4], "RESCUE", 0xFFC62828, t));
                    }
                }
            }
            final int fs = safe;
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                distressed.clear();
                distressed.addAll(bad);
                b.txtCounts.setText(fs + " safe  |  " + bad.size() + " need rescue  |  " + dots.size() + " responses");
                b.mapCheckins.setDots(dots);
                adapter.submit(rows);
            });
        });
    }

    /** Hand the distressed list to the national rescue system (any messaging / mail app). */
    private void share() {
        if (distressed.isEmpty()) {
            Ui.toast(this, "Nobody is asking for rescue right now");
            return;
        }
        StringBuilder sb = new StringBuilder("RESCUE LIST (name, latitude, longitude, last update)\n");
        for (String[] t : distressed) {
            sb.append(t[0]).append(", ").append(t[2]).append(", ").append(t[3]).append(", ").append(t[4]).append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "AidAlert rescue list");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "Send to national rescue"));
    }
}
