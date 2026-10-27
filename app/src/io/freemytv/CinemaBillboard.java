package io.freemytv;

import android.util.JsonReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.*;
import java.util.*;
import org.json.*;

/** The website's own current programme, filtered to public KMAD sessions in the next seven days. */
final class CinemaBillboard {
    private static final ZoneId ZONE=ZoneId.of("Europe/Madrid");
    private CinemaBillboard() {}

    static List<Cinema.Movie> load(long now) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("https://kinepolis.es/").openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(18000);
        try {
            if(c.getResponseCode()!=200) throw new IOException("Cinema website HTTP error");
            try(Reader in=new InputStreamReader(c.getInputStream(),"UTF-8")) { return readPage(in,now); }
        } finally { c.disconnect(); }
    }

    static List<Cinema.Movie> readPage(Reader source,long now) throws Exception {
        Reader bounded=new FilterReader(source) {
            int count;
            private void used(int n) throws IOException { if(n>0 && (count+=n)>24*1024*1024) throw new IOException("Programme limit"); }
            @Override public int read() throws IOException { int n=in.read(); used(n<0?0:1); return n; }
            @Override public int read(char[] b,int o,int n) throws IOException { int k=in.read(b,o,n); used(k); return k; }
        };
        BufferedReader reader=new BufferedReader(bounded);
        String marker="\"current_movies\":"; int matched=0,ch;
        while((ch=reader.read())!=-1) {
            matched=ch==marker.charAt(matched)?matched+1:ch==marker.charAt(0)?1:0;
            if(matched==marker.length()) {
                try(JsonReader json=new JsonReader(reader)) { return read(json,now); }
            }
        }
        throw new IOException("Missing current cinema programme");
    }

    static List<Cinema.Movie> read(JsonReader reader,long now) throws Exception {
        List<JSONObject> rows=new ArrayList<>(); Map<String,JSONObject> films=new HashMap<>();
        boolean sawFilms=false,sawSessions=false;
        long end=windowEnd(now);
        reader.beginObject();
        while(reader.hasNext()) {
            String key=reader.nextName();
            if("sessions".equals(key)) {
                sawSessions=true; reader.beginArray();
                while(reader.hasNext()) {
                    JSONObject s=(JSONObject)Cinema.readJson(reader);
                    if(!"KMAD".equals(s.optString("complexOperator")) || !s.optBoolean("isPublicScreening",true)) continue;
                    long time=OffsetDateTime.parse(s.getString("showtime")).toInstant().toEpochMilli();
                    if(time>now && time<end) rows.add(s);
                }
                reader.endArray();
            } else if("films".equals(key)) {
                sawFilms=true; reader.beginArray();
                while(reader.hasNext()) {
                    JSONObject f=(JSONObject)Cinema.readJson(reader);
                    if(!f.optString("id").isEmpty()) films.put(f.getString("id"),f);
                }
                reader.endArray();
            } else reader.skipValue();
        }
        reader.endObject();
        if(!sawFilms || !sawSessions) throw new IOException("Incomplete cinema programme");
        Map<Integer,Cinema.Movie> grouped=new LinkedHashMap<>();
        Map<Integer,Set<String>> seen=new HashMap<>();
        for(JSONObject row:rows) {
            JSONObject version=row.optJSONObject("film"); if(version==null) continue;
            JSONObject f=films.get(version.optString("id"));
            if(f==null) continue;
            int corporate=version.optInt("corporateId");
            if(corporate<=0 || corporate!=f.optInt("corporateId")) continue;
            Cinema.Movie m=grouped.get(corporate);
            if(m==null) {
                m=movie(f); if(m==null) continue;
                grouped.put(corporate,m); seen.put(corporate,new HashSet<>());
            }
            // Language belongs to this exact film version, not another dub sharing its corporate ID.
            version.put("data",f);
            List<Cinema.Session> parsed=Cinema.parseSessions(new JSONArray().put(row),now);
            for(Cinema.Session s:parsed) if(seen.get(corporate).add(s.time+"/"+s.hall+"/"+s.format+"/"+s.language)) m.sessions.add(s);
        }
        List<Cinema.Movie> result=new ArrayList<>();
        for(Cinema.Movie m:grouped.values()) if(!m.sessions.isEmpty()) {
            m.sessions.sort(Comparator.comparingLong(s->s.time)); m.state=Cinema.READY; m.checkedAt=now;
            result.add(m);
        }
        result.sort(Comparator.comparingInt((Cinema.Movie m)->m.sortOrder).thenComparing(m->m.title));
        return result;
    }

    private static Cinema.Movie movie(JSONObject f) {
        if(Config.HIDE_ANIMATION) {
            JSONArray genres=f.optJSONArray("genres");
            for(int i=0;genres!=null && i<genres.length();i++) {
                JSONObject g=genres.optJSONObject(i);
                if(g!=null && ("ANI".equals(g.optString("shortName")) || g.optString("name").startsWith("Anim"))) return null;
            }
        }
        Cinema.Movie m=new Cinema.Movie();
        m.corporate=f.optInt("corporateId"); m.id=-m.corporate;
        m.title=CinemaTitle.clean(f.optString("title"));
        m.overview=f.optString("synopsis"); m.imdb=f.optString("imdbCode");
        m.minutes=f.optInt("duration"); m.sortOrder=f.optInt("sortingNumber",9999);
        String date=f.optString("releaseDate"); m.year=date.length()>=4?date.substring(0,4):"";
        JSONArray images=f.optJSONArray("images");
        for(int i=0;images!=null && i<images.length();i++) {
            JSONObject image=images.optJSONObject(i); if(image==null) continue;
            String url=image.optString("url");
            if(url.startsWith("/ES/")) url="https://cdn.kinepolis.es"+url;
            if(!url.startsWith("https://cdn.kinepolis.es/")) continue;
            if("Backdrop".equals(image.optString("mediaType"))) m.image=url;
            else if("Poster Graphic".equals(image.optString("mediaType")) && !m.posters.contains(url)) m.posters.add(url);
        }
        m.cinemaImage=m.image;
        m.sessions=new ArrayList<>();
        return m.corporate>0 && !m.title.isEmpty()?m:null;
    }

    static long windowEnd(long now) { return Instant.ofEpochMilli(now).atZone(ZONE).plusDays(7).toInstant().toEpochMilli(); }
    static boolean scheduled(Cinema.Movie m,long now) {
        long end=windowEnd(now);
        for(Cinema.Session s:m.sessions) if(s.time>now && s.time<end) return true;
        return false;
    }

    static JSONArray encode(List<Cinema.Movie> movies) throws JSONException {
        JSONArray out=new JSONArray();
        for(Cinema.Movie m:movies) {
            JSONArray sessions=new JSONArray();
            for(Cinema.Session s:m.sessions) sessions.put(new JSONObject().put("time",s.time).put("hall",s.hall)
                    .put("language",s.language).put("format",s.format).put("soldOut",s.soldOut));
            out.put(new JSONObject().put("corporate",m.corporate).put("title",m.title).put("overview",m.overview)
                    .put("image",m.cinemaImage).put("posters",new JSONArray(m.posters)).put("imdb",m.imdb).put("year",m.year).put("minutes",m.minutes)
                    .put("sort",m.sortOrder).put("sessions",sessions).put("checkedAt",m.checkedAt));
        }
        return out;
    }

    static List<Cinema.Movie> decode(JSONArray array,long now) {
        List<Cinema.Movie> out=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            JSONObject o=array.optJSONObject(i); if(o==null || o.optInt("corporate")<=0) continue;
            Cinema.Movie m=new Cinema.Movie(); m.corporate=o.optInt("corporate"); m.id=-m.corporate;
            m.title=CinemaTitle.clean(o.optString("title")); m.overview=o.optString("overview"); m.image=o.optString("image");
            m.cinemaImage=m.image;
            JSONArray posters=o.optJSONArray("posters");
            for(int p=0;posters!=null && p<posters.length();p++) {
                String source=posters.optString(p); if(source.startsWith("https://cdn.kinepolis.es/")) m.posters.add(source);
            }
            m.imdb=o.optString("imdb"); m.year=o.optString("year"); m.minutes=o.optInt("minutes");
            m.sortOrder=o.optInt("sort",9999); m.checkedAt=o.optLong("checkedAt");
            m.sessions=new ArrayList<>(); JSONArray sessions=o.optJSONArray("sessions");
            for(int j=0;sessions!=null && j<sessions.length();j++) {
                JSONObject s=sessions.optJSONObject(j); if(s==null || s.optLong("time")<=now) continue;
                m.sessions.add(new Cinema.Session(s.optLong("time"),s.optString("hall"),s.optString("language"),s.optString("format"),s.optBoolean("soldOut")));
            }
            m.state=Cinema.READY;
            if(scheduled(m,now)) out.add(m);
        }
        return out;
    }
}
