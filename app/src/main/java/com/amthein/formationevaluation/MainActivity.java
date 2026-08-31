package com.amthein.formationevaluation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_FILE_REQUEST = 1002;

    private ByteArrayOutputStream pendingFileBuffer;
    private byte[] pendingSaveBytes;
    private String pendingFileName;
    private String pendingMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.rgb(238, 240, 243));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        } else {
            int resourceId = getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
            int statusBarHeight = resourceId > 0
                    ? getResources().getDimensionPixelSize(resourceId) : 0;
            root.setPadding(0, statusBarHeight, 0, 0);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
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
                intent.setType("*/*");

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }

                return true;
            }
        });

        webView.loadUrl("file:///android_asset/Index.html");
    }

    private class AndroidBridge {

        @JavascriptInterface
        public synchronized void saveBlobChunk(
                String base64,
                String filename,
                String mimeType,
                boolean first,
                boolean last) {
            try {
                if (first || pendingFileBuffer == null) {
                    pendingFileBuffer = new ByteArrayOutputStream();
                    pendingFileName = filename;
                    pendingMimeType = (mimeType == null || mimeType.isEmpty())
                            ? "application/octet-stream" : mimeType;
                }

                if (base64 != null && !base64.isEmpty()) {
                    byte[] chunk = Base64.decode(base64, Base64.DEFAULT);
                    pendingFileBuffer.write(chunk);
                }

                if (last) {
                    pendingSaveBytes = pendingFileBuffer.toByteArray();
                    final String outName = pendingFileName;
                    final String outMime = pendingMimeType;
                    pendingFileBuffer = null;

                    runOnUiThread(() -> {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType(outMime);
                        intent.putExtra(Intent.EXTRA_TITLE, outName);

                        try {
                            startActivityForResult(intent, CREATE_FILE_REQUEST);
                        } catch (Exception e) {
                            pendingSaveBytes = null;
                            webView.evaluateJavascript(
                                    "alert('Could not open the save dialog: "
                                            + escapeJs(e.getMessage()) + "')",
                                    null
                            );
                        }
                    });
                }
            } catch (Exception e) {
                pendingFileBuffer = null;
                pendingSaveBytes = null;
                pendingFileName = null;
                pendingMimeType = null;

                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(
                                "alert('Export failed: " + escapeJs(e.getMessage()) + "')",
                                null
                        );
                    }
                });
            }
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                if (webView == null) return;

                PrintManager printManager =
                        (PrintManager) getSystemService(PRINT_SERVICE);

                if (printManager == null) return;

                String jobName = "LAS Log Viewer";

                PrintAttributes attributes = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build();

                printManager.print(
                        jobName,
                        webView.createPrintDocumentAdapter(jobName),
                        attributes
                );
            });
        }
    }

    private String escapeJs(String text) {
        if (text == null) return "Unknown error";
        return text.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }

        if (requestCode == CREATE_FILE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && pendingSaveBytes != null) {
                try (OutputStream out =
                             getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) {
                        throw new IllegalStateException(
                                "Cannot open the selected save location");
                    }
                    out.write(pendingSaveBytes);
                    out.flush();
                } catch (Exception e) {
                    if (webView != null) {
                        webView.evaluateJavascript(
                                "alert('Could not save the file: "
                                        + escapeJs(e.getMessage()) + "')",
                                null
                        );
                    }
                }
            }

            pendingSaveBytes = null;
            pendingFileName = null;
            pendingMimeType = null;
        }
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
