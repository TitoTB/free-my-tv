package io.freemytv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.AtomicFile;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** One daily public feed, persistent category choices, bounded thumbnails and stale-data fallback. */
final class CulturalEvents {
    static final String SOURCE="https://www.esmadrid.com/opendata/agenda_v1_es.xml";
    private static final long TTL=24*60*60_000L,RETRY=15*60_000L;
    private static CulturalEvents instance;
    static synchronized CulturalEvents get(Context context) {
        if(instance==null) instance=new CulturalEvents(context.getApplicationContext()); return instance;
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService artistIO=Executors.newSingleThreadExecutor();
    private final ExecutorService recommendationIO=Executors.newSingleThreadExecutor();
    private final CulturalArtistSearch artistSearch;
    private final SharedPreferences prefs;
    private final File directory,art;
    private final Set<Runnable> listeners=new HashSet<>();
    private final Set<String> loading=new HashSet<>();
    private final Map<String,Long> failed=new HashMap<>();
    private volatile Set<String> wantedImages=Collections.emptySet();
    private final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(10*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    CulturalData.Catalog catalog=CulturalData.empty();
    Set<String> selection;
    final CulturalLikes likes;
    private final CulturalRecommendations.History history;
    private long visit,lastVisit;
    private final Map<CulturalData.Hit,CulturalRecommendations.Decision> explanations=new IdentityHashMap<>();
    private final Map<Integer,Long> planning=new HashMap<>();
    private volatile long recommendationGeneration;
    boolean fetching=true;
    String error="";
    private long checkedAt,retryAfter;
    private CulturalData.Catalog filteredCatalog;
    private Set<String> filteredSelection;
    private LocalDate filteredDate;
    private java.time.YearMonth loadedMonth=java.time.YearMonth.now(CulturalData.ZONE);
    private final Map<Integer,List<CulturalData.Hit>> filtered=new HashMap<>();
    private final Runnable poll=this::refresh;
    private CulturalEvents(Context context) {
        directory=new File(context.getCacheDir(),"cultural-events"); art=new File(directory,"images");
        artistSearch=new CulturalArtistSearch(new File(directory,"artists"));
        prefs=context.getSharedPreferences("cultural-events",Context.MODE_PRIVATE);
        likes=CulturalLikes.decode(prefs.getString("likes","{}"));
        history=CulturalRecommendations.History.decode(prefs.getString("recommendation_history","{}"),System.currentTimeMillis());
        visit=prefs.getLong("recommendation_visit",0);lastVisit=prefs.getLong("recommendation_visit_at",0);
        selection=Collections.unmodifiableSet(new HashSet<>(prefs.getStringSet("categories",Collections.singleton(CulturalData.ALL))));
        io.execute(()->{
            CulturalData.Catalog cached=CulturalData.empty();
            try(InputStream in=new AtomicFile(new File(directory,"agenda.xml")).openRead()) {
                cached=CulturalData.monthly(CulturalData.parse(bounded(in,10*1024*1024)),LocalDate.now(CulturalData.ZONE));
                prune(cached);
            } catch(Exception ignored) {}
            final CulturalData.Catalog ready=cached;
            main.post(()->{ catalog=ready; checkedAt=ready.events.isEmpty()?0:prefs.getLong("checked",0); fetching=false; changed(); refresh(); });
        });
    }
    void addListener(Runnable listener) { listeners.add(listener); listener.run(); refresh(); }
    void removeListener(Runnable listener) { listeners.remove(listener); if(listeners.isEmpty()) main.removeCallbacks(poll); }
    private void changed() { for(Runnable listener:new ArrayList<>(listeners)) listener.run(); }
    void select(Set<String> keys) {
        selection=Collections.unmodifiableSet(new HashSet<>(keys));
        prefs.edit().putStringSet("categories",new HashSet<>(keys)).apply(); changed();
    }
    private void invalidateRecommendations(){recommendationGeneration++;filtered.clear();explanations.clear();planning.clear();}
    boolean planning(int tab){return planning.containsKey(tab);}
    void saveLikes() { prefs.edit().putString("likes",likes.encode()).apply();invalidateRecommendations(); changed(); }
    boolean beginVisit() {
        long now=System.currentTimeMillis();if(visit>0 && now>=lastVisit && now-lastVisit<10*60_000L)return false;
        visit++;lastVisit=now;invalidateRecommendations();
        prefs.edit().putLong("recommendation_visit",visit).putLong("recommendation_visit_at",now).apply();return true;
    }
    void recordVisible(List<CulturalData.Hit> visible,String featuredId) {
        long now=System.currentTimeMillis();boolean edited=false;
        for(CulturalData.Hit hit:visible)edited|=history.record(hit.event.id,hit.event.id.equals(featuredId),now);
        if(edited)prefs.edit().putString("recommendation_history",history.encode()).apply();
        // Keep every cached list stable until a later visit/filter refresh; do not reorder while browsing.
    }
    CulturalRecommendations.Decision decision(CulturalData.Hit hit) {
        CulturalRecommendations.Decision saved=explanations.get(hit);return saved!=null?saved:CulturalRecommendations.describe(hit,likes,catalog,false,LocalDate.now(CulturalData.ZONE));
    }
    String reasonLabel(CulturalData.Hit hit) {
        CulturalRecommendations.Decision d=decision(hit);
        switch(d.type){case "artist":return Ui.tr("Followed artist")+": "+d.label;case "subcategory":case "category":return Ui.tr("Because you like")+" "+d.label;
            case "venue":return Ui.tr("Favourite venue")+": "+d.label;case "less":return Ui.tr("Style with lower priority")+(d.label.isEmpty()?"":": "+d.label);default:return Ui.tr(d.discovery?"For discovery":"Within your selected categories");}
    }
    String reasonDetails(CulturalData.Hit hit) {
        CulturalRecommendations.Decision d=decision(hit);StringBuilder text=new StringBuilder(reasonLabel(hit));
        text.append("\n\n").append(Ui.tr("This event matches your selected dates and categories."));
        if(d.discovery)text.append("\n\n").append(Ui.tr("A discovery slot introduces an event outside your favourites, within your allowed categories."));
        if(d.variety)text.append("\n\n").append(Ui.tr("The order also favours variety of styles, venues and recognisable cycles."));
        if(d.rotated)text.append("\n\n").append(Ui.tr("Featured events alternate between several related suggestions when you return."));
        if(d.repeated)text.append("\n\n").append(Ui.tr("You have already seen this event. Its priority is temporarily reduced, without hiding it."));
        if(d.urgent)text.append("\n\n").append(Ui.tr("Its date is close: repetition does not reduce its priority."));
        return text.toString();
    }
    void suggestArtists(CulturalData.Event event,java.util.concurrent.atomic.AtomicBoolean active,java.util.function.Consumer<CulturalArtistSearch.Result> callback) {
        artistIO.execute(()->{
            java.util.function.Consumer<CulturalArtistSearch.Result> publish=result->main.post(()->{if(active.get())callback.accept(result);});
            publish.accept(artistSearch.search(event,active::get,publish));
        });
    }
    List<CulturalData.Hit> results(int tab) {
        LocalDate today=LocalDate.now(CulturalData.ZONE);
        if(filteredCatalog!=catalog || filteredSelection!=selection || !today.equals(filteredDate)) {
            invalidateRecommendations();filteredCatalog=catalog; filteredSelection=selection; filteredDate=today;
        }
        List<CulturalData.Hit> hits=filtered.get(tab);
        if(hits!=null)return hits;
        if(catalog.events.isEmpty() || tab<0 || tab>3){hits=Collections.emptyList();filtered.put(tab,hits);return hits;}
        if(!planning.containsKey(tab)) {
            final long generation=recommendationGeneration,now=System.currentTimeMillis(),serial=visit;
            final CulturalData.Catalog source=catalog;final Set<String> allowed=selection;
            final CulturalLikes preferences=CulturalLikes.decode(likes.encode());final CulturalRecommendations.History seen=history.copy();
            planning.put(tab,generation);
            recommendationIO.execute(()->{
                if(generation!=recommendationGeneration)return;
                CulturalRecommendations.Plan plan=CulturalRecommendations.plan(CulturalData.filter(source,allowed,tab,today),preferences,source,seen,today,now,serial);
                main.post(()->{if(generation!=recommendationGeneration)return;planning.remove(tab);filtered.put(tab,plan.results);explanations.putAll(plan.reasons);changed();});
            });
        }
        return Collections.emptyList();
    }
    void clockChanged() {
        java.time.YearMonth month=java.time.YearMonth.now(CulturalData.ZONE);
        if(!loadedMonth.equals(month)) {
            loadedMonth=month;checkedAt=0;retryAfter=0;
            // Re-read the saved feed for the new month even when the network is offline.
            io.execute(()->{
                CulturalData.Catalog next=CulturalData.monthly(catalog,LocalDate.now(CulturalData.ZONE));
                try(InputStream in=new AtomicFile(new File(directory,"agenda.xml")).openRead()) {
                    next=CulturalData.monthly(CulturalData.parse(bounded(in,10*1024*1024)),LocalDate.now(CulturalData.ZONE));
                } catch(Exception ignored) {}
                prune(next);final CulturalData.Catalog ready=next;
                main.post(()->{if(loadedMonth.equals(month)){catalog=ready;changed();}});
            });
            refresh();
        }
        if(filteredDate!=null && !filteredDate.equals(LocalDate.now(CulturalData.ZONE)))changed();
    }
    void refresh() {
        main.removeCallbacks(poll);
        if(fetching || listeners.isEmpty()) return;
        long now=System.currentTimeMillis(),wait=Math.max(checkedAt+TTL-now,retryAfter-now);
        if(wait>0) { main.postDelayed(poll,Math.min(wait,TTL)); return; }
        fetching=true; changed();
        io.execute(()->{
            CulturalData.Catalog next=null; String problem="";
            try {
                byte[] bytes=download(SOURCE,10*1024*1024);
                next=CulturalData.monthly(CulturalData.parse(bytes),LocalDate.now(CulturalData.ZONE)); directory.mkdirs();
                AtomicFile file=new AtomicFile(new File(directory,"agenda.xml")); FileOutputStream out=null;
                try { out=file.startWrite(); out.write(bytes); file.finishWrite(out); }
                catch(Exception e) { if(out!=null)file.failWrite(out); }
                prune(next);
            } catch(Exception ignored) { problem=Ui.tr("Could not update events. Showing saved information."); }
            final CulturalData.Catalog result=next; final String message=problem;
            main.post(()->{
                fetching=false; error=message;
                if(result!=null) { catalog=result;loadedMonth=java.time.YearMonth.now(CulturalData.ZONE);checkedAt=System.currentTimeMillis(); retryAfter=0; prefs.edit().putLong("checked",checkedAt).apply(); }
                else retryAfter=System.currentTimeMillis()+RETRY;
                changed(); refresh();
            });
        });
    }
    Bitmap image(String url) { return images.get(url); }
    void prepare(List<CulturalData.Hit> items,int first,int count) {
        Set<String> wanted=new HashSet<>();
        for(int i=first;i<items.size() && i<first+count;i++)wanted.add(items.get(i).event.image);
        wantedImages=Collections.unmodifiableSet(wanted);
        for(int i=first;i<items.size() && i<first+count;i++) {
            String url=items.get(i).event.image;
            if(url.isEmpty() || images.get(url)!=null || loading.contains(url)) continue;
            Long failure=failed.get(url); if(failure!=null && SystemClock.elapsedRealtime()-failure<RETRY) continue;
            loading.add(url);
            io.execute(()->{
                if(!wantedImages.contains(url)) { main.post(()->loading.remove(url)); return; }
                Bitmap bitmap=null;
                try {
                    art.mkdirs(); File file=new File(art,key(url)+".img"); byte[] bytes=null;
                    if(file.isFile()) try { bytes=readFile(file,5*1024*1024); bitmap=decode(bytes); } catch(Exception ignored) {}
                    if(bitmap==null) {
                        bytes=download(url,5*1024*1024); bitmap=decode(bytes);
                        if(bitmap!=null) {
                            AtomicFile stored=new AtomicFile(file); FileOutputStream out=null;
                            try { out=stored.startWrite(); out.write(bytes); stored.finishWrite(out); }
                            catch(Exception e) { if(out!=null) stored.failWrite(out); }
                        }
                    }
                    if(file.isFile()) file.setLastModified(System.currentTimeMillis());
                    trimDisk();
                } catch(Exception ignored) {}
                final Bitmap ready=bitmap;
                main.post(()->{
                    loading.remove(url);
                    if(ready!=null) { images.put(url,ready); failed.remove(url); } else failed.put(url,SystemClock.elapsedRealtime());
                    // Do not schedule another batch from image callbacks: a large feed must not fill memory in a loop.
                    changed();
                });
            });
        }
    }
    private static Bitmap decode(byte[] bytes) {
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<2 || options.outHeight<2 || options.outWidth>20000 || options.outHeight>20000) return null;
        options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>720 || options.outHeight/options.inSampleSize>720) options.inSampleSize*=2;
        options.inJustDecodeBounds=false; return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }
    static byte[] download(String url,int limit) throws Exception {
        for(int redirects=0;redirects<4;redirects++) {
            if(!CulturalData.allowed(url,"www.esmadrid.com","esmadrid.com","estaticos.esmadrid.com")) throw new IOException("Unsupported event source");
            HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
            try {
                connection.setConnectTimeout(6000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent","FreeMyTV/1.0 (Madrid cultural agenda)");
                int code=connection.getResponseCode();
                if(code==301 || code==302 || code==303 || code==307 || code==308) {
                    String location=connection.getHeaderField("Location"); if(location==null) throw new IOException("Missing redirect");
                    url=new URL(new URL(url),location).toString(); continue;
                }
                if(code!=200) throw new IOException("Cultural source unavailable");
                try(InputStream in=connection.getInputStream()) { return bounded(in,limit); }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }
    private static byte[] readFile(File file,int limit) throws Exception {
        if(file.length()>limit) throw new IOException("Cache too large");
        try(InputStream in=new FileInputStream(file)) { return bounded(in,limit); }
    }
    private static byte[] bounded(InputStream in,int limit) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
        while((n=in.read(buffer))!=-1) { if(out.size()+n>limit) throw new IOException("Event response too large"); out.write(buffer,0,n); }
        return out.toByteArray();
    }
    private static String key(String value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8")); StringBuilder out=new StringBuilder();
        for(byte b:digest) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString();
    }
    private void prune(CulturalData.Catalog next) {
        Set<String> keep=new HashSet<>();
        for(CulturalData.Event event:next.events) if(!event.image.isEmpty()) try { keep.add(key(event.image)+".img"); } catch(Exception ignored) {}
        File[] files=art.listFiles(); if(files!=null) for(File file:files) if(!keep.contains(file.getName())) file.delete();
        trimDisk();
    }
    private void trimDisk() {
        File[] files=art.listFiles(); if(files==null) return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified)); long bytes=0;
        for(File file:files) bytes+=file.length();
        for(File file:files) if(bytes>24*1024*1024L) { long size=file.length(); if(file.delete())bytes-=size; }
    }
}
