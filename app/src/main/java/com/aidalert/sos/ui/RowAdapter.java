package com.aidalert.sos.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aidalert.sos.databinding.ItemRowBinding;

import java.util.ArrayList;
import java.util.List;

/** One reusable RecyclerView adapter (ViewHolder pattern) for every simple list in the app. */
public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    public static class Row {
        public String title = "";
        public String sub = "";
        public String badge = "";
        public int color = 0xFFC62828;
        public Object tag;

        public Row(String title, String sub, String badge, int color, Object tag) {
            this.title = title;
            this.sub = sub;
            this.badge = badge;
            this.color = color;
            this.tag = tag;
        }
    }

    public interface Listener {
        void onClick(Row row);
    }

    private final List<Row> rows = new ArrayList<>();
    private Listener click;
    private Listener longClick;

    public void setOnClick(Listener l) {
        click = l;
    }

    public void setOnLongClick(Listener l) {
        longClick = l;
    }

    public void submit(List<Row> list) {
        rows.clear();
        rows.addAll(list);
        notifyDataSetChanged();
    }

    public int size() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRowBinding v;

        VH(ItemRowBinding v) {
            super(v.getRoot());
            this.v = v;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Row r = rows.get(position);
        h.v.txtTitle.setText(r.title);
        h.v.txtSub.setText(r.sub);
        h.v.colorBar.setBackgroundColor(r.color);
        // Tinted outlined card in the row's colour, like the Public Alerts mockup
        android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
        float dp = h.v.getRoot().getResources().getDisplayMetrics().density;
        card.setCornerRadius(14 * dp);
        card.setColor((r.color & 0x00FFFFFF) | 0x14000000);
        card.setStroke(Math.max(1, (int) dp), (r.color & 0x00FFFFFF) | 0x59000000);
        h.v.getRoot().setBackground(card);
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(14 * dp);
        pill.setColor(r.color);
        h.v.txtBadge.setBackground(pill);
        h.v.txtBadge.setTextColor(0xFFFFFFFF);
        h.v.txtBadge.setText(r.badge);
        h.v.txtBadge.setVisibility(r.badge == null || r.badge.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        h.v.getRoot().setOnClickListener(x -> {
            if (click != null) click.onClick(r);
        });
        h.v.getRoot().setOnLongClickListener(x -> {
            if (longClick == null) return false;
            longClick.onClick(r);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }
}
