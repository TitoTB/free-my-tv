package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The selected Jellyfin user's actual additions, independent of Jellyseerr's request history. */
final class JellyfinRecent {
    static final int LIMIT=20;
    private static final long TTL=5*60_000L;
    static final class Item {
        final String id,name,type,year,imageId,imageTag;
        String key,landscapeId="",landscapeType="",landscapeTag="";
        long positionTicks,durationTicks,lastPlayed;
        String owner="org.jellyfin.androidtv",intentUri="",artUri="",subtitle="";
        Item(String id,String name,String type,String year,String imageId,String imageTag) {
            this.id=id; this.name=name; this.type=type; this.year=year; this.imageId=imageId; this.imageTag=imageTag;
            key=imageId+"/"+imageTag;
        }
        String imageKey() { return key; }
        void landscape(String id,String type,String tag) {
            if(!validId(id) || tag.isEmpty() || (!type.equals("Thumb") && !type.equals("Backdrop"))) return;
            landscapeId=id; landscapeType=type; landscapeTag=tag; key=id+"/"+type+"/"+tag;
        }
    }
    private static JellyfinRecent instance;
    private static JellyfinRecent continuing;
    static synchronized JellyfinRecent get(Context c) {
        if(instance==null) instance=new JellyfinRecent(c.getApplicationContext(),false);
        return instance;
    }
    static synchronized JellyfinRecent getContinue(Context c) {
        if(continuing==null) continuing=new JellyfinRecent(c.getApplicationContext(),true);
        return continuing;
    }
    static boolean enabled() {
        return !Config.JELLYFIN_URL.isEmpty() && !Config.JELLYFIN_KEY.isEmpty() && validId(Config.JELLYFIN_USER);
    }
    static boolean validId(String id) { return id!=null && id.replace("-","").matches("[0-9a-fA-F]{32}"); }
    static String intentId(String id) {
        String s=id.replace("-","");
        if(!validId(s)) throw new IllegalArgumentException("Invalid Jellyfin ID");
        return s.substring(0,8)+"-"+s.substring(8,12)+"-"+s.substring(12,16)+"-"+s.substring(16,20)+"-"+s.substring(20);
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final List<Runnable> listeners=new ArrayList<>();
    private final Map<String,Bitmap> images=new LinkedHashMap<>(16,0.75f,true);
    private final Set<String> loading=new HashSet<>();
    private final Map<String,Long> failedImages=new HashMap<>();
    private final SharedPreferences cache;
    private final Context context;
    final boolean resumeMode;
    private final String identity=Config.JELLYFIN_URL+"|"+Config.JELLYFIN_USER;
    List<Item> items=Collections.emptyList();
    boolean fetching;
    String error="";
    private long loadedAt,failedAt;

    private JellyfinRecent(Context context,boolean resumeMode) {
        this.context=context; this.resumeMode=resumeMode;
        cache=context.getSharedPreferences("jellyfin-recent",Context.MODE_PRIVATE);
        if(!resumeMode && enabled() && identity.equals(cache.getString("identity",""))) try {
            loadedAt=cache.getLong("at",0);
            if(System.currentTimeMillis()-loadedAt<24*60*60_000L)
                items=parse(new JSONArray(cache.getString("items","[]")));
            if(cache.getInt("artworkVersion",0)!=1) loadedAt=0;
        } catch(Exception ignored) { }
    }
    void addListener(Runnable l) { if(!listeners.contains(l)) listeners.add(l); }
    void removeListener(Runnable l) { listeners.remove(l); }
    private void changed() { for(Runnable l:new ArrayList<>(listeners)) l.run(); }
    void refreshProgress() { if(resumeMode) loadedAt=0; refresh(); }
    void refresh() {
        long now=System.currentTimeMillis();
        if(!enabled() || fetching || (loadedAt>0 && now>=loadedAt && now-loadedAt<(resumeMode?30_000:TTL))
                || (failedAt>0 && now>=failedAt && now-failedAt<60_000)) return;
        fetching=true;
        if(resumeMode) { refreshContinue(); return; }
        io.execute(()->{
            try {
                byte[] bytes=read("/Users/"+Config.JELLYFIN_USER+"/Items/Latest?Limit="+LIMIT
                        +"&IncludeItemTypes=Movie,Episode&GroupItems=true&EnableImages=true&ImageTypeLimit=1&EnableImageTypes=Primary,Thumb,Backdrop",2*1024*1024);
                List<Item> found=parse(new JSONArray(new String(bytes,StandardCharsets.UTF_8)));
                // Cache only display metadata, not user data, tokens or the server's complete response.
                JSONArray saved=encode(found); long at=System.currentTimeMillis();
                cache.edit().putString("identity",identity).putString("items",saved.toString()).putLong("at",at).putInt("artworkVersion",1).apply();
                main.post(()->{ items=found; loadedAt=at; failedAt=0; fetching=false; error=""; changed(); });
            } catch(Exception e) {
                main.post(()->{ fetching=false; failedAt=System.currentTimeMillis();
                    error=Ui.tr("Couldn't update Jellyfin. Try again later."); changed(); });
            }
        });
    }
    private void refreshContinue() {
        io.execute(()->{
            List<Item> found=new ArrayList<>(); boolean failed=false;
            try {
                JSONObject response=new JSONObject(new String(read("/Users/"+Config.JELLYFIN_USER
                        +"/Items/Resume?Limit=20&MediaTypes=Video&EnableUserData=true&EnableImages=true&ImageTypeLimit=1&EnableImageTypes=Primary,Thumb,Backdrop",2*1024*1024),StandardCharsets.UTF_8));
                JSONArray rows=response.optJSONArray("Items");
                if(rows==null) throw new IOException("Invalid resume response");
                found.addAll(parseResume(rows));
            } catch(Exception e) { failed=true; }
            found.addAll(WatchNext.load(context));
            found.sort((a,b)->Long.compare(b.lastPlayed,a.lastPlayed));
            final boolean error=failed;
            main.post(()->{ items=found; fetching=false; loadedAt=System.currentTimeMillis();
                failedAt=error?loadedAt:0;
                this.error=error?Ui.tr("Couldn't update Jellyfin. Try again later."):""; changed(); });
        });
    }
    static List<Item> parseResume(JSONArray rows) {
        List<Item> result=new ArrayList<>();
        for(int i=0;i<rows.length() && result.size()<LIMIT;i++) {
            JSONObject row=rows.optJSONObject(i); if(row==null) continue;
            JSONObject data=row.optJSONObject("UserData");
            long position=data==null?0:data.optLong("PlaybackPositionTicks"),duration=row.optLong("RunTimeTicks");
            if(data==null || data.optBoolean("Played") || position<=0 || duration<=position
                    || (!row.optString("Type").equals("Movie") && !row.optString("Type").equals("Episode"))) continue;
            JSONObject display=row;
            if(row.optString("Type").equals("Episode") && !row.optString("SeriesName").isEmpty()) try {
                display=new JSONObject(row.toString()).put("Name",row.optString("SeriesName")+" · "+row.optString("Name"));
            } catch(Exception ignored) { }
            List<Item> parsed=parse(new JSONArray().put(display)); if(parsed.isEmpty()) continue;
            Item item=parsed.get(0); item.positionTicks=position; item.durationTicks=duration;
            try { item.lastPlayed=java.time.Instant.parse(data.optString("LastPlayedDate")).toEpochMilli(); }
            catch(Exception ignored) { }
            item.subtitle="Jellyfin";
            result.add(item);
        }
        return result;
    }
    static List<Item> parse(JSONArray rows) {
        List<Item> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length() && result.size()<LIMIT;i++) {
            JSONObject row=rows.optJSONObject(i); if(row==null) continue;
            String id=row.optString("Id"),name=row.optString("Name").trim(),type=row.optString("Type");
            if(!validId(id) || name.isEmpty() || !seen.add(id.replace("-","").toLowerCase(java.util.Locale.ROOT))) continue;
            if(!type.equals("Movie") && !type.equals("Series") && !type.equals("Season") && !type.equals("Episode")) continue;
            JSONObject tags=row.optJSONObject("ImageTags"); String tag=tags==null?"":tags.optString("Primary");
            String imageId=id;
            if(tag.isEmpty() && validId(row.optString("SeriesId"))) {
                imageId=row.optString("SeriesId"); tag=row.optString("SeriesPrimaryImageTag");
            }
            int year=row.optInt("ProductionYear");
            Item item=new Item(id,name,type,year>0?Integer.toString(year):"",imageId,tag);
            String thumb=tags==null?"":tags.optString("Thumb");
            JSONArray backdrops=row.optJSONArray("BackdropImageTags"),parentBackdrops=row.optJSONArray("ParentBackdropImageTags");
            if(!thumb.isEmpty()) item.landscape(id,"Thumb",thumb);
            else if(type.equals("Episode") && !tag.isEmpty() && imageId.equals(id)) { /* Episode primary images are still frames. */ }
            else if(backdrops!=null && !backdrops.optString(0).isEmpty()) item.landscape(id,"Backdrop",backdrops.optString(0));
            else if(validId(row.optString("ParentThumbItemId")) && !row.optString("ParentThumbImageTag").isEmpty())
                item.landscape(row.optString("ParentThumbItemId"),"Thumb",row.optString("ParentThumbImageTag"));
            else if(parentBackdrops!=null && !parentBackdrops.optString(0).isEmpty())
                item.landscape(row.optString("ParentBackdropItemId"),"Backdrop",parentBackdrops.optString(0));
            if(validId(row.optString("LandscapeImageId")))
                item.landscape(row.optString("LandscapeImageId"),row.optString("LandscapeImageType"),row.optString("LandscapeImageTag"));
            result.add(item);
        }
        return result;
    }
    static JSONArray encode(List<Item> items) throws Exception {
        JSONArray rows=new JSONArray();
        for(Item item:items) {
            JSONObject row=new JSONObject().put("Id",item.id).put("Name",item.name).put("Type",item.type)
                    .put("ProductionYear",item.year.isEmpty()?0:Integer.parseInt(item.year));
            if(item.imageId.equals(item.id)) row.put("ImageTags",new JSONObject().put("Primary",item.imageTag));
            else row.put("SeriesId",item.imageId).put("SeriesPrimaryImageTag",item.imageTag);
            if(!item.landscapeTag.isEmpty()) row.put("LandscapeImageId",item.landscapeId)
                    .put("LandscapeImageType",item.landscapeType).put("LandscapeImageTag",item.landscapeTag);
            rows.put(row);
        }
        return rows;
    }
    Bitmap poster(Item item) { return images.get(item.imageKey()); }
    void prepareImages(int first,int count) {
        for(int i=Math.max(0,first);i<items.size() && i<first+count;i++) {
            Item item=items.get(i); String key=item.imageKey(); long now=System.currentTimeMillis();
            Long failed=failedImages.get(key);
            if((item.imageTag.isEmpty() && item.landscapeTag.isEmpty() && item.artUri.isEmpty()) || images.containsKey(key) || loading.contains(key)
                    || (failed!=null && now-failed<60_000)) continue;
            loading.add(key);
            io.execute(()->{
                Bitmap bitmap=null;
                try {
                    String imageId=item.landscapeTag.isEmpty()?item.imageId:item.landscapeId;
                    String imageType=item.landscapeTag.isEmpty()?"Primary":item.landscapeType;
                    String tag=item.landscapeTag.isEmpty()?item.imageTag:item.landscapeTag;
                    byte[] bytes=item.artUri.isEmpty()?read("/Items/"+imageId+"/Images/"+imageType+"?MaxWidth=480&MaxHeight=270&Quality=85&Tag="
                            +java.net.URLEncoder.encode(tag,"UTF-8"),1024*1024):WatchNext.image(context,item.artUri);
                    BitmapFactory.Options options=new BitmapFactory.Options(); options.inPreferredConfig=Bitmap.Config.RGB_565;
                    options.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                    options.inSampleSize=1;
                    while(options.outWidth/options.inSampleSize>960 || options.outHeight/options.inSampleSize>540) options.inSampleSize*=2;
                    options.inJustDecodeBounds=false;
                    bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                } catch(Exception ignored) { }
                final Bitmap found=bitmap;
                main.post(()->{
                    loading.remove(key);
                    if(found!=null) { images.put(key,found); failedImages.remove(key);
                        while(images.size()>12) images.remove(images.keySet().iterator().next());
                    } else failedImages.put(key,System.currentTimeMillis());
                    changed();
                });
            });
        }
    }
    static byte[] read(String path,int limit) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(Config.JELLYFIN_URL.replaceAll("/+$","")+path).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("X-Emby-Token",Config.JELLYFIN_KEY);
        try {
            if(c.getResponseCode()!=200) throw new IOException("Jellyfin unavailable");
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while((n=in.read(buffer))!=-1) { if(out.size()+n>limit) throw new IOException("Response too large"); out.write(buffer,0,n); }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }
    void open(Activity activity,int index) {
        if(index<0 || index>=items.size()) return;
        if(resumeMode) {
            Item item=items.get(index);
            if(!item.intentUri.isEmpty()) WatchNext.open(activity,item);
            else ContinuePlayback.get().resume(activity,item);
            return;
        }
        Intent intent=activity.getPackageManager().getLeanbackLaunchIntentForPackage("org.jellyfin.androidtv");
        if(intent==null) intent=activity.getPackageManager().getLaunchIntentForPackage("org.jellyfin.androidtv");
        if(intent==null) { Toast.makeText(activity,Ui.tr("Jellyfin is not installed."),Toast.LENGTH_LONG).show(); return; }
        // A launcher MAIN intent can merely restore an existing Jellyfin task, ignoring the new item extras.
        // VIEW with a distinct item URI makes the exported startup activity resolve the requested details screen.
        String id=intentId(items.get(index).id);
        intent.setAction(Intent.ACTION_VIEW); intent.setData(Uri.parse(id));
        intent.removeCategory(Intent.CATEGORY_LAUNCHER); intent.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("ItemId",id); intent.putExtra("ItemIsUserView",false);
        try { activity.startActivity(intent); }
        catch(Exception e) { Toast.makeText(activity,Ui.tr("Couldn't open this title in Jellyfin."),Toast.LENGTH_LONG).show(); }
    }
}
