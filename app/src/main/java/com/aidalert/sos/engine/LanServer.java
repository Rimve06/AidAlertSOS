package com.aidalert.sos.engine;

import android.content.Context;
import android.util.Log;

import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.util.AppExecutors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Socket programming, server side (control-center phone): a ServerSocket thread accepts TCP
 * connections from citizen phones on the same Wi-Fi / hotspot, reads one alert line, creates the
 * incident and answers "OK". Each client is handled on a pool thread (concurrency).
 */
public final class LanServer {
    public static final int PORT = 8765;
    private static final String TAG = "AidLan";
    private static LanServer instance;

    public static synchronized LanServer get(Context c) {
        if (instance == null) instance = new LanServer(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptThread;

    private LanServer(Context c) {
        app = c;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "aid-lan-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        server = null;
    }

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket(PORT)) {
            synchronized (this) {
                server = ss;
            }
            while (running) {
                final Socket client = ss.accept();
                AppExecutors.io(() -> handle(client));
            }
        } catch (IOException e) {
            if (running) Log.w(TAG, "server stopped: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    private void handle(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            SmsProtocol.Parsed p = SmsProtocol.decode(line == null ? "" : line.trim());
            OutputStream out = s.getOutputStream();
            if (p == null) {
                out.write("ERR\n".getBytes(StandardCharsets.UTF_8));
            } else {
                IncidentIngest.fromText(app, p, "LAN");
                out.write("OK\n".getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        } catch (IOException e) {
            Log.w(TAG, "client error: " + e.getMessage());
        }
    }
}
