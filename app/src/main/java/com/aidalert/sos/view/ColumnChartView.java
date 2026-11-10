package com.aidalert.sos.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view (course topic): a vertical column chart drawn with Canvas. Blue columns with the value above each
 * one, faint grid lines, and the names under the columns turned sideways so long names fit. The view is as wide as
 * its data needs, so place it inside a HorizontalScrollView.
 */
public class ColumnChartView extends View {
    private final List<String> labels = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;

    public ColumnChartView(Context c) {
        this(c, null);
    }

    public ColumnChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        bar.setColor(0xFF2F80ED);
        grid.setColor(0xFFE3E8F0);
        grid.setStrokeWidth(dp);
        text.setColor(0xFF4B5563);
        text.setTextSize(11 * dp);
        value.setColor(0xFF111827);
        value.setTextSize(11 * dp);
        value.setTextAlign(Paint.Align.CENTER);
        value.setFakeBoldText(true);
    }

    public void setData(List<String> l, List<Double> v) {
        labels.clear();
        values.clear();
        labels.addAll(l);
        values.addAll(v);
        requestLayout();
        invalidate();
    }

    private float columnWidth() {
        return 46 * dp;
    }

    private float labelArea() {
        float longest = 0;
        for (String s : labels) longest = Math.max(longest, text.measureText(shorten(s)));
        return (float) (longest * 0.72 + 14 * dp);       // a label turned 45 degrees needs about 0.72 of its length
    }

    private static String shorten(String s) {
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = (int) (columnWidth() * Math.max(1, labels.size()) + 16 * dp + labelArea());
        int height = (int) (210 * dp + labelArea());
        setMeasuredDimension(resolveSize(Math.max(width, getSuggestedMinimumWidth()), w), resolveSize(height, h));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (labels.isEmpty()) return;
        double max = 1;
        for (double d : values) max = Math.max(max, d);
        float top = 24 * dp;
        float plotBottom = getHeight() - labelArea();
        float plotH = plotBottom - top;
        for (int g = 0; g <= 4; g++) {
            float y = plotBottom - plotH * g / 4f;
            canvas.drawLine(0, y, getWidth(), y, grid);
        }
        float cw = columnWidth();
        for (int i = 0; i < labels.size(); i++) {
            float left = 8 * dp + i * cw;
            float h = (float) (values.get(i) / max) * plotH;
            rect.set(left + 7 * dp, plotBottom - Math.max(h, 2 * dp), left + cw - 7 * dp, plotBottom);
            canvas.drawRoundRect(rect, 4 * dp, 4 * dp, bar);
            canvas.drawText(String.valueOf(Math.round(values.get(i))), left + cw / 2, rect.top - 5 * dp, value);
            canvas.save();
            canvas.rotate(45, left + cw / 2, plotBottom + 8 * dp);
            canvas.drawText(shorten(labels.get(i)), left + cw / 2, plotBottom + 8 * dp + 10 * dp, text);
            canvas.restore();
        }
    }
}
