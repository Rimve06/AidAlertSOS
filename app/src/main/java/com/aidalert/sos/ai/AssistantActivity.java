package com.aidalert.sos.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityAssistantBinding;
import com.aidalert.sos.learn.LearnDetailActivity;
import com.aidalert.sos.support.SupportActivity;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * "Talk to someone": an assistant for emergency and safety questions. Online it asks Groq (the user's own API key,
 * typed in the app). Offline, or if Groq fails, it answers from the saved Emergency Learning guides.
 * Anything that sounds like self-harm is never sent online: the user is offered the private Support companion.
 */
public class AssistantActivity extends BaseActivity {
    private ActivityAssistantBinding b;
    private ChatStore store;
    private final List<ChatRules.Turn> history = new ArrayList<>();
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAssistantBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("AI assistant");
        store = new ChatStore(this);
        b.btnSend.setOnClickListener(v -> send(Ui.clean(b.editMsg.getText())));
        b.btnKey.setOnClickListener(v -> keyDialog());
        b.btnClear.setOnClickListener(v -> {
            store.clear();
            history.clear();
            b.log.removeAllViews();
            bubble("Chat cleared.", false, "");
        });
        b.chipFire.setOnClickListener(v -> send("What should I do if there is a fire at home?"));
        b.chipBleed.setOnClickListener(v -> send("How do I stop heavy bleeding?"));
        b.chipSnake.setOnClickListener(v -> send("What to do after a snakebite?"));
        b.chipCpr.setOnClickListener(v -> send("How do I do CPR?"));
        for (String[] m : store.all()) {
            bubble(m[1], "user".equals(m[0]), m[2]);
            history.add(new ChatRules.Turn(m[0], m[1]));
        }
        if (history.isEmpty()) {
            bubble("Hello. Ask me about first aid, fire, floods, earthquakes or staying safe. In danger, call 999 first.", false, "");
        }
        updateMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMode();
    }

    private boolean online() {
        return NetUtil.isOnline(this) && !Prefs.of(this).groqKey().isEmpty();
    }

    private void updateMode() {
        boolean net = NetUtil.isOnline(this);
        boolean key = !Prefs.of(this).groqKey().isEmpty();
        b.txtMode.setText(net && key ? "Online AI (Groq)"
                : net ? "Offline guide: add your Groq key (Key button) for online AI"
                : "Offline guide: answers come from saved emergency guides");
    }

    private void send(String text) {
        if (text.isEmpty() || busy) return;
        b.editMsg.setText("");
        bubble(text, true, "");
        if (!ChatRules.mayGoOnline(text)) {
            // stays on the phone, never sent to the cloud
            store.add("user", text, "local");
            history.add(new ChatRules.Turn("user", text));
            bubble("What you wrote matters, and I do not want to answer it with an online service. "
                    + "Please talk in the private Support companion, or call 999 if you are in danger.", false, "local");
            b.btnSupport.setVisibility(View.VISIBLE);
            b.btnSupport.setOnClickListener(v -> startActivity(new Intent(this, SupportActivity.class)));
            return;
        }
        busy = true;
        final String q = text;
        final List<ChatRules.Turn> snapshot = new ArrayList<>(history);
        final Prefs prefs = Prefs.of(this);
        final boolean useOnline = online();
        b.txtMode.setText(useOnline ? "Asking Groq..." : "Searching saved guides...");
        AppExecutors.io(() -> {
            String reply = "";
            String mode = "offline";
            String topic = "";
            String note = "";
            if (useOnline) {
                // Try the chosen model, then the current Groq models. A retired model (404) just moves on to the next one.
                String key = prefs.groqKey().trim();
                String[][] auth = new String[][]{{"Authorization", "Bearer " + key}};
                java.util.List<String> models = ChatRules.candidates(prefs.groqModel());
                String lastErr = "";
                boolean modelProblem = false;
                boolean stop = false;
                for (int mi = 0; mi < models.size() && reply.isEmpty() && !stop; mi++) {
                    try {
                        String body = ChatRules.requestBody(models.get(mi), snapshot, q);
                        String json = com.aidalert.sos.net.Http.postJson(ChatRules.ENDPOINT, body, auth, 30000);
                        reply = ChatRules.parseReply(json);
                        if (reply.isEmpty()) {
                            lastErr = "Groq answered with an empty message (model " + models.get(mi) + ").";
                        } else {
                            mode = "online";
                            prefs.groqModel(models.get(mi));
                        }
                    } catch (java.io.IOException | JSONException e) {
                        lastErr = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        modelProblem = ChatRules.isModelProblem(lastErr);
                        if (lastErr.startsWith("HTTP 401") || lastErr.startsWith("HTTP 403")) {
                            lastErr = "Groq rejected the API key. Open Key and paste a valid one. " + lastErr;
                            stop = true;
                        } else if (lastErr.startsWith("HTTP 429")) {
                            lastErr = "Groq says too many requests right now. Wait a minute. " + lastErr;
                            stop = true;
                        }
                    }
                }
                if (reply.isEmpty() && modelProblem && !stop) {
                    // Every built-in model was refused: ask Groq which models this key can use right now.
                    try {
                        String list = com.aidalert.sos.net.Http.getWith(ChatRules.MODELS_ENDPOINT, auth, 15000);
                        String best = ChatRules.pickModel(ChatRules.chatModelsFrom(list));
                        if (!best.isEmpty()) {
                            String json = com.aidalert.sos.net.Http.postJson(ChatRules.ENDPOINT,
                                    ChatRules.requestBody(best, snapshot, q), auth, 30000);
                            reply = ChatRules.parseReply(json);
                            if (!reply.isEmpty()) {
                                mode = "online";
                                prefs.groqModel(best);
                            }
                        }
                    } catch (java.io.IOException | JSONException e) {
                        lastErr = e.getMessage() == null ? lastErr : e.getMessage();
                    }
                }
                if (reply.isEmpty()) note = "Online AI failed: " + lastErr + "\n\nUsing saved guides.\n\n";
            }
            if (reply.isEmpty()) {
                OfflineGuide.Answer a = OfflineGuide.answer(q);
                reply = note + a.text;
                topic = a.topicId;
                mode = "offline";
            }
            final String r = reply, m = mode, t = topic;
            AppExecutors.main(() -> {
                busy = false;
                if (isFinishing()) return;
                store.add("user", q, m);
                store.add("assistant", r, m);
                history.add(new ChatRules.Turn("user", q));
                history.add(new ChatRules.Turn("assistant", r));
                bubble(r, false, m);
                if (!t.isEmpty()) {
                    b.btnGuide.setVisibility(View.VISIBLE);
                    b.btnGuide.setOnClickListener(v -> startActivity(new Intent(this, LearnDetailActivity.class)
                            .putExtra(LearnDetailActivity.EXTRA_ID, t)));
                } else {
                    b.btnGuide.setVisibility(View.GONE);
                }
                updateMode();
            });
        });
    }

    private void bubble(String text, boolean mine, String mode) {
        TextView v = new TextView(this);
        v.setText(text + (mine || mode.isEmpty() ? "" : "\n[" + ("online".equals(mode) ? "Online AI" : "Offline guide") + "]"));
        v.setTextSize(15);
        v.setPadding(28, 20, 28, 20);
        // Clear contrast like the mockup: my messages navy with white text, answers white with dark text.
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(40f);
        bg.setColor(mine ? 0xFF0B2A5B : 0xFFFFFFFF);
        bg.setStroke(2, mine ? 0xFF0B2A5B : 0xFFCBD3E3);
        v.setBackground(bg);
        v.setTextColor(mine ? 0xFFFFFFFF : 0xFF111827);
        v.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.topMargin = 12;
        lp.leftMargin = mine ? 80 : 0;
        lp.rightMargin = mine ? 0 : 80;
        v.setLayoutParams(lp);
        b.log.addView(v);
        b.scroll.post(() -> b.scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void keyDialog() {
        final Prefs p = Prefs.of(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        final EditText key = new EditText(this);
        key.setHint("Groq API key (starts with gsk_)");
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(p.groqKey());
        final EditText model = new EditText(this);
        model.setHint("Model (default openai/gpt-oss-20b)");
        model.setText(p.groqModel());
        box.addView(key);
        box.addView(model);
        new AlertDialog.Builder(this).setTitle("Online AI settings")
                .setMessage("Create a key at console.groq.com. It is stored only on this phone.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    p.groqKey(Ui.clean(key.getText()));
                    p.groqModel(Ui.clean(model.getText()));
                    updateMode();
                })
                .setNeutralButton("Test connection", (d, w) -> testConnection(Ui.clean(key.getText()), Ui.clean(model.getText())))
                .setNegativeButton("Cancel", null).show();
    }

    /** Sends one tiny request to Groq and shows exactly what came back, so a failure is never a mystery. */
    private void testConnection(final String key, final String model) {
        Ui.toast(this, "Testing...");
        AppExecutors.io(() -> {
            String result;
            if (!NetUtil.isOnline(this)) {
                result = "The phone reports no internet connection.";
            } else if (!key.startsWith("gsk_")) {
                result = "The key does not start with gsk_ (" + key.length() + " characters). Paste the whole key.";
            } else {
                try {
                    java.util.List<ChatRules.Turn> none = new ArrayList<>();
                    String useModel = ChatRules.isRetired(model) ? ChatRules.DEFAULT_MODEL : model;
                    String json = com.aidalert.sos.net.Http.postJson(ChatRules.ENDPOINT,
                            ChatRules.requestBody(useModel, none, "Say OK"),
                            new String[][]{{"Authorization", "Bearer " + key}}, 25000);
                    String answer = ChatRules.parseReply(json);
                    result = "WORKS with model " + useModel + ". Groq answered: " + (answer.isEmpty() ? "(empty)" : answer);
                } catch (java.io.IOException | JSONException e) {
                    result = "FAILED: " + e.getMessage();
                }
            }
            final String r = result;
            AppExecutors.main(() -> {
                if (!isFinishing()) new AlertDialog.Builder(this).setTitle("Groq test").setMessage(r)
                        .setPositiveButton("OK", null).show();
            });
        });
    }
}
