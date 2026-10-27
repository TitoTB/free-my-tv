package io.freemytv;

import android.app.*;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.provider.Settings;
import android.view.Display;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** MQTT Discovery bridge, isolated from the launcher/watchdog and explicitly enabled in Settings. */
public class AndroidService extends Service {
    private static volatile AndroidService running;
    static boolean connected() { AndroidService service=running;return service!=null && service.mqtt!=null && service.mqtt.connected(); }
    static final String HOME="home",DREAM="dream";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AndroidNotice.Queue queue=new AndroidNotice.Queue();
    private AndroidBanner banner;
    private AndroidMqtt mqtt;
    private volatile boolean stopped;
    private volatile JSONObject latest=new JSONObject();
    private boolean home,dream;
    private String deviceId,base;
    private long lastCommand;
    private volatile long powerEpoch;
    private final java.util.concurrent.atomic.AtomicInteger incoming=new java.util.concurrent.atomic.AtomicInteger();
    static boolean enabled(Context c) { return new File(c.getFilesDir(),"android-enabled").isFile(); }
    static String identity(Context c) {
        android.content.SharedPreferences prefs=c.getSharedPreferences("android-identity",MODE_PRIVATE);
        String id=prefs.getString("id",null);
        if(id==null) {
            id=UUID.randomUUID().toString();
            if(!prefs.edit().putString("id",id).commit()) throw new IllegalStateException("Identity storage");
        }
        return id;
    }
    static void state(Context c,String part,boolean visible) {
        if(!enabled(c)) return;
        try { c.startForegroundService(new Intent(c,AndroidService.class).putExtra(part,visible)); }
        catch(RuntimeException ignored) { AppLog.w("Android TV: background start unavailable"); }
    }
    static void start(Context c) { state(c,"start",true); }
    @Override public void onCreate() {
        super.onCreate();
        running=this;
        Ui.init(this);
        deviceId=identity(this); base="android_tv/"+deviceId;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("android-tv","Android TV",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,AndroidSettingsActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(8766,new Notification.Builder(this,"android-tv").setSmallIcon(R.drawable.android_icon)
            .setContentTitle("Android TV").setContentText(Ui.tr("Home Assistant connection active")).setContentIntent(open).setOngoing(true).build());
        banner=new AndroidBanner(this,()->finish("acknowledged"));
        IntentFilter filter=new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(power,filter);
        updateState(); main.postDelayed(tick,5000);
        AndroidMqttSettings settings=new AndroidMqttSettings(this);
        if(settings.configured()) {
            mqtt=new AndroidMqtt(settings.uri(),settings.user,settings.password,deviceId,(topic,payload,retained)->{
                if(retained || incoming.incrementAndGet()>10) {
                    if(!retained) incoming.decrementAndGet();
                    return;
                }
                // Capture receipt time: a blocked UI must not turn stale commands into fresh ones.
                long received=System.currentTimeMillis();
                boolean awake=latest.optBoolean("screen_on");long epoch=powerEpoch;
                main.post(()->{ try { receive(topic,payload,received,awake && epoch==powerEpoch); } finally { incoming.decrementAndGet(); } });
            },transport->{
                String version="";try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch(Exception ignored) { }
                for(Map.Entry<String,String> config:AndroidDiscovery.configs(deviceId,settings.name,Build.MANUFACTURER,Build.MODEL,version).entrySet())
                    transport.publish(config.getKey(),config.getValue(),true);
                transport.publish(base+"/state",latest.toString(),true);
                transport.publish(base+"/awake",latest.optBoolean("screen_on")?"online":"offline",true);
                transport.publish(base+"/availability","online",true);
            });
            mqtt.start();
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(!enabled(this)) { stopSelf(); return START_NOT_STICKY; }
        if(intent!=null) {
            if(intent.hasExtra(HOME)) home=intent.getBooleanExtra(HOME,false);
            if(intent.hasExtra(DREAM)) dream=intent.getBooleanExtra(DREAM,false);
        }
        updateState(); return START_STICKY;
    }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
    private boolean screenOn() {
        Display display=getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        return display!=null && display.getState()==Display.STATE_ON;
    }
    private final BroadcastReceiver power=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent intent) {
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) { powerEpoch++;discardAll("screen_off"); }
            updateState();
        }
    };
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if(stopped) return;
            if(!screenOn()) discardAll("screen_off");
            else if(!Settings.canDrawOverlays(AndroidService.this)) discardAll("overlay_permission");
            updateState(); main.postDelayed(this,5000);
        }
    };
    private static JSONObject object(Object... fields) {
        JSONObject json=new JSONObject();
        try { for(int i=0;i<fields.length;i+=2) json.put((String)fields[i],fields[i+1]); }
        catch(Exception e) { throw new IllegalArgumentException(e); }
        return json;
    }
    private void updateState() {
        boolean on=screenOn();
        latest=object("screen_on",on,"home_visible",on&&home,"screensaver_active",on&&dream,
            "overlay_permission",Settings.canDrawOverlays(this),"pending_notifications",queue.pending(),
            "active_notification",queue.current==null?"":queue.current.id);
        if(mqtt!=null) {
            mqtt.publish(base+"/state",latest.toString(),true);
            mqtt.publish(base+"/awake",on?"online":"offline",true);
        }
    }
    private void receive(String topic,String payload,long received,boolean awakeAtReceipt) {
        if(stopped) return;
        long now=System.currentTimeMillis();
        if(now-received>10000 || SystemClock.elapsedRealtime()-lastCommand<100) return;
        lastCommand=SystemClock.elapsedRealtime();
        AndroidNotice notice;
        try { notice=new AndroidNotice(AndroidDiscovery.command(topic,payload,received),now); }
        catch(IllegalArgumentException invalid) {
            if(mqtt!=null) mqtt.publish(base+"/event",object("event_type","rejected","reason","invalid_notification").toString(),false);
            return;
        }
        if(!awakeAtReceipt || !screenOn()) { discardAll("screen_off");event(notice,"discarded","screen_off"); }
        else if(!Settings.canDrawOverlays(this)) event(notice,"rejected","overlay_permission");
        else {
            String status=queue.offer(notice,true);
            if("shown".equals(status)) show();
            else if("queue_full".equals(status)) event(notice,"discarded","queue_full");
            else event(notice,"queued","");
        }
        updateState();
    }
    private boolean show() {
        main.removeCallbacks(expire);
        AndroidNotice current=queue.current;
        if(current==null) return false;
        if(!screenOn()) { discardAll("screen_off"); return false; }
        try {
            banner.show(current); event(current,"shown","");
            if(!current.persistent) main.postDelayed(expire,current.duration*1000L);
            return true;
        } catch(RuntimeException failure) {
            event(current,"discarded","overlay_unavailable"); queue.next();
            banner.hide(); if(queue.current!=null) show();
            return false;
        }
    }
    private final Runnable expire=()->finish("expired");
    private void finish(String kind) {
        if(queue.current==null) return;
        if(!screenOn()) { discardAll("screen_off"); return; }
        event(queue.current,kind,""); main.removeCallbacks(expire);
        queue.next(); if(queue.current==null) banner.hide(); else show(); updateState();
    }
    private void discardAll(String reason) {
        main.removeCallbacks(expire);
        for(AndroidNotice notice:queue.clear()) event(notice,"discarded",reason);
        if(banner!=null) banner.hideImmediately();
    }
    private void event(AndroidNotice notice,String event,String reason) {
        if(mqtt!=null) mqtt.publish(base+"/event",object("event_type",event,"notification_id",notice.id,"reason",reason).toString(),false);
    }
    @Override public void onDestroy() {
        if(running==this) running=null;
        stopped=true; main.removeCallbacksAndMessages(null); discardAll("service_stopped");
        unregisterReceiver(power);
        if(mqtt!=null) mqtt.close();
        super.onDestroy();
    }
}
