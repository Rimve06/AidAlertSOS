package com.aidalert.sos.directory;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.aidalert.sos.engine.LocationHelper;
import com.aidalert.sos.net.Http;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.CallHelper;
import com.aidalert.sos.util.Geo;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.Perms;
import com.aidalert.sos.view.TileMapView;

import org.json.JSONException;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Map: a real street map (OpenStreetMap tiles, saved on the phone as you look at them so they still show offline),
 * category chips (hospitals, police, food, fuel, hotels, shops, ATMs...), search for a destination, directions with
 * distance and time, and your own "safe" / "unsafe" marks on paths. Directions need internet; with no internet a
 * straight line and the distance are shown. Map data (c) OpenStreetMap contributors, ODbL.
 */
public class MapActivity extends BaseActivity {
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    public static final String EXTRA_NAME = "name";

    private static final int SAFE_GREEN = 0xFF2E7D32, UNSAFE_RED = 0xFFC62828, ROUTE_BLUE = 0xFF1E5BD8;
    private static final double DHAKA_LAT = 23.8103, DHAKA_LON = 90.4125;

    private static final String[][] CHIPS = {
            {"Hospitals & clinics", Facility.HOSPITAL}, {"Police", Facility.POLICE}, {"Fire stations", Facility.FIRE},
            {"Blood banks", Facility.BLOOD}, {"Safe havens", Facility.SHELTER}, {"Pharmacies", Facility.PHARMACY},
            {"Restaurants & coffee", Facility.FOOD}, {"Fuel", Facility.FUEL}, {"Hotels", Facility.HOTEL},
            {"Shopping", Facility.SHOP}, {"ATMs", Facility.ATM}};

    private TileMapView map;
    private TextView txtTitle, txtInfo, btnMode;
    private ListView resultList;
    private final List<Facility> results = new ArrayList<>();
    private ArrayAdapter<Facility> resultAdapter;
    private EditText edtSearch;
    private final List<TextView> chipViews = new ArrayList<>();
    private DirectoryRepo repo;
    private MapStore store;
    private LocationHelper live;
    private boolean liveOn;
    private Location here;
    private boolean centeredOnMe;

    private String kind = "";
    private String mode = RouteRules.MODE_CAR;
    private double destLat = Double.NaN, destLon = Double.NaN;
    private String destName = "";
    private Facility destFacility;
    private RouteRules.Route route;
    private boolean offerSafeUnsafe;

