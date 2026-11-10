package com.aidalert.sos.ui.center;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;

import com.aidalert.sos.data.Incident;
import com.aidalert.sos.databinding.ActivityAnalyticsBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.view.TileMapView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Analytics from this app's own SOS incidents (not the police crime statistics): monthly bar chart,
 * emergency types, busiest hours, response performance, a map of every SOS and the areas with most reports.
 * Areas are ~11 km grid cells; the on-screen note explains why "more reports" does not mean "less safe".
 */
public class AnalyticsActivity extends BaseActivity {
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private ActivityAnalyticsBinding b;
    private final List<Incident> all = new ArrayList<>();
    private String reportText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAnalyticsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Monthly report & safety");
        b.btnShareReport.setOnClickListener(v -> {
            if (reportText.isEmpty()) {
                Ui.toast(this, "Nothing to share yet");
                return;
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "AidAlertSOS monthly report");
            send.putExtra(Intent.EXTRA_TEXT, reportText);
            startActivity(Intent.createChooser(send, "Share monthly report"));
        });
        load();
    }

    private void load() {
        if (!NetUtil.isOnline(this)) {
            b.txtSummary.setText("Offline. Analytics need internet to read the incident database.");
            return;
        }
        b.txtSummary.setText("Loading incidents...");
        AppExecutors.io(() -> {
            final List<Incident> list = CenterData.parseIncidents(new FirebaseRest(this).getObj("incidents"));
            // Area names (best effort, needs a geocoder on the phone); computed off the main thread.
            final Map<String, int[]> cells = cells(list);
            final List<String> areaLines = areaLines(cells, list.size());
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                all.clear();
                all.addAll(list);
                render(areaLines);
            });
        });
    }

    private static String cellKey(Incident i) {
        return String.format(Locale.US, "%.1f,%.1f", i.lat, i.lon);
    }

    private static Map<String, int[]> cells(List<Incident> list) {
        Map<String, int[]> m = new HashMap<>();
        for (Incident i : list) {
            if (!i.hasFix()) continue;
            int[] c = m.get(cellKey(i));
            if (c == null) {
                c = new int[1];
                m.put(cellKey(i), c);
            }
            c[0]++;
        }
        return m;
    }

    private List<String> areaLines(Map<String, int[]> cells, int total) {
        List<Map.Entry<String, int[]>> top = new ArrayList<>(cells.entrySet());
        Collections.sort(top, (x, y) -> y.getValue()[0] - x.getValue()[0]);
        List<String> out = new ArrayList<>();
        boolean geo = Geocoder.isPresent();
        Geocoder g = geo ? new Geocoder(this, Locale.ENGLISH) : null;
        for (int k = 0; k < top.size() && k < 8; k++) {
            String key = top.get(k).getKey();
            String[] ll = key.split(",");
            double lat = Double.parseDouble(ll[0]), lon = Double.parseDouble(ll[1]);
            String name = String.format(Locale.US, "Area near %.1f N, %.1f E", lat, lon);
            if (g != null) {
                try {
                    List<Address> a = g.getFromLocation(lat, lon, 1);
                    if (a != null && !a.isEmpty()) {
                        Address ad = a.get(0);
                        String n = ad.getSubAdminArea() != null ? ad.getSubAdminArea()
                                : ad.getLocality() != null ? ad.getLocality() : ad.getAdminArea();
                        if (n != null) name = n;
                    }
                } catch (java.io.IOException | RuntimeException e) {
                    g = null;   // no geocoder backend: stop trying, coordinates are shown instead
                }
            }
            int n = top.get(k).getValue()[0];
            out.add((k + 1) + ". " + name + ": " + n + " SOS (" + Math.round(100.0 * n / Math.max(1, total)) + "%)");
        }
        return out;
    }

    private void render(List<String> areaLines) {
        Calendar now = Calendar.getInstance();
        // Last 6 months, oldest first.
        int[] perMonth = new int[6];
        String[] mLabels = new String[6];
        int[] mKey = new int[6];
        for (int k = 0; k < 6; k++) {
            Calendar c = (Calendar) now.clone();
            c.set(Calendar.DAY_OF_MONTH, 1);
            c.add(Calendar.MONTH, k - 5);
            mKey[k] = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH);
            mLabels[k] = MONTHS[c.get(Calendar.MONTH)];
        }
        Map<String, Integer> types = new HashMap<>();
        int[] hours = new int[24];
        int resolved = 0, falseAlarm = 0, cancelled = 0, unanswered = 0, active = 0;
        int thisMonth = 0, thisMonthResolved = 0;
        long ackSum = 0, ackN = 0;
        int nowKey = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH);
        List<TileMapView.Marker> markers = new ArrayList<>();
        List<double[]> pts = new ArrayList<>();
        Calendar tmp = Calendar.getInstance();
        for (Incident i : all) {
            tmp.setTimeInMillis(i.createdAt);
            int key = tmp.get(Calendar.YEAR) * 12 + tmp.get(Calendar.MONTH);
            for (int k = 0; k < 6; k++) if (mKey[k] == key) perMonth[k]++;
            hours[tmp.get(Calendar.HOUR_OF_DAY)]++;
            types.merge(Incident.label(i.type), 1, Integer::sum);
            if ("RESOLVED".equals(i.status)) resolved++;
            if ("FALSE_ALARM".equals(i.status)) falseAlarm++;
            if ("CANCELLED".equals(i.status)) cancelled++;
            if (i.isActive()) active++;
            if ("NEW".equals(i.status)) unanswered++;
            if (key == nowKey) {
                thisMonth++;
                if ("RESOLVED".equals(i.status)) thisMonthResolved++;
            }
            if (!i.ackBy.isEmpty() && i.opsAt > i.createdAt && i.createdAt > 0) {
                ackSum += i.opsAt - i.createdAt;
                ackN++;
            }
            if (i.hasFix()) {
                TileMapView.Marker m = new TileMapView.Marker();
                m.lat = i.lat;
                m.lon = i.lon;
                m.color = Incident.color(i.type);
                markers.add(m);
                pts.add(new double[]{i.lat, i.lon});
            }
        }
        List<String> ml = new ArrayList<>();
        List<Float> mv = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            ml.add(mLabels[k]);
            mv.add((float) perMonth[k]);
        }
        b.chartMonths.setData(ml, mv, 0xFF0B2A5B);

        List<Map.Entry<String, Integer>> te = new ArrayList<>(types.entrySet());
        Collections.sort(te, (x, y) -> y.getValue() - x.getValue());
        List<String> tl = new ArrayList<>();
        List<Float> tv = new ArrayList<>();
        for (Map.Entry<String, Integer> e : te) {
            String l = e.getKey();
            tl.add(l.length() > 9 ? l.substring(0, 8) + "." : l);
            tv.add((float) e.getValue());
        }
        b.chartTypes.setData(tl, tv, 0xFFC62828);

        List<String> hl = new ArrayList<>();
        List<Float> hv = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hl.add(String.valueOf(h));
            hv.add((float) hours[h]);
        }
        b.chartHours.setData(hl, hv, 0xFF1565C0);

        String avg = ackN == 0 ? "no data yet" : formatMs(ackSum / ackN);
        String perf = "Total SOS: " + all.size() + "   (this month: " + thisMonth + ")\n"
                + "Active now: " + active + "   Still unanswered: " + unanswered + "\n"
                + "Resolved: " + resolved + "   False alarms: " + falseAlarm + "   Cancelled by citizen: " + cancelled + "\n"
                + "Average time until the control center acted (approx.): " + avg;
        b.txtPerf.setText(perf);
        b.txtSummary.setText(thisMonth + " SOS this month, " + all.size() + " in total.");

        b.mapAll.setMarkers(markers);
        if (!pts.isEmpty()) {
            if (pts.size() == 1) {
                b.mapAll.setCenter(pts.get(0)[0], pts.get(0)[1]);
                b.mapAll.setZoom(13);
            } else {
                b.mapAll.post(() -> b.mapAll.fitTo(pts));
            }
        } else {
            b.mapAll.setCenter(23.80, 90.40);
            b.mapAll.setZoom(6.5);
        }
        b.txtAreas.setText(areaLines.isEmpty() ? "No located incidents yet." : String.join("\n", areaLines));

        StringBuilder r = new StringBuilder("AidAlertSOS monthly report - ")
                .append(MONTHS[now.get(Calendar.MONTH)]).append(' ').append(now.get(Calendar.YEAR)).append("\n\n")
                .append("SOS this month: ").append(thisMonth).append(" (resolved ").append(thisMonthResolved).append(")\n")
                .append(perf).append("\n\nSOS per month:\n");
        for (int k = 0; k < 6; k++) r.append("  ").append(mLabels[k]).append(": ").append(perMonth[k]).append('\n');
        r.append("\nBy type:\n");
        for (Map.Entry<String, Integer> e : te) r.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        r.append("\nAreas with most SOS reports (~11 km cells):\n");
        for (String l : areaLines) r.append("  ").append(l).append('\n');
        r.append("\nNote: report counts depend on app usage in each area and do not by themselves measure danger.");
        reportText = r.toString();
    }

    private static String formatMs(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + " s";
        if (s < 3600) return (s / 60) + " min " + (s % 60) + " s";
        return (s / 3600) + " h " + ((s % 3600) / 60) + " min";
    }
}
