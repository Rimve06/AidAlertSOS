package com.aidalert.sos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.aidalert.sos.auth.AuthManager;
import com.aidalert.sos.auth.PasswordRules;
import com.aidalert.sos.auth.PhoneFormat;
import com.aidalert.sos.auth.Roles;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityAuthBinding;
import com.aidalert.sos.directory.DirectoryActivity;
import com.aidalert.sos.net.Crypto;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.center.CenterHomeActivity;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.OAuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

public class AuthActivity extends BaseActivity {
    @Override
    protected boolean useShell() {
        return false;
    }

    private ActivityAuthBinding b;
    private Prefs prefs;
    private int subtitleTaps;
    private FirebaseUser pendingSocialUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        setTitle("AidAlert SOS");

        b.btnDirectory.setOnClickListener(v -> startActivity(new Intent(this, DirectoryActivity.class)));
        b.txtSubtitle.setOnClickListener(v -> {
            if (++subtitleTaps >= 5) {
                b.radioRole.setVisibility(View.VISIBLE);
                subtitleTaps = 0;
            }
        });

        // Login buttons
        b.btnLogin.setOnClickListener(v -> login());
        b.btnGoogleLogin.setOnClickListener(v -> signInWithProvider("google.com"));
        b.btnGithubLogin.setOnClickListener(v -> signInWithProvider("github.com"));
        b.btnForgot.setOnClickListener(v -> forgot());
        b.btnShowRegister.setOnClickListener(v -> showRegister());

        // Register buttons
        b.btnRegister.setOnClickListener(v -> register());
        b.btnGoogleRegister.setOnClickListener(v -> signInWithProvider("google.com"));
        b.btnGithubRegister.setOnClickListener(v -> signInWithProvider("github.com"));
        b.btnBackLogin.setOnClickListener(v -> showLogin());

        // Social phone prompt
        b.btnSubmitSocialPhone.setOnClickListener(v -> submitSocialPhone());
        b.btnBackFromSocialPhone.setOnClickListener(v -> {
            AuthManager.signOut();
            showLogin();
        });

