package io.freemytv;

import java.util.*;
import org.json.*;

public final class CinemaPosterChecks {
    private static void check(boolean value,String name) { if(!value) throw new AssertionError(name); System.out.println("PASS "+name); }
    public static void main(String[] args) throws Exception {
        JSONArray images=new JSONArray()
                .put(new JSONObject().put("file_path","/small.jpg").put("width",600).put("height",900))
                .put(new JSONObject().put("file_path","/large.jpg").put("width",2000).put("height",3000))
                .put(new JSONObject().put("file_path","/medium.jpg").put("width",1000).put("height",1500))
                .put(new JSONObject().put("file_path","https://wrong.test/a.jpg").put("width",9999).put("height",9999));
        List<String> ranked=CinemaPoster.candidates(images,"/small.jpg");
        check(ranked.equals(Arrays.asList("/large.jpg","/medium.jpg","/small.jpg")),"poster candidates are ordered by native resolution and deduplicated");
        check(!CinemaPoster.path("//evil.test/a.jpg") && !CinemaPoster.path("/../a.jpg") && !CinemaPoster.path("/a.jpg?secret=x"),
                "poster paths stay within the expected TMDB image namespace");
        for(int[] size:new int[][]{{2000,3000},{1000,1000},{1920,1080}}) {
            float[] b=CinemaPoster.bounds(size[0],size[1],1920,1080);
            check(Math.abs((b[2]-b[0])/(b[3]-b[1])-size[0]/(float)size[1])<0.0001f
                    && b[0]<=0 && b[1]<=0 && b[2]>=1920 && b[3]>=1080
                    && Math.abs((b[0]+b[2])/2-960)<0.01f && Math.abs((b[1]+b[3])/2-540)<0.01f,
                    "original poster "+size[0]+"x"+size[1]+" fills the screen without stretching or empty edges");
        }
        check(!CinemaPoster.key("source",1920,1080).equals(CinemaPoster.key("source",1280,720)),"processed cache keys reflect the TV rendering resolution");
        check(!CinemaPoster.key("source",1920,1080).startsWith("poster-v1:"),"old blurred side-poster compositions cannot be reused");
        CinemaMetadata entry=new CinemaMetadata(1,"tt123",42,120,"text","","",0,10).withPosters(ranked,true);
        CinemaMetadata restored=CinemaMetadata.decode(entry.encode());
        check(restored.posters.equals(ranked) && restored.postersChecked,"ranked poster lookup survives restart without another images request");
        JSONObject legacy=entry.encode(); legacy.remove("posters"); legacy.remove("postersChecked");
        check(!CinemaMetadata.decode(legacy).postersChecked,"old metadata caches can discover posters without discarding film identity");
        Cinema.Movie a=new Cinema.Movie(),b=new Cinema.Movie(),c=new Cinema.Movie();
        a.id=1; b.id=2; c.id=3; List<Cinema.Movie> programme=Arrays.asList(a,b,c);
        check(Cinema.visibleMovies(programme).isEmpty(),"unverified or entirely unavailable artwork is never exposed in the carousel");
        a.artworkReady=true; c.artworkReady=true;
        check(Cinema.visibleMovies(programme).equals(Arrays.asList(a,c)),"only movies with a validated image are visible, in programme order");
        b.artworkReady=true;
        check(Cinema.visibleMovies(programme).equals(programme),"previously hidden movies rejoin when an image becomes available");
        a.artworkReady=false;
        check(Cinema.visibleMovies(programme).equals(Arrays.asList(b,c)) && programme.size()==3,
                "losing all images hides the title without losing it from the retry programme");
        a.corporate=1; a.cinemaImage=""; a.image="/later-resolved-background.jpg"; a.posters.add("https://cdn.kinepolis.es/ES/poster.jpg");
        a.sessions=Collections.singletonList(new Cinema.Session(System.currentTimeMillis()+3600000,"1","es","2D",false));
        Cinema.Movie saved=CinemaBillboard.decode(CinemaBillboard.encode(Collections.singletonList(a)),System.currentTimeMillis()).get(0);
        check(saved.image.isEmpty() && saved.posters.equals(a.posters) && !saved.artworkReady,
                "poster-only and unverified titles survive programme persistence without being prematurely exposed");
    }
}
