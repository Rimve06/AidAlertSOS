package com.aidalert.sos.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aidalert.sos.net.ServerCheck;
import com.aidalert.sos.util.AppExecutors;

import com.google.android.material.button.MaterialButton;

/** One screen that tests the login, the database address, the live rules and the phone clock, and says what to fix. */
public class ServerCheckActivity extends BaseActivity {
    private TextView txt;
    private MaterialButton run;
    private String last = "";

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Server check");
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView intro = new TextView(this);
        intro.setText("Tests why posts, comments, donor profiles or blood requests are refused. It reads a few things and writes one "
                + "tiny test value that it deletes again. Nothing else is changed.");
        intro.setTextSize(13);
        box.addView(intro);
        run = new MaterialButton(this);
        run.setText("Run the check");
        run.setOnClickListener(v -> runCheck());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(12);
        box.addView(run, bp);
        MaterialButton copy = new MaterialButton(this);
        copy.setText("Copy the result");
        copy.setOnClickListener(v -> {
            if (last.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Server check", last));
            Ui.toast(this, "Copied. Paste it into a message.");
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(8);
        box.addView(copy, cp);
        txt = new TextView(this);
        txt.setTypeface(Typeface.MONOSPACE);
        txt.setTextSize(12);
        txt.setPadding(0, dp(12), 0, 0);
        txt.setTextIsSelectable(true);
        box.addView(txt);
        sv.addView(box);
        setContentView(sv);
        runCheck();
    }

    private void runCheck() {
        run.setEnabled(false);
        txt.setText("Checking...");
        AppExecutors.io(() -> {
            ServerCheck.Report r = ServerCheck.run(this);
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                last = r.text();
                txt.setText(last);
                run.setEnabled(true);
            });
        });
    }
}
