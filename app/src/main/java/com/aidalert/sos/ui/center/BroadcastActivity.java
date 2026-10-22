package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.aidalert.sos.ui.BaseActivity;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityBroadcastBinding;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.ChatActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;

import org.json.JSONException;
import org.json.JSONObject;

/** Control center -> all users (announcement) or -> one user (looked up by phone number). */
public class BroadcastActivity extends BaseActivity {
    private ActivityBroadcastBinding b;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityBroadcastBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        setTitle("Messages");

        b.radioTarget.setOnCheckedChangeListener((g, id) -> {
            boolean one = id == b.radioOne.getId();
            b.editPhone.setVisibility(one ? View.VISIBLE : View.GONE);
            b.btnOpenChat.setVisibility(one ? View.VISIBLE : View.GONE);
        });
        b.btnSend.setOnClickListener(v -> send());
        b.btnOpenChat.setOnClickListener(v -> {
            String phone = Ui.clean(b.editPhone.getText());
            if (Crypto.normalizePhone(phone).length() < 6) {
                Ui.toast(this, "Enter the user's phone number");
                return;
            }
            startActivity(new Intent(this, ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_MODE, ChatActivity.MODE_CENTER)
                    .putExtra(ChatActivity.EXTRA_TARGET_UID, Crypto.userId(phone))
                    .putExtra(ChatActivity.EXTRA_TARGET_NAME, "+" + Crypto.normalizePhone(phone)));
        });
    }

    private void send() {
        final String text = Ui.clean(b.editText.getText());
        if (text.isEmpty()) {
            Ui.toast(this, "Write a message first");
            return;
        }
        final boolean one = b.radioOne.isChecked();
        final String phone = Ui.clean(b.editPhone.getText());
        if (one && Crypto.normalizePhone(phone).length() < 6) {
            Ui.toast(this, "Enter the user's phone number");
            return;
        }
        final JSONObject o = new JSONObject();
        try {
            o.put("from", "CENTER");
            o.put("uid", prefs.uid());
            o.put("name", "Control center");
            o.put("text", text);
            o.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        b.btnSend.setEnabled(false);
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            String key;
            if (one) {
                String uid = Crypto.userId(phone);
                // Make sure the user exists so messages are not written into the void.
                if (rest.getObj("users/" + uid) == null) {
                    AppExecutors.main(() -> {
                        b.btnSend.setEnabled(true);
                        b.txtResult.setText("No registered user with that number.");
                    });
                    return;
                }
                key = rest.post("msgs/" + uid, o);
            } else {
                key = rest.post("broadcasts", o);
            }
            final boolean ok = key != null;
            AppExecutors.main(() -> {
                b.btnSend.setEnabled(true);
                b.txtResult.setText(ok ? "Sent." : "Failed to send (offline?).");
                if (ok) b.editText.setText("");
            });
        });
    }
}
