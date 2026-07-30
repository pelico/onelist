package com.onelist.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "OneListTV";
    private static final String KEY_SERVER_URL = "server_url";

    private WebView webView;
    private ProgressBar progressBar;
    private String serverUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load saved server URL
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        serverUrl = prefs.getString(KEY_SERVER_URL, null);

        if (serverUrl == null || serverUrl.isEmpty()) {
            // No URL configured, go to setup
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        webView = findViewById(R.id.webView);

        configureWebView();
        webView.loadUrl(serverUrl);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // Cache - persist between sessions
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);

        // Viewport - use screen width, don't force mobile width
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Zoom support for TV
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Media playback
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Cookie persistence - this is the key to remembering login
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // WebViewClient - handle navigation within the app
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Keep navigation within the app for same-host URLs
                if (url.startsWith(serverUrl)) {
                    return false;
                }
                // Allow relative URLs
                if (url.startsWith("/")) {
                    return false;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
        });

        // WebChromeClient - for progress tracking
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // Focus for D-pad navigation on TV
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Back button goes back in WebView history
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Flush cookies to disk
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if URL changed (from setup screen)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String newUrl = prefs.getString(KEY_SERVER_URL, null);
        if (newUrl != null && !newUrl.equals(serverUrl)) {
            serverUrl = newUrl;
            if (webView != null) {
                webView.loadUrl(serverUrl);
            }
        }
    }
}
