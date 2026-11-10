package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.databinding.ActivityIncidentsBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.TimeUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** All SOS incidents: active (unanswered first) or full history, with CSV export. */
public class IncidentsActivity extends BaseActivity {
    private ActivityIncidentsBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private final List<Incident> all = new ArrayList<>();
    private boolean activeOnly = true;
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            load();
            AppExecutors.mainDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityIncidentsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("SOS incidents");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> startActivity(new Intent(this, IncidentDetailActivity.class)
                .putExtra(IncidentDetailActivity.EXTRA_ID, ((Incident) r.tag).id)));
        b.btnShowActive.setOnClickListener(v -> {
            activeOnly = true;
            render();
        });
        b.btnShowAll.setOnClickListener(v -> {
            activeOnly = false;
            render();
        });
        b.btnExport.setOnClickListener(v -> exportCsv());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.cancelMain(refresher);
        refresher.run();
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(refresher);
        super.onPause();
    }

    private void load() {
        if (!NetUtil.isOnline(this)) {
            b.txtSummary.setText("Offline: showing the last loaded list.");
            return;
        }
        AppExecutors.io(() -> {
            JSONObject node = new FirebaseRest(this).getObj("incidents");
            final List<Incident> list = CenterData.parseIncidents(node);
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                all.clear();
                all.addAll(list);
                render();
            });
        });
    }

    private void render() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        int active = 0;
        List<Incident> shown = new ArrayList<>(all);
        // Unanswered first, then newest.
        Collections.sort(shown, (x, y) -> {
            int px = "NEW".equals(x.status) ? 0 : 1, py = "NEW".equals(y.status) ? 0 : 1;
            if (px != py) return px - py;
            return Long.compare(y.createdAt, x.createdAt);
        });
        for (Incident i : all) if (i.isActive()) active++;
        for (Incident i : shown) {
            if (activeOnly && !i.isActive()) continue;
            rows.add(CenterData.row(i));
        }
        b.txtSummary.setText(active + " active  |  " + all.size() + " total incidents");
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void exportCsv() {
        StringBuilder sb = new StringBuilder(Incident.csvHeader()).append('\n');
        for (Incident i : all) sb.append(i.csvRow()).append('\n');
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "AidAlert incident report " + TimeUtil.clock(System.currentTimeMillis()));
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "Share incident report"));
    }
}
