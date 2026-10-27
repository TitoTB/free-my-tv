package io.freemytv;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

public final class FanartChecks {
    private static final String IMAGE="https://assets.fanart.tv/fanart/movies/42/moviebackground/test.jpg";
    private static void check(boolean value,String name) { if(!value) throw new AssertionError(name); System.out.println("PASS "+name); }
    private static JSONObject response() throws Exception {
        return new JSONObject().put("tmdb_id","42").put("imdb_id","tt123")
                .put("moviebackground",new JSONArray().put(new JSONObject().put("url",IMAGE).put("likes","5").put("lang","00")));
    }
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory(Paths.get(args[0]),"fanart-check-");
        try {
            check(Fanart.identity(42,"tt123").equals("tt123") && Fanart.identity(42,"").equals("42")
                    && Fanart.identity(0,"bad/id").isEmpty(),"lookup uses exact IMDb or TMDB identity, never title guessing");
            check(Fanart.select(response(),42,"tt123").equals(IMAGE),"matching movie selects a genuine background");
            check(Fanart.select(response(),43,"tt123").isEmpty() && Fanart.select(response(),42,"tt456").isEmpty(),
                    "conflicting identifiers never show another movie's image");
            check(Fanart.select(new JSONObject().put("moviebackground",response().getJSONArray("moviebackground")),42,"tt123").isEmpty(),
                    "unidentified responses cannot provide artwork");
            JSONObject posters=response(); posters.remove("moviebackground"); posters.put("movieposter",response().getJSONArray("moviebackground"));
            check(Fanart.select(posters,42,"tt123").isEmpty(),"posters and trailers are not used as backgrounds");
            JSONObject preferred=response(); preferred.getJSONArray("moviebackground").put(new JSONObject().put("url",IMAGE+"?other=1").put("lang","en").put("likes",100));
            check(Fanart.select(preferred,42,"tt123").equals(IMAGE),"neutral artwork is preferred over text in a different language");
            check(!Fanart.allowed("http://assets.fanart.tv/fanart/movies/42/a.jpg")
                    && !Fanart.allowed("https://assets.fanart.tv.evil.test/fanart/movies/42/a.jpg")
                    && !Fanart.allowed("https://assets.fanart.tv:444/fanart/movies/42/a.jpg")
                    && !Fanart.allowed("https://assets.fanart.tv/fanart/music/42/a.jpg"),"only HTTPS movie artwork on the actual provider host is accepted");
            File file=directory.resolve("cache.json").toFile(); AtomicInteger calls=new AtomicInteger();
            Fanart.Transport network=(id,key)->{ calls.incrementAndGet(); return response(); };
            Fanart fanart=new Fanart("private-test-key",file,network);
            check(fanart.background(42,"tt123").equals(IMAGE) && fanart.background(42,"tt123").equals(IMAGE) && calls.get()==1,
                    "repeated requests reuse the positive lookup cache");
            Fanart restarted=new Fanart("private-test-key",file,network);
            check(restarted.background(42,"tt123").equals(IMAGE) && calls.get()==1,"provider lookup survives a launcher restart without another API request");
            check(!Files.readString(file.toPath()).contains("private-test-key"),"persistent provider metadata contains no API key");
            new Fanart("",file,(id,key)->{ throw new AssertionError("No key must mean no request"); }).background(42,"tt123");
            System.out.println("PASS missing key disables the optional provider cleanly");
            restarted.retain(Collections.singleton("tt999"));
            check(new JSONArray(Files.readString(file.toPath())).length()==0,"programme reconciliation removes old provider entries");
            AtomicInteger misses=new AtomicInteger(); File missing=directory.resolve("missing.json").toFile();
            Fanart negative=new Fanart("test",missing,(id,key)->{ misses.incrementAndGet(); return new JSONObject(); });
            negative.background(42,"tt123"); negative.background(42,"tt123");
            check(misses.get()==1,"missing artwork is cached to prevent repeated requests on navigation");
            JSONArray stale=new JSONArray(Files.readString(missing.toPath())); stale.getJSONObject(0).put("at",System.currentTimeMillis()-16*60_000L);
            Files.write(missing.toPath(),stale.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new Fanart("test",missing,(id,key)->{ misses.incrementAndGet(); return new JSONObject(); }).background(42,"tt123");
            check(misses.get()==2,"missing backgrounds are checked again after the short retry interval");
            AtomicInteger failures=new AtomicInteger();
            Fanart flaky=new Fanart("test",directory.resolve("flaky.json").toFile(),(id,key)->{
                if(failures.incrementAndGet()==1) throw new IOException("offline"); return response();
            });
            try { flaky.background(42,"tt123"); } catch(IOException expected) { }
            check(flaky.background(42,"tt123").equals(IMAGE) && failures.get()==2,"network failures do not poison the missing-artwork cache");
            JSONArray aged=new JSONArray(Files.readString(file.toPath()));
            // The positive cache file was pruned above; create an expired positive record explicitly.
            aged.put(new JSONObject().put("id","tt123").put("image",IMAGE).put("at",System.currentTimeMillis()-25*60*60_000L));
            Files.write(file.toPath(),aged.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            check(new Fanart("test",file,(id,key)->{ throw new IOException("offline"); }).background(42,"tt123").equals(IMAGE),
                    "an offline refresh retains previously cached background references");
            new Fanart("test",file,network).background(42,"tt123");
            check(calls.get()==2,"positive artwork metadata is refreshed after 24 hours");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(directory)) {
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }
}
