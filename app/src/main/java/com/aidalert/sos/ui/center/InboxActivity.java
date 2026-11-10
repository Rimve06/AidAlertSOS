package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.ChatActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Citizen inbox: every citizen who has written to the control center (read and unread) in one list.
 * Unread chats come first with a counter; opening a chat marks it read on this phone.
 */
public class InboxActivity extends BaseActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private InboxData.Result last;
    private boolean unreadOnly;
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
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Citizen inbox");
        b.txtInfo.setVisibility(View.VISIBLE);
        b.txtInfo.setText("Loading conversations...");
        b.btnAdd.setVisibility(View.VISIBLE);
        b.btnAdd.setText("Showing: all messages (tap for unread only)");
        b.btnAdd.setOnClickListener(v -> {
            unreadOnly = !unreadOnly;
            b.btnAdd.setText(unreadOnly ? "Showing: unread only (tap for all)" : "Showing: all messages (tap for unread only)");
            render();
        });
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> {
            InboxData.Convo c = (InboxData.Convo) r.tag;
            InboxData.markSeen(this, c.uid, c.lastTs);
            c.unread = 0;
            startActivity(new Intent(this, ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_CENTER)
                    .putExtra(ChatActivity.EXTRA_TARGET_UID, c.uid)
                    .putExtra(ChatActivity.EXTRA_TARGET_NAME, c.name));
        });
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
            b.txtInfo.setText("Offline. The inbox needs internet.");
            return;
        }
        AppExecutors.io(() -> {
            final InboxData.Result res = InboxData.fetch(getApplicationContext(), new FirebaseRest(this));
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                last = res;
                render();
            });
        });
    }

    private void render() {
        if (last == null) return;
        List<RowAdapter.Row> rows = new ArrayList<>();
        int unreadMsgs = 0;
        for (InboxData.Convo c : last.list) {
            unreadMsgs += c.unread;
            if (unreadOnly && c.unread == 0) continue;
            String txt = c.lastText.length() > 100 ? c.lastText.substring(0, 100) + "..." : c.lastText;
            String sub = ("CENTER".equals(c.lastFrom) ? "You: " : "") + txt + "\n" + TimeUtil.ago(c.lastTs);
            String badge = c.unread > 0 ? c.unread + " NEW" : ("USER".equals(c.lastFrom) ? "Read, no reply" : "Replied");
            int color = c.unread > 0 ? 0xFFC62828 : ("USER".equals(c.lastFrom) ? 0xFFEF6C00 : 0xFF2E7D32);
            rows.add(new RowAdapter.Row(c.name, sub, badge, color, c));
        }
        adapter.submit(rows);
        String info = last.list.size() + " conversations  |  " + last.unreadChats() + " unread (" + unreadMsgs + " new messages)";
        if (!last.error.isEmpty()) info += "\n" + last.error;
        b.txtInfo.setText(info);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.txtEmpty.setText(unreadOnly ? "No unread messages." : "No citizen has written to the control center yet.");
    }
}
