package com.aidalert.sos.engine;

import android.content.Context;

import androidx.annotation.NonNull;

import com.aidalert.sos.Config;
import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.Perms;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline store-and-forward relay over Nearby Connections (Bluetooth / Wi-Fi direct).
 *
 * Every phone with the relay switched on advertises and discovers. When two phones meet they swap the
 * encrypted SOS packets they hold. Packets are encrypted for the control center only (see Crypto),
 * so a relaying phone, and its owner, cannot read them. The first phone that has internet uploads them
 * to relay/{id}; the control center decrypts and turns them into incidents.
 */
public final class RelayManager {
    private static RelayManager instance;

    public static synchronized RelayManager get(Context c) {
        if (instance == null) instance = new RelayManager(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private ConnectionsClient client;
    private boolean running;
    private final Set<String> connected = new HashSet<>();
    private final Set<String> requested = new HashSet<>();

    private RelayManager(Context c) {
        app = c;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        if (running || !Perms.nearby(app)) return;
        Prefs p = Prefs.of(app);
        if (!p.isLoggedIn()) return;
        try {
            client = Nearby.getConnectionsClient(app);
            String name = "aid-" + (p.uid().length() > 6 ? p.uid().substring(0, 6) : p.uid());
            client.startAdvertising(name, Config.NEARBY_SERVICE_ID, lifecycle,
                    new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build());
            client.startDiscovery(Config.NEARBY_SERVICE_ID, discovery,
                    new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build());
            running = true;
        } catch (RuntimeException ignored) {
            running = false;
        }
    }

    public synchronized void stop() {
        if (client != null) {
            try {
                client.stopAdvertising();
                client.stopDiscovery();
                client.stopAllEndpoints();
            } catch (RuntimeException ignored) {
                // already stopped
            }
        }
        connected.clear();
        requested.clear();
        running = false;
    }

    /** New packet queued: push it to everyone we are connected to right now. */
    public synchronized void nudge() {
        if (!running) {
            if (Prefs.of(app).relayOn()) start();
            return;
        }
        for (String ep : new HashSet<>(connected)) sendAll(ep);
    }

    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            synchronized (RelayManager.this) {
                if (connected.contains(endpointId) || !requested.add(endpointId) || client == null) return;
                Prefs p = Prefs.of(app);
                client.requestConnection("aid-" + p.uid(), endpointId, lifecycle);
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            synchronized (RelayManager.this) {
                requested.remove(endpointId);
            }
        }
    };

    private final ConnectionLifecycleCallback lifecycle = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            if (client != null) client.acceptConnection(endpointId, payloads);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            synchronized (RelayManager.this) {
                requested.remove(endpointId);
                if (result.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                    connected.add(endpointId);
                    sendAll(endpointId);
                    // Leave the link open briefly so the other side can send too, then free it.
                    AppExecutors.scheduler().schedule(() -> disconnect(endpointId), 8, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            synchronized (RelayManager.this) {
                connected.remove(endpointId);
            }
        }
    };

    private synchronized void disconnect(String endpointId) {
        if (client != null) client.disconnectFromEndpoint(endpointId);
        connected.remove(endpointId);
    }

    private void sendAll(String endpointId) {
        if (client == null) return;
        List<Db.Packet> list = Db.get(app).pendingPackets();
        for (Db.Packet pk : list) {
            if (pk.hops >= Config.MAX_RELAY_HOPS) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", pk.id);
                o.put("payload", pk.payload);
                o.put("hops", pk.hops + 1);
                o.put("created", pk.created);
                client.sendPayload(endpointId, Payload.fromBytes(o.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (JSONException | RuntimeException ignored) {
                // skip this packet
            }
        }
    }

    private final PayloadCallback payloads = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            byte[] b = payload.asBytes();
            if (b == null) return;
            try {
                JSONObject o = new JSONObject(new String(b, StandardCharsets.UTF_8));
                Db.Packet pk = new Db.Packet();
                pk.id = o.getString("id");
                pk.payload = o.getString("payload");
                pk.hops = o.optInt("hops", 1);
                pk.created = o.optLong("created", System.currentTimeMillis());
                // Ignore packets we already hold (stops loops between phones).
                if (Db.get(app).savePacket(pk)) {
                    AppExecutors.io(() -> {
                        // We might be the lucky phone with internet.
                        Dispatcher.flushRelay(app);
                        if (Prefs.of(app).isCenter()) SyncManager.ingestRelay(app);
                    });
                }
            } catch (JSONException ignored) {
                // not a packet we understand
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            // BYTES payloads arrive in one piece; nothing to track.
        }
    };
}
