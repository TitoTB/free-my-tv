package io.freemytv;
import org.json.*;
import java.lang.reflect.Method;
import java.time.*;
import java.util.*;

public class CinemaChecks {
    static void check(boolean value,String name) { if(!value) throw new AssertionError(name); System.out.println("PASS " + name); }
    static JSONObject row(String cinema,String time,int hall) throws Exception {
        return new JSONObject().put("complexOperator",cinema).put("showtime",time).put("hall",hall)
            .put("isPublicScreening",true).put("film",new JSONObject().put("format",new JSONObject().put("name","IMAX"))
            .put("data",new JSONObject().put("spokenLanguage",new JSONObject().put("name","Versión español"))));
    }
    public static void main(String[] args) throws Exception {
        long now=Instant.parse("2026-10-22T18:00:00Z").toEpochMilli();
        JSONArray input=new JSONArray().put(row("KMAD","2026-10-22T22:00:00+02:00",1))
            .put(row("KMAD","2026-10-22T21:00:00+02:00",2))
            .put(row("KMAD","2026-10-22T21:00:00+02:00",2))
            .put(row("KMAD","2026-10-22T19:00:00+02:00",3))
            .put(row("KALCO","2026-10-22T23:00:00+02:00",4))
            .put(row("KMAD","2026-10-23T00:30:00+02:00",5).put("isSoldOut",true))
            .put(row("KMAD","2026-10-22T23:00:00+02:00",6).put("isPublicScreening",false));
        List<Cinema.Session> sessions=Cinema.parseSessions(input,now);
        check(sessions.size()==3,"filter cinema, past, private and duplicate sessions");
        check(sessions.get(0).hall.equals("2") && sessions.get(2).soldOut,"sort instants, preserve sold-out status and midnight");
        check(Cinema.parseSessions(new JSONArray(),now).isEmpty(),"empty response");
        JSONArray films=new JSONArray().put(new JSONObject().put("corporateId",11).put("imdbCode","tt111").put("title","Test").put("releaseDate","2026-01-01"))
            .put(new JSONObject().put("corporateId",22).put("imdbCode","tt222").put("title","Test").put("releaseDate","2026-01-01"));
        check(Cinema.matchFilm(films,"tt222","Test","2026")==22,"IMDb identity wins over same title");
        check(Cinema.matchFilm(films,"tt333","Test","2026")==0,"conflicting IMDb cannot match by title");
        films.getJSONObject(0).put("imdbCode","").put("title","Película: prueba");
        check(Cinema.matchFilm(films,"","Pelicula prueba","2026")==11,"normalized title requires release year");
        check(Cinema.matchFilm(films,"","Pelicula prueba","2025")==0,"reject wrong remake year");
        check(Cinema.trailer(new JSONObject().put("results",new JSONArray().put(new JSONObject().put("site","YouTube").put("type","Trailer").put("key","bad/url")))).isEmpty(),"reject invalid video identifiers");
        if(args.length==0) return;
        Method tmdb=Cinema.class.getDeclaredMethod("tmdb",String.class); tmdb.setAccessible(true);
        JSONObject list=(JSONObject)tmdb.invoke(null,"/movie/now_playing?language=es-ES&region=ES&page=1");
        List<Cinema.Movie> movies=Cinema.parseMovies(list.getJSONArray("results"));
        check(!movies.isEmpty(),"live TMDB key and movie list");
        System.out.println("MOVIE_COUNT="+movies.size()+" FIRST="+movies.get(0).title);
        JSONArray candidates=Cinema.searchFilms("La bola negra");
        check(candidates!=null,"live cinema search schema");
        System.out.println("SEARCH_FILMS="+candidates.length());
        if(candidates.length()>0) System.out.println("FIRST_FIELDS="+candidates.getJSONObject(0).names());
        int id=Cinema.matchFilm(candidates,"","La bola negra","2026");
        check(id>0,"live cinema identity match");
        System.out.println("LIVE_UPCOMING_SESSIONS="+Cinema.loadSessions(id,System.currentTimeMillis()).size());
    }
}
