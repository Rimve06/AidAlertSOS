package com.aidalert.sos.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

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

    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                AppExecutors.io(() -> {
                    boolean ok = AvatarStore.save(this, uri);
                    AppExecutors.main(() -> {
                        if (isFinishing()) return;
                        if (ok) showAvatar();
                        else Ui.toast(this, "Could not use that picture. Try another one.");
                    });
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        p = Prefs.of(this);
        setTitle("My profile");

        b.editName.setText(p.name());
        b.txtPhone.setText("+" + p.phone());
        String nm = p.name() == null ? "" : p.name().trim();
        b.txtAvatar.setText(nm.isEmpty() ? "?" : nm.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        b.editArea.setText(p.area());
        b.editBio.setText(p.bio());
        b.editBlood.setText(p.blood());
        b.editMedical.setText(p.medicalNote());
        b.switchSearchable.setChecked(p.searchable());
        b.switchShowPhone.setChecked(p.showPhone());
        b.switchMedical.setChecked(p.includeMedical());

        b.btnSave.setOnClickListener(v -> save());
        b.imgAvatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        b.imgAvatar.setBackgroundResource(com.aidalert.sos.R.drawable.bg_avatar);
        b.imgAvatar.setClipToOutline(true);
        View.OnClickListener choose = v -> pickPhoto.launch("image/*");
        View.OnLongClickListener drop = v -> {
            AvatarStore.remove(this);
            showAvatar();
            Ui.toast(this, "Photo removed");
            return true;
        };
        b.btnPhoto.setOnClickListener(choose);
        b.btnPhoto.setOnLongClickListener(drop);
        b.txtAvatar.setOnClickListener(choose);
        b.imgAvatar.setOnClickListener(choose);
        b.imgAvatar.setOnLongClickListener(drop);
        showAvatar();
    }

    /** Shows the saved picture, or the first letter of the name when there is none. */
    private void showAvatar() {
        Bitmap bm = AvatarStore.load(this);
        b.imgAvatar.setVisibility(bm == null ? View.GONE : View.VISIBLE);
        if (bm != null) b.imgAvatar.setImageBitmap(bm);
    }

    private void save() {
        String newName = Ui.clean(b.editName.getText());
        if (newName.length() >= 2) p.name(newName);
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
            try {
                JSONObject userObj = rest.getObj("users/" + p.uid());
                if (userObj == null) userObj = new JSONObject();
                userObj.put("name", p.name());
                userObj.put("phone", p.phone());
                userObj.put("setupDone", true);
                rest.put("users/" + p.uid(), userObj);
            } catch (JSONException ignored) {}

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
