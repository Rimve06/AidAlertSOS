package com.aidalert.sos.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A real street map drawn in plain Java: OpenStreetMap tiles are downloaded over HTTPS, kept in memory and on disk
 * (so places you have looked at still show with no internet), and the map can be dragged and pinch-zoomed.
 * Shows your position and tappable markers. Map data and tiles (c) OpenStreetMap contributors, ODbL / tile usage policy.
 */
public class TileMapView extends View {
    public static final class Marker {
        public double lat, lon;
        public String label = "";
        public int color = 0xFFD32F2F;
        public Object tag;
    }

    public interface OnMarkerClick {
        void onClick(Marker m);
    }

    /** A line drawn on the map (a route, or a stretch marked safe / unsafe). */
    public static final class Line {
        public final List<double[]> points = new ArrayList<>();   // {lat, lon}
        public int color = 0xFF1E5BD8;
        public float widthDp = 5;
        public boolean dashed;
    }

    /** Called when an empty spot of the map is tapped. */
    public interface OnMapTap {
        void onTap(double lat, double lon);
    }

    private static final int MIN_ZOOM = 3, MAX_ZOOM = 18;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    private double centerLat = 23.685, centerLon = 90.356; // Bangladesh until a position is known
    private double zoom = 6.5;
    private Double meLat, meLon;
    private float meAccuracyM;
    private final List<Marker> markers = new ArrayList<>();
    private OnMarkerClick clickListener;
    private OnMapTap tapListener;
    private final List<Line> lines = new ArrayList<>();

    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final float dp;

    private final LruCache<String, Bitmap> memory = new LruCache<>(80);
    private final Set<String> loading = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private File cacheDir;
    private boolean offlineNoticeShown;

    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;

    public TileMapView(Context c) {
        this(c, null);
    }

