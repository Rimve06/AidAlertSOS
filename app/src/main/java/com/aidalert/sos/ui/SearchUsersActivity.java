package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.AppExecutors;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Profile search. Only the public "directory" entry is read (name, area, bio, and a phone number
 * only if its owner chose to show it). Messaging is allowed only while the user's own SOS is active.
 */
public class SearchUsersActivity extends AppCompatActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        setTitle("Find people");
        b.editSearch.setVisibility(View.VISIBLE);
        b.editSearch.setHint("Search by name or area");
        b.txtInfo.setVisibility(View.VISIBLE);
        b.txtInfo.setText("You can look people up any time. Chatting with them is possible only after you trigger your own SOS, and chats are monitored by the control center.");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.editSearch.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                Ui.hideKeyboard(this);
                search(Ui.clean(b.editSearch.getText()));
                return true;
            }
            return false;
        });
        adapter.setOnClick(r -> {
            String[] t = (String[]) r.tag;
            if (!prefs.sosActive()) {
                Ui.toast(this, "Chat is locked: it opens only while your own SOS is active.");
                return;
            }
            startActivity(new Intent(this, ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_DM)
                    .putExtra(ChatActivity.EXTRA_PEER_UID, t[0])
                    .putExtra(ChatActivity.EXTRA_PEER_NAME, t[1]));
        });
        search("");
    }

    private void search(final String q) {
        b.txtEmpty.setVisibility(View.VISIBLE);
        b.txtEmpty.setText("Searching...");
        AppExecutors.io(() -> {
            JSONObject all = new FirebaseRest(this).getObj("directory");
            final List<RowAdapter.Row> rows = new ArrayList<>();
            String needle = q.toLowerCase(Locale.ROOT);
            if (all != null) {
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String uid = it.next();
                    if (uid.equals(prefs.uid())) continue;
                    JSONObject o = all.optJSONObject(uid);
                    if (o == null) continue;
                    String name = o.optString("name", "");
                    String area = o.optString("area", "");
                    if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)
                            && !area.toLowerCase(Locale.ROOT).contains(needle)) continue;
                    String phone = o.optString("phone", "");
                    String bio = o.optString("bio", "");
                    String sub = (area.isEmpty() ? "" : area + "  ") + (phone.isEmpty() ? "" : phone + "  ") + bio;
                    rows.add(new RowAdapter.Row(name, sub.trim(), "", 0xFF00695C, new String[]{uid, name}));
                }
            }
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                adapter.submit(rows);
                b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                b.txtEmpty.setText("No matching profiles.");
            });
        });
    }
}
