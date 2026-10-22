package com.aidalert.sos.ui;

import android.location.Location;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityHelpRequestBinding;
import com.aidalert.sos.engine.LocationHelper;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.Notifier;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** A volunteer helper's view of a nearby request. Position is shared only after "I can help". */
public class HelpRequestActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "req_id";
    public static final String EXTRA_TYPE = "req_type";
    public static final String EXTRA_LAT = "req_lat";
    public static final String EXTRA_LON = "req_lon";
    public static final String EXTRA_KM = "req_km";

    private ActivityHelpRequestBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityHelpRequestBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Help request");
        Notifier.cancel(this, Notifier.ID_HELP);

        final String id = getIntent().getStringExtra(EXTRA_ID);
        final String type = getIntent().getStringExtra(EXTRA_TYPE);
        final double lat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
        final double lon = getIntent().getDoubleExtra(EXTRA_LON, 0);
        double km = getIntent().getDoubleExtra(EXTRA_KM, 0);

        b.txtTitle.setText(Incident.label(type) + " emergency");
        b.txtDistance.setText(String.format(Locale.US, "About %.1f km from you", km));
        b.btnMap.setOnClickListener(v -> Ui.openMap(this, lat, lon, "Help needed"));
        b.btnDecline.setOnClickListener(v -> finish());
        b.btnAccept.setOnClickListener(v -> {
            b.btnAccept.setEnabled(false);
            accept(id);
        });
    }

    private void accept(final String id) {
        final Prefs p = Prefs.of(this);
        final Location l = new LocationHelper(this).lastKnown();
        AppExecutors.io(() -> {
            JSONObject o = new JSONObject();
            try {
                o.put("uid", p.uid());
                o.put("name", p.name());
                o.put("phone", "+" + p.phone());
                if (l != null) {
                    o.put("lat", l.getLatitude());
                    o.put("lon", l.getLongitude());
                }
                o.put("ts", System.currentTimeMillis());
            } catch (JSONException e) {
                return;
            }
            final boolean ok = new FirebaseRest(this).put("helpResponses/" + id + "/" + p.uid(), o);
            AppExecutors.main(() -> {
                if (ok) b.txtResult.setText("Thank you. The control center knows you are coming. Stay in touch with them.");
                else {
                    b.btnAccept.setEnabled(true);
                    b.txtResult.setText("Could not reach the server. Try again.");
                }
            });
        });
    }
}
