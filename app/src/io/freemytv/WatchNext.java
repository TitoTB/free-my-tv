package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Only the unfinished titles apps explicitly publish to Android TV's public Watch Next provider. */
final class WatchNext {
    static List<JellyfinRecent.Item> load(Context context) {
        List<JellyfinRecent.Item> result=new ArrayList<>();
        String[] columns={"_id","package_name","title","intent_uri","poster_art_uri","watch_next_type",
                "last_playback_position_millis","duration_millis","last_engagement_time_utc_millis","browsable"};
        try(Cursor c=context.getContentResolver().query(Uri.parse("content://android.media.tv/watch_next_program"),columns,null,null,null)) {
            while(c!=null && c.moveToNext() && result.size()<100) {
                String pkg=c.getString(1),title=c.getString(2),intent=c.getString(3),art=c.getString(4);
                long position=c.getLong(6),duration=c.getLong(7);
                if(pkg==null || pkg.equals("org.jellyfin.androidtv") || title==null || title.isEmpty()
                        || intent==null || intent.isEmpty() || c.getInt(5)!=0 || c.getInt(9)==0 || position<=0 || duration<=position) continue;
                try { context.getPackageManager().getApplicationInfo(pkg,0); } catch(Exception e) { continue; }
                JellyfinRecent.Item item=new JellyfinRecent.Item("watch:"+pkg+":"+c.getLong(0),title,"Video","","watch:"+pkg+":"+c.getLong(0),art==null?"":art);
                item.owner=pkg; item.intentUri=intent; item.artUri=art==null?"":art;
                item.positionTicks=position*10_000L; item.durationTicks=duration*10_000L; item.lastPlayed=c.getLong(8);
                try { item.subtitle=context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg,0)).toString(); }
                catch(Exception ignored) { item.subtitle=pkg; }
                result.add(item);
            }
        } catch(Exception ignored) { /* Missing permission or unsupported provider must not hide Jellyfin's own items. */ }
        return result;
    }
    static byte[] image(Context context,String uri) throws Exception {
        Uri parsed=Uri.parse(uri); HttpURLConnection connection=null;
        InputStream stream;
        if("content".equals(parsed.getScheme())) stream=context.getContentResolver().openInputStream(parsed);
        else if("https".equals(parsed.getScheme())) {
            connection=(HttpURLConnection)new URL(uri).openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
            stream=connection.getInputStream();
        } else throw new java.io.IOException("Unsupported artwork URI");
        try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null) throw new java.io.IOException("Artwork unavailable");
            byte[] buffer=new byte[4096]; int count;
            while((count=in.read(buffer))!=-1) { if(out.size()+count>1024*1024) throw new java.io.IOException("Image too large"); out.write(buffer,0,count); }
            return out.toByteArray();
        } finally { if(connection!=null) connection.disconnect(); }
    }
    static void open(Activity activity,JellyfinRecent.Item item) {
        try {
            Intent intent=Intent.parseUri(item.intentUri,Intent.URI_INTENT_SCHEME);
            if(intent.getComponent()!=null && !intent.getComponent().getPackageName().equals(item.owner)) throw new SecurityException();
            intent.setSelector(null); intent.setPackage(item.owner);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(intent);
        } catch(Exception e) { Toast.makeText(activity,Ui.tr("Couldn't resume this title in its app."),Toast.LENGTH_LONG).show(); }
    }
}
