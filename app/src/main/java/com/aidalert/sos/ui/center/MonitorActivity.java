package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.ChatActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.util.AppExecutors;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Chat monitoring: the community forum and every user-to-user thread, read only. */
public class MonitorActivity extends AppCompatActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Monitor chats");
        b.txtInfo.setVisibility(View.VISIBLE);
        b.txtInfo.setText("Community and user-to-user chats are open only during an SOS and are monitored for misuse.");
        b.btnAdd.setVisibility(View.VISIBLE);
        b.btnAdd.setText("Open community forum");
        b.btnAdd.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_FORUM)
                .putExtra(ChatActivity.EXTRA_READONLY, true)));
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_DM)
                .putExtra(ChatActivity.EXTRA_THREAD, (String) r.tag)
                .putExtra(ChatActivity.EXTRA_READONLY, true)));
        load();
    }

    private void load() {
        AppExecutors.io(() -> {
            JSONObject shallow = new FirebaseRest(this).getShallow("dm");
            final List<RowAdapter.Row> rows = new ArrayList<>();
            if (shallow != null) {
                Iterator<String> it = shallow.keys();
                while (it.hasNext()) {
                    String thread = it.next();
                    rows.add(new RowAdapter.Row("Thread " + thread.substring(0, Math.min(10, thread.length())) + "...",
                            "Tap to read", "DM", 0xFF1565C0, thread));
                }
            }
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                adapter.submit(rows);
                b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                b.txtEmpty.setText("No direct-message threads yet.");
            });
        });
    }
}