    public TileMapView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        cacheDir = new File(c.getFilesDir(), "osm_tiles");     // not the cache folder: Android may empty that, and the map must stay for offline use
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        bgPaint.setColor(0xFFE8ECF2);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * dp);
        text.setTextSize(11 * dp);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                moveByPixels(dx, dy);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                setZoom(zoom + 1);
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Marker hit = markerAt(e.getX(), e.getY());
                if (hit != null && clickListener != null) clickListener.onClick(hit);
                if (hit == null && tapListener != null) {
                    double[] ll = latLonAt(e.getX(), e.getY());
                    tapListener.onTap(ll[0], ll[1]);
                    return true;
                }
                return hit != null;
            }
        });
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                setZoom(zoom + Math.log(d.getScaleFactor()) / Math.log(2));
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ public api

    public void setCenter(double lat, double lon) {
        centerLat = Math.max(-TileMath.MAX_LAT, Math.min(TileMath.MAX_LAT, lat));
        centerLon = lon;
        invalidate();
    }

    public void setZoom(double z) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        invalidate();
    }

    public double zoom() {
        return zoom;
    }

    /** Shows the blue "you are here" dot. */
    public void setMe(double lat, double lon, float accuracyM) {
        meLat = lat;
        meLon = lon;
        meAccuracyM = accuracyM;
        invalidate();
    }

    public boolean hasMe() {
        return meLat != null;
    }

    /** Moves the map to the user's position (call after setMe). */
    public void centerOnMe(double zoomLevel) {
        if (meLat == null) return;
        setCenter(meLat, meLon);
        setZoom(zoomLevel);
    }

    public void setMarkers(List<Marker> list) {
        markers.clear();
        if (list != null) markers.addAll(list);
        invalidate();
    }

    public void setOnMarkerClick(OnMarkerClick l) {
        clickListener = l;
    }

    public void setOnMapTap(OnMapTap l) {
        tapListener = l;
    }

    public void setLines(List<Line> list) {
        lines.clear();
        if (list != null) lines.addAll(list);
        invalidate();
    }

    /** Latitude / longitude of a point on the view. */
    public double[] latLonAt(float px, float py) {
        int z = (int) Math.floor(zoom);
        float tp = tilePx(z);
        double cx = TileMath.lonToX(centerLon, z), cy = TileMath.latToY(centerLat, z);
        double x = cx + (px - getWidth() / 2.0) / tp, y = cy + (py - getHeight() / 2.0) / tp;
        return new double[]{TileMath.yToLat(y, z), TileMath.xToLon(x, z)};
    }

    public double centerLat() {
        return centerLat;
    }

    public double centerLon() {
        return centerLon;
    }

    /** Moves and zooms the map so that all the points are visible. */
    public void fitTo(List<double[]> pts) {
        if (pts == null || pts.isEmpty() || getWidth() == 0) return;
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (double[] p : pts) {
            minLat = Math.min(minLat, p[0]);
            maxLat = Math.max(maxLat, p[0]);
            minLon = Math.min(minLon, p[1]);
            maxLon = Math.max(maxLon, p[1]);
        }
        setCenter((minLat + maxLat) / 2, (minLon + maxLon) / 2);
        double z = MAX_ZOOM;
        while (z > MIN_ZOOM) {
            int zi = (int) Math.floor(z);
            double scale = Math.pow(2, z - zi);
            float tp = (float) (TileMath.TILE * dp * 0.5 * scale);
            double w = Math.abs(TileMath.lonToX(maxLon, zi) - TileMath.lonToX(minLon, zi)) * tp;
            double h = Math.abs(TileMath.latToY(minLat, zi) - TileMath.latToY(maxLat, zi)) * tp;
            if (w < getWidth() * 0.8 && h < getHeight() * 0.8) break;
            z -= 0.5;
        }
        setZoom(z);
    }

    /** Saves the tiles of a square of (2*reach+1)^2 tiles around a point at the given zoom levels, for offline use. */
    public void prefetchArea(double lat, double lon, int reach, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            int cx = (int) Math.floor(TileMath.lonToX(lon, z)), cy = (int) Math.floor(TileMath.latToY(lat, z));
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    int y = cy + dy;
                    if (TileMath.validY(y, z)) request(z, cx + dx, y);
                }
            }
        }
    }

    /** Downloads the tiles around a point for a few zoom levels so the area still shows offline. Call when online. */
    public void prefetchAround(double lat, double lon) {
        for (int z = 13; z <= 16; z++) {
            int cx = (int) Math.floor(TileMath.lonToX(lon, z)), cy = (int) Math.floor(TileMath.latToY(lat, z));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = cx + dx, y = cy + dy;
                    if (TileMath.validY(y, z)) request(z, x, y);
                }
            }
        }
    }

    // ------------------------------------------------------------------ gestures

    private void moveByPixels(float dx, float dy) {
        int z = (int) Math.floor(zoom);
        double scale = TileMath.TILE * dp * Math.pow(2, zoom - z) * 0.5; // pixels per tile at this zoom
        double cx = TileMath.lonToX(centerLon, z) + dx / scale;
        double cy = TileMath.latToY(centerLat, z) + dy / scale;
        centerLon = TileMath.xToLon(cx, z);
        centerLat = Math.max(-TileMath.MAX_LAT, Math.min(TileMath.MAX_LAT, TileMath.yToLat(cy, z)));
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean a = scaler.onTouchEvent(e);
        boolean b = gestures.onTouchEvent(e);
        return a || b || super.onTouchEvent(e);
    }

    // ------------------------------------------------------------------ drawing

    private float tilePx(int z) {
        return (float) (TileMath.TILE * dp * 0.5 * Math.pow(2, zoom - z));
    }

    private float xOf(double lon, int z, double cx, float tp) {
        return (float) (getWidth() / 2.0 + (TileMath.lonToX(lon, z) - cx) * tp);
    }

    private float yOf(double lat, int z, double cy, float tp) {
        return (float) (getHeight() / 2.0 + (TileMath.latToY(lat, z) - cy) * tp);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        int z = (int) Math.floor(zoom);
        float tp = tilePx(z);
        double cx = TileMath.lonToX(centerLon, z), cy = TileMath.latToY(centerLat, z);
        int x0 = (int) Math.floor(cx - getWidth() / 2.0 / tp), x1 = (int) Math.floor(cx + getWidth() / 2.0 / tp);
        int y0 = (int) Math.floor(cy - getHeight() / 2.0 / tp), y1 = (int) Math.floor(cy + getHeight() / 2.0 / tp);
        RectF dst = new RectF();
        int missing = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (!TileMath.validY(y, z)) continue;
                float left = (float) (getWidth() / 2.0 + (x - cx) * tp), top = (float) (getHeight() / 2.0 + (y - cy) * tp);
                dst.set(left, top, left + tp + 1, top + tp + 1);
                Bitmap bm = tile(z, x, y);
                if (bm != null) c.drawBitmap(bm, null, dst, tilePaint);
                else missing++;
            }
        }
        // my position
        if (meLat != null) {
            float mx = xOf(meLon, z, cx, tp), my = yOf(meLat, z, cy, tp);
            float r = 10 * dp;
            fill.setColor(0x331E5BD8);
            c.drawCircle(mx, my, r * 2.2f, fill);
            fill.setColor(0xFF1E5BD8);
            c.drawCircle(mx, my, 8 * dp, fill);
            stroke.setColor(0xFFFFFFFF);
            c.drawCircle(mx, my, 8 * dp, stroke);
        }
        // routes and marked stretches
        for (Line ln : lines) {
            if (ln.points.size() < 2) continue;
            android.graphics.Path path = new android.graphics.Path();
            boolean first = true;
            for (double[] pt : ln.points) {
                float px = xOf(pt[1], z, cx, tp), py = yOf(pt[0], z, cy, tp);
                if (first) path.moveTo(px, py);
                else path.lineTo(px, py);
                first = false;
            }
            stroke.setColor(ln.color);
            stroke.setStrokeWidth(ln.widthDp * dp);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setPathEffect(ln.dashed ? new android.graphics.DashPathEffect(new float[]{18 * dp, 12 * dp}, 0) : null);
            c.drawPath(path, stroke);
            stroke.setPathEffect(null);
            stroke.setStrokeWidth(2 * dp);
        }
        // markers
        for (Marker m : markers) {
            float mx = xOf(m.lon, z, cx, tp), my = yOf(m.lat, z, cy, tp);
            if (mx < -40 || my < -40 || mx > getWidth() + 40 || my > getHeight() + 40) continue;
            fill.setColor(m.color);
            c.drawCircle(mx, my - 12 * dp, 11 * dp, fill);
            stroke.setColor(0xFFFFFFFF);
            c.drawCircle(mx, my - 12 * dp, 11 * dp, stroke);
            fill.setColor(m.color);
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(mx - 6 * dp, my - 4 * dp);
            p.lineTo(mx + 6 * dp, my - 4 * dp);
            p.lineTo(mx, my + 2 * dp);
            p.close();
            c.drawPath(p, fill);
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            String l = m.label.isEmpty() ? "" : m.label.substring(0, 1);
            c.drawText(l, mx, my - 8 * dp, text);
        }
        // attribution (required by OpenStreetMap)
        String attr = "© OpenStreetMap contributors";
        text.setTextAlign(Paint.Align.RIGHT);
        float w = text.measureText(attr) + 10 * dp;
        fill.setColor(0xB3FFFFFF);
        c.drawRect(getWidth() - w, getHeight() - 18 * dp, getWidth(), getHeight(), fill);
        text.setColor(0xFF333333);
        text.setTextSize(10 * dp);
        c.drawText(attr, getWidth() - 4 * dp, getHeight() - 5 * dp, text);
        text.setTextSize(11 * dp);
        if (missing > 0 && !failed.isEmpty() && memory.size() == 0 && !offlineNoticeShown) {
            text.setColor(0xFF555555);
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("Map tiles need internet the first time. Places you viewed online stay available.", getWidth() / 2f, getHeight() / 2f, text);
        }
    }

    private Marker markerAt(float px, float py) {
        int z = (int) Math.floor(zoom);
        float tp = tilePx(z);
        double cx = TileMath.lonToX(centerLon, z), cy = TileMath.latToY(centerLat, z);
        Marker best = null;
        float bestD = 28 * dp;
        for (Marker m : markers) {
            float mx = xOf(m.lon, z, cx, tp), my = yOf(m.lat, z, cy, tp) - 12 * dp;
            float d = (float) Math.hypot(px - mx, py - my);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ tiles

    private Bitmap tile(int z, int x, int y) {
        String k = TileMath.key(z, x, y);
        Bitmap b = memory.get(k);
        if (b != null) return b;
        request(z, x, y);
        // while the sharp tile loads, show a blurry parent so the map is never blank
        for (int up = 1; up <= 3 && z - up >= MIN_ZOOM; up++) {
            int pz = z - up, px = TileMath.wrapX(x, z) >> up, py = y >> up;
            Bitmap parent = memory.get(TileMath.key(pz, px, py));
            if (parent != null) {
                int size = parent.getWidth() >> up;
                int sx = ((TileMath.wrapX(x, z)) - (px << up)) * size, sy = (y - (py << up)) * size;
                return Bitmap.createBitmap(parent, sx, sy, Math.max(1, size), Math.max(1, size));
            }
        }
        return null;
    }

    private void request(final int z, final int x, final int y) {
        final String k = TileMath.key(z, x, y);
        synchronized (loading) {
            if (memory.get(k) != null || loading.contains(k)) return;
            loading.add(k);
        }
        POOL.execute(() -> {
            Bitmap bm = null;
            File f = new File(cacheDir, k);
            try {
                if (f.exists() && f.length() > 0) bm = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bm == null) {
                    HttpURLConnection con = (HttpURLConnection) new URL(TileMath.url(z, x, y)).openConnection();
                    try {
                        con.setConnectTimeout(8000);
                        con.setReadTimeout(10000);
                        con.setRequestProperty("User-Agent", "AidAlertSOS/1.0 (Android student project)");
                        if (con.getResponseCode() == 200) {
                            try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                                byte[] buf = new byte[8192];
                                int n;
                                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            }
                            bm = BitmapFactory.decodeFile(f.getAbsolutePath());
                        }
                    } finally {
                        con.disconnect();
                    }
                }
            } catch (Exception ignored) {
                // offline or blocked: tile stays blank, retried on the next draw
            }
            final Bitmap result = bm;
            synchronized (loading) {
                loading.remove(k);
                if (result != null) {
                    memory.put(k, result);
                    failed.remove(k);
                } else {
                    failed.add(k);
                }
            }
            if (result != null) postInvalidate();
        });
    }
}
