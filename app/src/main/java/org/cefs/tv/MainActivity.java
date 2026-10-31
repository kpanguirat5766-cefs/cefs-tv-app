package org.cefs.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
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
    private static final int FILE_REQUEST = 42;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Android 15+ : l'affichage va jusqu'aux bords, on garde le contenu hors des barres du système.
        final View root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (customView != null) { v.setPadding(0, 0, 0, 0); return insets; }
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return insets;
        });

        // Android 13+ : bouton Retour (obligatoire à partir d'Android 16).
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Notifications : rappels des émissions et alertes de direct.
        try { Rappels.demarrer(this); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

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
        s.setUserAgentString(s.getUserAgentString() + " CEFSTV/1.5");

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
            web.loadUrl(startUrl(getIntent()));
            // Filet de sécurité : l'écran d'accueil ne reste jamais bloqué.
            splash.postDelayed(this::hideSplash, SPLASH_MAX_MS);
        }
    }

    /** Adresse à ouvrir : le lien partagé reçu, sinon la chaîne. */
    private String startUrl(Intent intent) {
        String home = getString(R.string.cefs_url);
        if (intent == null || intent.getData() == null) return home;
        Uri u = intent.getData();
        if ("cefstv".equals(u.getScheme())) return home;
        if ("https".equals(u.getScheme()) && isInternal(u)) return u.toString();
        return home;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getData() != null) {
            if (customView != null) exitFullscreen();
            web.loadUrl(startUrl(intent));
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
        return host.equals("kpanguirat5766-cefs.github.io")
                || host.equals("claude.ai") || host.endsWith(".claude.ai")
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
            // La page caméra du direct s'ouvre dans Chrome (caméra et micro du téléphone).
            String path = uri.getPath();
            if (("http".equals(scheme) || "https".equals(scheme)) && isInternal(uri)
                    && !(path != null && path.endsWith("/camera.html"))) {
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
            ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0).requestApplyInsets();
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        // Bouton « Choisir un fichier » des pages : ouvre la galerie ou les fichiers du téléphone.
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = callback;
            Intent pick;
            try {
                pick = params.createIntent();
            } catch (Exception e) {
                pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.setType("*/*");
            }
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(pick, "Choisir une vidéo"), FILE_REQUEST);
            } catch (ActivityNotFoundException e) {
                filePathCallback = null;
                Toast.makeText(MainActivity.this, "Aucune application pour choisir un fichier", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQUEST) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0).requestApplyInsets();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (customView != null) {
            exitFullscreen();
        } else if (web.canGoBack() && !mainFrameFailed) {
            web.goBack();
        } else if (SystemClock.uptimeMillis() - lastBackPress < 2000) {
            finish();
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
