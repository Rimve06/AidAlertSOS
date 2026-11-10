package com.aidalert.sos.ui;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.alerts.AlertItem;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.news.NewsRepo;
import com.aidalert.sos.news.NewsRules;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/** News portal: daily news from public feeds, emergencies and safety news pinned to the top. */
public class NewsActivity extends BaseActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("News");
        b.txtInfo.setVisibility(View.VISIBLE);
        b.btnAdd.setVisibility(View.VISIBLE);
        b.btnAdd.setText("Refresh");
        b.btnAdd.setOnClickListener(v -> load());
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> {
            AlertItem a = (AlertItem) r.tag;
            if (a.link != null && a.link.startsWith("http")) startActivity(WebActivity.open(this, a.link, a.source));
        });
        show(NewsRepo.cached(Prefs.of(this), null));
        load();
    }

    private void load() {
        b.txtInfo.setText("Updating...");
        AppExecutors.io(() -> {
            NewsRepo.Result r = NewsRepo.fetch(this);
            AppExecutors.main(() -> {
                if (!isFinishing()) show(r);
            });
        });
    }

    private void show(NewsRepo.Result r) {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (AlertItem a : r.items) {
            int c = a.severity == 3 ? 0xFFC62828 : a.severity == 2 ? 0xFFEF6C00 : a.severity == 1 ? 0xFF1565C0 : 0xFF6B7A90;
            rows.add(new RowAdapter.Row(a.title, a.source + "  |  " + (a.timeMs == 0 ? "" : TimeUtil.ago(a.timeMs)),
                    NewsRules.label(a.severity), c, a));
        }
        adapter.submit(rows);
        String note = r.errors.isEmpty() ? "" : "\n" + r.errors.get(0);
        b.txtInfo.setText("Order: Bangladesh emergencies and safety, then world emergencies, then other Bangladesh news, then world news. Tap a headline to read it at its source."
                + (r.fromCache ? "\nShowing saved news." : "") + note);
    }
}