    private final ActivityResultLauncher<String[]> locPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (Perms.location(this)) startLive();
                else Ui.toast(this, "Location not allowed: the map still works, but directions need a starting point.");
            });

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new DirectoryRepo(this);
        store = new MapStore(this);
        buildUi();
        setTitle("Map");

        double lat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
        double lon = getIntent().getDoubleExtra(EXTRA_LON, Double.NaN);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        map.setCenter(DHAKA_LAT, DHAKA_LON);
        map.setZoom(12);
        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            map.setCenter(lat, lon);
            map.setZoom(15);
            setDestination(lat, lon, name == null || name.isEmpty() ? "Selected place" : name, null);
        } else {
            restoreLastRoute();
        }
        drawLines();
        if (Perms.location(this)) startLive();
        else locPermission.launch(Perms.locationPerms());
    }

    // ------------------------------------------------------------------ UI

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView button(String text, int fill, int textColor) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(textColor);
        t.setTextSize(13);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(pill(fill, 0x33000000));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        map = new TileMapView(this);
        root.addView(map, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // top: search + chips
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), 0);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(pill(0xFFFFFFFF, 0x33000000));
        searchRow.setPadding(dp(14), 0, dp(4), 0);
        edtSearch = new EditText(this);
        edtSearch.setHint("Search a place or address");
        edtSearch.setSingleLine(true);
        edtSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        edtSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edtSearch.setBackground(null);
        edtSearch.setTextSize(15);
        edtSearch.setTextColor(0xFF111111);
        edtSearch.setHintTextColor(0xFF777777);
        edtSearch.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        searchRow.addView(edtSearch, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView go = button("Search", 0xFF1E5BD8, Color.WHITE);
        go.setOnClickListener(v -> search());
        searchRow.addView(go);
        top.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, dp(4));
        for (final String[] c : CHIPS) {
            final TextView t = button(c[0], 0xFFFFFFFF, 0xFF222222);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            t.setOnClickListener(v -> chooseKind(c[1]));
            t.setTag(c[1]);
            chipViews.add(t);
            chips.addView(t, lp);
        }
        hs.addView(chips);
        top.addView(hs);
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // right: zoom and locate
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        TextView plus = button("+", 0xFFFFFFFF, 0xFF222222);
        TextView minus = button("-", 0xFFFFFFFF, 0xFF222222);
        TextView me = button("Me", 0xFFFFFFFF, 0xFF1E5BD8);
        plus.setOnClickListener(v -> map.setZoom(Math.min(18, map.zoom() + 1)));
        minus.setOnClickListener(v -> map.setZoom(Math.max(3, map.zoom() - 1)));
        me.setOnClickListener(v -> goToMe());
        for (TextView t : new TextView[]{plus, minus, me}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
            lp.topMargin = dp(8);
            t.setPadding(0, 0, 0, 0);
            side.addView(t, lp);
        }
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        sp.rightMargin = dp(8);
        root.addView(side, sp);

        // bottom card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xFFFFFFFF);
        cg.setCornerRadii(new float[]{dp(18), dp(18), dp(18), dp(18), 0, 0, 0, 0});
        card.setBackground(cg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setElevation(dp(8));
        resultList = new ListView(this);
        resultList.setVisibility(View.GONE);
        resultAdapter = new ArrayAdapter<Facility>(this, android.R.layout.simple_list_item_2, android.R.id.text1, results) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                Facility f = getItem(position);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                t1.setTextColor(0xFF111111);
                t2.setTextColor(0xFF555555);
                t1.setText(f.name == null || f.name.isEmpty() ? Facility.label(f.kind) : f.name);
                t2.setText(RouteRules.formatDistance(f.distanceKm * 1000) + "  -  "
                        + (f.hasPhone() ? f.phone : "no phone on record") + "  -  tap for directions");
                return v;
            }
        };
        resultList.setAdapter(resultAdapter);
        resultList.setOnItemClickListener((AdapterView<?> p, View v, int pos, long id) -> {
            Facility f = results.get(pos);
            setDestination(f.lat, f.lon, f.name == null || f.name.isEmpty() ? Facility.label(f.kind) : f.name, f);
            map.setCenter(f.lat, f.lon);
            map.setZoom(16);
            getDirections();
        });
        card.addView(resultList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));
        txtTitle = new TextView(this);
        txtTitle.setTextColor(0xFF111111);
        txtTitle.setTextSize(16);
        txtTitle.setTypeface(null, Typeface.BOLD);
        txtTitle.setText("Tap the map or a pin, or search, to choose a destination");
        txtInfo = new TextView(this);
        txtInfo.setTextColor(0xFF444444);
        txtInfo.setTextSize(13);
        card.addView(txtTitle);
        card.addView(txtInfo);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(8), 0, 0);
        btnMode = button("By car", 0xFFEAF1FF, 0xFF1E5BD8);
        btnMode.setOnClickListener(v -> cycleMode());
        TextView dir = button("Directions", 0xFF1E5BD8, Color.WHITE);
        dir.setOnClickListener(v -> getDirections());
        TextView call = button("Call", 0xFF2E7D32, Color.WHITE);
        call.setOnClickListener(v -> callDestination());
        for (TextView t : new TextView[]{btnMode, dir, call}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(6);
            row1.addView(t, lp);
        }
        card.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(6), 0, 0);
        TextView safe = button("Mark safe", 0xFFE6F4EA, SAFE_GREEN);
        safe.setOnClickListener(v -> markRoute(true));
        TextView unsafe = button("Mark unsafe", 0xFFFDE8E8, UNSAFE_RED);
        unsafe.setOnClickListener(v -> markRoute(false));
        TextView marks = button("My marks", 0xFFF1F1F1, 0xFF222222);
        marks.setOnClickListener(v -> showMarks());
        TextView offline = button("Save area", 0xFFF1F1F1, 0xFF222222);
        offline.setOnClickListener(v -> saveArea());
        for (TextView t : new TextView[]{safe, unsafe, marks, offline}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(6);
            t.setPadding(dp(4), dp(8), dp(4), dp(8));
            t.setTextSize(12);
            row2.addView(t, lp);
        }
        card.addView(row2);
        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        map.setOnMapTap((lat, lon) -> setDestination(lat, lon, "Dropped pin", null));
        map.setOnMarkerClick(m -> {
            if (m.tag instanceof Facility) {
                Facility f = (Facility) m.tag;
                setDestination(f.lat, f.lon, f.name == null || f.name.isEmpty() ? Facility.label(f.kind) : f.name, f);
            }
        });
        setContentView(root);
    }

    // ------------------------------------------------------------------ position

    private void startLive() {
        if (live == null) live = new LocationHelper(this);
        Location cached = live.lastKnown();
        if (cached != null && here == null) onFix(cached);
        if (!liveOn) {
            liveOn = live.start(8000, this::onFix);
        }
        if (!liveOn && !live.servicesOn()) txtInfo.setText(live.whyNoFix());
    }

    private void onFix(Location l) {
        here = l;
        map.setMe(l.getLatitude(), l.getLongitude(), l.getAccuracy());
        if (!centeredOnMe && Double.isNaN(destLat)) {
            centeredOnMe = true;
            map.setCenter(l.getLatitude(), l.getLongitude());
            map.setZoom(15);
            if (NetUtil.isOnline(this)) {
                map.prefetchAround(l.getLatitude(), l.getLongitude());
                final double wLat = l.getLatitude(), wLon = l.getLongitude();
                final android.content.Context app = getApplicationContext();
                AppExecutors.io(() -> RouteCache.warm(app, wLat, wLon, false));
            }
        }
        refreshInfo();
    }

    private void goToMe() {
        if (here == null) {
            if (live == null || !Perms.location(this)) {
                locPermission.launch(Perms.locationPerms());
            } else {
                Ui.toast(this, live.whyNoFix());
            }
            return;
        }
        map.setCenter(here.getLatitude(), here.getLongitude());
        map.setZoom(Math.max(map.zoom(), 15));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Perms.location(this) && !liveOn && live != null) startLive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (live != null) live.stop();
        liveOn = false;
    }

    // ------------------------------------------------------------------ categories

    private void chooseKind(String k) {
        kind = k.equals(kind) ? "" : k;
        for (TextView t : chipViews) {
            boolean on = t.getTag().equals(kind);
            t.setBackground(pill(on ? 0xFF1E5BD8 : 0xFFFFFFFF, 0x33000000));
            t.setTextColor(on ? Color.WHITE : 0xFF222222);
        }
        if (kind.isEmpty()) {
            map.setMarkers(null);
            results.clear();
            resultAdapter.notifyDataSetChanged();
            resultList.setVisibility(View.GONE);
            return;
        }
        resultList.setVisibility(View.VISIBLE);
        showSaved();
        final String wanted = kind;
        final double lat = here != null ? here.getLatitude() : map.centerLat();
        final double lon = here != null ? here.getLongitude() : map.centerLon();
        if (!NetUtil.isOnline(this)) {
            txtInfo.setText("Offline: showing the " + Facility.label(wanted).toLowerCase(Locale.US) + " places saved on this phone.");
            return;
        }
        txtInfo.setText("Looking for " + Facility.label(wanted).toLowerCase(Locale.US) + " nearby...");
        AppExecutors.io(() -> {
            String group = RouteRules.groupOf(wanted);
            String result;
            try {
                List<Facility> list = repo.downloadGroupForMap(group, lat, lon);
                repo.save(list);
                result = "Found " + list.size() + " nearby (OpenStreetMap).";
            } catch (IOException | JSONException e) {
                result = "The free map data server is slow right now; showing saved places. Try again in a minute.";
            }
            final String msg = result;
            AppExecutors.main(() -> {
                if (isFinishing() || !wanted.equals(kind)) return;
                showSaved();
                txtInfo.setText(msg);
            });
        });
    }

    /** Shows the saved places of the chosen kind, nearest first (at most 80 pins). */
    private void showSaved() {
        if (kind.isEmpty()) return;
        final double lat = here != null ? here.getLatitude() : map.centerLat();
        final double lon = here != null ? here.getLongitude() : map.centerLon();
        final String wanted = kind;
        AppExecutors.io(() -> {
            List<Facility> all = repo.store().facilities();
            List<Facility> pick = new ArrayList<>();
            for (Facility f : all) {
                if (wanted.equals(f.kind) && f.hasLocation()) {
                    f.distanceKm = Geo.distanceKm(lat, lon, f.lat, f.lon);
                    pick.add(f);
                }
            }
            java.util.Collections.sort(pick, (a, c) -> Double.compare(a.distanceKm, c.distanceKm));
            final List<TileMapView.Marker> pins = new ArrayList<>();
            for (int i = 0; i < pick.size() && i < 80; i++) {
                Facility f = pick.get(i);
                TileMapView.Marker m = new TileMapView.Marker();
                m.lat = f.lat;
                m.lon = f.lon;
                m.label = Facility.pin(f.kind);
                m.color = 0xFFD32F2F;          // red pins, like Google Maps
                m.tag = f;
                pins.add(m);
            }
            final List<Facility> top = new ArrayList<>(pick.subList(0, Math.min(pick.size(), 80)));
            AppExecutors.main(() -> {
                if (isFinishing() || !wanted.equals(kind)) return;
                map.setMarkers(pins);
                results.clear();
                results.addAll(top);
                resultAdapter.notifyDataSetChanged();
            });
        });
    }

    // ------------------------------------------------------------------ destination and search

    private void setDestination(double lat, double lon, String name, Facility f) {
        destLat = lat;
        destLon = lon;
        destName = name;
        destFacility = f;
        route = null;
        offerSafeUnsafe = false;
        drawLines();
        txtTitle.setText(name);
        refreshInfo();
    }

    private void refreshInfo() {
        if (Double.isNaN(destLat)) return;
        StringBuilder sb = new StringBuilder();
        if (destFacility != null) sb.append(Facility.label(destFacility.kind));
        if (here != null) {
            double km = Geo.distanceKm(here.getLatitude(), here.getLongitude(), destLat, destLon);
            if (sb.length() > 0) sb.append(" - ");
            sb.append(RouteRules.formatDistance(km * 1000)).append(" straight line, ")
                    .append(RouteRules.direction(here.getLatitude(), here.getLongitude(), destLat, destLon)).append(" of you");
        } else {
            if (sb.length() > 0) sb.append(" - ");
            sb.append("Waiting for your position");
        }
        if (destFacility != null && destFacility.hasPhone()) sb.append("\nPhone: ").append(destFacility.phone);
        if (route != null) {
            sb.append("\nRoute: ").append(RouteRules.formatDistance(route.distanceM)).append(", ")
                    .append(RouteRules.formatDuration(route.durationS));
            if (route.straightLine) sb.append(" (offline road estimate)");
        }
        txtInfo.setText(sb.toString());
    }

    private void search() {
        final String q = Ui.clean(edtSearch.getText());
        Ui.hideKeyboard(this);
        if (q.length() < 2) {
            Ui.toast(this, "Type at least 2 letters");
            return;
        }
        final double lat = here != null ? here.getLatitude() : map.centerLat();
        final double lon = here != null ? here.getLongitude() : map.centerLon();
        final boolean online = NetUtil.isOnline(this);
        txtInfo.setText("Searching...");
        AppExecutors.io(() -> {
            final List<RouteRules.Place> found = new ArrayList<>();
            final List<Facility> saved = new ArrayList<>();
            String q2 = q.toLowerCase(Locale.US);
            for (Facility f : repo.store().facilities()) {
                if (f.hasLocation() && f.name != null && f.name.toLowerCase(Locale.US).contains(q2)) {
                    f.distanceKm = Geo.distanceKm(lat, lon, f.lat, f.lon);
                    saved.add(f);
                }
            }
            java.util.Collections.sort(saved, (a, c) -> Double.compare(a.distanceKm, c.distanceKm));
            for (int i = 0; i < saved.size() && i < 4; i++) {
                RouteRules.Place p = new RouteRules.Place();
                p.name = saved.get(i).name + " (saved)";
                p.lat = saved.get(i).lat;
                p.lon = saved.get(i).lon;
                found.add(p);
            }
            String err = "";
            if (online) {
                try {
                    String url = "https://nominatim.openstreetmap.org/search?format=json&limit=6&q=" + URLEncoder.encode(q, "UTF-8");
                    found.addAll(RouteRules.parsePlaces(Http.get(url, "application/json", 512 * 1024, 15000)));
                } catch (IOException | JSONException e) {
                    err = "The address search server did not answer.";
                }
            } else {
                err = "Offline: only saved places can be searched.";
            }
            final String note = err;
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (found.isEmpty()) {
                    txtInfo.setText(note.isEmpty() ? "Nothing found for \"" + q + "\"." : note);
                    return;
                }
                txtInfo.setText(note);
                chooseResult(found);
            });
        });
    }

    private void chooseResult(final List<RouteRules.Place> found) {
        String[] names = new String[found.size()];
        for (int i = 0; i < names.length; i++) names[i] = found.get(i).name;
        new AlertDialog.Builder(this).setTitle("Choose destination").setItems(names, (d, which) -> {
            RouteRules.Place p = found.get(which);
            String shortName = p.name.split(",")[0];
            setDestination(p.lat, p.lon, shortName, null);
            map.setCenter(p.lat, p.lon);
            map.setZoom(15);
        }).setNegativeButton("Cancel", null).show();
    }

    private void callDestination() {
        if (destFacility == null || !destFacility.hasPhone()) {
            Ui.toast(this, "No phone number is saved for this place.");
            return;
        }
        final String number = destFacility.phone;
        new AlertDialog.Builder(this).setTitle(destFacility.name).setMessage("Call " + number + "?")
                .setPositiveButton("Call", (x, w) -> CallHelper.dial(this, number))
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ directions

    private void cycleMode() {
        if (RouteRules.MODE_CAR.equals(mode)) {
            mode = RouteRules.MODE_FOOT;
            btnMode.setText("On foot");
        } else if (RouteRules.MODE_FOOT.equals(mode)) {
            mode = RouteRules.MODE_BIKE;
            btnMode.setText("By bike");
        } else {
            mode = RouteRules.MODE_CAR;
            btnMode.setText("By car");
        }
        if (route != null) getDirections();
    }

    private void getDirections() {
        if (Double.isNaN(destLat)) {
            Ui.toast(this, "Choose a destination first");
            return;
        }
        if (here == null) {
            Ui.toast(this, "Your position is not known yet. Wait for the blue dot, then try again.");
            if (live != null) txtInfo.setText(live.whyNoFix());
            return;
        }
        final double fLat = here.getLatitude(), fLon = here.getLongitude();
        final double tLat = destLat, tLon = destLon;
        final String m = mode;
        final boolean online = NetUtil.isOnline(this);
        txtInfo.setText(online ? "Finding the road route..." : "Offline: routing via Dijkstra road network...");
        AppExecutors.io(() -> {
            RouteRules.Route r = null;
            if (online) {
                try {
                    r = RouteRules.parseRoute(Http.get(RouteRules.routeUrl(m, fLat, fLon, tLat, tLon), "application/json", 2 * 1024 * 1024, 15000));
                } catch (IOException | JSONException e) {
                    r = null;
                }
                if (r == null && RouteRules.MODE_CAR.equals(m)) {
                    try {
                        r = RouteRules.parseRoute(Http.get(RouteRules.routeUrlFallback(fLat, fLon, tLat, tLon), "application/json", 2 * 1024 * 1024, 15000));
                    } catch (IOException | JSONException e) {
                        r = null;
                    }
                }
            }
            if (r != null && !r.straightLine) {
                MapStore.Saved sv = new MapStore.Saved();
                sv.fromLat = fLat; sv.fromLon = fLon; sv.toLat = tLat; sv.toLon = tLon; sv.mode = m;
                sv.dist = r.distanceM; sv.dur = r.durationS;
                sv.points = RouteRules.encodePoints(RouteRules.thin(r.points, 400));
                try { store.saveRoute(sv); } catch (RuntimeException ignored) { }
            }
            if (r == null) {
                // no internet or no answer: use a REAL road route saved earlier (within 25 km of where the trip started)
                r = RouteRules.fromSaved(store.routesTo(tLat, tLon), m, fLat, fLon, 25);
                if (r == null) r = RouteRules.fromSaved(store.routesTo(tLat, tLon), RouteRules.MODE_CAR, fLat, fLon, 25);
            }
            if (r == null) {
                List<List<double[]>> savedRoads = new ArrayList<>();
                for (MapStore.Mark mk : store.marks()) {
                    savedRoads.add(RouteRules.decodePoints(mk.points));
                }
                List<double[]> last = RouteRules.decodePoints(store.meta("last_route"));
                if (!last.isEmpty()) savedRoads.add(last);
                r = RouteRules.dijkstra(fLat, fLon, tLat, tLon, savedRoads);
            }
            final RouteRules.Route done = r;
            final boolean unsafeNear = overlapsUnsafe(done);
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                route = done;
                offerSafeUnsafe = true;
                store.setMeta("last_route", RouteRules.encodePoints(RouteRules.thin(done.points, 300)));
                store.setMeta("last_name", destName);
                drawLines();
                map.fitTo(done.points);
                refreshInfo();
                if (unsafeNear) {
                    txtInfo.append("\nWarning: this route passes close to a path you marked UNSAFE.");
                }
                if (done.fromCache) {
                    txtInfo.append("\nOffline: showing the road route saved earlier on this phone.");
                }
                if (done.straightLine && online) {
                    txtInfo.append("\nThe routing server did not answer, so a straight line is shown.");
                }
            });
        });
    }

    private boolean overlapsUnsafe(RouteRules.Route r) {
        if (r.straightLine) return false;
        List<double[]> mine = RouteRules.thin(r.points, 200);
        for (MapStore.Mark mk : store.marks()) {
            if (mk.safe) continue;
            if (RouteRules.touches(mine, RouteRules.decodePoints(mk.points), 40)) return true;
        }
        return false;
    }

    private void restoreLastRoute() {
        List<double[]> pts = RouteRules.decodePoints(store.meta("last_route"));
        if (pts.size() < 2) return;
        route = new RouteRules.Route();
        route.points.addAll(pts);
        double[] a = pts.get(0), z = pts.get(pts.size() - 1);
        double d = 0;
        for (int i = 1; i < pts.size(); i++) d += Geo.distanceKm(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1]) * 1000;
        route.distanceM = d;
        route.durationS = d / 8.3;
        destLat = z[0];
        destLon = z[1];
        destName = store.meta("last_name");
        txtTitle.setText(destName.isEmpty() ? "Last route" : destName);
        offerSafeUnsafe = true;
        map.setCenter(a[0], a[1]);
        txtInfo.setText("Last route (saved on this phone): about " + RouteRules.formatDistance(d) + " along the path.");
    }

    // ------------------------------------------------------------------ safe / unsafe marks

    private void drawLines() {
        List<TileMapView.Line> lines = new ArrayList<>();
        for (MapStore.Mark mk : store.marks()) {
            TileMapView.Line l = new TileMapView.Line();
            l.points.addAll(RouteRules.decodePoints(mk.points));
            l.color = mk.safe ? SAFE_GREEN : UNSAFE_RED;
            l.widthDp = 7;
            l.dashed = !mk.safe;
            if (l.points.size() >= 2) lines.add(l);
        }
        if (route != null) {
            TileMapView.Line l = new TileMapView.Line();
            l.points.addAll(route.points);
            l.color = ROUTE_BLUE;
            l.widthDp = 4;
            l.dashed = route.straightLine;
            lines.add(l);
        }
        map.setLines(lines);
    }

    private void markRoute(boolean safe) {
        if (route == null || !offerSafeUnsafe || route.straightLine) {
            Ui.toast(this, "First get road directions, then mark that path safe or unsafe.");
            return;
        }
        String label = (destName.isEmpty() ? "Route" : "To " + destName) + " (" + RouteRules.formatDistance(route.distanceM) + ")";
        store.addMark(label, safe, RouteRules.encodePoints(RouteRules.thin(route.points, 200)));
        drawLines();
        Ui.toast(this, safe ? "Marked safe (green). Saved on this phone." : "Marked unsafe (red dashed). Routes near it will warn you.");
    }

    private void showMarks() {
        final List<MapStore.Mark> marks = store.marks();
        if (marks.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("My marked paths")
                    .setMessage("Nothing yet. Get directions, then tap Mark safe or Mark unsafe. Your marks are private and stay on this phone.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[marks.size()];
        for (int i = 0; i < items.length; i++) items[i] = (marks.get(i).safe ? "SAFE: " : "UNSAFE: ") + marks.get(i).label;
        new AlertDialog.Builder(this).setTitle("My marked paths (tap to delete)").setItems(items, (d, which) -> {
            final MapStore.Mark mk = marks.get(which);
            new AlertDialog.Builder(this).setTitle("Delete this mark?").setMessage(mk.label)
                    .setPositiveButton("Delete", (d2, w2) -> {
                        store.deleteMark(mk.id);
                        drawLines();
                    }).setNegativeButton("Keep", null).show();
        }).setNegativeButton("Close", null).show();
    }

    // ------------------------------------------------------------------ offline

    private void saveArea() {
        if (!NetUtil.isOnline(this)) {
            Ui.toast(this, "Connect to the internet to save this area for offline use.");
            return;
        }
        map.prefetchArea(map.centerLat(), map.centerLon(), 3, 12, 16);
        Ui.toast(this, "Saving the map around the centre of the screen for offline use...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (store != null) store.close();
    }
}
