package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resume through Jellyfin's public session API; never target another device or another signed-in user. */
final class ContinuePlayback {
    private static ContinuePlayback instance;
    static synchronized ContinuePlayback get() {
        if(instance==null) instance=new ContinuePlayback();
        return instance;
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile long generation;
    private boolean busy;
    void cancel() { generation++; busy=false; }
    void resume(Activity activity,JellyfinRecent.Item item) {
        if(busy) return;
        Intent launch=activity.getPackageManager().getLeanbackLaunchIntentForPackage("org.jellyfin.androidtv");
        if(launch==null) { Toast.makeText(activity,Ui.tr("Jellyfin is not installed."),Toast.LENGTH_LONG).show(); return; }
        busy=true; long ticket=++generation;
        Toast.makeText(activity,Ui.tr("Resuming in Jellyfin…"),Toast.LENGTH_SHORT).show();
        io.execute(()->{
            try {
                JSONObject current=new JSONObject(new String(JellyfinRecent.read("/Users/"+Config.JELLYFIN_USER+"/Items/"+item.id,2*1024*1024),StandardCharsets.UTF_8));
                List<JellyfinRecent.Item> fresh=JellyfinRecent.parseResume(new JSONArray().put(current));
                if(fresh.isEmpty()) throw new java.io.IOException("No longer resumable");
                long position=fresh.get(0).positionTicks;
                main.post(()->{ if(ticket==generation) try { activity.startActivity(launch); }
                    catch(Exception e) { fail(activity,ticket); } });
                List<String> addresses=new ArrayList<>();
                for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces()))
                    for(java.net.InetAddress address:Collections.list(network.getInetAddresses()))
                        if(address instanceof Inet4Address && !address.isLoopbackAddress()) addresses.add(address.getHostAddress());
                long deadline=System.currentTimeMillis()+20_000;
                while(ticket==generation && System.currentTimeMillis()<deadline) {
                    JSONArray sessions=new JSONArray(new String(JellyfinRecent.read("/Sessions",1024*1024),StandardCharsets.UTF_8));
                    String session=findSession(sessions,Config.JELLYFIN_USER,addresses);
                    if(session!=null) {
                        if(ticket!=generation) return;
                        sendPlay(session,item.id,position);
                        main.post(()->{ if(ticket==generation) busy=false; }); return;
                    }
                    Thread.sleep(500);
                }
                if(ticket==generation) main.post(()->fail(activity,ticket));
            } catch(Exception e) { main.post(()->fail(activity,ticket)); }
        });
    }
    private void fail(Activity activity,long ticket) {
        if(ticket!=generation) return;
        busy=false; generation++;
        Toast.makeText(activity,Ui.tr("Couldn't resume. Open Jellyfin with your configured profile and try again."),Toast.LENGTH_LONG).show();
    }
    static String findSession(JSONArray sessions,String user,List<String> addresses) {
        String match=null;
        for(int i=0;i<sessions.length();i++) {
            JSONObject s=sessions.optJSONObject(i); if(s==null) continue;
            if(!s.optString("UserId").replace("-","").equalsIgnoreCase(user.replace("-",""))
                    || !s.optBoolean("SupportsRemoteControl")
                    || !s.optString("Client").toLowerCase(java.util.Locale.ROOT).contains("android tv")) continue;
            String remote=s.optString("RemoteEndPoint"); boolean local=false;
            for(String address:addresses) local|=remote.equals(address)||remote.startsWith(address+":")
                    ||remote.equals("::ffff:"+address)||remote.startsWith("[::ffff:"+address+"]:");
            String id=s.optString("Id");
            if(!local || !id.matches("[a-zA-Z0-9]{1,128}")) continue;
            if(match!=null) return null; // An ambiguous device must never receive a playback command.
            match=id;
        }
        return match;
    }
    private static void sendPlay(String session,String item,long position) throws Exception {
        URL url=new URL(Config.JELLYFIN_URL.replaceAll("/+$","")+playPath(session,item,position));
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST"); c.setRequestProperty("X-Emby-Token",Config.JELLYFIN_KEY);
        try { int status=c.getResponseCode(); if(status<200 || status>=300) throw new java.io.IOException("Resume rejected"); }
        finally { c.disconnect(); }
    }
    static String playPath(String session,String item,long position) throws java.io.IOException {
        if(!session.matches("[a-zA-Z0-9]{1,128}") || !JellyfinRecent.validId(item) || position<=0)
            throw new java.io.IOException("Invalid resume request");
        return "/Sessions/"+session+"/Playing?PlayCommand=PlayNow&ItemIds="+item+"&StartPositionTicks="+position;
    }
}
