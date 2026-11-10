package com.aidalert.sos.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Custom view: ring chart of shares (for example reported cases by crime type), with a total in the middle. */
public class DonutChartView extends View {
    public static final int[] COLORS = {0xFF3F51B5, 0xFFFFA000, 0xFF2E7D32, 0xFFD32F2F, 0xFF00897B, 0xFF8E24AA, 0xFF78909C};

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final List<Double> values = new ArrayList<>();
    private String centre = "";

    public DonutChartView(Context c) {
        this(c, null);
    }

    public DonutChartView(Context c, AttributeSet a) {
        super(c, a);
        arc.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFF111827);
        text.setFakeBoldText(true);
    }

    public void setData(List<Double> v, String centreText) {
        values.clear();
        values.addAll(v);
        centre = centreText;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = (int) (getResources().getDisplayMetrics().density * 150);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float stroke = 26 * d;
        arc.setStrokeWidth(stroke);
        float pad = stroke / 2 + 4 * d;
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);
        double sum = 0;
        for (double v : values) sum += v;
        if (sum <= 0) {
            arc.setColor(0xFFE3E8F0);
            canvas.drawArc(box, 0, 360, false, arc);
            return;
        }
        float start = -90;
        for (int i = 0; i < values.size(); i++) {
            float sweep = (float) (values.get(i) / sum * 360.0);
            arc.setColor(COLORS[i % COLORS.length]);
            canvas.drawArc(box, start, Math.max(sweep - 1.5f, 0.5f), false, arc);
            start += sweep;
        }
        text.setTextSize(20 * d);
        canvas.drawText(centre, getWidth() / 2f, getHeight() / 2f + 2 * d, text);
        text.setTextSize(11 * d);
        text.setFakeBoldText(false);
        text.setColor(0xFF667085);
        canvas.drawText("cases", getWidth() / 2f, getHeight() / 2f + 18 * d, text);
        text.setFakeBoldText(true);
        text.setColor(0xFF111827);
    }
}
