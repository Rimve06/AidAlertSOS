package com.aidalert.sos.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aidalert.sos.databinding.ActivitySourceBrowserBinding;

/**
 * An official website inside the app. The first source opens by itself. The three-line button at the left of the
 * header opens a side menu with the other sources; choosing one loads it here. Optional extra menu entries open
 * other screens (for example the control center's own verified list).
 */
public class SourceBrowserActivity extends BaseActivity {
    @Override
    protected boolean useShell() {
        return false;
    }

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_NAMES = "names";
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_KIND = "kind"; // MISSING or WANTED: adds the center-verified list entry

    private ActivitySourceBrowserBinding b;
    private String[] names;
    private String[] urls;
    private String kind;
    private int current;

    public static Intent intent(Context c, String title, String[] names, String[] urls, String kind) {
        return new Intent(c, SourceBrowserActivity.class).putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_NAMES, names).putExtra(EXTRA_URLS, urls).putExtra(EXTRA_KIND, kind);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        b = ActivitySourceBrowserBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        names = getIntent().getStringArrayExtra(EXTRA_NAMES);
        urls = getIntent().getStringArrayExtra(EXTRA_URLS);
        kind = getIntent().getStringExtra(EXTRA_KIND);
        if (names == null || urls == null || names.length == 0 || names.length != urls.length) {
            finish();
            return;
        }
        b.txtTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));

        WebSettings s = b.web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        b.web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sc = r.getUrl().getScheme();
                return sc == null || !(sc.equals("https") || sc.equals("http"));
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse e) {
                if (r.isForMainFrame() && e.getStatusCode() >= 400) {
                    showError("This page is not available right now (error " + e.getStatusCode()
                            + "). Open the menu at the top left and try another source.");
                }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) {
                    showError("Could not load this page. Check your internet connection, or choose another source from the menu.");
                }
            }
        });
        b.web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                b.progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
                b.progress.setProgress(p);
            }
        });
        b.btnMenu.setOnClickListener(v -> togglePanel());
        b.scrim.setOnClickListener(v -> closePanel());
        buildPanel();
        load(0);
    }

    private void showError(String text) {
        b.txtError.setText(text);
        b.txtError.setVisibility(View.VISIBLE);
    }

    private void load(int i) {
        current = i;
        b.txtError.setVisibility(View.GONE);
        b.txtSource.setText(names[i]);
        b.web.loadUrl(urls[i]);
        closePanel();
        buildPanel();
    }

    private void buildPanel() {
        LinearLayout list = b.panelList;
        list.removeAllViews();
        list.addView(entry("Sources", null, true, false));
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            list.addView(entry(names[i], v -> load(idx), false, i == current));
        }
        list.addView(entry("Open this page in my browser", v -> {
            closePanel();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls[current])));
            } catch (RuntimeException e) {
                Ui.toast(this, "No browser found.");
            }
        }, false, false));
        if (kind != null && !kind.isEmpty()) {
            list.addView(entry("Control center verified list", v -> {
                closePanel();
                startActivity(new Intent(this, RecordsActivity.class).putExtra(RecordsActivity.EXTRA_KIND, kind));
            }, false, false));
        }
    }

    private TextView entry(String text, View.OnClickListener click, boolean header, boolean selected) {
        TextView t = new TextView(this);
        t.setText(text);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        t.setPadding(pad, header ? pad : pad - 4, pad, header ? 8 : pad - 4);
        t.setTextSize(header ? 13 : 15);
        t.setTextColor(header ? 0xFF6B7A90 : selected ? 0xFFD32F2F : 0xFF0F2A4A);
        if (header || selected) t.setTypeface(null, android.graphics.Typeface.BOLD);
        if (click != null) {
            t.setOnClickListener(click);
            t.setBackgroundResource(android.R.drawable.list_selector_background);
        }
        return t;
    }

    private void togglePanel() {
        if (b.panel.getVisibility() == View.VISIBLE) closePanel();
        else {
            b.panel.setVisibility(View.VISIBLE);
            b.scrim.setVisibility(View.VISIBLE);
        }
    }

    private void closePanel() {
        b.panel.setVisibility(View.GONE);
        b.scrim.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (b != null && b.panel.getVisibility() == View.VISIBLE) closePanel();
        else if (b != null && b.web.canGoBack()) b.web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (b != null) b.web.destroy();
        super.onDestroy();
    }
}
