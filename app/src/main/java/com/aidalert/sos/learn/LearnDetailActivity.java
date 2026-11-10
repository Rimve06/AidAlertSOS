package com.aidalert.sos.learn;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aidalert.sos.databinding.ActivityLearnDetailBinding;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.util.CallHelper;

/** One emergency guide as a full page: what to do first, numbered steps, and what not to do. */
public class LearnDetailActivity extends BaseActivity {
    public static final String EXTRA_ID = "topic";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityLearnDetailBinding b = ActivityLearnDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        LearnContent.Topic t = LearnContent.byId(getIntent().getStringExtra(EXTRA_ID));
        if (t == null) {
            finish();
            return;
        }
        setTitle(t.group);
        b.txtTitle.setText(t.title);
        b.txtBangla.setText(t.bangla);
        b.txtCall.setText(t.callFirst);
        b.btnCall999.setOnClickListener(v -> CallHelper.dial(this, "999"));
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < t.steps.length; i++) addLine(b.steps, (i + 1) + ".", t.steps[i], 0xFF0F2A4A, d);
        for (String s : t.donts) addLine(b.donts, "X", s, 0xFFC62828, d);
        b.txtDisclaimer.setText(LearnContent.DISCLAIMER);
    }

    private void addLine(LinearLayout parent, String mark, String text, int markColor, float d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (6 * d), 0, (int) (6 * d));
        TextView m = new TextView(this);
        m.setText(mark);
        m.setTextColor(markColor);
        m.setTextSize(15);
        m.setTypeface(null, android.graphics.Typeface.BOLD);
        m.setLayoutParams(new LinearLayout.LayoutParams((int) (28 * d), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(0xFF1A1A1A);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(m);
        row.addView(t);
        parent.addView(row);
        row.setVisibility(View.VISIBLE);
    }
}
