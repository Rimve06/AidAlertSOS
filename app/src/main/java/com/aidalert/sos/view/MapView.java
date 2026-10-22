package com.aidalert.sos.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom view (no map SDK, no API key): plots coloured dots on an auto-scaled plane.
 *  - each point has a colour, a short label and an optional trail (a polyline),
 *  - the view zooms to fit all points,
 *  - tapping a dot reports its index.
 * "Open in Maps" for a real street map is done with an implicit intent from the screens.
 */
public class MapView extends View {

    public static class Dot {
        public double lat;
        public double lon;
        public int color;
        public String label = "";
        public float radiusDp = 7;
        public Object tag;
    }

    public interface OnDotClickListener {
        void onDotClick(int index, Dot dot);
    }

    private final List<Dot> dots = new ArrayList<>();
    private final List<double[]> trail = new ArrayList<>();
    private final Paint bg = new Paint();
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private double minLat, maxLat, minLon, maxLon;
    private float[] sx = new float[0];
    private float[] sy = new float[0];
    private OnDotClickListener listener;
    private int selected = -1;

    public MapView(Context c) {
        this(c, null);
    }

    public MapView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        bg.setColor(0xFFEEF2F6);
        grid.setColor(0xFFD5DCE5);
        grid.setStrokeWidth(dp);
        halo.setColor(0x33000000);
        label.setColor(0xFF1B1F27);
        label.setTextSize(11 * dp);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * dp);
        line.setColor(0xAAC62828);
        line.setStrokeJoin(Paint.Join.ROUND);
        hint.setColor(0xFF667085);
        hint.setTextSize(13 * dp);
        hint.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    public void setOnDotClickListener(OnDotClickListener l) {
        listener = l;
    }

    public void setDots(List<Dot> list) {
        dots.clear();
        dots.addAll(list);
        selected = -1;
        recomputeBounds();
        invalidate();
    }

    /** Ordered GPS points {lat, lon} drawn as a line (movement trail of one person). */
    public void setTrail(List<double[]> points) {
        trail.clear();
        trail.addAll(points);
        recomputeBounds();
        invalidate();
    }

    private void recomputeBounds() {
        minLat = minLon = Double.MAX_VALUE;
        maxLat = maxLon = -Double.MAX_VALUE;
        for (Dot d : dots) extend(d.lat, d.lon);
        for (double[] p : trail) extend(p[0], p[1]);
        sx = new float[dots.size()];
        sy = new float[dots.size()];
    }

    private void extend(double lat, double lon) {
        minLat = Math.min(minLat, lat);
        maxLat = Math.max(maxLat, lat);
        minLon = Math.min(minLon, lon);
        maxLon = Math.max(maxLon, lon);
    }

    private boolean hasData() {
        return minLat <= maxLat;
    }

    /** Longitude degrees are shorter than latitude degrees away from the equator. */
    private double lonScale() {
        return Math.cos(Math.toRadians((minLat + maxLat) / 2));
    }

    private float[] project(double lat, double lon) {
        float pad = 36 * dp;
        float w = getWidth() - 2 * pad;
        float h = getHeight() - 2 * pad;
        double spanLat = Math.max(maxLat - minLat, 0.0008);
        double spanLon = Math.max((maxLon - minLon) * lonScale(), 0.0008);
        double scale = Math.min(w / spanLon, h / spanLat);
        double cLat = (minLat + maxLat) / 2;
        double cLon = (minLon + maxLon) / 2;
        float x = (float) (getWidth() / 2f + (lon - cLon) * lonScale() * scale);
        float y = (float) (getHeight() / 2f - (lat - cLat) * scale);
        return new float[]{x, y};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bg);
        float step = 48 * dp;
        for (float x = 0; x < getWidth(); x += step) canvas.drawLine(x, 0, x, getHeight(), grid);
        for (float y = 0; y < getHeight(); y += step) canvas.drawLine(0, y, getWidth(), y, grid);

        if (!hasData()) {
            canvas.drawText("No locations to show yet", getWidth() / 2f, getHeight() / 2f, hint);
            return;
        }
        if (trail.size() > 1) {
            Path path = new Path();
            for (int i = 0; i < trail.size(); i++) {
                float[] p = project(trail.get(i)[0], trail.get(i)[1]);
                if (i == 0) path.moveTo(p[0], p[1]);
                else path.lineTo(p[0], p[1]);
            }
            canvas.drawPath(path, line);
        }
        for (int i = 0; i < dots.size(); i++) {
            Dot d = dots.get(i);
            float[] p = project(d.lat, d.lon);
            sx[i] = p[0];
            sy[i] = p[1];
            float r = d.radiusDp * dp;
            canvas.drawCircle(p[0], p[1] + dp, r + 2 * dp, halo);
            dot.setColor(i == selected ? 0xFF000000 : 0xFFFFFFFF);
            canvas.drawCircle(p[0], p[1], r + 2 * dp, dot);
            dot.setColor(d.color);
            canvas.drawCircle(p[0], p[1], r, dot);
            if (d.label != null && !d.label.isEmpty()) {
                canvas.drawText(d.label, p[0] + r + 4 * dp, p[1] + 4 * dp, label);
            }
        }
        // scale hint
        double km = (maxLat - minLat) * 111.0;
        String s = String.format(Locale.US, "span ~ %.1f km", Math.max(km, 0.1));
        hint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(s, 8 * dp, getHeight() - 8 * dp, hint);
        hint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int best = -1;
            float bestD = 28 * dp;
            for (int i = 0; i < sx.length; i++) {
                float d = (float) Math.hypot(sx[i] - e.getX(), sy[i] - e.getY());
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            selected = best;
            invalidate();
            if (best >= 0 && listener != null) listener.onDotClick(best, dots.get(best));
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
