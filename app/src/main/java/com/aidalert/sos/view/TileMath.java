package com.aidalert.sos.view;

/** Web-Mercator "slippy map" tile maths used by TileMapView (OpenStreetMap tile scheme). Pure Java. */
public final class TileMath {
    private TileMath() {}

    public static final int TILE = 256;
    public static final double MAX_LAT = 85.05112878;

    /** Tile column (fractional) of a longitude at integer zoom z. */
    public static double lonToX(double lon, int z) {
        return (lon + 180.0) / 360.0 * (1 << z);
    }

    /** Tile row (fractional) of a latitude at integer zoom z. */
    public static double latToY(double lat, int z) {
        double l = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));
        double r = Math.toRadians(l);
        return (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * (1 << z);
    }

    public static double xToLon(double x, int z) {
        return x / (1 << z) * 360.0 - 180.0;
    }

    public static double yToLat(double y, int z) {
        double n = Math.PI - 2.0 * Math.PI * y / (1 << z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /** Wraps a tile column into 0..2^z-1 (the map repeats east-west). */
    public static int wrapX(int x, int z) {
        int n = 1 << z;
        return ((x % n) + n) % n;
    }

    public static boolean validY(int y, int z) {
        return y >= 0 && y < (1 << z);
    }

    /** Standard OpenStreetMap tile address. */
    public static String url(int z, int x, int y) {
        return "https://tile.openstreetmap.org/" + z + "/" + wrapX(x, z) + "/" + y + ".png";
    }

    /** Cache file name for a tile. */
    public static String key(int z, int x, int y) {
        return z + "_" + wrapX(x, z) + "_" + y + ".png";
    }

    /** Metres per pixel at a latitude and fractional zoom (for the scale bar). */
    public static double metresPerPixel(double lat, double zoom) {
        return 156543.03392 * Math.cos(Math.toRadians(lat)) / Math.pow(2.0, zoom);
    }
}
