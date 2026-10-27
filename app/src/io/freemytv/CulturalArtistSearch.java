package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;
import org.json.*;

/** Used only from a single worker. Bounded persistent query cache, cancellation and rate limiting. */
final class CulturalArtistSearch {
    interface Clock { long now();void sleep(long millis) throws InterruptedException; }
    interface Transport { String get(String query) throws IOException; }
    static final class Result {
        final List<CulturalArtists.Artist> artists;final boolean incomplete,done;
        Result(Collection<CulturalArtists.Artist> artists,boolean incomplete,boolean done){this.artists=Collections.unmodifiableList(new ArrayList<>(artists));this.incomplete=incomplete;this.done=done;}
    }
    private static final long DAY=86_400_000L;
    private final File directory;private final Clock clock;private final Transport transport;
    private long nextRequest,cooldown;
    CulturalArtistSearch(File directory){this(directory,new Clock(){public long now(){return System.currentTimeMillis();}public void sleep(long millis)throws InterruptedException{Thread.sleep(millis);}},CulturalArtistSearch::download);}
    CulturalArtistSearch(File directory,Clock clock,Transport transport){this.directory=directory;this.clock=clock;this.transport=transport;}
    Result search(CulturalData.Event event,BooleanSupplier active,Consumer<Result> progress) {
        LinkedHashMap<String,CulturalArtists.Artist> found=new LinkedHashMap<>();boolean incomplete=false;
        for(String candidate:CulturalArtists.candidates(event)) {
            if(!active.getAsBoolean())break;
            File file=new File(directory,key(candidate)+".json");String body=null,stale=null;long expires=0;
            try {
                if(file.length()>0 && file.length()<300_000){JSONObject cached=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
                    stale=cached.getString("body");CulturalArtists.parse(stale,candidate,event);expires=cached.getLong("expires");if(expires>clock.now())body=stale;}
            }catch(Exception ignored){stale=null;body=null;file.delete();}
            if(body==null && clock.now()>=cooldown)try {
                long wait=nextRequest-clock.now();if(wait>0)clock.sleep(wait);
                if(!active.getAsBoolean())break;
                nextRequest=clock.now()+2100;
                body=transport.get(CulturalArtists.query(candidate));
                // Validate before caching. An empty valid response is cached too; failures never become negatives.
                List<CulturalArtists.Artist> checked=CulturalArtists.parse(body,candidate,event);
                write(file,body,clock.now()+(checked.isEmpty()?7:30)*DAY);
            }catch(Exception ignored){if(ignored instanceof InterruptedException)Thread.currentThread().interrupt();cooldown=clock.now()+(ignored instanceof RetryLater?Math.max(15*60_000L,((RetryLater)ignored).waitMillis):15*60_000L);incomplete=true;}
            if(body==null){body=stale;incomplete=true;}
            if(body!=null)try{for(CulturalArtists.Artist artist:CulturalArtists.parse(body,candidate,event))found.putIfAbsent(artist.id,artist);}catch(Exception ignored){incomplete=true;}
            if(active.getAsBoolean())progress.accept(new Result(found.values(),incomplete,false));
        }
        return new Result(found.values(),incomplete,true);
    }
    private void write(File file,String body,long expires) {
        try {
            directory.mkdirs();File temp=new File(directory,file.getName()+".tmp");
            byte[] bytes=new JSONObject().put("expires",expires).put("body",body).toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>300_000)return;
            Files.write(temp.toPath(),bytes);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            File[] files=directory.listFiles((dir,name)->name.endsWith(".json"));if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File entry:files)total+=entry.length();
                for(int i=0;i<files.length && (i<files.length-128 || total>4*1024*1024L);i++){long size=files[i].length();if(files[i].delete())total-=size;}}
        }catch(Exception ignored){}
    }
    private static final class RetryLater extends IOException {
        final long waitMillis;RetryLater(int code,long waitMillis){super("Artist lookup HTTP "+code);this.waitMillis=waitMillis;}
    }
    private static String key(String value) {
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(Locale.ROOT,"%02x",b&255));return key.toString();}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    static String download(String query) throws IOException {
        URL url=new URL("https://musicbrainz.org/ws/2/artist/?fmt=json&limit=5&query="+URLEncoder.encode(query,"UTF-8"));
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        try {
            connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","FreeMyTVArtistSuggestions/1.0 (https://github.com/simbasjungles/free-my-tv)");connection.setRequestProperty("Accept","application/json");
            int code=connection.getResponseCode();if(code!=200) {
                long retry=15*60_000L;String header=connection.getHeaderField("Retry-After");
                if(header!=null)try{retry=Long.parseLong(header)*1000L;}catch(Exception notSeconds){try{retry=java.time.ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis();}catch(Exception invalid){}}
                if(code==429 || code==503)throw new RetryLater(code,Math.max(0,Math.min(86_400_000L,retry)));
                throw new IOException("Artist lookup HTTP "+code);
            }
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>250_000)throw new IOException("Artist response too large");out.write(buffer,0,n);}
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{connection.disconnect();}
    }
}
