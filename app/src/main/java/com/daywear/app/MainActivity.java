package com.daywear.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
public class MainActivity extends Activity {
  WebView web;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    setContentView(web);
    WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
        Uri uri=request.getUrl();
        if ("file".equals(uri.getScheme())) return false;
        if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
          startActivity(new Intent(Intent.ACTION_VIEW,uri)); return true;
        }
        return false;
      }
    });
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
