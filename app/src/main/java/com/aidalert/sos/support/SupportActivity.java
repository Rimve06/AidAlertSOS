package com.aidalert.sos.support;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.databinding.ActivitySupportBinding;
import com.aidalert.sos.engine.SosEngine;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;

/**
 * Support companion for people having suicidal thoughts. Scripted and private: the conversation is never
 * stored or sent. If the words suggest danger, the user is asked, with a countdown they can cancel, before the
 * emergency contacts and the control center are told. Only "this person may be in crisis" and the location go out.
 */
public class SupportActivity extends BaseActivity {
    private static final int COUNTDOWN_S = 45;

    private ActivitySupportBinding b;
    private final RiskRules.Tracker tracker = new RiskRules.Tracker();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int turn;
    private boolean askedDirect;
    private boolean escalated;
    private AlertDialog countdownDialog;
    private Runnable tick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySupportBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Support companion");
        bubble(SupportScript.WELCOME, false);
        b.btnSend.setOnClickListener(v -> send());
        b.btnHelplines.setOnClickListener(v -> helplines());
        b.btnCalm.setOnClickListener(v -> {
            bubble(SupportScript.GROUNDING, false);
            bubble(SupportScript.GROUNDING_BN, false);
        });
        b.btnMood.setOnClickListener(v -> mood());
        b.btnGetHelp.setOnClickListener(v -> offerAlert(true));
    }

    private void send() {
        String t = Ui.clean(b.editMsg.getText());
        if (t.isEmpty()) return;
        b.editMsg.setText("");
        bubble(t, true);
        int r = tracker.add(t);
        if (r == RiskRules.HIGH) {
            bubble(SupportScript.HIGH_REPLY, false);
            offerAlert(false);
        } else if (r == RiskRules.CONCERN && !askedDirect) {
            askedDirect = true;
            bubble(SupportScript.CONCERN_REPLY, false);
        } else {
            bubble(SupportScript.listen(turn++), false);
        }
    }

    private void bubble(String text, boolean mine) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(15);
        v.setPadding(28, 20, 28, 20);
        v.setBackgroundResource(com.aidalert.sos.R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.topMargin = 12;
        lp.leftMargin = mine ? 80 : 0;
        lp.rightMargin = mine ? 0 : 80;
        v.setLayoutParams(lp);
        b.log.addView(v);
        b.scroll.post(() -> b.scroll.fullScroll(android.view.View.FOCUS_DOWN));
    }

    private void mood() {
        final String[] items = {"Sad", "Overwhelmed", "Anxious", "I need someone"};
        new AlertDialog.Builder(this).setTitle("How are you feeling?").setItems(items, (d, i) -> {
            bubble(items[i], true);
            if (i == 0) bubble(SupportScript.SAD, false);
            else if (i == 1) bubble(SupportScript.OVERWHELMED, false);
            else if (i == 2) {
                bubble(SupportScript.GROUNDING, false);
                bubble(SupportScript.GROUNDING_BN, false);
            } else bubble(SupportScript.NEED_SOMEONE, false);
        }).show();
    }

    private void helplines() {
        final String[][] h = SupportScript.HELPLINES;
        String[] names = new String[h.length];
        for (int i = 0; i < h.length; i++) names[i] = h[i][0] + "\n" + h[i][2];
        new AlertDialog.Builder(this).setTitle("Call someone now (opens your dialer)")
                .setItems(names, (d, i) -> dial(h[i][1]))
                .setNegativeButton("Close", null).show();
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (RuntimeException e) {
            Ui.toast(this, "No dialer found. Number: " + number);
        }
    }

    /** @param byUser true when the user tapped the button themselves (alert at once, no countdown). */
    private void offerAlert(boolean byUser) {
        if (escalated) {
            Ui.toast(this, "Your contacts and the control center were already told.");
            return;
        }
        if (byUser) {
            confirmNow();
            return;
        }
        if (countdownDialog != null) return;
        final TextView msg = new TextView(this);
        msg.setPadding(48, 24, 48, 0);
        msg.setTextSize(15);
        final int[] left = {COUNTDOWN_S};
        msg.setText(countdownText(left[0]));
        countdownDialog = new AlertDialog.Builder(this).setTitle("Do you want a person to check on you?")
                .setView(msg)
                .setCancelable(false)
                .setPositiveButton("Yes, alert them now", (d, w) -> {
                    stopTick();
                    doAlert();
                })
                .setNeutralButton("Call 999", (d, w) -> {
                    stopTick();
                    dial("999");
                    doAlert();
                })
                .setNegativeButton("I am safe, do not alert", (d, w) -> stopTick())
                .create();
        countdownDialog.setOnDismissListener(d -> countdownDialog = null);
        countdownDialog.show();
        tick = new Runnable() {
            @Override
            public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    stopTick();
                    doAlert();
                    return;
                }
                msg.setText(countdownText(left[0]));
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tick, 1000);
    }

    private static String countdownText(int s) {
        return "What you wrote worries me. Your emergency contacts and the control center will be told that you may need "
                + "help, with your location, in " + s + " seconds, unless you say you are safe.\n\n"
                + "They will not see this conversation.";
    }

    private void confirmNow() {
        new AlertDialog.Builder(this).setTitle("Alert your emergency contacts and the control center?")
                .setMessage("They get a message that you may need someone to check on you, with your location. "
                        + "They do not see this conversation.")
                .setPositiveButton("Alert them", (d, w) -> doAlert())
                .setNegativeButton("Cancel", null).show();
    }

    private void stopTick() {
        if (tick != null) handler.removeCallbacks(tick);
        tick = null;
        if (countdownDialog != null) {
            countdownDialog.dismiss();
            countdownDialog = null;
        }
    }

    private void doAlert() {
        if (escalated) return;
        escalated = true;
        final boolean noContacts = Db.get(this).contacts().isEmpty();
        AppExecutors.io(() -> {
            SosEngine.get(getApplicationContext()).triggerSupport("Support companion: possible suicidal crisis");
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                bubble("I told the control center" + (noContacts ? "." : " and your emergency contacts.")
                        + " Someone should reach out. " + (noContacts ? "You have no emergency contacts saved. Add some in Contacts. " : "")
                        + "Stay where you are if it is safe, and keep your phone with you. You can keep talking to me here.", false);
            });
        });
    }

    @Override
    protected void onDestroy() {
        stopTick();
        super.onDestroy();
    }
}
