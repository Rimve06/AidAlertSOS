package com.aidalert.sos.channel;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.wifi.WifiManager;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.engine.LanServer;
import com.aidalert.sos.net.SmsProtocol;
import com.aidalert.sos.util.NetUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Socket programming: when there is no internet but the phone is on the same Wi-Fi / hotspot as the
 * control center, the alert is sent over a plain TCP socket to the center's LanServer.
 * Target address: the one typed in Settings, or the Wi-Fi gateway (the center phone's hotspot address).
 */
public class LanChannel extends AbstractChannel {
    public LanChannel() {
        super(15_000);
    }

    @Override
    public String name() {
        return "LAN";
    }

    @Override
    public boolean isAvailable(Context c) {
        return NetUtil.wifiNetwork(c) != null && !host(c).isEmpty();
    }

    private static String host(Context c) {
        String h = Prefs.of(c).lanHost();
        if (!h.isEmpty()) return h;
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return "";
        DhcpInfo d = wm.getDhcpInfo();
        if (d == null || d.gateway == 0) return "";
        int g = d.gateway; // little-endian
        return (g & 0xff) + "." + ((g >> 8) & 0xff) + "." + ((g >> 16) & 0xff) + "." + ((g >> 24) & 0xff);
    }

    @Override
    protected void send(Context app, Incident i) throws DeliveryException {
        Network wifi = NetUtil.wifiNetwork(app);
        if (wifi == null) throw new DeliveryException("not on Wi-Fi");
        String line = SmsProtocol.encode(i.id, i.type, i.lat, i.lon, System.currentTimeMillis(),
                i.userId, i.userName, i.phone) + "\n";
        try (Socket s = new Socket()) {
            wifi.bindSocket(s); // use Wi-Fi even if mobile data is the default network
            s.connect(new InetSocketAddress(host(app), LanServer.PORT), 3000);
            s.setSoTimeout(3000);
            OutputStream out = s.getOutputStream();
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.flush();
            String reply = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            if (!"OK".equals(reply)) throw new DeliveryException("control center answered: " + reply);
        } catch (IOException e) {
            throw new DeliveryException("LAN socket failed: " + e.getMessage(), e);
        }
    }
}
