package com.aidalert.sos.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Plain HTTP GET returning text (used for the XML alert feed). Call from a background thread. */
public final class Http {
    private Http() {}

    private static final int MAX_BYTES = 512 * 1024;

    public static String get(String url) throws IOException {
        return get(url, "application/xml, application/rss+xml, application/atom+xml, text/xml");
    }

    public static String get(String url, String accept) throws IOException {
        return get(url, accept, MAX_BYTES, 10000);
    }

    /** Larger answers (map data, long feeds) with an explicit size limit and read timeout. */
    public static String get(String url, String accept, int maxBytes, int readTimeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(readTimeoutMs);
            c.setRequestProperty("Accept", accept);
            c.setRequestProperty("User-Agent", "AidAlertSOS/1.0 (Android)");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > maxBytes) throw new IOException("answer too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    /** GET with extra headers (for example an API key); errors carry the server's own message. */
    public static String getWith(String url, String[][] headers, int readTimeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(readTimeoutMs);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "AidAlertSOS/1.0 (Android)");
            if (headers != null) for (String[] h : headers) c.setRequestProperty(h[0], h[1]);
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String text = "";
            if (in != null) {
                try (InputStream is = in) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (out.size() > 1024 * 1024) throw new IOException("answer too large");
                    }
                    text = out.toString("UTF-8");
                }
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + (text.isEmpty() ? "" : ": " + (text.length() > 200 ? text.substring(0, 200) : text)));
            }
            return text;
        } finally {
            c.disconnect();
        }
    }

    /** POST application/x-www-form-urlencoded (the way Overpass wants queries). */
    public static String postForm(String url, String formBody, int maxBytes, int readTimeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(readTimeoutMs);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "AidAlertSOS/1.0 (Android; student project)");
            byte[] body = formBody.getBytes("UTF-8");
            c.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream o = c.getOutputStream()) {
                o.write(body);
            }
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > maxBytes) throw new IOException("answer too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    /** POST a JSON body with extra headers and return the answer text. Errors carry the server's own message. */
    public static String postJson(String url, String json, String[][] headers, int readTimeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(readTimeoutMs);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "AidAlertSOS/1.0 (Android)");
            if (headers != null) for (String[] h : headers) c.setRequestProperty(h[0], h[1]);
            byte[] body = json.getBytes("UTF-8");
            c.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream o = c.getOutputStream()) {
                o.write(body);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String text = "";
            if (in != null) {
                try (InputStream is = in) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (out.size() > 1024 * 1024) throw new IOException("answer too large");
                    }
                    text = out.toString("UTF-8");
                }
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + (text.isEmpty() ? "" : ": " + (text.length() > 200 ? text.substring(0, 200) : text)));
            }
            return text;
        } finally {
            c.disconnect();
        }
    }
}
