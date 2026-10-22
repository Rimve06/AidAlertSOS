package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.aidalert.sos.Config;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityAuthBinding;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.center.CenterHomeActivity;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Registration and login. Passwords are stored as salted PBKDF2 hashes and user ids are hashes of the
 * phone number, so the database never holds a readable phone number as a key.
 */
public class AuthActivity extends AppCompatActivity {
    private ActivityAuthBinding b;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        setTitle("AidAlert SOS");
        b.editServer.setText(prefs.dbUrl());
        b.editToken.setText(prefs.authToken());

        b.radioRole.setOnCheckedChangeListener((g, id) ->
                b.editCenterCode.setVisibility(id == b.radioCenter.getId() ? View.VISIBLE : View.GONE));
        b.btnLogin.setOnClickListener(v -> submit(false));
        b.btnRegister.setOnClickListener(v -> submit(true));
    }

    private void busy(boolean on, String msg) {
        b.btnLogin.setEnabled(!on);
        b.btnRegister.setEnabled(!on);
        b.txtStatus.setText(msg == null ? "" : msg);
    }

    private void submit(boolean register) {
        Ui.hideKeyboard(this);
        final String server = Ui.clean(b.editServer.getText());
        final String name = Ui.clean(b.editName.getText());
        final String phone = Ui.clean(b.editPhone.getText());
        final String pass = b.editPassword.getText().toString();
        final boolean center = b.radioCenter.isChecked();
        final String code = Ui.clean(b.editCenterCode.getText());

        if (server.isEmpty()) { busy(false, "Enter the server URL first."); return; }
        if (Crypto.normalizePhone(phone).length() < 6) { busy(false, "Enter a valid phone number."); return; }
        if (pass.length() < 6) { busy(false, "Password must have at least 6 characters."); return; }
        if (register && name.isEmpty()) { busy(false, "Enter your name to register."); return; }
        if (register && center && !Config.CENTER_CODE.equals(code)) { busy(false, "Wrong control center access code."); return; }
        if (!NetUtil.isOnline(this)) { busy(false, "You need internet to log in or register."); return; }

        prefs.dbUrl(server);
        prefs.authToken(Ui.clean(b.editToken.getText()));
        busy(true, register ? "Creating account..." : "Logging in...");

        AppExecutors.io(() -> {
            String error = register ? doRegister(name, phone, pass, center) : doLogin(phone, pass, center);
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (error != null) {
                    busy(false, error);
                } else {
                    openHome();
                }
            });
        });
    }

    /** @return error text or null on success. */
    private String doRegister(String name, String phone, String pass, boolean center) {
        FirebaseRest rest = new FirebaseRest(this);
        String uid = Crypto.userId(phone);
        FirebaseRest.Result existing = rest.get("users/" + uid);
        if (!existing.ok()) return "Server error (" + existing.code + "). Check the URL and database rules.";
        if (existing.json() != null) return "This phone number is already registered. Log in instead.";

        String role = center ? Prefs.ROLE_CENTER : Prefs.ROLE_CITIZEN;
        String salt = Crypto.newSalt();
        String hash = Crypto.hashPassword(pass, salt);
        try {
            JSONObject u = new JSONObject();
            u.put("name", name);
            u.put("phone", Crypto.normalizePhone(phone));
            u.put("role", role);
            u.put("salt", salt);
            u.put("hash", hash);
            u.put("createdAt", System.currentTimeMillis());
            if (!rest.put("users/" + uid, u)) return "Could not save the account.";

            if (center) {
                // The first control center creates the key pair. The private key never leaves this phone.
                JSONObject cfg = rest.getObj("config/center");
                if (cfg == null || cfg.optString("pub", "").isEmpty()) {
                    String[] kp = Crypto.generateKeyPair();
                    prefs.centerPriv(kp[1]);
                    JSONObject c = new JSONObject();
                    c.put("pub", kp[0]);
                    c.put("phone", Crypto.normalizePhone(phone));
                    c.put("ambulance", "999");
                    c.put("fire", "199");
                    c.put("police", "999");
                    c.put("rescue", "999");
                    rest.put("config/center", c);
                }
            } else {
                JSONObject d = new JSONObject();
                d.put("name", name);
                d.put("area", "");
                d.put("bio", "");
                d.put("phone", "");
                rest.put("directory/" + uid, d);
            }
        } catch (JSONException e) {
            return "Could not build the account record.";
        }
        prefs.setSession(uid, name, Crypto.normalizePhone(phone), role);
        prefs.setPasswordHash(salt, hash);
        return null;
    }

    private String doLogin(String phone, String pass, boolean center) {
        FirebaseRest rest = new FirebaseRest(this);
        String uid = Crypto.userId(phone);
        FirebaseRest.Result r = rest.get("users/" + uid);
        if (!r.ok()) return "Server error (" + r.code + "). Check the URL and database rules.";
        JSONObject u = r.obj();
        if (u == null) return "No account for this number. Create one first.";
        if (!Crypto.verifyPassword(pass, u.optString("salt"), u.optString("hash"))) return "Wrong password.";
        String role = u.optString("role", Prefs.ROLE_CITIZEN);
        if (center != Prefs.ROLE_CENTER.equals(role)) {
            return Prefs.ROLE_CENTER.equals(role) ? "This is a control center account." : "This is a citizen account.";
        }
        prefs.setSession(uid, u.optString("name"), u.optString("phone"), role);
        prefs.setPasswordHash(u.optString("salt"), u.optString("hash"));
        return null;
    }

    private void openHome() {
        Intent i = new Intent(this, prefs.isCenter() ? CenterHomeActivity.class : PermissionsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
