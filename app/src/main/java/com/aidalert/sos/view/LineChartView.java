package com.aidalert.sos.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Custom view: line with dots, one point per report period, with the period under each dot. */
public class LineChartView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final List<String> labels = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();

    public LineChartView(Context c) {
        this(c, null);
    }

    public LineChartView(Context c, AttributeSet a) {
        super(c, a);
        line.setColor(0xFFD32F2F);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        fill.setColor(0x22D32F2F);
        dot.setColor(0xFFD32F2F);
        txt.setColor(0xFF667085);
        txt.setTextAlign(Paint.Align.CENTER);
        grid.setColor(0xFFE3E8F0);
    }

    public void setData(List<String> l, List<Double> v) {
        labels.clear();
        values.clear();
        labels.addAll(l);
        values.addAll(v);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (getResources().getDisplayMetrics().density * 170));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.size();
        if (n == 0) return;
        float d = getResources().getDisplayMetrics().density;
        float left = 36 * d, right = getWidth() - 16 * d, top = 16 * d, bottom = getHeight() - 28 * d;
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;
        txt.setTextSize(10 * d);
        for (int g = 0; g <= 2; g++) {
            float y = bottom - (bottom - top) * g / 2f;
            canvas.drawLine(left, y, right, y, grid);
            txt.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.valueOf(Math.round(max * g / 2.0)), left - 4 * d, y + 3 * d, txt);
        }
        txt.setTextAlign(Paint.Align.CENTER);
        Path p = new Path(), area = new Path();
        for (int i = 0; i < n; i++) {
            float x = n == 1 ? (left + right) / 2 : left + (right - left) * i / (n - 1);
            float y = (float) (bottom - (bottom - top) * values.get(i) / max);
            if (i == 0) {
                p.moveTo(x, y);
                area.moveTo(x, bottom);
                area.lineTo(x, y);
            } else {
                p.lineTo(x, y);
                area.lineTo(x, y);
            }
            if (i == n - 1) {
                area.lineTo(x, bottom);
                area.close();
            }
        }
        canvas.drawPath(area, fill);
        canvas.drawPath(p, line);
        for (int i = 0; i < n; i++) {
            float x = n == 1 ? (left + right) / 2 : left + (right - left) * i / (n - 1);
            float y = (float) (bottom - (bottom - top) * values.get(i) / max);
            canvas.drawCircle(x, y, 4 * d, dot);
            canvas.drawText(labels.get(i), x, getHeight() - 8 * d, txt);
        }
    }
}
