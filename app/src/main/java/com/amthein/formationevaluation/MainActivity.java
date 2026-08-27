package com.amthein.formationevaluation;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        setContentView(webView);

        WebSettings settings = webView.getSettings();

        // JavaScript is required by the LAS application.
        settings.setJavaScriptEnabled(true);

        // Required for the application's UI state.
        settings.setDomStorageEnabled(true);

        // Allow the local HTML application to access its resources.
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Improve Android rendering.
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);

        // Disable browser-style zoom controls.
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Allow media where required by the HTML application.
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Keep navigation inside the WebView.
        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // LAS is not a standard Android MIME type.
                // */* ensures LAS files are visible.
                intent.setType("*/*");

                try {
                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                } catch (Exception e) {

                    filePathCallback = null;
                    callback.onReceiveValue(null);

                    return false;
                }

                return true;
            }
        });

        /*
         * Load the actual LAS application.
         */
        webView.loadUrl(
                "file:///android_asset/Index.html"
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != FILE_CHOOSER_REQUEST) {
            return;
        }

        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;

        if (resultCode == RESULT_OK && data != null) {

            if (data.getClipData() != null) {

                int count =
                        data.getClipData().getItemCount();

                results = new Uri[count];

                for (int i = 0; i < count; i++) {

                    results[i] =
                            data.getClipData()
                                    .getItemAt(i)
                                    .getUri();
                }

            } else if (data.getData() != null) {

                results = new Uri[] {
                        data.getData()
                };
            }
        }

        filePathCallback.onReceiveValue(results);

        filePathCallback = null;
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
