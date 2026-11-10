package com.aidalert.sos.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Minimal bar chart drawn with Canvas (no library): one bar per label, value written above the bar. */
public class CountBarChartView extends View {
    private final List<String> labels = new ArrayList<>();
    private final List<Float> values = new ArrayList<>();
    private int barColor = 0xFF0B2A5B;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint();
    private final float dp;

    public CountBarChartView(Context c) {
        this(c, null);
    }

    public CountBarChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setColor(0xFF111827);
        axis.setColor(0xFFCBD2DC);
    }

    public void setData(List<String> l, List<Float> v, int color) {
        labels.clear();
        values.clear();
        labels.addAll(l);
        values.addAll(v);
        barColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = values.size();
        float w = getWidth(), h = getHeight();
        if (n == 0) {
            txt.setTextSize(13 * dp);
            c.drawText("No data yet", w / 2, h / 2, txt);
            return;
        }
        float max = 1;
        for (float f : values) max = Math.max(max, f);
        float padX = 10 * dp, top = 22 * dp, bottom = 26 * dp;
        float slot = (w - 2 * padX) / n;
        float barW = Math.min(slot * 0.62f, 48 * dp);
        float base = h - bottom;
        c.drawLine(padX, base, w - padX, base, axis);
        bar.setColor(barColor);
        for (int i = 0; i < n; i++) {
            float cx = padX + slot * i + slot / 2;
            float bh = (base - top) * (values.get(i) / max);
            c.drawRoundRect(cx - barW / 2, base - bh, cx + barW / 2, base, 4 * dp, 4 * dp, bar);
            txt.setTextSize(11 * dp);
            String v = values.get(i) == Math.floor(values.get(i)) ? String.valueOf((int) (float) values.get(i)) : String.format(java.util.Locale.US, "%.1f", values.get(i));
            c.drawText(v, cx, base - bh - 4 * dp, txt);
            txt.setTextSize((n > 8 ? 8 : 10) * dp);
            c.drawText(labels.get(i), cx, h - 8 * dp, txt);
        }
    }
}
