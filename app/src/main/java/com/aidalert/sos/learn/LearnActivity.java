package com.aidalert.sos.learn;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.databinding.ActivityLearnBinding;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.RowAdapter;

import java.util.ArrayList;
import java.util.List;

/** Offline emergency guides: a searchable list. Each guide opens as its own full page. */
public class LearnActivity extends BaseActivity {
    private ActivityLearnBinding b;
    private final RowAdapter adapter = new RowAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLearnBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Emergency learning");
        b.txtNote.setText(LearnContent.DISCLAIMER);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> startActivity(new Intent(this, LearnDetailActivity.class)
                .putExtra(LearnDetailActivity.EXTRA_ID, ((LearnContent.Topic) r.tag).id)));
        b.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { show(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        show("");
    }

    private void show(String q) {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (LearnContent.Topic t : LearnContent.ALL) {
            if (!t.matches(q)) continue;
            int c = "Disaster".equals(t.group) ? 0xFFEF6C00 : "Health".equals(t.group) ? 0xFF2E7D32
                    : "Safety".equals(t.group) ? 0xFF6A1B9A : 0xFFC62828;
            rows.add(new RowAdapter.Row(t.title, t.bangla, t.group, c, t));
        }
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
