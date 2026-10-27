package io.freemytv;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Conservative candidates, not inferred favourites. Only exact provider names/aliases can become suggestions. */
final class CulturalArtists {
    static final int MAX_QUERIES=6;
    static final class Artist {
        final String id,name,detail;
        final List<String> aliases;
        Artist(String id,String name,String detail,List<String> aliases){this.id=id;this.name=name;this.detail=detail;this.aliases=Collections.unmodifiableList(new ArrayList<>(aliases));}
    }
    private static final Pattern NAMES=Pattern.compile("(?<![\\p{L}\\p{N}])(?:[\\p{Lu}\\p{N}][\\p{L}\\p{N}'’&.!$-]*)(?:\\s+(?:(?:de|del|la|las|los|y|and|the|of|in)\\s+)?[\\p{Lu}\\p{N}][\\p{L}\\p{N}'’&.!$-]*){0,4}");
    private static final Set<String> GENERIC=new HashSet<>(Arrays.asList("madrid","musica","concierto","festival","jazz","rock","pop","indie","teatro","sala","entradas","el","la","los","las","the","banda","grupo","artista"));
    static List<String> candidates(CulturalData.Event event) {
        LinkedHashSet<String> values=new LinkedHashSet<>();
        String title=event.title.replaceFirst("(?iu)^(?:concierto de|concierto|en concierto|actuación de)\\s+","");
        for(String part:title.split("\\s+[—–-]\\s+|\\s*[:|+]\\s*|(?iu)\\s+(?:feat\\.?|ft\\.?|con|junto a)\\s+"))add(values,part,event);
        add(values,title,event);
        // Description names outrank single generic words; do not send the entire description to a third party.
        LinkedHashSet<String> names=new LinkedHashSet<>();Matcher matcher=NAMES.matcher(event.description.length()>3500?event.description.substring(0,3500):event.description);
        while(matcher.find() && names.size()<40) {
            String name=matcher.group().replaceFirst("^(?:El|La|Los|Las)\\s+(?=[A-ZÁÉÍÓÚÑ])","");
            String normal=CulturalData.normalise(name),venue=CulturalData.normalise(event.venueName);
            if(!normal.equals(venue) && !normal.matches(".*\\b(?:teatro|sala|festival|auditorio|palacio|centro|madrid|club)\\b.*"))names.add(name);
        }
        for(String name:names)if(CulturalData.normalise(name).contains(" "))add(values,name,event);
        for(String name:names)add(values,name,event);
        List<String> result=new ArrayList<>(values);return Collections.unmodifiableList(result.subList(0,Math.min(MAX_QUERIES,result.size())));
    }
    private static void add(Set<String> values,String name,CulturalData.Event event) {
        String normal=CulturalData.normalise(name);
        if(normal.length()<3 || normal.length()>80 || normal.split(" ").length>7 || GENERIC.contains(normal) ||
                normal.matches(".*\\b(?:festival|tributo|homenaje|entradas|concierto|gira|tour|temporada)\\b.*"))return;
        if(event.searchText.contains(" "+normal+" "))values.add(normal);
    }
    static List<Artist> parse(String json,String candidate,CulturalData.Event event) throws JSONException {
        JSONArray rows=new JSONObject(json).getJSONArray("artists");List<Artist> result=new ArrayList<>();
        String wanted=CulturalData.normalise(candidate);
        if(!event.searchText.contains(" "+wanted+" "))return result;
        for(int i=0;i<Math.min(rows.length(),10);i++) {
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
            String id=row.optString("id"),name=row.optString("name"),detail=row.optString("disambiguation");
            if(!id.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") || name.isEmpty() || name.length()>120 || row.optInt("score",0)<90)continue;
            LinkedHashSet<String> aliases=new LinkedHashSet<>();aliases.add(name);
            JSONArray others=row.optJSONArray("aliases");if(others!=null)for(int j=0;j<Math.min(40,others.length());j++) {
                JSONObject alias=others.optJSONObject(j);if(alias==null)continue;String value=alias.optString("name");
                if(value.length()<=120 && CulturalData.normalise(value).length()>=3)aliases.add(value);
            }
            boolean exact=false;for(String alias:aliases)if(wanted.equals(CulturalData.normalise(alias)))exact=true;
            if(!exact)continue;
            if(detail.length()>180)detail=detail.substring(0,180);
            String country=row.optString("country");if(country.matches("[A-Z]{2}"))detail=detail.isEmpty()?country:detail+" · "+country;
            result.add(new Artist(id,name,detail,new ArrayList<>(aliases)));
        }
        return result;
    }
    static String query(String candidate) {
        // Normalisation removes Lucene metacharacters before quoting; URL encoding is done by the transport.
        String name=CulturalData.normalise(candidate);return "artist:\""+name+"\" OR alias:\""+name+"\"";
    }
}
