package com.aidalert.sos.ui;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Shows an official web page inside the app (a WebView). Only http(s) pages open; local files and
 * other schemes are refused. JavaScript is on because government list pages need it to display.
 */
public class WebActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        setTitle(title == null || title.isEmpty() ? "Web page" : title);
        web = new WebView(this);
        setContentView(web);
        Uri u = url == null ? null : Uri.parse(url);
        if (u == null || u.getScheme() == null || !(u.getScheme().equals("https") || u.getScheme().equals("http"))) {
            finish();
            return;
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                String sc = r.getUrl().getScheme();
                return sc == null || !(sc.equals("https") || sc.equals("http"));
            }
        });
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    public static android.content.Intent open(android.content.Context c, String url, String title) {
        return new android.content.Intent(c, WebActivity.class).putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title);
    }
}