        if (prefs.isLoggedIn() && prefs.isCenter()) {
            openHome();
        } else if (AuthManager.user() != null) {
            resume();
        } else {
            showLogin();
        }
    }

    private void busy(boolean on, String msg) {
        b.progress.setVisibility(on ? View.VISIBLE : View.GONE);
        for (View v : new View[]{b.btnLogin, b.btnGoogleLogin, b.btnGithubLogin, b.btnForgot,
                b.btnShowRegister, b.btnRegister, b.btnGoogleRegister, b.btnGithubRegister,
                b.btnBackLogin, b.btnSubmitSocialPhone, b.btnBackFromSocialPhone}) {
            if (v != null) v.setEnabled(!on);
        }
        b.txtStatus.setText(msg == null ? "" : msg);
    }

    private void hideAll() {
        b.secLogin.setVisibility(View.GONE);
        b.secRegister.setVisibility(View.GONE);
        b.secSocialPhone.setVisibility(View.GONE);
    }

    private void showLogin() {
        hideAll();
        b.secLogin.setVisibility(View.VISIBLE);
        b.txtSubtitle.setText("Sign in to continue");
        b.editLoginEmail.setText("");
        b.editLoginPassword.setText("");
    }

    private void showRegister() {
        hideAll();
        b.secRegister.setVisibility(View.VISIBLE);
        b.txtSubtitle.setText("Create your account");
        b.editRegName.setText("");
        b.editRegEmail.setText("");
        b.editRegPassword.setText("");
        b.editRegPhone.setText("");
    }

    private void showSocialPhonePrompt(FirebaseUser u) {
        pendingSocialUser = u;
        hideAll();
        b.secSocialPhone.setVisibility(View.VISIBLE);
        b.txtSubtitle.setText("Mobile number required");
        b.editSocialName.setText(u != null && u.getDisplayName() != null ? u.getDisplayName() : "");
        b.editSocialPhone.setText("");
    }

    private boolean centerTabSelected() {
        return b.radioCenter.getVisibility() == View.VISIBLE && b.radioCenter.isChecked();
    }

    private void resume() {
        busy(true, "Checking your account...");
        AppExecutors.io(() -> {
            FirebaseUser u = AuthManager.user();
            if (u == null) {
                AppExecutors.main(() -> { busy(false, null); showLogin(); });
                return;
            }
            try {
                com.google.android.gms.tasks.Tasks.await(u.reload(), 10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                AuthManager.signOut();
                prefs.logout();
                AppExecutors.main(() -> { busy(false, "Account no longer exists. Please sign in again."); showLogin(); });
                return;
            }
            u = AuthManager.user();
            if (u == null || AuthManager.idToken(true).isEmpty()) {
                AuthManager.signOut();
                prefs.logout();
                AppExecutors.main(() -> { busy(false, "Account no longer exists. Please sign in again."); showLogin(); });
                return;
            }
            FirebaseRest rest = new FirebaseRest(this);
            JSONObject profile = rest.getObj("users/" + u.getUid());
            if (Roles.isAdminEmail(u.getEmail())) {          // the fixed admin account goes straight to the Control Center
                completeLogin(u, profile);
                return;
            }
            if (centerTabSelected()) {
                Object cr = rest.get("roles/" + u.getUid()).json();
                if (cr instanceof String && Prefs.ROLE_CENTER.equals(cr)) {
                    completeLogin(u, profile);
                    return;
                }
            }
            boolean done = profile != null && profile.optBoolean("setupDone", false) && !profile.optString("phone", "").isEmpty();
            final FirebaseUser finalU = u;
            if (done) {
                completeLogin(finalU, profile);
            } else {
                AppExecutors.main(() -> {
                    busy(false, null);
                    showSocialPhonePrompt(finalU);
                });
            }
        });
    }

    private void completeLogin(FirebaseUser u, JSONObject profile) {
        FirebaseRest rest = new FirebaseRest(this);
        boolean isAdminEmail = u.getEmail() != null && u.getEmail().equalsIgnoreCase("meshaidalertsos@gmail.com");
        if (isAdminEmail) {
            rest.putString("roles/" + u.getUid(), Prefs.ROLE_CENTER);
        }
        String role = isAdminEmail ? Prefs.ROLE_CENTER : Prefs.ROLE_CITIZEN;   // only the fixed admin email is a control center

        final boolean wantsCenter = centerTabSelected() || isAdminEmail;
        final String err = Roles.check(wantsCenter, role);
        if (err != null && !isAdminEmail) {
            AuthManager.signOut();
            prefs.logout();
            AppExecutors.main(() -> { busy(false, err); showLogin(); });
            return;
        }
        final String phone = profile != null ? profile.optString("phone", "") : "";
        final String name = profile != null ? profile.optString("name", "Control Center") : (u.getDisplayName() != null ? u.getDisplayName() : "Control Center");

        if (!Prefs.ROLE_CENTER.equals(role) && (profile == null || !profile.optBoolean("setupDone", false) || phone.isEmpty())) {
            final FirebaseUser finalU = u;
            AppExecutors.main(() -> {
                busy(false, null);
                showSocialPhonePrompt(finalU);
            });
            return;
        }

        if (Prefs.ROLE_CENTER.equals(role)) ensureCenterKeys(rest, phone);
        prefs.setSession(u.getUid(), name, Crypto.normalizePhone(phone), role);
        AppExecutors.main(() -> openHome());
    }

    private void ensureCenterKeys(FirebaseRest rest, String phone) {
        try {
            JSONObject cfg = rest.getObj("config/center");
            if (cfg != null && !cfg.optString("pub", "").isEmpty()) {
                if (!prefs.centerPriv().isEmpty()) return;
                // new phone / laptop: restore the relay decryption key saved (password-protected) on the server
                JSONObject bk = rest.getObj("centerKeyBackup");
                String w = bk == null ? "" : bk.optString("w", "");
                String priv = w.isEmpty() ? null : Crypto.unwrapSecret(w, Roles.ADMIN_PASSWORD);
                if (priv != null && !priv.isEmpty()) {
                    prefs.centerPriv(priv);
                    return;
                }
            }
            String[] kp = Crypto.generateKeyPair();
            prefs.centerPriv(kp[1]);
            rest.put("centerKeyBackup", new JSONObject().put("w", Crypto.wrapSecret(kp[1], Roles.ADMIN_PASSWORD)));
            JSONObject c = new JSONObject();
            c.put("pub", kp[0]);
            c.put("phone", Crypto.normalizePhone(phone));
            c.put("ambulance", "999");
            c.put("fire", "102");
            c.put("police", "999");
            c.put("rescue", "999");
            rest.put("config/center", c);
        } catch (JSONException ignored) {}
    }

    private void login() {
        Ui.hideKeyboard(this);
        String email = Ui.clean(b.editLoginEmail.getText());
        String pass = b.editLoginPassword.getText().toString();
        if (email.isEmpty() || pass.isEmpty()) { busy(false, "Enter your email and password."); return; }

        if (!NetUtil.isOnline(this)) { busy(false, "You need internet to log in."); return; }
        if (Roles.isAdminEmail(email)) { adminLogin(email, pass); return; }
        busy(true, "Logging in...");
        AuthManager.auth().signInWithEmailAndPassword(email, pass).addOnCompleteListener(t -> {
            if (t.isSuccessful()) resume();
            else busy(false, explain(t.getException()));
        });
    }

    /** Real Firebase sign-in for the fixed admin account (created on first use), so the Control Center has a valid token. */
    private void adminLogin(String email, String pass) {
        busy(true, "Logging in as Control Center...");
        AuthManager.auth().signInWithEmailAndPassword(email, pass).addOnCompleteListener(t -> {
            if (t.isSuccessful()) { resume(); return; }
            Exception ex = t.getException();
            // newer Firebase projects answer "invalid credential" (not "no such user") for an account that does not exist yet
            if ((ex instanceof FirebaseAuthInvalidUserException || ex instanceof FirebaseAuthInvalidCredentialsException)
                    && Roles.ADMIN_PASSWORD.equals(pass)) {
                AuthManager.auth().createUserWithEmailAndPassword(email, pass).addOnCompleteListener(c -> {
                    if (c.isSuccessful()) resume();
                    else if (c.getException() instanceof FirebaseAuthUserCollisionException)
                        busy(false, "The admin account already exists in Firebase with a DIFFERENT password. In the Firebase console > Authentication > Users, open meshaidalertsos@gmail.com > Reset password / or delete the user, then log in again.");
                    else busy(false, explain(c.getException()));
                });
            } else {
                busy(false, explain(ex));
            }
        });
    }

    private void signInWithProvider(String providerId) {
        if (!NetUtil.isOnline(this)) { busy(false, "You need internet to sign in."); return; }
        busy(true, "Connecting...");
        OAuthProvider.Builder provider = OAuthProvider.newBuilder(providerId);
        AuthManager.auth().startActivityForSignInWithProvider(this, provider.build())
            .addOnCompleteListener(t -> {
                if (t.isSuccessful()) {
                    resume();
                } else {
                    busy(false, explain(t.getException()));
                }
            });
    }

    private void forgot() {
        String email = Ui.clean(b.editLoginEmail.getText());
        if (email.isEmpty()) { busy(false, "Type your email above first."); return; }
        AuthManager.auth().sendPasswordResetEmail(email).addOnCompleteListener(t ->
                busy(false, t.isSuccessful() ? "If that email has an account, a reset link was sent."
                        : explain(t.getException())));
    }

    private void register() {
        Ui.hideKeyboard(this);
        String name = Ui.clean(b.editRegName.getText());
        String email = Ui.clean(b.editRegEmail.getText());
        String pass = b.editRegPassword.getText().toString();
        String phone = PhoneFormat.toE164(Ui.clean(b.editRegPhone.getText()));

        if (name.length() < 2) { busy(false, "Enter your full name."); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { busy(false, "Enter a valid email address."); return; }
        if (Roles.isAdminEmail(email)) { busy(false, "This email is reserved for the control center. Use Log in."); return; }
        if (phone.isEmpty()) { busy(false, "Mobile number is mandatory when creating an account."); return; }
        String passErr = PasswordRules.check(pass, pass, email);
        if (passErr != null) { busy(false, passErr); return; }
        if (!NetUtil.isOnline(this)) { busy(false, "You need internet to create an account."); return; }

        busy(true, "Checking phone number...");
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            String phoneKey = Crypto.phoneKey(phone);
            if (isPhoneTakenByAnotherUser(rest, phoneKey, "")) {
                AppExecutors.main(() -> busy(false, "This phone number is already registered to another account."));
                return;
            }
            AppExecutors.main(() -> {
                busy(true, "Creating account...");
                AuthManager.auth().createUserWithEmailAndPassword(email, pass).addOnCompleteListener(t -> {
                    if (!t.isSuccessful()) {
                        busy(false, explain(t.getException()));
                        return;
                    }
                    FirebaseUser u = AuthManager.user();
                    if (u == null) { busy(false, "Session lost."); return; }
                    saveProfileAndComplete(u, name, phone, email);
                });
            });
        });
    }

    private void submitSocialPhone() {
        Ui.hideKeyboard(this);
        String name = Ui.clean(b.editSocialName.getText());
        String phone = PhoneFormat.toE164(Ui.clean(b.editSocialPhone.getText()));
        if (name.length() < 2) { busy(false, "Enter your full name."); return; }
        if (phone.isEmpty()) { busy(false, "Enter your mobile number."); return; }
        if (!NetUtil.isOnline(this)) { busy(false, "You need internet."); return; }

        FirebaseUser u = pendingSocialUser != null ? pendingSocialUser : AuthManager.user();
        if (u == null) { showLogin(); return; }

        busy(true, "Checking phone number...");
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            String phoneKey = Crypto.phoneKey(phone);
            if (isPhoneTakenByAnotherUser(rest, phoneKey, u.getUid())) {
                AppExecutors.main(() -> busy(false, "This phone number is already registered to another account."));
                return;
            }
            String email = u.getEmail() != null ? u.getEmail() : "";
            saveProfileAndComplete(u, name, phone, email);
        });
    }

    /** Background thread helper: verifies if the phone number belongs to an active user. */
    private boolean isPhoneTakenByAnotherUser(FirebaseRest rest, String phoneKey, String currentUid) {
        Object existing = rest.get("phoneIndex/" + phoneKey).json();
        if (existing == null) return false;
        String existingUid = existing.toString();
        if (!currentUid.isEmpty() && existingUid.equals(currentUid)) return false;
        JSONObject existingProfile = rest.getObj("users/" + existingUid);
        return existingProfile != null && existingProfile.optBoolean("setupDone", false);
    }

    private void saveProfileAndComplete(FirebaseUser u, String name, String phone, String email) {
        FirebaseRest rest = new FirebaseRest(this);
        try {
            JSONObject p = new JSONObject();
            p.put("name", name);
            p.put("phone", phone);
            p.put("email", email);
            p.put("createdAt", System.currentTimeMillis());
            p.put("setupDone", true);
            FirebaseRest.Result res = rest.request("PUT", "users/" + u.getUid(), null, p.toString());
            if (!res.ok()) {
                final String err = com.aidalert.sos.blood.AccessInfo.explain("Saving profile", res.code, res.body, rest.configured(), com.aidalert.sos.blood.AccessInfo.fromToken(AuthManager.idToken(false)));
                AppExecutors.main(() -> busy(false, err));
                return;
            }
            String key = Crypto.phoneKey(phone);
            rest.putString("phoneIndex/" + key, u.getUid());

            Object role = rest.get("roles/" + u.getUid()).json();
            if (!(role instanceof String) || !Prefs.ROLE_CENTER.equals(role)) {
                JSONObject d = new JSONObject();
                d.put("name", name);
                d.put("area", "");
                d.put("bio", "");
                d.put("phone", phone);
                rest.put("directory/" + u.getUid(), d);
            }
            completeLogin(u, p);
        } catch (JSONException e) {
            AppExecutors.main(() -> busy(false, "Error saving profile."));
        }
    }

    private String explain(Throwable e) {
        if (e == null) return "Something went wrong.";
        if (e instanceof FirebaseNetworkException) return "No internet connection.";
        if (e instanceof FirebaseTooManyRequestsException) return "Too many attempts. Wait a while and try again.";
        if (e instanceof FirebaseAuthUserCollisionException) return "That email already belongs to another account. Log in with it instead.";
        if (e instanceof FirebaseAuthInvalidUserException) return "No account matches these details.";
        if (e instanceof FirebaseAuthInvalidCredentialsException) return "Wrong email or password.";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? "Something went wrong." : m;
    }

    private void openHome() {
        Intent i = new Intent(this, prefs.isCenter() ? CenterHomeActivity.class : PermissionsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
