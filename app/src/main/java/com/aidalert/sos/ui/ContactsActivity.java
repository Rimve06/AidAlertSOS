package com.aidalert.sos.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import com.aidalert.sos.ui.BaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Db;
import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.util.Perms;

import java.util.ArrayList;
import java.util.List;

/** Preselected emergency contacts: each one gets an SMS with type + map link when an SOS starts. */
public class ContactsActivity extends BaseActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Emergency contacts");

        b.txtInfo.setVisibility(View.VISIBLE);
        b.txtInfo.setText("These people get an SMS with your emergency type and location link. The primary contact is listed first. The phone also calls: "
                + Prefs.of(this).callNumber() + " (change in Settings).");
        b.btnAdd.setVisibility(View.VISIBLE);
        b.btnAdd.setText("Add contact");
        b.btnAdd.setOnClickListener(v -> addDialog());
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> optionsDialog((Db.Contact) r.tag));
        if (!Perms.sms(this)) Ui.toast(this, "SMS permission is needed to text your contacts (Settings > Permissions).");
        load();
    }

    private void load() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Contact c : Db.get(this).contacts()) {
            rows.add(new RowAdapter.Row(c.name, c.phone, c.primary ? "PRIMARY" : "", 0xFF00695C, c));
        }
        adapter.submit(rows);
        b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.txtEmpty.setText("No contacts yet. Add at least one trusted person.");
    }

    private void addDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 16, 48, 0);
        final EditText name = new EditText(this);
        name.setHint("Name");
        name.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText phone = new EditText(this);
        phone.setHint("Phone number");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        box.addView(name);
        box.addView(phone);
        new AlertDialog.Builder(this)
                .setTitle("New emergency contact")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = Ui.clean(name.getText());
                    String p = Ui.clean(phone.getText());
                    if (n.isEmpty() || p.length() < 3) {
                        Ui.toast(this, "Enter a name and a phone number");
                        return;
                    }
                    Db.get(this).addContact(n, p);
                    load();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void optionsDialog(final Db.Contact c) {
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(new String[]{"Make primary", "Delete"}, (d, which) -> {
                    if (which == 0) Db.get(this).setPrimaryContact(c.id);
                    else Db.get(this).deleteContact(c.id);
                    load();
                })
                .show();
    }
}
