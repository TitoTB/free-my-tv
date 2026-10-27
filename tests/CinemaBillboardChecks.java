package io.freemytv;

import android.util.JsonReader;
import java.io.*;
import java.time.*;
import java.util.*;
import org.json.*;

public class CinemaBillboardChecks {
    private static void check(boolean value,String name) {
        if(!value) throw new AssertionError(name); System.out.println("PASS "+name);
    }
    private static JSONObject film(String id,int corporate,String title,String language) throws Exception {
        return new JSONObject().put("id",id).put("corporateId",corporate).put("title",title).put("imdbCode","tt123")
            .put("releaseDate","2026-10-01T00:00:00").put("duration",100).put("sortingNumber",1)
            .put("spokenLanguage",new JSONObject().put("name",language))
            .put("images",new JSONArray().put(new JSONObject().put("mediaType","Backdrop").put("url","/ES/test.jpg")));
    }
    private static JSONObject session(String id,int corporate,String cinema,long time) throws Exception {
        return new JSONObject().put("complexOperator",cinema).put("showtime",Instant.ofEpochMilli(time).toString())
            .put("hall",1).put("isPublicScreening",true).put("film",new JSONObject().put("id",id).put("corporateId",corporate)
                .put("format",new JSONObject().put("name","2D")));
    }
    private static List<Cinema.Movie> parse(JSONObject o,long now) throws Exception {
        return CinemaBillboard.read(new JsonReader(new StringReader(o.toString())),now);
    }
    public static void main(String[] args) throws Exception {
        check(Cinema.artworkUrl("https://cdn.kinepolis.es/ES/título (VOSE).jpg")
                .equals("https://cdn.kinepolis.es/ES/t%C3%ADtulo%20(VOSE).jpg"),"encode accented cinema artwork filenames as UTF-8");
        check(Cinema.artworkUrl("/already%20encoded.jpg").endsWith("/already%20encoded.jpg"),"preserve existing URL escapes");
        long now=Instant.parse("2026-10-22T21:00:00Z").toEpochMilli(), first=now+3_600_000, second=now+7_200_000;
        check(CinemaBillboard.windowEnd(now)-now==169*3_600_000L,"seven Madrid days across autumn DST");
        JSONArray films=new JSONArray().put(film("HO1",1,"Test (sin subtítulos)","Castellano"))
            .put(film("HO2",1,"VOSE Test","Inglés"))
            .put(film("HO3",2,"Another cinema","Castellano"))
            .put(film("HO4",3,"Presale","Castellano"));
        JSONArray rows=new JSONArray().put(session("HO1",1,"KMAD",first))
            .put(session("HO2",1,"KMAD",second).put("isSoldOut",true))
            .put(session("HO1",1,"KMAD",first))
            .put(session("HO3",2,"KALCO",first))
            .put(session("HO4",3,"KMAD",CinemaBillboard.windowEnd(now)))
            .put(session("HO1",1,"KMAD",now-1))
            .put(session("HO1",1,"KMAD",first+1).put("isPublicScreening",false));
        JSONObject programme=new JSONObject().put("films",films).put("sessions",rows);
        List<Cinema.Movie> loaded=parse(programme,now);
        check(loaded.size()==1,"KMAD only; exclude past, private and out-of-week sessions");
        Cinema.Movie movie=loaded.get(0);
        check(movie.title.equals("Test"),"strip subtitle version labels from the display title");
        check(movie.sessions.size()==2 && movie.id==-1,"group dubs by corporate identity and deduplicate sessions");
        check(movie.sessions.get(0).language.equals("Castellano") && movie.sessions.get(1).language.equals("Inglés")
            && movie.sessions.get(1).soldOut,"preserve exact version languages and sold-out state");
        check(movie.image.equals("https://cdn.kinepolis.es/ES/test.jpg"),"official cinema backdrop URL");
        check(CinemaBillboard.decode(CinemaBillboard.encode(loaded),first+1).get(0).sessions.size()==1,"cached programme drops past sessions");
        JSONArray legacy=CinemaBillboard.encode(loaded);legacy.getJSONObject(0).put("title","Test (sin subtítulos)");
        check(CinemaBillboard.decode(legacy,now).get(0).title.equals("Test"),"clean old cached titles without waiting for a network refresh");
        check(CinemaBillboard.decode(CinemaBillboard.encode(loaded),second+1).isEmpty(),"cached titles leave carousel after final session");
        check(CinemaBillboard.readPage(new StringReader("<script>{\"current_movies\":"+programme+"}</script>"),now).size()==1,
            "extract programme from website without consuming other settings");
        check(parse(new JSONObject().put("sessions",new JSONArray()).put("films",new JSONArray()),now).isEmpty(),"valid empty programme");
        boolean rejected=false;
        try { parse(new JSONObject().put("films",films),now); } catch(IOException expected) { rejected=true; }
        check(rejected,"reject missing sessions instead of treating invalid schema as empty cinema");
        if(args.length>0) {
            try(Reader in=new InputStreamReader(new FileInputStream(args[0]),"UTF-8")) {
                List<Cinema.Movie> captured=CinemaBillboard.read(new JsonReader(in),now);
                check(captured.size()>8,"captured real programme exceeds old eight-film selection");
                Set<Integer> ids=new HashSet<>();
                for(Cinema.Movie m:captured) check(ids.add(m.corporate) && CinemaBillboard.scheduled(m,now),"captured title has unique identity and upcoming passes: "+m.title);
                System.out.println("CAPTURED_COUNT="+captured.size());
            }
        }
        if(args.length>1) {
            List<Cinema.Movie> live=CinemaBillboard.load(System.currentTimeMillis());
            check(!live.isEmpty(),"live website programme from TV network");
            for(Cinema.Movie m:live) if(!CinemaBillboard.scheduled(m,System.currentTimeMillis())) throw new AssertionError("stale live movie");
            System.out.println("LIVE_COUNT="+live.size());
            for(int i=0;i<Math.min(5,live.size());i++) System.out.println("LIVE_TITLE="+live.get(i).title);
        }
    }
}
