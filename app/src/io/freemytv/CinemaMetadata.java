package io.freemytv;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;

/** Immutable, persistent TMDB enrichment. Session times and request state are never cached here. */
final class CinemaMetadata {
    final int id,tmdbId,minutes;
    final String imdb,overview,image,trailer;
    final double rating;
    final long at;
    final List<String> posters;
    final boolean postersChecked;
    CinemaMetadata(int id,String imdb,int tmdbId,int minutes,String overview,String image,String trailer,double rating,long at) {
        this(id,imdb,tmdbId,minutes,overview,image,trailer,rating,at,Collections.emptyList(),false);
    }
    private CinemaMetadata(int id,String imdb,int tmdbId,int minutes,String overview,String image,String trailer,double rating,long at,List<String> posters,boolean checked) {
        this.id=id; this.imdb=imdb; this.tmdbId=tmdbId; this.minutes=minutes; this.overview=overview;
        this.image=image.startsWith("/")?image:""; this.trailer=trailer; this.rating=rating; this.at=at;
        this.posters=Collections.unmodifiableList(new ArrayList<>(posters)); this.postersChecked=checked;
    }
    CinemaMetadata withPosters(List<String> posters,boolean checked) {
        return new CinemaMetadata(id,imdb,tmdbId,minutes,overview,image,trailer,rating,at,posters,checked);
    }
    boolean matches(int id,String imdb) { return this.id==id && this.imdb.equals(imdb); }
    boolean fresh(long now) { return at>0 && now>=at && now-at<(tmdbId>0?24*60*60_000L:15*60_000L); }
    JSONObject encode() throws Exception {
        return new JSONObject().put("id",id).put("imdb",imdb).put("tmdb",tmdbId).put("minutes",minutes)
                .put("overview",overview).put("image",image).put("trailer",trailer).put("rating",rating).put("at",at)
                .put("posters",new JSONArray(posters)).put("postersChecked",postersChecked);
    }
    static CinemaMetadata decode(JSONObject o) {
        List<String> posters=new ArrayList<>(); JSONArray list=o.optJSONArray("posters");
        for(int i=0;list!=null && i<list.length();i++) if(CinemaPoster.path(list.optString(i))) posters.add(list.optString(i));
        return new CinemaMetadata(o.optInt("id"),o.optString("imdb"),o.optInt("tmdb"),o.optInt("minutes"),
                o.optString("overview"),o.optString("image"),o.optString("trailer"),o.optDouble("rating",0),o.optLong("at"))
                .withPosters(posters,o.optBoolean("postersChecked"));
    }
}
