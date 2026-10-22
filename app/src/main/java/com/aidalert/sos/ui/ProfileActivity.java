package com.aidalert.sos.ui;

import android.os.Bundle;

import com.aidalert.sos.ui.BaseActivity;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityProfileBinding;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import org.json.JSONException;
import org.json.JSONObject;

/** Profile + privacy switches. The public directory entry holds only what the user agrees to show. */
public class ProfileActivity extends BaseActivity {
    private ActivityProfileBinding b;
    private Prefs p;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        p = Prefs.of(this);
        setTitle("My profile");

        b.txtName.setText(p.name());
        b.txtPhone.setText("+" + p.phone());
        b.editArea.setText(p.area());
        b.editBio.setText(p.bio());
        b.editBlood.setText(p.blood());
        b.editMedical.setText(p.medicalNote());
        b.switchSearchable.setChecked(p.searchable());
        b.switchShowPhone.setChecked(p.showPhone());
        b.switchMedical.setChecked(p.includeMedical());

        b.btnSave.setOnClickListener(v -> save());
    }

    private void save() {
        p.area(Ui.clean(b.editArea.getText()));
        p.bio(Ui.clean(b.editBio.getText()));
        p.blood(Ui.clean(b.editBlood.getText()));
        p.medicalNote(Ui.clean(b.editMedical.getText()));
        p.searchable(b.switchSearchable.isChecked());
        p.showPhone(b.switchShowPhone.isChecked());
        p.includeMedical(b.switchMedical.isChecked());

        if (!NetUtil.isOnline(this)) {
            Ui.toast(this, "Saved on this phone. Public profile updates when you are online.");
            return;
        }
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            boolean ok;
            if (p.searchable()) {
                JSONObject d = new JSONObject();
                try {
                    d.put("name", p.name());
                    d.put("area", p.area());
                    d.put("bio", p.bio());
                    d.put("phone", p.showPhone() ? "+" + p.phone() : "");
                } catch (JSONException e) {
                    return;
                }
                ok = rest.put("directory/" + p.uid(), d);
            } else {
                ok = rest.delete("directory/" + p.uid());
            }
            final boolean fin = ok;
            AppExecutors.main(() -> Ui.toast(this, fin ? "Profile saved" : "Saved here, server update failed"));
        });
    }
}
