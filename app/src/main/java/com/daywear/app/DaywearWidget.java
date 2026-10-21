package com.daywear.app;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DaywearWidget extends AppWidgetProvider {
  @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){refresh(context);}
  @Override public void onReceive(Context context,Intent intent){super.onReceive(context,intent);if(Intent.ACTION_DATE_CHANGED.equals(intent.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction()))refresh(context);}
  public static void refresh(Context context){
    Context app=context.getApplicationContext();
    new Thread(()->{try{fetchAndStore(app);}catch(Exception ignored){}render(app);}).start();
  }
  private static void fetchAndStore(Context context)throws Exception{
    JSONObject saved=new JSONObject(context.getSharedPreferences("daywear-widget",0).getString("data","{}"));
    JSONObject city=saved.optJSONObject("city");if(city==null)return;
    double lat=city.optDouble("latitude",Double.NaN),lon=city.optDouble("longitude",Double.NaN);
    if(!Double.isFinite(lat)||!Double.isFinite(lon))return;
    String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&hourly=temperature_2m,apparent_temperature,weather_code&timezone=auto&forecast_days=2";
    HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(12000);conn.setReadTimeout(12000);
    if(conn.getResponseCode()!=200){conn.disconnect();return;}
    ByteArrayOutputStream buf=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()){byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1)buf.write(chunk,0,n);}finally{conn.disconnect();}
    JSONObject root=new JSONObject(buf.toString("UTF-8")),hourly=root.getJSONObject("hourly");
    String zone=root.optString("timezone","Asia/Seoul"),date=LocalDate.now(ZoneId.of(zone)).toString();
    JSONArray times=hourly.getJSONArray("time"),temps=hourly.getJSONArray("temperature_2m"),feels=hourly.getJSONArray("apparent_temperature");
    int[] hours={8,13,20},t=new int[3],codes=new int[3];double[] f=new double[3];JSONArray weatherCodes=hourly.getJSONArray("weather_code");
    for(int i=0;i<3;i++){String target=date+"T"+String.format(java.util.Locale.US,"%02d:00",hours[i]);int idx=-1;for(int j=0;j<times.length();j++)if(target.equals(times.optString(j))){idx=j;break;}if(idx<0)return;t[i]=(int)Math.round(temps.getDouble(idx));codes[i]=weatherCodes.optInt(idx,3);f[i]=feels.getDouble(idx)+saved.optDouble("sensitivity",0);}
    double low=Math.min(f[0],Math.min(f[1],f[2])),mid=f[1];
    String outer=low<0?"두꺼운 패딩 + 목도리":low<5?"패딩 또는 두꺼운 울 코트":low<10?"울 코트 또는 보온 점퍼":low<16?"두툼한 가디건 또는 점퍼":low<20?"얇은 가디건 또는 가벼운 재킷":low<24?"가벼운 셔츠 아우터":"아우터 없이, 냉방용 얇은 셔츠";
    String inner=mid<5?"보온 이너 + 니트":mid<12?"긴팔 티셔츠 + 니트":mid<20?"얇은 긴팔 티셔츠":mid<25?"얇은 긴팔 또는 반팔":"통기성 좋은 반팔";
    saved.put("temps",new JSONArray().put(t[0]).put(t[1]).put(t[2]));saved.put("codes",new JSONArray().put(codes[0]).put(codes[1]).put(codes[2]));saved.put("outer",outer);saved.put("inner",inner);saved.put("widgetDate",date);
    context.getSharedPreferences("daywear-widget",0).edit().putString("data",saved.toString()).apply();
  }
  private static void render(Context context){
    try{
      JSONObject data=new JSONObject(context.getSharedPreferences("daywear-widget",0).getString("data","{}"));
      String theme=context.getSharedPreferences("daywear-widget",0).getString("theme","black");
      RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_daywear);
      String city=data.optString("cityName","오늘");views.setTextViewText(R.id.widget_title,"DAYWEAR · "+city);
      JSONArray t=data.optJSONArray("temps"),codes=data.optJSONArray("codes");
      if(t!=null&&t.length()==3){String[] parts=new String[3];for(int i=0;i<3;i++){int c=codes!=null?codes.optInt(i,3):3;String icon=c==0?"☀":c<=3?"☁":c<=48?"≋":c>=95?"ϟ":(c>=71&&c<=77)||(c>=85&&c<=86)?"❄":"☂";parts[i]=icon+t.optInt(i)+"°";}views.setTextViewText(R.id.widget_temps,parts[0]+"  "+parts[1]+"  "+parts[2]);}
      String outer=data.optString("outer",""),inner=data.optString("inner","");
      views.setTextViewText(R.id.widget_outfit,outer.isEmpty()?"오늘의 조합 · 앱에서 확인":outer+" · "+inner);
      int color="pink".equals(theme)?Color.rgb(237,22,138):"blue".equals(theme)?Color.rgb(36,80,220):Color.BLACK;
      views.setTextColor(R.id.widget_title,color);views.setTextColor(R.id.widget_temps,color);views.setTextColor(R.id.widget_outfit,color);
      Intent intent=new Intent(context,MainActivity.class);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
      PendingIntent open=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      views.setOnClickPendingIntent(R.id.widget_title,open);
      views.setOnClickPendingIntent(R.id.widget_temps,open);
      views.setOnClickPendingIntent(R.id.widget_outfit,open);
      AppWidgetManager manager=AppWidgetManager.getInstance(context);ComponentName name=new ComponentName(context,DaywearWidget.class);manager.updateAppWidget(name,views);
    }catch(Exception ignored){}
  }
}