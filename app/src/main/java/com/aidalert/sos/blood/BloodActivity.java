package com.aidalert.sos.blood;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.alerts.Regions;
import com.aidalert.sos.auth.PhoneFormat;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityBloodBinding;
import com.aidalert.sos.directory.DirectoryActivity;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.net.FirestoreJson;
import com.aidalert.sos.net.FirestoreRest;
import com.aidalert.sos.news.FeedConfig;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.ui.SourceBrowserActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.CallHelper;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Blood donors, working on its own inside AidAlert:
 *  - A donor registers once (verified phone, consent to show it). The profile goes to the shared donor list.
 *  - Anyone verified can search by group and district, online or offline: the list is copied to SQLite on every sync.
 *  - Phone numbers are shown only for donors who agreed. Calling or messaging is done with the phone's own apps.
 *  - Partner websites are opened in the app browser; their data is NOT copied (personal data, their terms).
 *  - Blood centres near you (OpenStreetMap) come from the offline directory.
 */
public class BloodActivity extends BaseActivity {
    private static final String NOTICE = "Blood is given voluntarily and free of charge. Group matching is only a hint: the hospital "
            + "or licensed blood centre screens the donor and tests compatibility.";

    private ActivityBloodBinding b;
    private final RowAdapter adapter = new RowAdapter();
    private final List<JSONObject> requests = new ArrayList<>();
    private List<Donor> allDonors = new ArrayList<>();
    private BloodStore store;
    private boolean showDonors = true;
    private String filterGroup = "";
    private String filterDistrict = "";
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityBloodBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Blood donors");
        prefs = Prefs.of(this);
        store = new BloodStore(this);
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> {
            if (r.tag instanceof Donor) donorInfo((Donor) r.tag);
            else if (r.tag instanceof BloodSites.Site) openSite((BloodSites.Site) r.tag);
            else requestDetail((JSONObject) r.tag);
        });
        b.btnRequest.setOnClickListener(v -> requestDialog());
        b.btnBecome.setOnClickListener(v -> donorProfile());
        b.tabDonors.setOnClickListener(v -> setTab(true));
        b.tabRequests.setOnClickListener(v -> setTab(false));
        b.btnGroup.setOnClickListener(v -> pickGroup());
        b.btnDistrict.setOnClickListener(v -> pickDistrict());
        b.switchCompat.setOnCheckedChangeListener((btn, on) -> render());
        b.btnCentres.setOnClickListener(v -> startActivity(new Intent(this, DirectoryActivity.class)));
        b.btnSites.setOnClickListener(v -> openSites());
        setHeaderAction(com.aidalert.sos.R.drawable.ic_history, "Refresh", this::load);
        allDonors = store.all();          // saved copy first: works with no internet
        render();
        load();
    }

    private void setTab(boolean donors) {
        showDonors = donors;
        
        render();
    }

    private void pickGroup() {
        final String[] g = new String[BloodRules.GROUPS.length + 1];
        g[0] = "Any group";
        System.arraycopy(BloodRules.GROUPS, 0, g, 1, BloodRules.GROUPS.length);
        new AlertDialog.Builder(this).setTitle("Blood group needed").setItems(g, (d, i) -> {
            filterGroup = i == 0 ? "" : g[i];
            b.btnGroup.setText(i == 0 ? "Group: any" : "Group: " + g[i]);
            render();
        }).show();
    }

    private void pickDistrict() {
        final List<String> ds = new ArrayList<>(Regions.districtsOnly());
        ds.add(0, "Any district");
        final String[] arr = ds.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("District").setItems(arr, (d, i) -> {
            filterDistrict = i == 0 ? "" : arr[i];
            b.btnDistrict.setText(i == 0 ? "District: any" : arr[i]);
            render();
        }).show();
    }

    private void openSites() {
        final String[] items = {"Open a partner blood site", "Save a donor I found on a site", "Register once: copy my details for all sites"};
        new AlertDialog.Builder(this).setTitle("Other blood sites").setItems(items, (d, i) -> {
            if (i == 0) openPartnerBrowser();
            else if (i == 1) saveExternalDonor();
            else shareEverywhere();
        }).setNegativeButton("Close", null).show();
    }

    /** Opens one partner site inside the app, with the chosen district for sites that support it. */
    private void openSite(BloodSites.Site first) {
        int n = BloodSites.ALL.length;
        String[] names = new String[n], urls = new String[n];
        names[0] = first.name;
        urls[0] = BloodSites.searchUrl(first, filterDistrict);
        int k = 1;
        for (BloodSites.Site s : BloodSites.ALL) {
            if (s == first) continue;
            names[k] = s.name;
            urls[k++] = BloodSites.searchUrl(s, filterDistrict);
        }
        String title = "Blood" + (filterGroup.isEmpty() ? "" : " " + filterGroup) + (filterDistrict.isEmpty() ? "" : " \u00B7 " + filterDistrict);
        startActivity(SourceBrowserActivity.intent(this, title, names, urls, "BLOOD"));
    }

    private void openPartnerBrowser() {
        String[] n = BloodSites.names();
        String[] u = new String[n.length];
        for (int i = 0; i < n.length; i++) u[i] = BloodSites.ALL[i].url;
        startActivity(SourceBrowserActivity.intent(this, "Blood sites", n, u, "BLOOD"));
    }

    /** The person copies a donor from a partner site; it is kept on the phone so it can be searched, called or texted offline. */
    private void saveExternalDonor() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final String[] group = {""};
        final String[] district = {""};
        final String[] source = {""};
        final EditText name = field("Donor name", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText phone = field("Phone shown on the site", InputType.TYPE_CLASS_PHONE);
        final EditText area = field("Area (optional)", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final android.widget.Button pick = new android.widget.Button(this);
        pick.setText("Blood group: choose");
        pick.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Blood group").setItems(BloodRules.GROUPS, (d, i) -> {
            group[0] = BloodRules.GROUPS[i];
            pick.setText("Blood group: " + group[0]);
        }).show());
        final android.widget.Button pickD = new android.widget.Button(this);
        pickD.setText(district[0].isEmpty() ? "District: choose" : "District: " + district[0]);
        final String[] ds = Regions.districtsOnly().toArray(new String[0]);
        pickD.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("District").setItems(ds, (d, i) -> {
            district[0] = ds[i];
            pickD.setText("District: " + ds[i]);
        }).show());
        final android.widget.Button pickS = new android.widget.Button(this);
        pickS.setText("Found on: choose");
        pickS.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Site").setItems(BloodSites.names(), (d, i) -> {
            source[0] = BloodSites.ALL[i].name;
            pickS.setText("Found on: " + source[0]);
        }).show());
        box.addView(name);
        box.addView(phone);
        box.addView(pick);
        box.addView(pickD);
        box.addView(area);
        box.addView(pickS);
        new AlertDialog.Builder(this).setTitle("Save a donor")
                .setMessage("Copy the details from the partner site. They stay on this phone only, so you can search and call them without internet.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String ph = PhoneFormat.toE164(Ui.clean(phone.getText()));
                    String err = BloodRules.checkExternal(Ui.clean(name.getText()), group[0], ph);
                    if (err != null) {
                        Ui.toast(this, err);
                        return;
                    }
                    Donor n = new Donor();
                    n.name = Ui.clean(name.getText());
                    n.group = group[0];
                    n.district = district[0];
                    n.area = Ui.clean(area.getText());
                    n.phone = ph;
                    n.source = source[0];
                    store.addExternal(n);
                    Ui.toast(this, "Saved on this phone.");
                    render();
                })
                .setNegativeButton("Cancel", null).show();
    }

    /**
     * One entry of the person's details, then each partner's own registration page. AidAlert cannot submit to other
     * sites for the person (they have no public sign-up interface), so the details are copied once and pasted.
     */
    private void shareEverywhere() {
        String phone = PhoneFormat.toE164(prefs.phone());
        final String text = BloodSites.registrationText(prefs.name(), "", phone == null ? "" : phone, "", "", -1);
        final String[] n = BloodSites.names();
        new AlertDialog.Builder(this).setTitle("Register once")
                .setMessage("Your details are copied once. Open each site, create the donor account there and paste them. "
                        + "AidAlert cannot sign you up on other sites by itself; each one needs you to confirm your own details.\n\n"
                        + text + "\n\n" + BloodSites.ONE_BLOOD_NOTE)
                .setItems(n, (d, i) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Donor details", text));
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BloodSites.ALL[i].url)));
                    } catch (RuntimeException e) {
                        Ui.toast(this, "No browser found.");
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    // ------------------------------------------------------------ loading and sync

    private final Runnable autoReload = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            load();
            AppExecutors.mainDelayed(this, 45_000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.cancelMain(autoReload);
        AppExecutors.mainDelayed(autoReload, 45_000);      // sync again by itself every 45 seconds while this screen is open
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(autoReload);
        super.onPause();
    }

    private void load() {
        if (!NetUtil.isOnline(this)) {
            render();
            return;
        }
        AppExecutors.io(() -> {
            // first send what is waiting on this phone (own donor profile), then download the list
            final String pushProblem = BloodSync.pushPending(this);
            FirebaseRest rest = new FirebaseRest(this);
            FirestoreRest fs = new FirestoreRest(this);
            FirebaseRest.Result dr = fs.list("donors");                 // donors live in Cloud Firestore
            FirebaseRest.Result rr = rest.get("bloodRequests");         // urgent requests stay in the Realtime Database
            final List<JSONObject> rq = readAll(rr.obj(), true);
            // An empty list comes back as 200 "null": that is a successful sync of zero donors, not a failure.
            final boolean ok = dr.ok();
            if (ok) {
                List<Donor> list = new ArrayList<>();
                JSONObject dn = dr.obj();
                if (dn != null) {
                    Iterator<String> it = dn.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject o = dn.optJSONObject(k);
                        if (o != null) list.add(Donor.fromJson(k, o));
                    }
                }
                store.replaceAll(list);
                // keep the phones of available donors who agreed to show them, so they can be called offline
                int fetched = 0;
                for (Donor d : store.all()) {
                    if (fetched >= 40) break;
                    if (d.showPhone && d.available && d.phone.isEmpty()) {
                        JSONObject c = fs.get("donorContacts", d.uid).obj();
                        if (c != null && !c.optString("phone").isEmpty()) store.setPhone(d.uid, c.optString("phone"));
                        fetched++;
                    }
                }
            }
            final String problem = ok ? "" : FirestoreJson.explain("Donor list sync", dr.code, dr.body) + " Showing the saved copy.";
            final String reqProblem = rr.ok() ? "" : AccessInfo.explain("Blood requests sync", rr.code, rr.body, rest.configured(), AccessInfo.fromToken(com.aidalert.sos.auth.AuthManager.idToken(false)));
            final List<Donor> fresh = store.all();
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                requests.clear();
                requests.addAll(rq);
                allDonors = fresh;
                render();
                if (!ok) Ui.toast(this, problem);
                if (!reqProblem.isEmpty()) Ui.toast(this, reqProblem);
                if (!pushProblem.isEmpty()) Ui.toast(this, pushProblem);
            });
        });
    }

    /** This phone's own donor profile (saved locally), or null when the person has not registered. */
    private Donor myLocalDonor() {
        String uid = prefs.uid();
        if (uid.isEmpty() || store.mineJson(uid).isEmpty()) return null;
        try {
            Donor d = Donor.fromJson(uid, new JSONObject(store.mineJson(uid)));
            d.phone = store.minePhone(uid);
            return d;
        } catch (JSONException e) {
            return null;
        }
    }

    private static List<JSONObject> readAll(JSONObject node, boolean requests) {
        List<JSONObject> out = new ArrayList<>();
        if (node == null) return out;
        Iterator<String> it = node.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject o = node.optJSONObject(k);
            if (o == null) continue;
            try {
                o.put("_id", k);
            } catch (JSONException ignored) {
                continue;
            }
            if (requests && !"OPEN".equals(o.optString("status", "OPEN"))) continue;
            out.add(o);
        }
        if (requests) {
            Collections.sort(out, (x, y) -> {
                int ux = "URGENT".equals(x.optString("urgency")) ? 1 : 0, uy = "URGENT".equals(y.optString("urgency")) ? 1 : 0;
                if (ux != uy) return Integer.compare(uy, ux);
                return Long.compare(y.optLong("ts"), x.optLong("ts"));
            });
        }
        return out;
    }

    private void render() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        b.searchBar.setVisibility(showDonors ? View.VISIBLE : View.GONE);
        if (showDonors) {
            long now = System.currentTimeMillis();
            List<Donor> pool = new ArrayList<>(allDonors);
            pool.addAll(store.external());
            Donor me = myLocalDonor();
            if (me != null) {
                boolean listed = false;
                for (Donor x : allDonors) if (me.uid.equals(x.uid)) listed = true;
                if (!listed) pool.add(me);
            }
            List<Donor> found = BloodRules.search(pool, filterGroup, b.switchCompat.isChecked(), filterDistrict, now);
            for (Donor d : found) {
                String where = d.district + (d.area.isEmpty() ? "" : ", " + d.area);
                String sub;
                if (!d.source.isEmpty()) {
                    sub = "Saved from " + d.source + "  \u00B7  " + where + "  \u00B7  phone saved";
                } else {
                    sub = (d.uid.equals(prefs.uid()) ? "YOU  \u00B7  " + (store.minePending() ? "saved on this phone, not uploaded yet  \u00B7  " : "") : "")
                            + (d.available ? "Available" : "Not available now") + "  \u00B7  " + where
                            + (BloodRules.recentlyDonated(d.lastDonation, now) ? "  \u00B7  donated recently" : "")
                            + (d.phone.isEmpty() ? (d.showPhone ? "  \u00B7  phone on request" : "  \u00B7  phone private") : "  \u00B7  phone saved");
                }
                rows.add(new RowAdapter.Row(d.name, sub, d.group, d.available ? 0xFFC62828 : 0xFF98A2B3, d));
            }
            // Donors on the partner websites: opened inside AidAlert so the person never has to leave the app.
            for (BloodSites.Site st : BloodSites.ALL) {
                String what = (filterGroup.isEmpty() ? "blood" : filterGroup) + " donors" + (filterDistrict.isEmpty() ? "" : " in " + filterDistrict);
                rows.add(new RowAdapter.Row("Find " + what + " on " + st.name, st.note + "  \u00B7  opens here in the app",
                        "Online", 0xFF1565C0, st));
            }
        } else {
            for (JSONObject o : requests) {
                boolean urgent = "URGENT".equals(o.optString("urgency"));
                rows.add(new RowAdapter.Row(o.optInt("units") + " bag(s) of " + o.optString("group") + " at " + o.optString("hospital"),
                        o.optString("district") + "  |  " + TimeUtil.ago(o.optLong("ts")),
                        urgent ? "URGENT" : "Open", urgent ? 0xFFC62828 : 0xFF1565C0, o));
            }
        }
        adapter.submit(rows);
    }

    // ------------------------------------------------------------ donors

    private void donorInfo(final Donor d) {
        long now = System.currentTimeMillis();
        String msg = d.group + "  \u00B7  " + (d.available ? "available" : "not available now") + "\n" + d.district
                + (d.area.isEmpty() ? "" : ", " + d.area)
                + (d.lastDonation > 0 ? "\nLast donation: " + TimeUtil.ago(d.lastDonation) : "")
                + (BloodRules.recentlyDonated(d.lastDonation, now) ? "\nDonated less than 4 months ago: may not be eligible yet." : "")
                + (d.source.isEmpty() ? "" : "\nSaved from " + d.source)
                + "\n\n" + (d.phone.isEmpty()
                ? (d.showPhone ? "Phone is shared by the donor. Tap Call to load it (needs internet)." : "This donor keeps the phone private. Post a request instead.")
                : "Phone: " + d.phone) + "\n\n" + NOTICE;
        AlertDialog.Builder bd = new AlertDialog.Builder(this).setTitle(d.name).setMessage(msg).setNegativeButton("Close", null);
        if (!d.source.isEmpty()) {
            bd.setNegativeButton("Remove", (x, w) -> {
                store.removeExternal(d.uid);
                render();
            });
        }
        if (d.showPhone) {
            bd.setPositiveButton("Call", (x, w) -> withPhone(d, p -> CallHelper.dial(this, p)));
            bd.setNeutralButton("Message", (x, w) -> withPhone(d, p -> {
                try {
                    startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p))
                            .putExtra("sms_body", BloodRules.smsText(filterGroup, "", d.district)));
                } catch (RuntimeException e) {
                    Ui.toast(this, "No messaging app found.");
                }
            }));
        } else {
            bd.setPositiveButton("Request blood", (x, w) -> requestDialog());
        }
        bd.show();
    }

    private interface PhoneAction {
        void run(String phone);
    }

    /** Uses the saved phone, or loads it once when online (and saves it for offline use). */
    private void withPhone(final Donor d, final PhoneAction action) {
        if (!d.phone.isEmpty()) {
            action.run(d.phone);
            return;
        }
        if (!NetUtil.isOnline(this)) {
            Ui.toast(this, "This donor's phone is not saved on your phone. Connect to the internet and try again.");
            return;
        }
        AppExecutors.io(() -> {
            JSONObject c = new FirestoreRest(this).get("donorContacts", d.uid).obj();
            final String p = c == null ? "" : c.optString("phone");
            if (!p.isEmpty()) store.setPhone(d.uid, p);
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (p.isEmpty()) {
                    Ui.toast(this, "The phone could not be loaded. The donor may have turned sharing off.");
                } else {
                    d.phone = p;
                    action.run(p);
                    render();
                }
            });
        });
    }

    private void donorProfile() {
        final String phone = PhoneFormat.toE164(prefs.phone());
        if (phone == null || phone.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Verify your phone first")
                    .setMessage("Donors must have a verified mobile number, so people can trust the list. Add and verify it in your profile.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final String[] group = {""};
        final String[] district = {""};
        JSONObject saved = null;
        try {
            if (!store.mineJson().isEmpty()) saved = new JSONObject(store.mineJson());
        } catch (JSONException ignored) {
            saved = null;
        }
        if (saved != null) {
            group[0] = saved.optString("group");
            district[0] = saved.optString("district");
        }
        final android.widget.Button pick = new android.widget.Button(this);
        pick.setText(group[0].isEmpty() ? "Blood group: choose" : "Blood group: " + group[0]);
        pick.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Blood group").setItems(BloodRules.GROUPS, (d, i) -> {
            group[0] = BloodRules.GROUPS[i];
            pick.setText("Blood group: " + group[0]);
        }).show());
        final android.widget.Button pickD = new android.widget.Button(this);
        pickD.setText("District: choose");
        final String[] ds = Regions.districtsOnly().toArray(new String[0]);
        pickD.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("District").setItems(ds, (d, i) -> {
            district[0] = ds[i];
            pickD.setText("District: " + ds[i]);
        }).show());
        final EditText area = field("Area or upazila (optional)", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (saved != null) area.setText(saved.optString("area"));
        final EditText days = field("Days since your last donation (blank if never)", InputType.TYPE_CLASS_NUMBER);
        final android.widget.CheckBox avail = new android.widget.CheckBox(this);
        avail.setText("I am available to donate now");
        avail.setChecked(true);
        final android.widget.CheckBox consent = new android.widget.CheckBox(this);
        consent.setText("Show my phone number to verified users who need blood");
        consent.setChecked(true);
        box.addView(pick);
        box.addView(pickD);
        box.addView(area);
        box.addView(days);
        box.addView(avail);
        box.addView(consent);
        new AlertDialog.Builder(this).setTitle("Donor profile")
                .setMessage("Voluntary and free. Your name, group and district are visible to verified users. Your phone ("
                        + phone + ") is shown only if you tick the box. You can change this or remove yourself at any time.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String err = BloodRules.checkDonor(group[0], district[0], phone);
                    if (err != null) {
                        Ui.toast(this, err);
                        return;
                    }
                    long last = 0;
                    String ds2 = days.getText().toString().trim();
                    if (!ds2.isEmpty()) {
                        try {
                            last = System.currentTimeMillis() - Long.parseLong(ds2) * 24L * 3600 * 1000;
                        } catch (NumberFormatException e) {
                            Ui.toast(this, "Days must be a number.");
                            return;
                        }
                    }
                    final JSONObject o = new JSONObject();
                    final JSONObject contact = new JSONObject();
                    try {
                        o.put("name", prefs.name());
                        o.put("group", group[0]);
                        o.put("district", district[0]);
                        o.put("area", Ui.clean(area.getText()));
                        o.put("available", avail.isChecked());
                        o.put("showPhone", consent.isChecked());
                        o.put("lastDonation", last);
                        o.put("ts", System.currentTimeMillis());
                        contact.put("phone", phone);
                    } catch (JSONException e) {
                        return;
                    }
                    final boolean share = consent.isChecked();
                    // 1. keep it on this phone first, so the profile is never lost and shows in the list at once
                    store.saveMine(o.toString(), phone, true);
                    allDonors = store.all();
                    render();
                    // 2. upload it; if the server refuses, say exactly why
                    AppExecutors.io(() -> {
                        FirestoreRest fs = new FirestoreRest(this);
                        FirebaseRest.Result pr = fs.put("donors", prefs.uid(), o);
                        boolean ok = pr.ok();
                        FirebaseRest.Result cr = null;
                        if (ok && share) cr = fs.put("donorContacts", prefs.uid(), contact);
                        if (ok && !share) fs.delete("donorContacts", prefs.uid());
                        final boolean res = ok && (cr == null || cr.ok());
                        if (res) store.markMineSynced();
                        final String why = res ? "" : FirestoreJson.explain("Saving your donor profile",
                                ok ? cr.code : pr.code, ok ? cr.body : pr.body);
                        AppExecutors.main(() -> {
                            if (isFinishing()) return;
                            if (res) {
                                Ui.toast(this, "Donor profile saved and shared. Thank you.");
                            } else {
                                new AlertDialog.Builder(this).setTitle("Saved on this phone")
                                        .setMessage("Your donor profile is saved on this phone and shows in your donor list. "
                                                + "It could not be uploaded for other people yet.\n\n" + why
                                                + "\n\nIt uploads by itself the next time the list syncs.")
                                        .setPositiveButton("OK", null).show();
                            }
                            render();
                        });
                    });
                })
                .setNeutralButton("Remove me", (d, w) -> AppExecutors.io(() -> {
                    store.clearMine();
                    FirestoreRest fs = new FirestoreRest(this);
                    boolean ok = fs.delete("donors", prefs.uid()).ok();
                    fs.delete("donorContacts", prefs.uid());
                    AppExecutors.main(() -> {
                        Ui.toast(this, ok ? "You were removed from the donor list." : "Could not remove. Check your internet.");
                        load();
                    });
                }))
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------ requests

    private void requestDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final String[] group = {""};
        final android.widget.Button pick = new android.widget.Button(this);
        pick.setText("Patient blood group: choose");
        pick.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Patient blood group").setItems(BloodRules.GROUPS, (d, i) -> {
            group[0] = BloodRules.GROUPS[i];
            pick.setText("Patient blood group: " + group[0]);
        }).show());
        final EditText units = field("Bags needed (1 to 10)", InputType.TYPE_CLASS_NUMBER);
        final EditText hospital = field("Hospital name", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText district = field("District", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText phone = field("Contact mobile number", InputType.TYPE_CLASS_PHONE);
        final android.widget.CheckBox urgent = new android.widget.CheckBox(this);
        urgent.setText("Urgent (needed within hours)");
        box.addView(pick);
        box.addView(units);
        box.addView(hospital);
        box.addView(district);
        box.addView(phone);
        box.addView(urgent);
        new AlertDialog.Builder(this).setTitle("Request blood")
                .setMessage("Your contact number will be shown to signed-in users who see this request. Do not offer or ask for payment for blood.")
                .setView(box)
                .setPositiveButton("Post", (d, w) -> {
                    int u = 0;
                    try {
                        u = Integer.parseInt(units.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                        // checked below
                    }
                    String e164 = PhoneFormat.toE164(Ui.clean(phone.getText()));
                    String err = BloodRules.checkRequest(group[0], u, Ui.clean(hospital.getText()), Ui.clean(district.getText()), e164);
                    if (err != null) {
                        Ui.toast(this, err);
                        return;
                    }
                    JSONObject o = new JSONObject();
                    try {
                        o.put("group", group[0]);
                        o.put("units", u);
                        o.put("hospital", Ui.clean(hospital.getText()));
                        o.put("district", Ui.clean(district.getText()));
                        o.put("contactName", prefs.name());
                        o.put("contactPhone", e164);
                        o.put("urgency", urgent.isChecked() ? "URGENT" : "NORMAL");
                        o.put("status", "OPEN");
                        o.put("by", prefs.uid());
                        o.put("ts", System.currentTimeMillis());
                    } catch (JSONException ex) {
                        return;
                    }
                    showDonors = false;
                    save("bloodRequests/" + prefs.uid() + "_" + System.currentTimeMillis(), o, "Request posted.");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void requestDetail(final JSONObject o) {
        final String id = o.optString("_id");
        final boolean mine = prefs.uid().equals(o.optString("by"));
        final String head = o.optInt("units") + " bag(s) of " + o.optString("group") + "\n" + o.optString("hospital") + ", "
                + o.optString("district") + "\nPosted " + TimeUtil.ago(o.optLong("ts"))
                + "\nContact: " + o.optString("contactName") + "  " + o.optString("contactPhone")
                + "\nUsually compatible donors: " + android.text.TextUtils.join(", ", BloodRules.donorGroupsFor(o.optString("group")))
                + "\n\n" + NOTICE;
        if (mine) {
            AppExecutors.io(() -> {
                JSONObject resp = new FirebaseRest(this).getObj("bloodResponses/" + id);
                final StringBuilder sb = new StringBuilder(head).append("\n\nAnswers:\n");
                int n = 0;
                if (resp != null) {
                    Iterator<String> it = resp.keys();
                    while (it.hasNext()) {
                        JSONObject r = resp.optJSONObject(it.next());
                        if (r == null) continue;
                        n++;
                        sb.append("- ").append(r.optString("name")).append("  ").append(r.optString("phone")).append('\n');
                    }
                }
                if (n == 0) sb.append("No answers yet.");
                AppExecutors.main(() -> new AlertDialog.Builder(this).setTitle("Your request").setMessage(sb.toString())
                        .setPositiveButton("Mark fulfilled", (d, w) -> close(o, "FULFILLED"))
                        .setNeutralButton("Cancel request", (d, w) -> close(o, "CLOSED"))
                        .setNegativeButton("Close", null).show());
            });
            return;
        }
        new AlertDialog.Builder(this).setTitle("Blood request").setMessage(head)
                .setPositiveButton("I can donate", (d, w) -> answer(o))
                .setNeutralButton("Call", (d, w) -> {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:" + o.optString("contactPhone"))));
                    } catch (RuntimeException e) {
                        Ui.toast(this, "No dialer found.");
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    private void answer(final JSONObject req) {
        new AlertDialog.Builder(this).setTitle("Share your contact?")
                .setMessage("Your name and phone number will be shown to the person who asked for blood, so they can arrange it with the "
                        + "hospital. The hospital still screens every donor. Never accept or pay money.")
                .setPositiveButton("Share and offer", (d, w) -> {
                    JSONObject r = new JSONObject();
                    try {
                        r.put("name", prefs.name());
                        r.put("phone", prefs.phone());
                        r.put("group", "");
                        r.put("response", "AVAILABLE");
                        r.put("ts", System.currentTimeMillis());
                    } catch (JSONException e) {
                        return;
                    }
                    save("bloodResponses/" + req.optString("_id") + "/" + prefs.uid(), r, "Your offer was sent.");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void close(final JSONObject o, final String status) {
        AppExecutors.io(() -> {
            boolean ok = new FirebaseRest(this).putString("bloodRequests/" + o.optString("_id") + "/status", status);
            AppExecutors.main(() -> {
                Ui.toast(this, ok ? "Request updated." : "Could not update. Check your internet.");
                load();
            });
        });
    }

    // ------------------------------------------------------------ helpers

    private void save(final String path, final JSONObject o, final String okText) {
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            FirebaseRest.Result r = rest.request("PUT", path, null, o.toString());
            final String why = r.ok() ? "" : AccessInfo.explain("Saving", r.code, r.body, rest.configured(),
                    AccessInfo.fromToken(com.aidalert.sos.auth.AuthManager.idToken(true)));
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (r.ok()) {
                    Ui.toast(this, okText);
                } else {
                    new AlertDialog.Builder(this).setTitle("Could not save").setMessage(why).setPositiveButton("OK", null).show();
                }
                load();
            });
        });
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        return e;
    }
}
