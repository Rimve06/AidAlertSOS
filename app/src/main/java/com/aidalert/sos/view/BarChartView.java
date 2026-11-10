package com.aidalert.sos.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view (course topic): a horizontal bar chart drawn with Canvas. Labels on the left, value at the
 * end of each bar. The view grows with the number of bars so it can sit inside a ScrollView.
 */
public class BarChartView extends View {
    private final List<String> labels = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    public BarChartView(Context c) {
        this(c, null);
    }

    public BarChartView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        bar.setColor(0xFFC62828);
        text.setColor(0xFF212121);
        text.setTextSize(12 * dp);
        value.setColor(0xFF212121);
        value.setTextSize(12 * dp);
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

    private float rowHeight() {
        return 30 * dp;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int desired = (int) (rowHeight() * Math.max(1, labels.size()) + 8 * dp);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), w), resolveSize(desired, h));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (labels.isEmpty()) return;
        double max = 1;
        for (double d : values) max = Math.max(max, d);
        float labelW = Math.min(getWidth() * 0.38f, 150 * dp);
        float valueW = 56 * dp;
        float barMax = getWidth() - labelW - valueW - 8 * dp;
        float rh = rowHeight();
        for (int i = 0; i < labels.size(); i++) {
            float top = 4 * dp + i * rh;
            float mid = top + rh / 2;
            String label = labels.get(i);
            while (label.length() > 3 && text.measureText(label) > labelW - 6 * dp) label = label.substring(0, label.length() - 2) + "…";
            canvas.drawText(label, 0, mid + 4 * dp, text);
            float len = (float) (values.get(i) / max) * barMax;
            canvas.drawRect(labelW, top + 5 * dp, labelW + Math.max(len, 2 * dp), top + rh - 5 * dp, bar);
            canvas.drawText(String.valueOf(Math.round(values.get(i))), labelW + len + 6 * dp, mid + 4 * dp, value);
        }
    }
}
