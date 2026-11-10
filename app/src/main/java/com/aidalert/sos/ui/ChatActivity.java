package com.aidalert.sos.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import com.aidalert.sos.ui.BaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aidalert.sos.data.Msg;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityChatBinding;
import com.aidalert.sos.databinding.ItemMsgBinding;
import com.aidalert.sos.engine.SyncManager;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * One screen for every conversation (RecyclerView with a custom bubble item):
 *   MODE_CENTER  citizen <-> control center      path msgs/{uid}     (center: EXTRA_TARGET_UID)
 *   MODE_DM      user <-> user                   path dm/{thread}    (only while own SOS is active)
 * The control center can read DM threads (MonitorActivity) - they are not private.
 */
public class ChatActivity extends BaseActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_TARGET_UID = "target_uid";
    public static final String EXTRA_TARGET_NAME = "target_name";
    public static final String EXTRA_PEER_UID = "peer_uid";
    public static final String EXTRA_PEER_NAME = "peer_name";
    public static final String EXTRA_THREAD = "thread";
    public static final String EXTRA_READONLY = "readonly";

    public static final int MODE_CENTER = 1;
    public static final int MODE_DM = 3;

    private ActivityChatBinding b;
    private Prefs prefs;
    private int mode;
    private String path;
    private String peerUid = "";
    private String peerName = "";
    private boolean readOnly;
    private boolean center;
    private final List<Msg> msgs = new ArrayList<>();
    private Adapter adapter;
    private boolean polling;
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            load();
            AppExecutors.mainDelayed(this, 4000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        center = prefs.isCenter();
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_CENTER);
        readOnly = getIntent().getBooleanExtra(EXTRA_READONLY, false);

        String notice;
        switch (mode) {
            case MODE_DM:
                peerUid = getIntent().getStringExtra(EXTRA_PEER_UID);
                peerName = getIntent().getStringExtra(EXTRA_PEER_NAME);
                String thread = getIntent().getStringExtra(EXTRA_THREAD);
                if (thread == null) thread = threadId(prefs.uid(), peerUid == null ? "" : peerUid);
                path = "dm/" + thread;
                setTitle(peerName == null || peerName.isEmpty() ? "Direct messages" : peerName);
                notice = "Private between you two, but monitored by the control center.";
                break;
            default:
                String target = center ? getIntent().getStringExtra(EXTRA_TARGET_UID) : prefs.uid();
                if (target == null) target = "";
                path = "msgs/" + target;
                String tn = getIntent().getStringExtra(EXTRA_TARGET_NAME);
                setTitle(center ? (tn == null ? "User chat" : tn) : "Control center");
                notice = center ? "Messages go only to this user." : "Two-way chat with the control center.";
        }
        b.txtNotice.setText(notice);

        boolean locked = !center && mode != MODE_CENTER && !prefs.sosActive();
        if (locked) {
            b.txtNotice.setText("Locked: this chat opens only while your own SOS is active. Reading is still possible.");
        }
        if (readOnly) {
            b.boxInput.setVisibility(View.GONE);
            b.txtNotice.setText("Monitoring view (read only).");
        }
        final boolean canSend = !locked && !readOnly;
        b.btnSend.setEnabled(canSend);
        b.editMsg.setEnabled(canSend);

        adapter = new Adapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        b.listMsgs.setLayoutManager(lm);
        b.listMsgs.setAdapter(adapter);
        b.btnSend.setOnClickListener(v -> send());
    }

    /** Same id for both users: the two uids sorted and joined. */
    public static String threadId(String a, String c) {
        return a.compareTo(c) < 0 ? a + "_" + c : c + "_" + a;
    }

    private boolean isMine(Msg m) {
        if (mode == MODE_CENTER) return center ? "CENTER".equals(m.from) : "USER".equals(m.from);
        return m.uid.equals(prefs.uid());
    }

    private void send() {
        final String text = Ui.clean(b.editMsg.getText());
        if (text.isEmpty()) return;
        if (!NetUtil.isOnline(this)) {
            Ui.toast(this, "You are offline. Messages need internet.");
            return;
        }
        final JSONObject o = new JSONObject();
        try {
            if (mode == MODE_CENTER) o.put("from", center ? "CENTER" : "USER");
            o.put("uid", prefs.uid());
            o.put("name", center ? "Control center" : prefs.name());
            o.put("text", text);
            o.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        b.editMsg.setText("");
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            String key = rest.post(path, o);
            if (key != null && mode == MODE_DM && peerUid != null && !peerUid.isEmpty()) {
                JSONObject idx = new JSONObject();
                try {
                    idx.put("name", prefs.name());
                    idx.put("ts", System.currentTimeMillis());
                    rest.put("dmIndex/" + peerUid + "/" + prefs.uid(), idx);
                    idx.put("name", peerName == null ? "" : peerName);
                    rest.put("dmIndex/" + prefs.uid() + "/" + peerUid, idx);
                } catch (JSONException ignored) {
                    // index is only a convenience for the monitor screen
                }
            }
            if (key == null) AppExecutors.main(() -> Ui.toast(this, "Could not send"));
            else load();
        });
    }

    private void load() {
        if (!NetUtil.isOnline(this)) return;
        AppExecutors.io(() -> {
            JSONObject all = new FirebaseRest(this).getQuery(path, SyncManager.LAST_50);
            final List<Msg> list = new ArrayList<>();
            if (all != null) {
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = all.optJSONObject(k);
                    if (o != null) list.add(Msg.from(k, o));
                }
                Collections.sort(list, (x, y) -> Long.compare(x.ts, y.ts));
            }
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                boolean grew = list.size() != msgs.size();
                msgs.clear();
                msgs.addAll(list);
                adapter.notifyDataSetChanged();
                if (grew && !msgs.isEmpty()) b.listMsgs.scrollToPosition(msgs.size() - 1);
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!polling) {
            polling = true;
            poll.run();
        }
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(poll);
        polling = false;
        super.onPause();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final ItemMsgBinding v;

            VH(ItemMsgBinding v) {
                super(v.getRoot());
                this.v = v;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemMsgBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Msg m = msgs.get(position);
            boolean mine = isMine(m);
            h.v.rowRoot.setGravity(mine ? Gravity.END : Gravity.START);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(40f);
            g.setColor(mine ? 0xFFFFE3E0 : 0xFFE8EAF0);
            h.v.bubble.setBackground(g);
            h.v.txtWho.setText(mine ? "You" : (m.name.isEmpty() ? ("CENTER".equals(m.from) ? "Control center" : "User") : m.name));
            h.v.txtText.setText(m.text);
            h.v.txtTime.setText(TimeUtil.clock(m.ts));
        }

        @Override
        public int getItemCount() {
            return msgs.size();
        }
    }
}
