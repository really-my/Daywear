package com.daywear.app;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
  private static final String PAGE="file:///android_asset/index.html";
  private static final int LOCATION_PERMISSION=17, PHOTO_PICKER=18;
  private WebView web;
  private LocationManager manager;
  private LocationListener listener;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private Runnable timeout;
  private boolean locating=false;
  private ValueCallback<Uri[]> photoCallback;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);
    web=new WebView(this);web.setBackgroundColor(Color.WHITE);
    root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
    getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
    if(Build.VERSION.SDK_INT>=30){
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController controller=getWindow().getInsetsController();
      if(controller!=null)controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
      root.setOnApplyWindowInsetsListener((v,insets)->{
        android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
        v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return WindowInsets.CONSUMED;
      });root.requestApplyInsets();
    }else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);settings.setAllowContentAccess(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.addJavascriptInterface(new Object(){
      @JavascriptInterface public void setTheme(String theme){getSharedPreferences("daywear-widget",0).edit().putString("theme",theme).apply();DaywearWidget.refresh(MainActivity.this);}
      @JavascriptInterface public void updateWidget(String json){try{org.json.JSONObject j=new org.json.JSONObject(json);getSharedPreferences("daywear-widget",0).edit().putString("data",json).putString("theme",j.optString("theme","black")).apply();DaywearWidget.refresh(MainActivity.this);}catch(Exception ignored){}}
      @JavascriptInterface public void requestLocation(){runOnUiThread(()->{if(web.getUrl()!=null&&web.getUrl().startsWith(PAGE))beginLocation();});}
    },"DaywearNative");
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result){
        new AlertDialog.Builder(MainActivity.this).setMessage(message)
          .setPositiveButton(message.contains("삭제")?"삭제":"확인",(d,w)->result.confirm())
          .setNegativeButton("취소",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
      }
      @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result){
        new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("확인",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;
      }
      @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(photoCallback!=null)photoCallback.onReceiveValue(null);photoCallback=callback;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"});
        try{startActivityForResult(intent,PHOTO_PICKER);}catch(ActivityNotFoundException e){photoCallback.onReceiveValue(null);photoCallback=null;}return true;
      }
    });
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
        Uri uri=request.getUrl();if(PAGE.equals(uri.toString())||uri.toString().startsWith(PAGE+"#"))return false;
        if(request.isForMainFrame()&&("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))){
          try{startActivity(new Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE));}
          catch(ActivityNotFoundException e){Toast.makeText(MainActivity.this,"링크를 열 브라우저가 없습니다.",Toast.LENGTH_SHORT).show();}
        }return true;
      }
    });
    web.loadUrl(PAGE);
  }
  private void beginLocation(){
    if(locating)return;
    if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION);return;
    }
    locating=true;manager=(LocationManager)getSystemService(LOCATION_SERVICE);
    listener=new LocationListener(){
      @Override public void onLocationChanged(Location location){finishLocation(location,null);}
      @Override public void onProviderDisabled(String provider){}
      @Override public void onProviderEnabled(String provider){}
      @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    };
    try{
      if(manager==null){finishLocation(null,"위치 서비스를 사용할 수 없습니다.");return;}
      boolean network=manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
      boolean gps=manager.isProviderEnabled(LocationManager.GPS_PROVIDER)&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
      if(!network&&!gps){finishLocation(null,"휴대전화 위치 서비스를 켜 주세요.");return;}
      Location cached=null;
      if(network)cached=manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
      if(gps){Location gpsCached=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(gpsCached!=null&&(cached==null||gpsCached.getElapsedRealtimeNanos()>cached.getElapsedRealtimeNanos()))cached=gpsCached;}
      if(cached!=null&&android.os.SystemClock.elapsedRealtimeNanos()-cached.getElapsedRealtimeNanos()<300000000000L){finishLocation(cached,null);return;}
      if(network)manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,listener,Looper.getMainLooper());
      if(gps)manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,listener,Looper.getMainLooper());
      timeout=()->finishLocation(null,"현재 위치를 확인하지 못했습니다.");handler.postDelayed(timeout,20000);
    }catch(Exception e){finishLocation(null,"현재 위치를 확인하지 못했습니다.");}
  }
  private void finishLocation(Location location,String error){
    locating=false;if(timeout!=null)handler.removeCallbacks(timeout);
    if(manager!=null&&listener!=null){try{manager.removeUpdates(listener);}catch(Exception ignored){}}
    listener=null;
    if(isDestroyed()||isFinishing())return;
    String js=location==null?"window.daywearLocation&&window.daywearLocation(null,null,"+org.json.JSONObject.quote(error==null?"위치 권한이 필요합니다.":error)+")":
      "window.daywearLocation&&window.daywearLocation("+location.getLatitude()+","+location.getLongitude()+",null)";
    web.evaluateJavascript(js,null);
    if(location!=null)resolveCity(location);
  }
  private void resolveCity(Location location){
    new Thread(()->{
      String city=null;
      try{
        if(android.location.Geocoder.isPresent()){
          java.util.List<android.location.Address> results=new android.location.Geocoder(getApplicationContext(),java.util.Locale.KOREAN).getFromLocation(location.getLatitude(),location.getLongitude(),1);
          if(results!=null&&!results.isEmpty()){
            android.location.Address a=results.get(0);
            String admin=a.getAdminArea();
            if(admin!=null&&(admin.endsWith("특별시")||admin.endsWith("광역시")||admin.contains("세종")))city=admin.replace("특별자치시","").replace("특별시","").replace("광역시","");
            else city=a.getLocality();
            if(city==null||city.trim().isEmpty())city=a.getSubAdminArea();
            if(city==null||city.trim().isEmpty())city=admin;
            if(city==null||city.trim().isEmpty())city=a.getCountryName();
          }
        }
      }catch(Exception ignored){}
      final String label=city;
      runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())web.evaluateJavascript("window.daywearCityName&&window.daywearCityName("+location.getLatitude()+","+location.getLongitude()+","+org.json.JSONObject.quote(label==null?"":label)+")",null);});
    },"daywear-city").start();
  }
  @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
    super.onRequestPermissionsResult(code,permissions,grants);
    if(code==LOCATION_PERMISSION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)beginLocation();else finishLocation(null,"위치 권한이 허용되지 않았습니다.");}
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==PHOTO_PICKER&&photoCallback!=null){
      Uri uri=result==RESULT_OK&&data!=null?data.getData():null;
      if(uri!=null){String type=getContentResolver().getType(uri);if(!"content".equals(uri.getScheme())||type==null||!type.startsWith("image/"))uri=null;}
      photoCallback.onReceiveValue(uri==null?null:new Uri[]{uri});photoCallback=null;
    }
  }
  @Override public void onBackPressed(){
    web.evaluateJavascript("(()=>{const d=document.querySelector('dialog[open]');if(d){d.close();return true}const b=document.querySelector('nav button.active');if(b&&b.dataset.tab!=='today'){document.querySelector('button[data-tab=today]').click();return true}return false})()",handled->{if(!"true".equals(handled))finish();});
  }
  @Override protected void onStop(){super.onStop();if(locating)finishLocation(null,"위치 확인이 중단되었습니다. 다시 시도하세요.");}
  @Override protected void onDestroy(){
    if(timeout!=null)handler.removeCallbacks(timeout);
    if(manager!=null&&listener!=null){try{manager.removeUpdates(listener);}catch(Exception ignored){}}
    if(photoCallback!=null)photoCallback.onReceiveValue(null);
    web.removeJavascriptInterface("DaywearNative");web.destroy();super.onDestroy();
  }
}