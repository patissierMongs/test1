package app.fold2unfold.demo;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** index.html 을 WebView 로 띄우는 얇은 래퍼. 웹앱 본체는 assets/index.html 이다. */
public class MainActivity extends Activity {
    private static final String START_URL = "file:///android_asset/index.html";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                // 앱 안의 페이지(탭 전환 등)는 그대로 둔다.
                if ("file".equals(scheme) && u.getPath() != null && u.getPath().startsWith("/android_asset/")) {
                    return false;
                }
                // 외부 링크는 브라우저 앱으로 넘기고, 그 밖의 주소는 막는다.
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (ActivityNotFoundException ignored) {
                        // 브라우저가 없으면 아무 일도 하지 않는다.
                    }
                }
                return true;
            }
        });

        // 실기기 모드가 열리면 웹페이지가 시스템 바 숨김과 화면 켜짐 유지를 요청한다.
        web.addJavascriptInterface(new Bridge(), "F2UNative");

        setContentView(web);
        if (savedInstanceState == null) {
            web.loadUrl(START_URL);
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void immersive(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    applyImmersive(on);
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive(boolean on) {
        Window w = getWindow();
        if (on) {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            if (on) {
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                c.hide(WindowInsets.Type.systemBars());
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            View decor = w.getDecorView();
            decor.setSystemUiVisibility(on
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    /** 뒤로 가기: 실기기 모드가 열려 있으면 설정창, 그다음 실기기 모드를 먼저 닫고 아니면 앱을 끝낸다. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        final String js = "(function(){"
                + "var v=document.getElementById('device'),s=document.getElementById('dvSheet');"
                + "if(v&&!v.hidden){"
                + "if(s&&!s.hidden){s.hidden=true;}else{document.getElementById('dvClose').click();}"
                + "return 'handled';}"
                + "return '';})()";
        web.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || !value.contains("handled")) {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
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
