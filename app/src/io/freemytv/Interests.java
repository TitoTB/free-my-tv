package io.freemytv;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;

/** Three followed programmes: bounded background refresh, public metadata and one cached image per programme. */
final class Interests {
    private static final long TTL=10*60_000L;
    private static Interests instance;
    static synchronized Interests get(Context c) { if(instance==null) instance=new Interests(c.getApplicationContext()); return instance; }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners=new HashSet<>();
    private final Map<String,Bitmap> images=new HashMap<>();
    private final Set<String> loading=new HashSet<>();
    private final Map<String,Long> failures=new HashMap<>();
    private final SharedPreferences cache;
    private final File directory;
    private final InterestApi api=new InterestApi();
    private boolean fetching;
    private long checkedAt;
    List<InterestData.Episode> items=InterestData.sorted(Collections.emptyList());
    private final Runnable poll=this::refresh;
    private Interests(Context c) {
        context=c; directory=new File(c.getCacheDir(),"followed-programmes");
        cache=c.getSharedPreferences("followed-programmes",Context.MODE_PRIVATE);
        try { items=InterestData.decode(new JSONArray(cache.getString("items","[]")),System.currentTimeMillis()); } catch(Exception ignored) {}
    }
    void addListener(Runnable l) { listeners.add(l); l.run(); refresh(); }
    void removeListener(Runnable l) { listeners.remove(l); if(listeners.isEmpty()) main.removeCallbacks(poll); }
    private void changed() { for(Runnable l:new ArrayList<>(listeners)) l.run(); }
    void refresh() {
        main.removeCallbacks(poll);
        if(fetching || listeners.isEmpty()) return;
        long now=SystemClock.elapsedRealtime();
        if(checkedAt>0 && now-checkedAt<TTL) { main.postDelayed(poll,TTL-(now-checkedAt)); return; }
        fetching=true; final List<InterestData.Episode> old=items;
        io.execute(()->{
            List<InterestData.Episode> next=new ArrayList<>();
            for(int p=0;p<3;p++) try {
                next.add(api.latest(p,System.currentTimeMillis()));
            } catch(Exception ignored) {
                // Preserve that programme's last valid result on a temporary source failure.
                for(InterestData.Episode e:old) if(e.programme==p) next.add(e);
            }
            final List<InterestData.Episode> result=InterestData.sorted(next);
            try { cache.edit().putString("items",InterestData.encode(result).toString()).apply(); } catch(Exception ignored) {}
            prune(result);
            main.post(()->{
                items=result; fetching=false; checkedAt=SystemClock.elapsedRealtime();
                Set<String> keep=new HashSet<>(); for(InterestData.Episode e:result) keep.add(e.image);
                images.keySet().retainAll(keep); failures.keySet().retainAll(keep);
                changed(); refresh();
            });
        });
    }
    Bitmap image(InterestData.Episode e) { return images.get(e.image); }
    void prepareImages() {
        for(InterestData.Episode e:items) {
            String url=e.image;
            if(url.isEmpty() || images.containsKey(url) || loading.contains(url)) continue;
            Long failed=failures.get(url); if(failed!=null && SystemClock.elapsedRealtime()-failed<60_000) continue;
            loading.add(url);
            io.execute(()->{
                Bitmap bitmap=null;
                try {
                    directory.mkdirs(); File file=new File(directory,key(url)+".jpg");
                    byte[] bytes=null;
                    if(file.isFile() && file.length()<=5*1024*1024) try(FileInputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                        byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); bytes=out.toByteArray();
                    }
                    if(bytes!=null) bitmap=decode(bytes);
                    if(bitmap==null) {
                        bytes=InterestApi.read(url,5*1024*1024); bitmap=decode(bytes);
                        if(bitmap!=null) {
                            File tmp=new File(directory,key(url)+".tmp");
                            try(FileOutputStream out=new FileOutputStream(tmp)) { out.write(bytes); }
                            if(!tmp.renameTo(file)) tmp.delete();
                        }
                    }
                } catch(Exception ignored) {}
                final Bitmap ready=bitmap;
                main.post(()->{
                    loading.remove(url);
                    boolean current=false; for(InterestData.Episode item:items) current|=item.image.equals(url);
                    if(current) {
                        if(ready!=null) images.put(url,ready); else failures.put(url,SystemClock.elapsedRealtime());
                        changed();
                    } else if(ready!=null) ready.recycle();
                    final List<InterestData.Episode> keep=items;
                    io.execute(()->prune(keep));
                });
            });
        }
    }
    private static Bitmap decode(byte[] bytes) {
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<2 || options.outHeight<2 || options.outWidth>20000 || options.outHeight>20000) return null;
        options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>1280 || options.outHeight/options.inSampleSize>900) options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }
    private static String key(String url) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8")); StringBuilder out=new StringBuilder();
            for(byte b:digest) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString();
        } catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private void prune(List<InterestData.Episode> keep) {
        Set<String> files=new HashSet<>(); for(InterestData.Episode e:keep) if(!e.image.isEmpty()) files.add(key(e.image)+".jpg");
        File[] stored=directory.listFiles(); if(stored!=null) for(File f:stored) if(!files.contains(f.getName())) f.delete();
    }
    boolean open(Activity activity,int index) {
        if(index<0 || index>=items.size()) return false;
        InterestData.Episode e=items.get(index); if(!e.ready()) return false;
        try {
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(e.url)).setPackage(InterestData.PACKAGES[e.programme]);
            // Start a fresh playback screen: some TV apps restore a paused task despite START_PLAYBACK.
            intent.putExtra("android.intent.extra.START_PLAYBACK",true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent); return true;
        } catch(Exception ignored) { return false; }
    }
}
