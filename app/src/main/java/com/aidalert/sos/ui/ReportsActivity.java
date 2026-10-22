package com.aidalert.sos.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import com.aidalert.sos.ui.BaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.IncidentProvider;
import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The user's own SOS history. Data is read through ContentResolver from the app's ContentProvider
 * (course topic) rather than by calling the database helper directly.
 */
public class ReportsActivity extends BaseActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("My SOS history");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> details((Incident) r.tag));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        try (Cursor c = getContentResolver().query(IncidentProvider.CONTENT_URI, null, "mine=1", null, "created DESC")) {
            while (c != null && c.moveToNext()) {
                Incident i = Incident.fromCursor(c);
                rows.add(new RowAdapter.Row(Incident.label(i.type) + (i.silent ? " (silent)" : ""),
                        TimeUtil.clock(i.createdAt) + "  |  via " + i.via + (i.synced ? "" : "  |  not uploaded"),
                        i.status.replace('_', ' '), Incident.color(i.type), i));
            }
        }
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.txtEmpty.setText("No SOS alerts yet.");
    }

    private void details(Incident i) {
        StringBuilder s = new StringBuilder();
        s.append("Time: ").append(TimeUtil.clock(i.createdAt)).append("\n");
        s.append("Trigger: ").append(i.source).append("\n");
        s.append("Status: ").append(i.status).append("\n");
        s.append("Sent via: ").append(i.via).append(i.synced ? " (delivered)" : " (waiting for internet)").append("\n");
        if (i.hasFix()) s.append("Location: ").append(Geo.pretty(i.lat, i.lon)).append("\n");
        if (!i.dispatched.isEmpty()) s.append("Dispatched: ").append(i.dispatched).append("\n");
        if (!i.note.isEmpty()) s.append("Your note: ").append(i.note).append("\n");
        if (!i.opsNote.isEmpty()) s.append("Control center note: ").append(i.opsNote).append("\n");
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(Incident.label(i.type)).setMessage(s.toString().trim())
                .setPositiveButton("Close", null);
        if (i.hasFix()) d.setNeutralButton("Open map", (x, w) -> Ui.openMap(this, i.lat, i.lon, "My SOS"));
        d.show();
    }
}
