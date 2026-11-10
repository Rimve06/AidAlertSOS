package com.aidalert.sos.ui;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** "How this app works": what each part does, what needs internet, and the limits. */
public class GuideActivity extends BaseActivity {
    private static final String[][] SECTIONS = {
            {"Sending an SOS",
                    "Hold the red SOS button on Home. Your location and a message go to the control center, to your emergency "
                            + "contacts by SMS, and to nearby helpers. Shortcuts: press the power button 5 times, hold Volume Up 2 seconds "
                            + "(SOS with emergency type) or Volume Down 2 seconds (silent mode). These shortcuts need the accessibility "
                            + "service turned on in Settings > Permissions. Add emergency contacts first."},
            {"Without internet",
                    "SOS falls back to SMS and to nearby phones that relay it. Hotlines, saved hospitals, police and fire stations, and "
                            + "all first-aid guides work offline. News, alerts, the AI assistant and blood search need internet, but show "
                            + "the last saved copy when you are offline."},
            {"Emergency directory and Map",
                    "The Emergency directory is a plain list of hospitals, fire stations, police stations, blood banks and safe havens, "
                            + "nearest first, with phone numbers where known. It downloads places around you from OpenStreetMap and keeps them "
                            + "for offline use. Phone numbers from OpenStreetMap are marked unverified. Always-working numbers: 999, fire service 102. "
                            + "The Map screen shows a real street map: pick a category, search a destination, get directions with distance and time, "
                            + "and mark a path safe or unsafe. Viewed map areas are saved for offline use; road directions need internet."},
            {"Public alerts",
                    "Four groups: disasters, law and order (Section 144, curfew, police advisories), safety news, and world alerts. "
                            + "News is never an official order. Pick your district to see local alerts. The Official sources button opens "
                            + "police, Home Affairs and district notice boards."},
            {"News, missing and wanted",
                    "News puts Bangladesh emergencies first. Missing persons and Wanted notices open official pages: tap the three-line "
                            + "icon to switch between sources."},
            {"Blood donors",
                    "Register once as a donor (verified phone, your consent). Search by blood group and area. Donors who agreed are shown "
                            + "with a call button. Partner websites are linked for wider search. A copy of donors you saw is kept for offline use."},
            {"AI assistant",
                    "Online it uses Groq with your own key (Key button). Offline it answers from the saved first-aid guides. Words about "
                            + "self-harm are never sent online: the private Support companion handles those."},
            {"Medicine reminders",
                    "Add a medicine and times by hand, or photograph a prescription (online, with your Groq key). The app only reads text: "
                            + "YOU must confirm every name, dose and time before any reminder is made. Check unclear names with a pharmacist."},
            {"Utilities",
                    "Flashlight, strobe, siren, spoken help message, fake call, audio and video recording, and accident detection."},
            {"Crime statistics",
                    "Charts from the Bangladesh Police monthly report. The control center loads the newest PDF around the 10th of each month."},
            {"Limits you should know",
                    "This app helps; it does not replace 999. Offline data can be out of date. Phone numbers from the map are not verified. "
                            + "The AI can be wrong: use it for basic guidance only."}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        float d = getResources().getDisplayMetrics().density;
        box.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (24 * d));
        for (String[] s : SECTIONS) {
            TextView h = new TextView(this);
            h.setText(s[0]);
            h.setTextSize(17);
            h.setTypeface(null, android.graphics.Typeface.BOLD);
            h.setTextColor(0xFF0B2A5B);
            h.setPadding(0, (int) (14 * d), 0, (int) (4 * d));
            TextView t = new TextView(this);
            t.setText(s[1]);
            t.setTextSize(15);
            t.setTextColor(0xFF1A1A1A);
            t.setLineSpacing(0, 1.15f);
            box.addView(h);
            box.addView(t);
        }
        sv.addView(box);
        setContentView(sv);
        setTitle("How this app works");
    }
}
