package org.cefs.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * CEFS TV : ouvre la page publique de la chaîne dans un lecteur plein écran,
 * avec écran d'accueil, plein écran vidéo et écran « pas de connexion ».
 */
public class MainActivity extends Activity {

    private static final long SPLASH_MIN_MS = 1800;
    private static final long SPLASH_MAX_MS = 9000;

    private WebView web;
    private View splash;
    private View error;
    private FrameLayout fullscreen;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private long startedAt;
    private boolean splashHidden = false;
    private boolean mainFrameFailed = false;
    private long lastBackPress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = findViewById(R.id.web);
        splash = findViewById(R.id.splash);
        error = findViewById(R.id.error);
        fullscreen = findViewById(R.id.fullscreen);
        startedAt = SystemClock.uptimeMillis();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(s.getUserAgentString() + " CEFSTV/1.0");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setBackgroundColor(getResources().getColor(R.color.studio, getTheme()));
        web.setWebViewClient(new CefsClient());
        web.setWebChromeClient(new CefsChrome());

        findViewById(R.id.retry).setOnClickListener(v -> {
            mainFrameFailed = false;
            error.setVisibility(View.GONE);
            web.setVisibility(View.VISIBLE);
            web.reload();
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
            hideSplash();
        } else {
            web.loadUrl(getString(R.string.cefs_url));
            // Filet de sécurité : l'écran d'accueil ne reste jamais bloqué.
            splash.postDelayed(this::hideSplash, SPLASH_MAX_MS);
        }
    }

    private void hideSplash() {
        if (splashHidden) return;
        splashHidden = true;
        long wait = Math.max(0, SPLASH_MIN_MS - (SystemClock.uptimeMillis() - startedAt));
        splash.postDelayed(() -> splash.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> splash.setVisibility(View.GONE))
                .start(), wait);
    }

    private void showError() {
        mainFrameFailed = true;
        web.setVisibility(View.INVISIBLE);
        error.setVisibility(View.VISIBLE);
        hideSplash();
    }

    private static boolean isInternal(Uri uri) {
        String host = uri.getHost();
        if (host == null) return true;
        return host.equals("claude.ai") || host.endsWith(".claude.ai")
                || host.endsWith("claudeusercontent.com")
                || host.endsWith("anthropic.com");
    }

    private void openOutside(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, uri.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private class CefsClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if (("http".equals(scheme) || "https".equals(scheme)) && isInternal(uri)) {
                return false;
            }
            // YouTube, WhatsApp, téléphone, e-mail… s'ouvrent dans l'application adaptée.
            openOutside(uri);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!mainFrameFailed) hideSplash();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError err) {
            if (request.isForMainFrame()) showError();
        }
    }

    private class CefsChrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customCallback = callback;
            fullscreen.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fullscreen.setVisibility(View.VISIBLE);
            web.setVisibility(View.INVISIBLE);
            fullscreen.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        fullscreen.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        web.setVisibility(View.VISIBLE);
        customView = null;
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (web.canGoBack() && !mainFrameFailed) {
            web.goBack();
        } else if (SystemClock.uptimeMillis() - lastBackPress < 2000) {
            super.onBackPressed();
        } else {
            lastBackPress = SystemClock.uptimeMillis();
            Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
