package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Cinema recommendations and public showtimes. All network and image work stays off the UI thread. */
final class Cinema {
    static final int UNKNOWN = 0, LOADING = 1, READY = 2, EMPTY = 3, ERROR = 4;
    private static final String API = "https://kinepolisweb-programmation.kinepolis.com/api/";
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final long SESSION_TTL = 15 * 60_000L;
    static final class Movie {
        int id, corporate, minutes, sortOrder, tmdbId, state = UNKNOWN;
        String title = "", overview = "", image = "", imdb = "", trailer = "", year = "";
        String cinemaImage="";
        List<String> posters=new ArrayList<>();
        boolean artworkReady;
        double rating;
        boolean details, preparing, imageFailed;
        long checkedAt, detailsAt;
        boolean requestKnown, requested, requestBusy;
        long requestCheckedAt;
        List<Session> sessions = Collections.emptyList();
    }
    /** A single atomic snapshot keeps obsolete queued work outside the three-picture memory budget. */
    static final class LoadWindow {
        private static final class Snapshot {
            final Movie current,previous,next;
            Snapshot(Movie c,Movie p,Movie n) { current=c; previous=p; next=n; }
        }
        private volatile Snapshot selected=new Snapshot(null,null,null);
        void set(Movie current,Movie previous,Movie next) {
            Snapshot s=selected;
            if(s.current!=current || s.previous!=previous || s.next!=next) selected=new Snapshot(current,previous,next);
        }
        boolean image(Movie m) { Snapshot s=selected; return m!=null && (m==s.current || m==s.previous || m==s.next); }
        boolean details(Movie m) { Snapshot s=selected; return m!=null && (m==s.current || m==s.next); }
    }
    static final class Session {
        final long time;
        final String hall, language, format;
        final boolean soldOut;
        Session(long t, String h, String l, String f, boolean s) { time=t; hall=h; language=l; format=f; soldOut=s; }
    }
    private static Cinema instance;
    static synchronized Cinema get(Context c) {
        if (instance == null) instance = new Cinema(c.getApplicationContext());
        return instance;
    }
    static boolean enabled() { return !Config.TMDB_KEY.trim().isEmpty(); }
    private final Context context;
    private final SharedPreferences cache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final ExecutorService imageIo = Executors.newSingleThreadExecutor();
    private final ExecutorService warmIo=Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"cinema-preload"); thread.setPriority(Thread.MIN_PRIORITY); return thread;
    });
    private final ConcurrentHashMap<Integer,CinemaMetadata> metadata=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer,Object> metadataLocks=new ConcurrentHashMap<>();
    private final CinemaImageCache diskImages;
    private final Fanart fanart;
    private final ConcurrentHashMap<Integer,ArtworkChoice> artworkChoices=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer,Object> artworkLocks=new ConcurrentHashMap<>();
    private static final class ArtworkChoice {
        final String imdb,primary,source,key;
        final boolean poster;
        final long at=System.currentTimeMillis();
        final File file;
        ArtworkChoice(String imdb,String primary,String source,File file) {
            this(imdb,primary,source,source,file,false);
        }
        ArtworkChoice(String imdb,String primary,String source,String key,File file,boolean poster) {
            this.imdb=imdb; this.primary=primary; this.source=source; this.file=file;
            this.key=key; this.poster=poster;
        }
        boolean matches(Movie m,String source) { return imdb.equals(m.imdb) && (primary.equals(source) || this.source.equals(source)); }
    }
    private final LoadWindow loadWindow=new LoadWindow();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Jellyseerr jellyseerr;
    private final Map<Integer, Backdrop> images = new LinkedHashMap<>(4,0.75f,true);
    private final Set<Integer> loadingImages = new HashSet<>();
    volatile List<Movie> movies = Collections.emptyList();
    private volatile List<Movie> programme=Collections.emptyList();
    String error = "";
    boolean fetching, panelOpen;
    private long listAt, failedAt;
    private long artworkAt;
    private volatile long warmGeneration;
    private final Runnable saveMetadata=this::persistMetadata;

    private Cinema(Context c) {
        context = c;
        diskImages=new CinemaImageCache(new File(c.getCacheDir(),"cinema-images"));
        fanart=new Fanart(Config.FANART_KEY,new File(c.getFilesDir(),"cinema-fanart.json"));
        jellyseerr = new Jellyseerr(c, this::changed);
        cache = c.getSharedPreferences("cinema", Context.MODE_PRIVATE);
        try {
            JSONArray saved=new JSONArray(cache.getString("filmMetadata","[]"));
            for(int i=0;i<saved.length();i++) {
                JSONObject record=saved.optJSONObject(i); if(record==null) continue;
                CinemaMetadata entry=CinemaMetadata.decode(record);
                if(entry.id!=0) metadata.put(entry.id,entry);
            }
        } catch(Exception ignored) { }
        try {
            listAt = cache.getLong("billboardAt", 0);
            if(System.currentTimeMillis()-listAt<24*60*60_000L)
                programme = CinemaBillboard.decode(new JSONArray(cache.getString("billboard", "[]")),System.currentTimeMillis());
            if(programme.isEmpty() && listAt>0 && System.currentTimeMillis()-listAt<SESSION_TTL)
                error=Ui.tr("No upcoming movies at your cinema.");
        } catch (Exception ignored) { }
        for(Movie movie:programme) restoreMetadata(movie);
        if(listAt>0) preloadProgramme();
    }
    void addListener(Runnable l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Runnable l) { listeners.remove(l); }
    private void changed() { for (Runnable l : new ArrayList<>(listeners)) l.run(); }
    void prepareRequest(Movie m) { if(m!=null) jellyseerr.refresh(m); }
    void requestMovie(Movie m) { if(m!=null) jellyseerr.request(m); }
    void focus(Movie current,Movie previous,Movie next) { loadWindow.set(current,previous,next); }

    void refresh() {
        long now = System.currentTimeMillis();
        boolean expired=false;
        for(Movie m:programme) if(!CinemaBillboard.scheduled(m,now)) { expired=true; break; }
        if(expired) {
            List<Movie> kept=new ArrayList<>();
            for(Movie m:programme) if(CinemaBillboard.scheduled(m,now)) kept.add(m);
            programme=kept; movies=visibleMovies(kept); warmGeneration++; changed(); preloadProgramme();
        }
        if (!enabled() || fetching || (now >= failedAt && now - failedAt < 60_000L)) return;
        if (listAt>0 && now >= listAt && now - listAt < SESSION_TTL) return;
        fetching = true;
        io.execute(() -> {
            try {
                List<Movie> loaded = CinemaBillboard.load(System.currentTimeMillis());
                for(Movie movie:loaded) restoreMetadata(movie);
                for(Movie movie:loaded) {
                    ArtworkChoice choice=artworkChoices.get(movie.id);
                    if(choice!=null && choice.matches(movie,movie.image) && readable(choice.file)) {
                        movie.image=choice.source; movie.artworkReady=true;
                    }
                }
                cache.edit().putString("billboard", CinemaBillboard.encode(loaded).toString())
                        .putLong("billboardAt", System.currentTimeMillis()).apply();
                main.post(() -> {
                    programme=loaded; movies = visibleMovies(loaded); listAt = System.currentTimeMillis();
                    error = loaded.isEmpty()?Ui.tr("No upcoming movies at your cinema."):"";
                    failedAt = 0; fetching = false; changed();
                    preloadProgramme();
                    AppLog.w("cinema: loaded " + loaded.size() + " Ciudad de la Imagen programme titles");
                });
            } catch (Exception e) {
                main.post(() -> { error = Ui.tr("Couldn't update cinema movies."); failedAt = System.currentTimeMillis(); fetching = false;
                    if(!programme.isEmpty() && System.currentTimeMillis()-artworkAt>=SESSION_TTL) preloadProgramme(); changed(); });
                AppLog.w("cinema: movie request failed (" + e.getClass().getSimpleName() + ")");
            }
        });
    }

    static List<Movie> parseMovies(JSONArray array) {
        List<Movie> out = new ArrayList<>();
        for (int i=0; i<array.length() && out.size()<8; i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null || o.optString("backdrop_path").isEmpty() || o.isNull("backdrop_path") || o.optBoolean("adult")) continue;
            if (Config.HIDE_ANIMATION) {
                JSONArray genres = o.optJSONArray("genre_ids");
                boolean animation = false;
                for (int g=0; genres!=null && g<genres.length(); g++) animation |= genres.optInt(g)==16;
                if (animation) continue;
            }
            Movie m = new Movie(); m.id=o.optInt("id"); m.title=o.optString("title");
            m.overview=o.optString("overview"); m.image=o.optString("backdrop_path"); m.rating=o.optDouble("vote_average");
            String date=o.optString("release_date"); m.year=date.length()>=4 ? date.substring(0,4) : "";
            if (m.id>0) out.add(m);
        }
        return out;
    }

    static List<Movie> visibleMovies(List<Movie> programme) {
        List<Movie> visible=new ArrayList<>();
        for(Movie movie:programme) if(movie.artworkReady) visible.add(movie);
        return visible;
    }

    void prepare(Movie m) {
        if(!loadWindow.details(m)) return;
        image(m);
        long now=System.currentTimeMillis();
        if (m.state==READY && available(m).isEmpty()) m.state=EMPTY;
        boolean sessionsFresh=m.checkedAt>0 && now>=m.checkedAt && now-m.checkedAt<SESSION_TTL;
        CinemaMetadata saved=metadata.get(m.id);
        boolean wantDetails=(!m.details || saved==null || !saved.matches(m.id,m.imdb) || !saved.fresh(now))
                && (m.detailsAt==0 || now-m.detailsAt>=60_000);
        if (m.preparing || (sessionsFresh && !wantDetails)) return;
        if(wantDetails) m.detailsAt=now;
        m.preparing=true; if(m.state!=READY) m.state=LOADING;
        main.post(this::changed);
        io.execute(() -> {
            if(!loadWindow.details(m)) { cancelPrepare(m); return; }
            // Optional TMDB enrichment must not prevent the cinema's own sessions from loading.
            if(wantDetails) try { enrich(m); }
            catch(Exception e) { AppLog.w("cinema: optional film details unavailable"); }
            if(!loadWindow.details(m)) { cancelPrepare(m); return; }
            if(sessionsFresh) { main.post(()->{ m.preparing=false; changed(); }); return; }
            try {
                int corporate=m.corporate;
                if(corporate<=0) {
                    JSONArray films=searchFilms(m.title);
                    if(films==null) throw new IOException("Unexpected cinema search response");
                    corporate=matchFilm(films,m.imdb,m.title,m.year);
                }
                List<Session> sessions=Collections.emptyList();
                if (corporate>0) {
                    sessions=loadSessions(corporate,System.currentTimeMillis());
                }
                final List<Session> found=sessions;
                main.post(() -> { m.sessions=found; m.state=found.isEmpty()?EMPTY:READY; m.checkedAt=System.currentTimeMillis(); m.preparing=false; changed();
                    AppLog.w("cinema: movie " + m.id + ", upcoming Ciudad de la Imagen sessions=" + found.size()); });
            } catch (Exception e) {
                main.post(() -> { m.state=ERROR; m.checkedAt=System.currentTimeMillis()-SESSION_TTL+60_000L; m.preparing=false; changed(); });
                AppLog.w("cinema: showtime request failed ("+e.getClass().getSimpleName()+")");
            }
        });
    }
    private void cancelPrepare(Movie m) {
        main.post(()->{ m.preparing=false; m.detailsAt=0; if(m.state==LOADING) m.state=UNKNOWN; changed(); });
    }

    private void enrich(Movie m) throws Exception {
        CinemaMetadata entry=resolveMetadata(m);
        main.post(()->{
            if(loadWindow.details(m) && movies.contains(m)) applyMetadata(m,entry);
            else m.detailsAt=0;
            queueMetadataSave(); changed();
        });
    }
    private CinemaMetadata resolveMetadata(Movie m) throws Exception {
        synchronized(metadataLocks.computeIfAbsent(m.id,id->new Object())) {
            CinemaMetadata saved=metadata.get(m.id);
            if(saved!=null && saved.matches(m.id,m.imdb) && saved.fresh(System.currentTimeMillis())) return saved;
            CinemaMetadata loaded=loadMetadata(m);
            metadata.put(m.id,loaded); return loaded;
        }
    }
    private CinemaMetadata loadMetadata(Movie m) throws Exception {
        int tmdbId=m.corporate>0?0:m.id;
        if(m.corporate>0 && m.imdb.matches("tt[0-9]+")) {
            JSONArray found=tmdb("/find/"+m.imdb+"?external_source=imdb_id&language=es-ES").optJSONArray("movie_results");
            if(found!=null && found.length()>0) tmdbId=found.getJSONObject(0).optInt("id");
        }
        if(tmdbId<=0) return new CinemaMetadata(m.id,m.imdb,0,0,"","","",0,System.currentTimeMillis());
        JSONObject detail=tmdb("/movie/"+tmdbId+"?language=es-ES&append_to_response=videos");
        String video=trailer(detail.optJSONObject("videos"));
        if(video.isEmpty()) try { video=trailer(tmdb("/movie/"+tmdbId+"/videos?language=en-US")); } catch(Exception ignored) { }
        return new CinemaMetadata(m.id,m.imdb,tmdbId,detail.optInt("runtime"),detail.optString("overview"),
                detail.optString("backdrop_path",""),video,detail.optDouble("vote_average",0),System.currentTimeMillis())
                .withPosters(CinemaPoster.candidates(null,detail.optString("poster_path")),false);
    }
    private void restoreMetadata(Movie m) {
        CinemaMetadata entry=metadata.get(m.id);
        if(entry!=null && entry.matches(m.id,m.imdb)) applyMetadata(m,entry);
    }
    private void applyMetadata(Movie m,CinemaMetadata entry) {
        if(!entry.matches(m.id,m.imdb)) return;
        m.tmdbId=entry.tmdbId; m.trailer=entry.trailer; if(entry.minutes>0) m.minutes=entry.minutes; m.rating=entry.rating;
        ArtworkChoice choice=artworkChoices.get(m.id);
        String artwork=choice!=null && choice.matches(m,entry.image)?choice.source:entry.image;
        if(!m.artworkReady && !artwork.isEmpty() && !artwork.equals(m.image)) {
            m.image=artwork; m.imageFailed=false; synchronized(images) { images.remove(m.id); }
        }
        if(!entry.overview.isEmpty()) m.overview=entry.overview;
        m.details=entry.fresh(System.currentTimeMillis());
    }
    private void queueMetadataSave() {
        main.removeCallbacks(saveMetadata); main.postDelayed(saveMetadata,500);
    }
    private void persistMetadata() {
        try {
            JSONArray saved=new JSONArray();
            for(Movie m:programme) {
                CinemaMetadata entry=metadata.get(m.id);
                if(entry!=null && entry.matches(m.id,m.imdb)) saved.put(entry.encode());
            }
            cache.edit().putString("filmMetadata",saved.toString()).apply();
        } catch(Exception ignored) { }
    }
    private Map<Integer,Set<String>> artworkSources(List<Movie> programme) {
        Map<Integer,Set<String>> result=new HashMap<>();
        for(Movie m:programme) {
            CinemaMetadata entry=metadata.get(m.id);
            String source=entry!=null && entry.matches(m.id,m.imdb) && !entry.image.isEmpty()?entry.image:m.image;
            ArtworkChoice choice=artworkChoices.get(m.id);
            Set<String> keep=new HashSet<>();
            if(choice!=null && choice.matches(m,source)) { keep.add(choice.key); if(choice.poster) keep.add(choice.source); }
            else keep.add(source);
            result.put(m.id,keep);
        }
        return result;
    }
    private void preloadProgramme() {
        artworkAt=System.currentTimeMillis();
        final long generation=++warmGeneration;
        final List<Movie> programme=this.programme;
        Set<Integer> ids=new HashSet<>(); for(Movie m:programme) ids.add(m.id);
        metadata.keySet().retainAll(ids); queueMetadataSave();
        artworkChoices.keySet().retainAll(ids);
        warmIo.execute(()->{
            if(generation!=warmGeneration) return;
            Set<String> identities=new HashSet<>();
            for(Movie movie:programme) {
                CinemaMetadata entry=metadata.get(movie.id);
                identities.add(Fanart.identity(entry!=null && entry.matches(movie.id,movie.imdb)?entry.tmdbId:0,movie.imdb));
            }
            fanart.retain(identities);
            boolean checked=listAt>0 && System.currentTimeMillis()-listAt<SESSION_TTL;
            if(checked) diskImages.reconcileSources(artworkSources(programme));
            for(Movie m:programme) {
                if(generation!=warmGeneration) return;
                try {
                    // Let visible artwork finish before spending bandwidth on the rest of the programme.
                    while(generation==warmGeneration) {
                        boolean visibleBusy; synchronized(images) { visibleBusy=!loadingImages.isEmpty(); }
                        if(!visibleBusy) break;
                        Thread.sleep(150);
                    }
                    if(generation!=warmGeneration) return;
                    CinemaMetadata entry;
                    try { entry=resolveMetadata(m); }
                    catch(Exception unavailable) { entry=metadata.get(m.id); }
                    if(generation!=warmGeneration) return;
                    final CinemaMetadata resolved=entry!=null && entry.matches(m.id,m.imdb)?entry:null;
                    if(resolved!=null) main.post(()->{ if(generation==warmGeneration && this.programme==programme) {
                        applyMetadata(m,resolved); queueMetadataSave(); changed();
                    } });
                    String source=resolved!=null && !resolved.image.isEmpty()?resolved.image:m.image;
                    ArtworkChoice artwork=ensureArtwork(m,source);
                    main.post(()->{ if(generation==warmGeneration && this.programme==programme) {
                        if(!artwork.source.equals(m.image)) { m.image=artwork.source; synchronized(images) { images.remove(m.id); } }
                        m.artworkReady=true; m.imageFailed=false; movies=visibleMovies(programme); error=""; changed();
                    } });
                } catch(Exception ignored) {
                    main.post(()->{ if(generation==warmGeneration && this.programme==programme) {
                        boolean cached; synchronized(images) { cached=images.containsKey(m.id); }
                        if(!cached) { m.artworkReady=false; movies=visibleMovies(programme); changed(); }
                    } });
                }
            }
            if(generation==warmGeneration && checked) diskImages.reconcileSources(artworkSources(programme));
            main.post(()->{ if(generation==warmGeneration && this.programme==programme && !programme.isEmpty() && movies.isEmpty()) {
                error=Ui.tr("No cinema images available yet."); changed();
            } });
        });
    }

    private ArtworkChoice ensureArtwork(Movie movie,String primary) throws Exception {
        synchronized(artworkLocks.computeIfAbsent(movie.id,id->new Object())) {
            CinemaMetadata entry=metadata.get(movie.id);
            if(entry!=null && entry.matches(movie.id,movie.imdb) && !entry.image.isEmpty()) primary=entry.image;
            else if(!movie.cinemaImage.isEmpty()) primary=movie.cinemaImage;
            ArtworkChoice saved=artworkChoices.get(movie.id);
            if(saved!=null && saved.matches(movie,primary) && (!saved.poster || System.currentTimeMillis()-saved.at<SESSION_TTL)) {
                try {
                    if(saved.poster && readable(saved.file)) return saved;
                    if(!saved.poster) {
                        File file=diskImages.ensure(movie.id,saved.source,Cinema::downloadArtwork);
                        if(!readable(file)) { file.delete(); throw new IOException("Invalid cached artwork"); }
                        return saved;
                    }
                }
                catch(Exception unavailable) { artworkChoices.remove(movie.id,saved); }
            }
            int tmdbId=entry!=null && entry.matches(movie.id,movie.imdb)?entry.tmdbId:movie.tmdbId;
            // TMDB remains first; without a TMDB backdrop, try Fanart before the cinema's own fallback.
            String alternate="";
            if(!primary.startsWith("/")) try { alternate=fanart.background(tmdbId,movie.imdb); } catch(Exception ignored) { }
            if(!alternate.isEmpty() && !alternate.equals(primary)) {
                try { return rememberArtwork(movie,primary,alternate); } catch(Exception ignored) { }
            }
            try { return rememberArtwork(movie,primary,primary); }
            catch(Exception primaryFailure) {
                if(primary.startsWith("/")) try { alternate=fanart.background(tmdbId,movie.imdb); } catch(Exception ignored) { }
                if(!alternate.isEmpty() && !alternate.equals(primary)) try { return rememberArtwork(movie,primary,alternate); } catch(Exception ignored) { }
                if(!movie.cinemaImage.isEmpty() && !movie.cinemaImage.equals(primary))
                    try { return rememberArtwork(movie,primary,movie.cinemaImage); } catch(Exception ignored) { }
                return posterArtwork(movie,primary);
            }
        }
    }
    private ArtworkChoice rememberArtwork(Movie movie,String primary,String source) throws Exception {
        File file=diskImages.ensure(movie.id,source,Cinema::downloadArtwork);
        if(!readable(file)) throw new IOException("Undecodable artwork");
        BitmapFactory.Options bounds=bounds(file);
        if(bounds.outWidth<=0 || bounds.outHeight<=0) { file.delete(); throw new IOException("Invalid artwork"); }
        if(bounds.outWidth<bounds.outHeight*1.2f) throw new IOException("Portrait artwork needs composition");
        ArtworkChoice choice=new ArtworkChoice(movie.imdb,primary,source,file);
        artworkChoices.put(movie.id,choice); return choice;
    }
    private static BitmapFactory.Options bounds(File file) {
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),bounds); return bounds;
    }
    private static boolean readable(File file) {
        BitmapFactory.Options options=bounds(file);
        if(options.outWidth<=0 || options.outHeight<=0) { file.delete();return false; }
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>128) options.inSampleSize*=2;
        Bitmap sample=BitmapFactory.decodeFile(file.getPath(),options);
        if(sample==null) { file.delete();return false; }
        boolean usable;
        try {
            int[] pixels=new int[sample.getWidth()*sample.getHeight()];
            sample.getPixels(pixels,0,sample.getWidth(),0,0,sample.getWidth(),sample.getHeight());
            usable=CinemaArtwork.usable(options.outWidth,options.outHeight,pixels);
        } finally { sample.recycle(); }
        if(!usable) file.delete();return usable;
    }
    private ArtworkChoice posterArtwork(Movie movie,String primary) throws Exception {
        CinemaMetadata entry=metadata.get(movie.id);
        if(entry!=null && entry.matches(movie.id,movie.imdb) && entry.tmdbId>0 && !entry.postersChecked) {
            try {
                JSONObject images=tmdb("/movie/"+entry.tmdbId+"/images");
                entry=entry.withPosters(CinemaPoster.candidates(images.optJSONArray("posters"),entry.posters.isEmpty()?"":entry.posters.get(0)),true);
                metadata.put(movie.id,entry); main.post(this::queueMetadataSave);
            } catch(Exception unavailable) { /* Keep the cached default poster and cinema candidates. */ }
        }
        List<String> candidates=new ArrayList<>(movie.posters);
        // On older installations, the programme's image field could contain a poster rather than a backdrop.
        if(!movie.cinemaImage.isEmpty() && !candidates.contains(movie.cinemaImage)) candidates.add(movie.cinemaImage);
        File best=null; String selected=""; long area=0;
        if(entry!=null && entry.matches(movie.id,movie.imdb)) {
            for(String path:entry.posters) {
                String source="https://image.tmdb.org/t/p/original"+path;
                try {
                    File file=diskImages.ensure(movie.id,source,Cinema::downloadArtwork);
                    if(!readable(file)) continue;
                    BitmapFactory.Options b=bounds(file);
                    if(b.outWidth<=0 || b.outHeight<=0) continue;
                    best=file; selected=source; area=(long)b.outWidth*b.outHeight;
                    break; // TMDB candidates are ordered by native pixel area, not popularity.
                } catch(Exception unavailable) { }
            }
        }
        for(String source:candidates) try {
            File file=diskImages.ensure(movie.id,source,Cinema::downloadArtwork);
            if(!readable(file)) continue;
            BitmapFactory.Options b=bounds(file);
            long pixels=(long)b.outWidth*b.outHeight;
            if(b.outWidth>0 && b.outHeight>0 && pixels>area) { area=pixels; best=file; selected=source; }
        } catch(Exception unavailable) { }
        if(best==null) throw new IOException("No downloadable cinema artwork");
        final File raw=best;
        String key=CinemaPoster.key(selected,Backdrop.W,Backdrop.H);
        File composition=diskImages.ensure(movie.id,key,(source,destination)->Backdrop.poster(raw,destination));
        if(!readable(composition)) throw new IOException("Invalid poster composition");
        ArtworkChoice choice=new ArtworkChoice(movie.imdb,primary,selected,key,composition,true);
        artworkChoices.put(movie.id,choice); return choice;
    }

    static int matchFilm(JSONArray films,String imdb,String title,String year) {
        for (int i=0; films!=null && i<films.length(); i++) {
            JSONObject f=films.optJSONObject(i); if (f==null) continue;
            if (f.optJSONObject("data")!=null) f=f.optJSONObject("data");
            String candidate=f.optString("imdbCode");
            if (!imdb.isEmpty() && imdb.equals(candidate)) return f.optInt("corporateId");
            // Never override conflicting IMDb IDs with a title match (remakes can have identical titles).
            if (!imdb.isEmpty() && !candidate.isEmpty()) continue;
            if (normalize(title).equals(normalize(f.optString("title"))) && !year.isEmpty()
                    && f.optString("releaseDate").startsWith(year)) return f.optInt("corporateId");
        }
        return 0;
    }
    private static String normalize(String s) { return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]",""); }
    /** The search payload repeats full film metadata in every session: skip it instead of retaining megabytes. */
    static JSONArray searchFilms(String title) throws Exception {
        HttpURLConnection c=connection(API+"search/ES/ES/WWW?search="+URLEncoder.encode(title,"UTF-8"));
        try(JsonReader reader=jsonReader(c)) {
            JSONArray films=null;
            if(reader.peek()==android.util.JsonToken.BEGIN_ARRAY) {
                films=new JSONArray(); reader.beginArray();
                while(reader.hasNext()) films.put(readJson(reader));
                reader.endArray();
                // Current Kinépolis search returns HO film codes; the public Details endpoint supplies their identities.
                JSONArray expanded=new JSONArray();
                for(int i=0;i<films.length() && i<16;i++) {
                    Object value=films.get(i);
                    if(value instanceof JSONObject) expanded.put(value);
                    else {
                        String code=String.valueOf(value);
                        if(!code.matches("HO[0-9]+")) throw new IOException("Unexpected film code");
                        expanded.put(new JSONObject(http(API+"Details/ES/ES/"+code+"/WWW",null)));
                    }
                }
                return expanded;
            }
            reader.beginObject();
            while(reader.hasNext()) {
                if("films".equals(reader.nextName())) {
                    films=new JSONArray(); reader.beginArray();
                    while(reader.hasNext()) {
                        JSONObject film=new JSONObject(); reader.beginObject();
                        while(reader.hasNext()) {
                            String key=reader.nextName();
                            if("corporateId".equals(key)) film.put(key,reader.nextInt());
                            else if("imdbCode".equals(key)||"title".equals(key)||"releaseDate".equals(key)) {
                                if(reader.peek()==android.util.JsonToken.NULL) reader.nextNull(); else film.put(key,reader.nextString());
                            } else reader.skipValue();
                        }
                        reader.endObject(); films.put(film);
                    }
                    reader.endArray();
                } else reader.skipValue();
            }
            reader.endObject();
            if(films==null) throw new IOException("Missing films");
            return films;
        } finally { c.disconnect(); }
    }
    static List<Session> loadSessions(int corporate,long now) throws Exception {
        HttpURLConnection c=connection(API+"Sessions/ES/ES/"+corporate+"/WWW/Cinema/KinepolisSpain");
        List<Session> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        try(JsonReader reader=jsonReader(c)) {
            reader.beginArray();
            while(reader.hasNext()) {
                // Read one session at a time; film metadata no longer accumulates across hundreds of sessions.
                JSONObject row=(JSONObject)readJson(reader);
                List<Session> parsed=parseSessions(new JSONArray().put(row),now);
                for(Session session:parsed) if(seen.add(session.time+"/"+session.hall+"/"+session.format)) out.add(session);
            }
            reader.endArray();
            out.sort(Comparator.comparingLong(s->s.time));
            return out;
        } finally { c.disconnect(); }
    }
    static Object readJson(JsonReader r) throws Exception {
        switch(r.peek()) {
            case BEGIN_OBJECT: {
                JSONObject o=new JSONObject(); r.beginObject();
                while(r.hasNext()) { String key=r.nextName(); o.put(key,readJson(r)); }
                r.endObject(); return o;
            }
            case BEGIN_ARRAY: {
                JSONArray a=new JSONArray(); r.beginArray(); while(r.hasNext()) a.put(readJson(r)); r.endArray(); return a;
            }
            case NULL: r.nextNull(); return JSONObject.NULL;
            case BOOLEAN: return r.nextBoolean();
            default: return r.nextString();
        }
    }
    private static HttpURLConnection connection(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(18000); c.setRequestProperty("Accept","application/json");
        return c;
    }
    private static JsonReader jsonReader(HttpURLConnection c) throws Exception {
        if(c.getResponseCode()!=200) { c.disconnect(); throw new IOException("Cinema HTTP error"); }
        InputStream bounded=new FilterInputStream(c.getInputStream()) {
            int total;
            private void count(int n) throws IOException { if(n>0 && (total+=n)>32*1024*1024) throw new IOException("Response limit"); }
            @Override public int read() throws IOException { int n=super.read(); count(n==-1?0:1); return n; }
            @Override public int read(byte[] b,int o,int l) throws IOException { int n=in.read(b,o,l); count(n); return n; }
        };
        return new JsonReader(new InputStreamReader(bounded,"UTF-8"));
    }
    static List<Session> parseSessions(JSONArray array,long now) {
        List<Session> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        boolean invalid=false;
        for (int i=0;i<array.length();i++) {
            JSONObject s=array.optJSONObject(i);
            if (s==null || !"KMAD".equals(s.optString("complexOperator")) || !s.optBoolean("isPublicScreening",true)) continue;
            try {
                long when=OffsetDateTime.parse(s.getString("showtime")).toInstant().toEpochMilli();
                if (when<=now) continue;
                JSONObject film=s.optJSONObject("film"), data=film==null?null:film.optJSONObject("data");
                JSONObject language=data==null?null:data.optJSONObject("spokenLanguage"), format=film==null?null:film.optJSONObject("format");
                String lang=language==null?"":language.optString("name");
                JSONArray subtitles=data==null?null:data.optJSONArray("subtitles");
                for (int j=0;subtitles!=null && j<subtitles.length();j++) {
                    JSONObject sub=subtitles.optJSONObject(j);
                    if (sub!=null && !"NoSub".equals(sub.optString("code"))) lang+=" · "+sub.optString("name");
                }
                String hall=s.optString("hall"), fmt=format==null?"":format.optString("name");
                if (seen.add(when+"/"+hall+"/"+fmt)) out.add(new Session(when,hall,lang,fmt,s.optBoolean("isSoldOut")));
            } catch (Exception ignored) { invalid=true; }
        }
        if(invalid && out.isEmpty()) throw new IllegalArgumentException("Invalid cinema session data");
        out.sort(Comparator.comparingLong(s->s.time));
        return out;
    }
    static List<Session> available(Movie m) {
        List<Session> out=new ArrayList<>(); long now=System.currentTimeMillis();
        for (Session s:m.sessions) if (s.time>now) out.add(s);
        return out;
    }
    static String trailer(JSONObject o) {
        JSONArray list=o==null?null:o.optJSONArray("results"); String fallback="";
        for(int i=0;list!=null && i<list.length();i++) {
            JSONObject v=list.optJSONObject(i);
            if(v==null || !"YouTube".equals(v.optString("site")) || !"Trailer".equals(v.optString("type"))) continue;
            String key=v.optString("key"); if(!key.matches("[A-Za-z0-9_-]{11}")) continue;
            if(v.optBoolean("official")) return key; fallback=key;
        }
        return fallback;
    }

    Backdrop image(Movie m) {
        if(!loadWindow.image(m) || m.imageFailed) return null;
        synchronized(images) {
            if(images.containsKey(m.id)) return images.get(m.id);
            if(!loadingImages.add(m.id)) return null;
        }
        final String imageSource=m.image;
        imageIo.execute(() -> {
            Backdrop loaded=null;
            String loadedSource=imageSource;
            boolean failed=false;
            try {
                if(!loadWindow.image(m) || !imageSource.equals(m.image)) return;
                // Reuse the prepared file immediately; optional source refresh stays in the background preloader.
                ArtworkChoice artwork=artworkChoices.get(m.id);
                if(artwork==null || !artwork.matches(m,imageSource) || !readable(artwork.file)) artwork=ensureArtwork(m,imageSource);
                File file=artwork.file; loadedSource=artwork.source;
                if(!loadWindow.image(m) || !imageSource.equals(m.image)) return;
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(file.getPath(),options); options.inJustDecodeBounds=false; options.inSampleSize=1;
                while(options.outWidth/(options.inSampleSize*2)>=Backdrop.W) options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);
                if(bitmap==null) { file.delete(); throw new IOException("Invalid image"); }
                if(!loadWindow.image(m) || !imageSource.equals(m.image)) { bitmap.recycle(); return; }
                loaded=Backdrop.make(bitmap,false);
            } catch(Exception | OutOfMemoryError e) {
                failed=true;
            }
            finally {
                final Backdrop found=loaded; final boolean error=failed; final String actualSource=loadedSource;
                main.post(()->{
                    synchronized(images) {
                        loadingImages.remove(m.id);
                        if(loadWindow.image(m) && imageSource.equals(m.image)) {
                            if(found!=null) { m.image=actualSource; images.put(m.id,found); while(images.size()>3) images.remove(images.keySet().iterator().next()); }
                            else if(error) {
                                m.imageFailed=true; m.artworkReady=false; movies=visibleMovies(programme);
                            }
                        }
                    }
                    changed();
                });
            }
        });
        return null;
    }

    static String artworkUrl(String source) throws URISyntaxException {
        String url;
        if(source.startsWith("https://cdn.kinepolis.es/") || Fanart.allowed(source)
                || source.startsWith("https://image.tmdb.org/t/p/original/") && CinemaPoster.path(source.substring("https://image.tmdb.org/t/p/original".length()))) url=source;
        else if(source.startsWith("/") && !source.startsWith("//")) url="https://image.tmdb.org/t/p/w1280"+source;
        else throw new URISyntaxException("artwork","Unsupported source");
        return new URI(url.replace(" ","%20")).toASCIIString();
    }

    private static void downloadArtwork(String source,File destination) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(artworkUrl(source)).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(8000);
        try {
            if(connection.getResponseCode()!=200) throw new IOException("Artwork HTTP error");
            try(InputStream in=connection.getInputStream(); OutputStream out=new FileOutputStream(destination)) {
                copy(in,out,8*1024*1024);
            }
            if(!readable(destination)) throw new IOException("Invalid artwork");
        } finally { connection.disconnect(); }
    }

    void openTrailer(Activity a,Movie m) {
        if(m.trailer.isEmpty()) return;
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+m.trailer));
        TrailerReturn.arm(a,"com.google.android.youtube.tv");
        try { a.startActivity(new Intent(intent).setPackage("com.google.android.youtube.tv")); }
        catch(Exception e) { TrailerReturn.clear(a); try { a.startActivity(intent); } catch(Exception ignored) { new AlertDialog.Builder(a).setMessage(Ui.tr("Couldn't open the trailer.")).setPositiveButton(android.R.string.ok,null).show(); } }
    }
    void openSessions(Activity a,Movie m) {
        List<Session> upcoming=available(m);
        if(m.state!=READY || upcoming.isEmpty()) return;
        CinemaSessions dialog=new CinemaSessions(a,m,upcoming,image(m));
        panelOpen=true;
        dialog.setOnDismissListener(d->{panelOpen=false; changed();});
        dialog.show();
    }

    private static JSONObject tmdb(String path) throws Exception {
        String key=Config.TMDB_KEY.trim(); boolean bearer=key.startsWith("eyJ");
        String url="https://api.themoviedb.org/3"+path+(bearer?"":(path.contains("?")?"&":"?")+"api_key="+URLEncoder.encode(key,"UTF-8"));
        return new JSONObject(http(url,bearer?key:null));
    }
    private static String http(String url,String bearer) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(18000); c.setRequestProperty("Accept","application/json");
        if(bearer!=null) c.setRequestProperty("Authorization","Bearer "+bearer);
        try { if(c.getResponseCode()!=200) throw new IOException("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) { copy(in,out,16*1024*1024); return out.toString("UTF-8"); }
        } finally { c.disconnect(); }
    }
    private static void copy(InputStream in,OutputStream out,int max) throws IOException {
        byte[] b=new byte[8192]; int n,total=0;
        while((n=in.read(b))!=-1) { total+=n; if(total>max) throw new IOException("Response limit"); out.write(b,0,n); }
    }
}
