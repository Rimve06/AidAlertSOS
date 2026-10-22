package com.aidalert.sos.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.databinding.ActivityListBinding;
import com.aidalert.sos.net.AlertFeedParser;
import com.aidalert.sos.net.Http;
import com.aidalert.sos.util.AppExecutors;
import com.aidalert.sos.util.NetUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

/** Public alerts: downloads the XML feed the control center configured and parses it (XML parsing). */
public class AlertsActivity extends BaseActivity {
    private ActivityListBinding b;
    private final RowAdapter adapter = new RowAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Public alerts");
        b.txtInfo.setVisibility(View.VISIBLE);
        b.txtInfo.setText("Weather, flood and safety bulletins from an XML (RSS / Atom / CAP) feed chosen by the control center.");
        b.btnAdd.setVisibility(View.VISIBLE);
        b.btnAdd.setText("Refresh");
        b.btnAdd.setOnClickListener(v -> load());
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        adapter.setOnClick(r -> {
            AlertFeedParser.Item it = (AlertFeedParser.Item) r.tag;
            new AlertDialog.Builder(this).setTitle(it.title)
                    .setMessage(it.description + (it.date.isEmpty() ? "" : "\n\n" + it.date))
                    .setPositiveButton("Close", null).show();
        });
        load();
    }

    private void setEmpty(String text) {
        b.txtEmpty.setVisibility(View.VISIBLE);
        b.txtEmpty.setText(text);
    }

    private void load() {
        final String url = Prefs.of(this).feedUrl();
        if (url.isEmpty()) {
            adapter.submit(new ArrayList<>());
            setEmpty("The control center has not set an alert feed yet.");
            return;
        }
        if (!NetUtil.isOnline(this)) {
            setEmpty("You are offline.");
            return;
        }
        setEmpty("Loading...");
        AppExecutors.io(() -> {
            final List<RowAdapter.Row> rows = new ArrayList<>();
            String error = null;
            try {
                for (AlertFeedParser.Item it : AlertFeedParser.parse(Http.get(url))) {
                    rows.add(new RowAdapter.Row(it.title, it.description, "", 0xFFEF6C00, it));
                }
            } catch (IOException e) {
                error = "Could not download the feed: " + e.getMessage();
            } catch (SAXException | ParserConfigurationException e) {
                error = "The feed is not valid XML.";
            }
            final String err = error;
            AppExecutors.main(() -> {
                if (isFinishing()) return;
                adapter.submit(rows);
                if (err != null) setEmpty(err);
                else if (rows.isEmpty()) setEmpty("No alerts in the feed.");
                else b.txtEmpty.setVisibility(View.GONE);
            });
        });
    }
}
