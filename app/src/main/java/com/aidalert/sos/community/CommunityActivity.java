package com.aidalert.sos.community;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.engine.SyncManager;
import com.aidalert.sos.blood.AccessInfo;
import com.aidalert.sos.net.FirebaseRest;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.RowAdapter;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;
import com.aidalert.sos.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Community board: NOT a chat room. People post a help request, a help offer or local information;
 * feedback goes privately to the control center. Posts are checked by PostRules, can be reported by
 * anyone, and the control center can hide posts, mute users, and every moderation step is audit logged.
 * Verified accounts only (enforced again by database.rules.json).
 */
public class CommunityActivity extends BaseActivity {
    private static final String V_POSTS = "community";
    private static final String V_FEEDBACK = "feedback";
    private static final String V_REPORTS = "reports";
    private static final String V_AUDIT = "audit";
    private static final String V_FACREPORTS = "facilityReports";
    private static final String V_SIGHTINGS = "sightings";
    private static final String COMMENTS = "communityComments";
    private long lastCommentAt;

    private ActivityListBinding b;
    private Prefs prefs;
    private final RowAdapter adapter = new RowAdapter();
    private String view = V_POSTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = Prefs.of(this);
        setTitle("Community help board");
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.txtInfo.setVisibility(View.VISIBLE);
        b.btnAdd.setVisibility(View.VISIBLE);
        if (prefs.isCenter()) {
            b.btnAdd.setText("Switch view (posts, feedback, reports, audit)");
            b.btnAdd.setOnClickListener(v -> pickView());
        } else {
            b.btnAdd.setText("New post or feedback");
            b.btnAdd.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("What do you want to do?")
                    .setItems(new CharSequence[]{"Post on the board (need, offer or info)",
                            "Send feedback to the control center"}, (d, i) -> compose(i == 1)).show());
        }
        adapter.setOnClick(r -> showDetail((JSONObject) r.tag));
        adapter.setOnLongClick(r -> actions((JSONObject) r.tag));
        load();
    }

    private final Runnable autoReload = new Runnable() {
        @Override
        public void run() {
            load();
            AppExecutors.mainDelayed(this, 10_000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        AppExecutors.cancelMain(autoReload);
        autoReload.run();                       // loads now, then again every 10 seconds while the board is open
    }

    @Override
    protected void onPause() {
        AppExecutors.cancelMain(autoReload);
        super.onPause();
    }

    /** The real reason a call was refused, in words, plus where to get a full diagnosis. */
    private String why(String what, FirebaseRest.Result r) {
        return AccessInfo.explain(what, r.code, r.body, true, AccessInfo.fromToken(com.aidalert.sos.auth.AuthManager.idToken(false)))
                + "\n\nSettings > Server check shows exactly what to fix.";
    }

    private void showFailure(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    // ------------------------------------------------------------ loading

    private void load() {
        if (!NetUtil.isOnline(this)) {
            b.txtInfo.setText("You are offline. The board needs internet.");
            return;
        }
        b.txtInfo.setText(infoText());
        final boolean center = prefs.isCenter();
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            FirebaseRest.Result res = V_POSTS.equals(view) ? rest.request("GET", V_POSTS, SyncManager.LAST_50, null) : rest.get(view);
            JSONObject all = res.obj();
            final String loadProblem = res.ok() ? "" : why("Loading the board", res);
            final List<RowAdapter.Row> rows = new ArrayList<>();
            if (all != null) {
                List<JSONObject> items = new ArrayList<>();
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = all.optJSONObject(k);
                    if (o == null) continue;
                    try {
                        o.put("_key", k);
                    } catch (JSONException ignored) {
                        continue;
                    }
                    if (V_POSTS.equals(view) && o.optBoolean("hidden", false) && !center) continue;
                    items.add(o);
                }
                Collections.sort(items, (x, y) -> Long.compare(y.optLong("ts"), x.optLong("ts")));
                for (JSONObject o : items) rows.add(toRow(o));
            }
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (!loadProblem.isEmpty()) {
                    b.txtInfo.setText(loadProblem);        // do not wipe the list on a failed refresh
                    b.txtEmpty.setVisibility(rows.isEmpty() && adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    b.txtEmpty.setText("Could not load the board. See the message above.");
                    return;
                }
                b.txtInfo.setText(infoText());
                adapter.submit(rows);
                b.txtEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                b.txtEmpty.setText(V_POSTS.equals(view)
                        ? "No posts yet. Post a help request, a help offer or useful local information."
                        : "Nothing here.");
            });
        });
    }

    private String infoText() {
        if (!V_POSTS.equals(view)) return "Control center view: " + view + ". Tap an item for details.";
        return prefs.isCenter()
                ? "Tap a post for its comments (long-press a comment to delete it). Long-press a post to hide it or mute its author. Every action is logged."
                : "For help requests, help offers and local information only. No chat, links or phone numbers. "
                + "Tap a post to read and write comments. Long-press a post to report it.";
    }

    private RowAdapter.Row toRow(JSONObject o) {
        String type = o.optString("type");
        String title;
        String sub;
        String badge;
        int color = 0xFF757575;
        switch (view) {
            case V_FEEDBACK:
                title = o.optString("text");
                sub = o.optString("name") + " · " + TimeUtil.ago(o.optLong("ts"));
                badge = "Feedback";
                break;
            case V_REPORTS:
                title = "Report: " + o.optString("reason");
                sub = "Post " + o.optString("postId") + " · " + TimeUtil.ago(o.optLong("ts"));
                badge = "Report";
                color = 0xFFEF6C00;
                break;
            case V_SIGHTINGS:
                title = o.optString("recordName") + " seen at " + o.optString("where");
                sub = (o.optString("when").isEmpty() ? "" : o.optString("when") + " · ") + o.optString("byName") + " · "
                        + TimeUtil.ago(o.optLong("ts")) + (o.optString("note").isEmpty() ? "" : "\n" + o.optString("note"));
                badge = "Sighting";
                color = 0xFFEF6C00;
                break;
            case V_FACREPORTS:
                title = o.optString("name") + ": " + o.optString("phone");
                sub = o.optString("kind") + " · " + o.optString("district") + " · " + TimeUtil.ago(o.optLong("ts"))
                        + "\nLong-press to approve and publish";
                badge = "Number";
                color = 0xFF1565C0;
                break;
            case V_AUDIT:
                title = o.optString("action") + " → " + o.optString("target");
                sub = TimeUtil.ago(o.optLong("ts"));
                badge = "Audit";
                break;
            default:
                boolean hidden = o.optBoolean("hidden", false);
                title = (hidden ? "[hidden] " : "") + o.optString("text");
                String area = o.optString("area");
                sub = o.optString("name") + (area.isEmpty() ? "" : " · " + area) + " · " + TimeUtil.ago(o.optLong("ts"));
                badge = PostRules.label(type);
                color = PostRules.NEED.equals(type) ? 0xFFC62828 : PostRules.OFFER.equals(type) ? 0xFF2E7D32 : 0xFF1565C0;
        }
        return new RowAdapter.Row(title, sub, badge, color, o);
    }

    private void pickView() {
        final String[] keys = {V_POSTS, V_FEEDBACK, V_REPORTS, V_SIGHTINGS, V_FACREPORTS, V_AUDIT};
        new AlertDialog.Builder(this).setTitle("View")
                .setItems(new CharSequence[]{"Community posts", "Feedback inbox", "Reports", "Missing-person sightings", "Facility number reports", "Audit log"}, (d, i) -> {
                    view = keys[i];
                    load();
                }).show();
    }

    // ------------------------------------------------------------ compose

    private void compose(boolean feedback) {
        int wait = PostRules.waitSeconds(prefs.lastPostAt(), System.currentTimeMillis());
        if (wait > 0) {
            Ui.toast(this, "Please wait " + wait + " s before posting again.");
            return;
        }
        final String[] types = feedback ? new String[]{PostRules.FEEDBACK}
                : new String[]{PostRules.NEED, PostRules.OFFER, PostRules.INFO};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final Spinner spin = new Spinner(this);
        List<String> labels = new ArrayList<>();
        for (String t : types) labels.add(PostRules.label(t));
        spin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        final EditText area = new EditText(this);
        area.setHint("Area (upazila / neighbourhood)");
        final EditText text = new EditText(this);
        text.setHint(feedback ? "Your feedback for the control center"
                : "What is needed / offered / happening? (15 to 280 characters)");
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setMinLines(3);
        if (!feedback) box.addView(spin);
        if (!feedback) box.addView(area);
        box.addView(text);
        new AlertDialog.Builder(this)
                .setTitle(feedback ? "Feedback to control center" : "New post")
                .setView(box)
                .setPositiveButton("Send", (d, w) -> {
                    String type = types[spin.getSelectedItemPosition() < 0 ? 0 : spin.getSelectedItemPosition()];
                    submit(type, Ui.clean(area.getText()), Ui.clean(text.getText()));
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void submit(String type, String area, String text) {
        String problem = PostRules.check(type, area, text);
        if (problem != null) {
            new AlertDialog.Builder(this).setTitle("Cannot post").setMessage(problem)
                    .setPositiveButton("OK", null).show();
            return;
        }
        final JSONObject o = new JSONObject();
        try {
            o.put("type", type);
            o.put("uid", prefs.uid());
            o.put("name", prefs.name());
            o.put("area", area);
            o.put("text", text.trim());
            o.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        final String node = PostRules.FEEDBACK.equals(type) ? V_FEEDBACK : V_POSTS;
        AppExecutors.io(() -> {
            FirebaseRest.PostResult pr = new FirebaseRest(this).postResult(node, o);
            final String key = pr.key();
            final String reason = key == null ? why("Posting", pr.result) : "";
            AppExecutors.main(() -> {
                if (key == null) {
                    showFailure("Could not post", reason);
                } else {
                    prefs.lastPostAt(System.currentTimeMillis());
                    Ui.toast(this, PostRules.FEEDBACK.equals(type) ? "Feedback sent." : "Posted.");
                    load();
                }
            });
        });
    }

    // ------------------------------------------------------------ details and actions

    private void showDetail(JSONObject o) {
        if (V_POSTS.equals(view) && !o.optString("_key").isEmpty()) {
            showWithComments(o);
            return;
        }
        new AlertDialog.Builder(this).setTitle(PostRules.label(o.optString("type", view)))
                .setMessage(o.optString("text") + "\n\n" + o.optString("name")
                        + (o.optString("area").isEmpty() ? "" : "\n" + o.optString("area")))
                .setPositiveButton("Close", null).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** The post, the comments under it (oldest first) and a box to add one. Comments are short, with no links or phone numbers. */
    private void showWithComments(final JSONObject post) {
        final String postKey = post.optString("_key");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        TextView head = new TextView(this);
        head.setText(post.optString("text") + "\n\n" + post.optString("name")
                + (post.optString("area").isEmpty() ? "" : " - " + post.optString("area")));
        head.setTextSize(15);
        head.setTextColor(0xFF111827);
        box.addView(head);
        TextView title = new TextView(this);
        title.setText("Comments");
        title.setTextSize(14);
        title.setPadding(0, dp(12), 0, dp(4));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF111827);
        box.addView(title);
        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(listBox);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        final EditText input = new EditText(this);
        input.setHint("Write a comment (3 to 200 characters)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(3);
        box.addView(input);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle(PostRules.label(post.optString("type")))
                .setView(box).setPositiveButton("Comment", null).setNegativeButton("Close", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = Ui.clean(input.getText());
            String problem = CommentRules.check(text);
            int wait = CommentRules.waitSeconds(lastCommentAt, System.currentTimeMillis());
            if (problem == null && wait > 0) problem = "Please wait " + wait + " s before commenting again.";
            if (problem != null) {
                Ui.toast(this, problem);
                return;
            }
            sendComment(postKey, text, input, listBox);
        });
        loadComments(postKey, listBox);
    }

    private void loadComments(final String postKey, final LinearLayout listBox) {
        TextView wait = new TextView(this);
        wait.setText(NetUtil.isOnline(this) ? "Loading comments..." : "Offline: comments need internet.");
        wait.setTextColor(0xFF111827);
        listBox.removeAllViews();
        listBox.addView(wait);
        if (!NetUtil.isOnline(this)) return;
        final boolean center = prefs.isCenter();
        AppExecutors.io(() -> {
            FirebaseRest.Result cres = new FirebaseRest(this).get(COMMENTS + "/" + postKey);
            final String cproblem = cres.ok() ? "" : why("Loading comments", cres);
            JSONObject all = cres.obj();
            final List<JSONObject> items = new ArrayList<>();
            if (all != null) {
                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = all.optJSONObject(k);
                    if (o == null) continue;
                    try {
                        o.put("_key", k);
                    } catch (JSONException ignored) {
                        continue;
                    }
                    items.add(o);
                }
            }
            final List<JSONObject> sorted = CommentRules.oldestFirst(items);
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                listBox.removeAllViews();
                if (!cproblem.isEmpty()) {
                    TextView err = new TextView(this);
                    err.setText(cproblem);
                    err.setTextColor(0xFF111827);
                    listBox.addView(err);
                    return;
                }
                if (sorted.isEmpty()) {
                    TextView none = new TextView(this);
                    none.setText("No comments yet. Be the first to reply.");
                    none.setTextColor(0xFF111827);
                    listBox.addView(none);
                    return;
                }
                for (final JSONObject c : sorted) {
                    TextView t = new TextView(this);
                    t.setText(c.optString("name", "Someone") + " - " + TimeUtil.ago(c.optLong("ts")) + "\n" + c.optString("text"));
                    t.setPadding(0, dp(6), 0, dp(6));
                    t.setTextColor(0xFF111827);
                    if (center) {
                        t.setOnLongClickListener(v -> {
                            deleteComment(postKey, c, listBox);
                            return true;
                        });
                    }
                    listBox.addView(t);
                }
            });
        });
    }

    private void sendComment(final String postKey, String text, final EditText input, final LinearLayout listBox) {
        final JSONObject o = new JSONObject();
        try {
            o.put("uid", prefs.uid());
            o.put("name", prefs.name());
            o.put("text", text.trim());
            o.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        AppExecutors.io(() -> {
            FirebaseRest.PostResult pr = new FirebaseRest(this).postResult(COMMENTS + "/" + postKey, o);
            final String key = pr.key();
            final String reason = key == null ? why("Commenting", pr.result) : "";
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (key == null) {
                    showFailure("Could not send the comment", reason);
                } else {
                    lastCommentAt = System.currentTimeMillis();
                    input.setText("");
                    loadComments(postKey, listBox);
                }
            });
        });
    }

    /** Control center only: removes a comment and logs it. */
    private void deleteComment(final String postKey, final JSONObject c, final LinearLayout listBox) {
        new AlertDialog.Builder(this).setTitle("Delete this comment?").setMessage(c.optString("text"))
                .setPositiveButton("Delete", (d, w) -> {
                    final JSONObject audit = new JSONObject();
                    try {
                        audit.put("by", prefs.uid());
                        audit.put("action", "DELETE_COMMENT");
                        audit.put("target", postKey + "/" + c.optString("_key"));
                        audit.put("ts", System.currentTimeMillis());
                    } catch (JSONException e) {
                        return;
                    }
                    AppExecutors.io(() -> {
                        FirebaseRest rest = new FirebaseRest(this);
                        boolean ok = rest.delete(COMMENTS + "/" + postKey + "/" + c.optString("_key"));
                        if (ok) rest.post(V_AUDIT, audit);
                        AppExecutors.main(() -> {
                            Ui.toast(this, ok ? "Deleted and logged." : "Failed.");
                            loadComments(postKey, listBox);
                        });
                    });
                }).setNegativeButton("Keep", null).show();
    }

    private void actions(JSONObject o) {
        if (V_FACREPORTS.equals(view) && prefs.isCenter()) {
            approveNumber(o);
            return;
        }
        if (!V_POSTS.equals(view)) return;
        final String key = o.optString("_key");
        final String author = o.optString("uid");
        if (prefs.isCenter()) {
            final boolean hidden = o.optBoolean("hidden", false);
            new AlertDialog.Builder(this).setTitle("Moderate")
                    .setItems(new CharSequence[]{hidden ? "Unhide post" : "Hide post", "Mute author"}, (d, i) -> {
                        if (i == 0) moderate(hidden ? "UNHIDE_POST" : "HIDE_POST", key, "community/" + key + "/hidden", !hidden);
                        else moderate("MUTE_USER", author, "muted/" + author, true);
                    }).show();
        } else if (!author.equals(prefs.uid())) {
            final String[] reasons = {"Not about help or information", "Spam or scam", "Abuse or harassment", "False emergency"};
            new AlertDialog.Builder(this).setTitle("Report this post")
                    .setItems(reasons, (d, i) -> report(key, reasons[i])).show();
        } else {
            Ui.toast(this, "This is your own post.");
        }
    }

    /** Center only: publishes the reported number to facilities/{id}, marked as checked by the center. */
    private void approveNumber(final JSONObject r) {
        new AlertDialog.Builder(this).setTitle("Publish this number?")
                .setMessage(r.optString("name") + "\n" + r.optString("phone")
                        + "\n\nOnly approve after you checked it (call it or confirm with an official source).")
                .setPositiveButton("Publish as verified", (d, w) -> {
                    final JSONObject f = new JSONObject();
                    final JSONObject audit = new JSONObject();
                    try {
                        f.put("id", r.optString("facilityId"));
                        f.put("kind", r.optString("kind"));
                        f.put("name", r.optString("name"));
                        f.put("lat", r.optDouble("lat", Double.NaN));
                        f.put("lon", r.optDouble("lon", Double.NaN));
                        f.put("district", r.optString("district"));
                        f.put("phone", r.optString("phone"));
                        f.put("phoneVerified", true);
                        f.put("source", "Checked by control center");
                        f.put("updated", System.currentTimeMillis());
                        audit.put("by", prefs.uid());
                        audit.put("action", "PUBLISH_FACILITY_NUMBER");
                        audit.put("target", r.optString("facilityId"));
                        audit.put("ts", System.currentTimeMillis());
                    } catch (JSONException e) {
                        return;
                    }
                    AppExecutors.io(() -> {
                        FirebaseRest rest = new FirebaseRest(this);
                        String fid = r.optString("facilityId").replace("/", "_").replace(":", "_");
                        boolean ok = rest.put("facilities/" + fid, f);
                        if (ok) {
                            rest.post(V_AUDIT, audit);
                            rest.delete("facilityReports/" + r.optString("_key"));
                        }
                        final boolean done = ok;
                        AppExecutors.main(() -> {
                            Ui.toast(this, done ? "Published and logged." : "Failed.");
                            load();
                        });
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void report(String postId, String reason) {
        final JSONObject o = new JSONObject();
        try {
            o.put("postId", postId);
            o.put("by", prefs.uid());
            o.put("reason", reason);
            o.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        AppExecutors.io(() -> {
            FirebaseRest.PostResult pr = new FirebaseRest(this).postResult(V_REPORTS, o);
            final String key = pr.key();
            final String failReason = key == null ? why("Reporting", pr.result) : "";
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                if (key == null) {
                    showFailure("Could not send the report", failReason);
                } else {
                    Ui.toast(this, "Reported. The control center will review it.");
                }
            });
        });
    }

    /** Applies a moderation change and writes an audit entry (who, what, which target, when). */
    private void moderate(String action, String target, String path, boolean value) {
        final JSONObject audit = new JSONObject();
        try {
            audit.put("by", prefs.uid());
            audit.put("action", action);
            audit.put("target", target);
            audit.put("ts", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        AppExecutors.io(() -> {
            FirebaseRest rest = new FirebaseRest(this);
            boolean ok = putFlag(rest, path, value);
            if (ok) rest.post(V_AUDIT, audit);
            final boolean done = ok;
            AppExecutors.main(() -> {
                Ui.toast(this, done ? "Done and logged." : "Failed.");
                load();
            });
        });
    }

    private boolean putFlag(FirebaseRest rest, String path, boolean value) {
        FirebaseRest.Result r = rest.request("PUT", path, null, value ? "true" : "false");
        return r.ok();
    }
}
