package io.freemytv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Anonymous shared albums only. Bounded downloads, private thumbnails, no URL logging. */
final class ExperiencePhotos {
    final Bitmap[] images=new Bitmap[3];
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int generation; private boolean busy,closed,active; private long last;
    private final ExperiencePhotoRotation rotation=new ExperiencePhotoRotation();
    private final String[] displayed=new String[3];
    private List<String> pool=Collections.emptyList();
    private Bitmap ready;
    private String readyUrl;
    private int cursor;
    Bitmap previous;
    int fadingSlot=-1;
    private long fadeAt;
    private final Runnable rotate=this::rotate;
    private String current="";
    Runnable changed;
    ExperiencePhotos(Context c){context=c;}
    static boolean valid(String value){
        if(value.isEmpty())return true;
        try{return allowed(new URL(value),false);}catch(Exception e){return false;}
    }
    private static boolean allowed(URL u,boolean image){
        return "https".equals(u.getProtocol()) && u.getUserInfo()==null && (u.getPort()==-1 || u.getPort()==443)
            && (image?u.getHost().matches("lh[0-9]+\\.googleusercontent\\.com"):
            u.getHost().equals("photos.app.goo.gl") || u.getHost().equals("photos.google.com"));
    }
    static ArrayList<String> sources(String html){
        LinkedHashSet<String> found=new LinkedHashSet<>();int position=0;
        while((position=html.indexOf("AF_initDataCallback(",position))>=0){
            int start=html.indexOf("data:",position);position+=20;
            if(start<0)continue;
            try{collect(new JSONTokener(html.substring(start+5)).nextValue(),found,0);}catch(Exception ignored){}
        }
        return new ArrayList<>(found);
    }
    private static void collect(Object value,Set<String> found,int depth)throws Exception{
        if(depth>40 || found.size()>=200 || !(value instanceof JSONArray))return;
        JSONArray a=(JSONArray)value;
        if(a.length()>=3 && a.opt(0) instanceof String && a.opt(1) instanceof Number && a.opt(2) instanceof Number
                && a.optInt(1)>=200 && a.optInt(2)>=200){
            String url=a.getString(0);try{if(allowed(new URL(url),true))found.add(url);}catch(MalformedURLException ignored){}
        }
        for(int i=0;i<a.length();i++)collect(a.opt(i),found,depth+1);
    }
    private static byte[] fetch(String value,boolean image,int limit)throws Exception{
        URL url=new URL(value);
        for(int redirects=0;redirects<6;redirects++){
            if(!allowed(url,image))throw new IOException("Unsupported photo host");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","Mozilla/5.0");
            try{
                int code=c.getResponseCode();
                if(code>=300 && code<400){url=new URL(url,c.getHeaderField("Location"));continue;}
                if(code!=200 || (image && !String.valueOf(c.getContentType()).startsWith("image/")))throw new IOException("Unavailable album");
                if(c.getContentLength()>limit)throw new IOException("Photo too large");
                try(InputStream input=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Photo too large");out.write(buffer,0,n);}return out.toByteArray();
                }
            }finally{c.disconnect();}
        }throw new IOException("Too many redirects");
    }
    private static String hash(String value)throws Exception{
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
        StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
    }
    void refresh(String album,boolean visible){
        if(closed)return;
        if(!current.equals(album)){
            generation++;current=album;last=0;busy=false;Arrays.fill(images,null);Arrays.fill(displayed,null);
            pool=Collections.emptyList();ready=null;readyUrl=null;previous=null;fadingSlot=-1;cursor=0;
        }
        active(visible && !album.isEmpty());
        if(!active || busy)return;
        if(!pool.isEmpty() && System.currentTimeMillis()-last<15*60*1000L){prefetch();return;}
        busy=true;last=System.currentTimeMillis();final int token=generation;final boolean first=images[0]==null;
        worker.execute(()->{
            ArrayList<String> candidates=new ArrayList<>();Bitmap[] loaded=new Bitmap[3];String[] ids=new String[3];
            try{
                File dir=directory(album);File manifest=new File(dir,"sources.json");ArrayList<String> urls=new ArrayList<>();
                if(manifest.isFile() && System.currentTimeMillis()-manifest.lastModified()<6*3600000L){
                    urls=readManifest(manifest);
                }else{
                    try{urls=sources(new String(fetch(album,false,8*1024*1024),"UTF-8"));}
                    catch(Exception offline){if(manifest.isFile())urls=readManifest(manifest);else throw offline;}
                    if(!urls.isEmpty())try(FileOutputStream out=new FileOutputStream(manifest)){out.write(new JSONArray(urls).toString().getBytes("UTF-8"));}
                }
                Collections.shuffle(urls);
                for(String url:urls){String thumb=url.replaceFirst("=(?:w[0-9]+|h[0-9]+|s[0-9]+).*$","")+"=w480-h480-c";if(!candidates.contains(thumb))candidates.add(thumb);if(candidates.size()==12)break;}
                if(first){
                    int index=0;
                    for(String url:candidates){
                        try{Bitmap image=thumbnail(dir,url);if(image!=null){loaded[index]=image;ids[index++]=url;if(index==3)break;}}catch(Exception ignored){}
                    }
                }
                trim(dir);
            }catch(Exception ignored){/* Keep the current photos while offline. */}
            main.post(()->{
                if(closed || generation!=token)return;busy=false;
                if(!candidates.isEmpty()){pool=candidates;cursor=0;}
                if(first && loaded[0]!=null){
                    System.arraycopy(loaded,0,images,0,3);System.arraycopy(ids,0,displayed,0,3);
                    rotation.loaded(SystemClock.uptimeMillis());main.removeCallbacks(rotate);
                    if(active)main.postDelayed(rotate,ExperiencePhotoRotation.INTERVAL);
                    notifyChanged();
                }
                prefetch();
            });
        });
    }
    private void active(boolean value){
        if(active==value)return;active=value;rotation.active(value,SystemClock.uptimeMillis());main.removeCallbacks(rotate);
        if(active)main.postDelayed(rotate,ExperiencePhotoRotation.INTERVAL);
        else {previous=null;fadingSlot=-1;}
    }
    void stop(){active(false);}
    private void rotate(){
        if(!active || closed)return;
        long now=SystemClock.uptimeMillis();
        if(rotation.due(now) && ready!=null){
            int slot=rotation.slot();previous=images[slot];fadingSlot=slot;fadeAt=now;
            images[slot]=ready;displayed[slot]=readyUrl;ready=null;readyUrl=null;
            rotation.replaced(now);notifyChanged();
        }
        prefetch();
        main.removeCallbacks(rotate);main.postDelayed(rotate,rotation.due(now)?ExperiencePhotoRotation.INTERVAL:rotation.delay(now));
    }
    private void prefetch(){
        if(!active || closed || busy || ready!=null || pool.size()<4 || images[0]==null)return;
        String candidate=null;
        for(int i=0;i<pool.size();i++){
            String url=pool.get(cursor++%pool.size());boolean visible=false;
            for(String value:displayed)if(url.equals(value))visible=true;
            if(!visible){candidate=url;break;}
        }
        if(candidate==null)return;
        final String next=candidate,album=current;final int token=generation;busy=true;
        worker.execute(()->{
            Bitmap loaded=null;
            try{File dir=directory(album);loaded=thumbnail(dir,next);trim(dir);}catch(Exception ignored){}
            final Bitmap result=loaded;
            main.post(()->{
                if(closed || generation!=token)return;busy=false;
                if(result!=null){ready=result;readyUrl=next;
                    if(active){main.removeCallbacks(rotate);main.postDelayed(rotate,rotation.delay(SystemClock.uptimeMillis()));}
                }
            });
        });
    }
    boolean step(long now){
        if(previous==null)return false;
        if(now-fadeAt>=ExperiencePhotoRotation.FADE){previous=null;fadingSlot=-1;}
        return true;
    }
    float fade(long now){
        float t=Math.max(0,Math.min(1,(now-fadeAt)/(float)ExperiencePhotoRotation.FADE));return t*t*(3-2*t);
    }
    private void notifyChanged(){if(changed!=null)changed.run();}
    private File directory(String album)throws Exception{
        File dir=new File(context.getCacheDir(),"experience-photos/"+hash(album));dir.mkdirs();return dir;
    }
    private static ArrayList<String> readManifest(File file)throws Exception{
        if(file.length()>1024*1024)throw new IOException("Album cache too large");
        JSONArray a=new JSONArray(new String(java.nio.file.Files.readAllBytes(file.toPath()),"UTF-8"));ArrayList<String> urls=new ArrayList<>();
        for(int i=0;i<a.length() && i<200;i++){String url=a.getString(i);if(allowed(new URL(url),true))urls.add(url);}return urls;
    }
    private static Bitmap thumbnail(File dir,String url)throws Exception{
        File file=new File(dir,hash(url)+".jpg");
        if(file.length()>3*1024*1024)file.delete();
        byte[] bytes=file.isFile()?java.nio.file.Files.readAllBytes(file.toPath()):fetch(url,true,3*1024*1024);
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1 || bounds.outWidth>4096 || bounds.outHeight<1 || bounds.outHeight>4096)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;
        options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/480);
        Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(image!=null){if(!file.isFile())try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}file.setLastModified(System.currentTimeMillis());}
        return image;
    }
    private static void trim(File dir){
        File[] files=dir.listFiles((d,name)->name.endsWith(".jpg"));if(files==null || files.length<=12)return;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(int i=12;i<files.length;i++)files[i].delete();
    }
    void close(){closed=true;generation++;stop();worker.shutdownNow();}
}
