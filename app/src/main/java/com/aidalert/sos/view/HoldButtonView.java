package com.aidalert.sos.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Custom view: big round button that must be held for a moment (a ring fills while holding).
 * Holding instead of tapping prevents accidental alerts from the on-screen button.
 */
public class HoldButtonView extends View {

    public interface OnHoldCompleteListener {
        void onHoldComplete();
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private ValueAnimator anim;
    private float progress;
    private long holdMs = 1500;
    private String label = "SOS";
    private String sub = "hold to send";
    private OnHoldCompleteListener listener;

    public HoldButtonView(Context c) {
        this(c, null);
    }

    public HoldButtonView(Context c, AttributeSet a) {
        super(c, a);
        fill.setColor(0xFFC62828);
        ringBg.setStyle(Paint.Style.STROKE);
        ringBg.setColor(0x33FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFFFFFFFF);
        ring.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setClickable(true);
    }

    public void setOnHoldCompleteListener(OnHoldCompleteListener l) {
        listener = l;
    }

    public void setHoldMs(long ms) {
        holdMs = ms;
    }

    public void setTexts(String main, String subtitle) {
        label = main;
        sub = subtitle;
        invalidate();
    }

    public void setColor(int color) {
        fill.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), (int) (getResources().getDisplayMetrics().density * 260));
        if (MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED) size = (int) (getResources().getDisplayMetrics().density * 220);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        float stroke = s * 0.045f;
        ring.setStrokeWidth(stroke);
        ringBg.setStrokeWidth(stroke);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = s / 2f - stroke;
        canvas.drawCircle(cx, cy, r, fill);
        box.set(cx - r + stroke, cy - r + stroke, cx + r - stroke, cy + r - stroke);
        canvas.drawArc(box, 0, 360, false, ringBg);
        if (progress > 0) canvas.drawArc(box, -90, 360 * progress, false, ring);
        text.setTextSize(s * 0.24f);
        canvas.drawText(label, cx, cy + s * 0.05f, text);
        text.setTextSize(s * 0.075f);
        canvas.drawText(sub, cx, cy + s * 0.2f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startHold();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void startHold() {
        cancelHold();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(holdMs);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
            if (progress >= 1f) {
                anim.removeAllUpdateListeners();
                progress = 0;
                invalidate();
                if (listener != null) listener.onHoldComplete();
            }
        });
        anim.start();
    }

    private void cancelHold() {
        if (anim != null) {
            anim.removeAllUpdateListeners();
            anim.cancel();
            anim = null;
        }
        progress = 0;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelHold();
        super.onDetachedFromWindow();
    }
}
