package com.aidalert.sos.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import com.aidalert.sos.ui.BaseActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityPermissionsBinding;
import com.aidalert.sos.databinding.ItemPermBinding;
import com.aidalert.sos.engine.RelayManager;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.util.Perms;

import java.util.ArrayList;
import java.util.List;

/** Guided checklist of everything the SOS triggers need. Shown after login and from Settings. */
public class PermissionsActivity extends BaseActivity {

    private static final int REQ = 77;

    interface Check { boolean ok(Context c); }
    interface Grant { void run(PermissionsActivity a); }

    static class Item {
        final String title, why;
        final Check check;
        final Grant grant;

        Item(String title, String why, Check check, Grant grant) {
            this.title = title;
            this.why = why;
            this.check = check;
            this.grant = grant;
        }
    }

    private ActivityPermissionsBinding b;
    private final List<Item> items = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPermissionsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Permissions");

        items.add(new Item("Location", "Sends your position to responders after an SOS.",
                Perms::location, a -> a.ask(Perms.locationPerms())));
        items.add(new Item("Location - all the time", "Lets the phone be tracked while the screen is off.",
                Perms::backgroundLocation, a -> {
                    if (Build.VERSION.SDK_INT >= 30) a.openAppSettings();
                    else a.ask(new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION});
                }));
        items.add(new Item("Send SMS", "Texts your emergency contacts and the control center when offline.",
                Perms::sms, a -> a.ask(Perms.smsPerms())));
        items.add(new Item("Phone calls", "Places the automatic emergency call.",
                Perms::call, a -> a.ask(Perms.callPerms())));
        items.add(new Item("Notifications", "Disaster alerts and control center replies.",
                Perms::notifications, a -> a.ask(Perms.notificationPerms())));
        items.add(new Item("Nearby devices", "Offline relay through other phones (Bluetooth / Wi-Fi).",
                Perms::nearby, a -> a.ask(Perms.nearbyPerms())));
        items.add(new Item("Volume-key service", "Accessibility: detects the 2-second volume holds. Turn on 'AidAlert SOS buttons'.",
                Perms::accessibilityEnabled, a -> a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        items.add(new Item("Show over other apps", "Lets the emergency page open on top when the phone is in use.",
                Perms::overlay, a -> a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + a.getPackageName())))));
        items.add(new Item("Unrestricted battery", "Keeps the power-button guard running in the background.",
                Perms::batteryUnrestricted, a -> a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + a.getPackageName())))));

        adapter = new Adapter();
        b.listPerms.setLayoutManager(new LinearLayoutManager(this));
        b.listPerms.setAdapter(adapter);
        b.btnContinue.setOnClickListener(v -> {
            Prefs.of(this).permsShown(true);
            if (Prefs.of(this).guardOn()) SosGuardService.start(this);
            if (Perms.nearby(this)) RelayManager.get(this).start();
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
    }

    void ask(String[] perms) {
        if (perms.length == 0) return;
        ActivityCompat.requestPermissions(this, perms, REQ);
    }

    void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final ItemPermBinding v;

            VH(ItemPermBinding v) {
                super(v.getRoot());
                this.v = v;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemPermBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Item it = items.get(pos);
            boolean ok = it.check.ok(PermissionsActivity.this);
            h.v.txtTitle.setText(it.title);
            h.v.txtWhy.setText(it.why);
            h.v.btnGrant.setText(ok ? "Done" : "Allow");
            h.v.btnGrant.setEnabled(!ok);
            h.v.btnGrant.setVisibility(View.VISIBLE);
            h.v.btnGrant.setOnClickListener(v -> it.grant.run(PermissionsActivity.this));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
