package io.freemytv;

import java.util.*;
import org.json.*;

/** Explicit taxonomy/venue/artist preferences and independent per-event hides. */
final class CulturalLikes {
    final Map<String,String> tags=new LinkedHashMap<>(),blocked=new LinkedHashMap<>(),less=new LinkedHashMap<>(),hiddenEvents=new LinkedHashMap<>();
    final Map<String,String> venues=new LinkedHashMap<>(),blockedVenues=new LinkedHashMap<>(),artists=new LinkedHashMap<>();
    final Map<String,Set<String>> artistAliases=new LinkedHashMap<>();
    final Map<String,String> artistIds=new LinkedHashMap<>();
    boolean disliked(CulturalData.Event event) {
        if(hiddenEvents.containsKey(event.id))return true;
        if(!event.venueId.isEmpty() && blockedVenues.containsKey(event.venueId))return true;
        for(String key:event.categories)if(blocked.containsKey(key))return true;
        return false;
    }
    boolean liked(CulturalData.Event event) {
        return !disliked(event) && affinity(event)>0;
    }
    boolean reduced(CulturalData.Event event) {
        for(String key:event.categories)if(less.containsKey(key))return true;
        return false;
    }
    int affinity(CulturalData.Event event) {
        if(artistMatch(event))return 100;
        if(reduced(event))return -70;
        for(String key:event.categories)if(key.startsWith("s:") && tags.containsKey(key))return 65;
        if(venues.containsKey(event.venueId))return 45;
        for(String key:event.categories)if(tags.containsKey(key))return 30;
        return 0;
    }
    void hide(CulturalData.Event event,boolean value) {
        if(event.id.isEmpty() || event.id.length()>300)return;
        if(value)hiddenEvents.put(event.id,event.title.substring(0,Math.min(300,event.title.length())));else hiddenEvents.remove(event.id);
    }
    boolean artistMatch(CulturalData.Event event) {
        return !matchedArtist(event).isEmpty();
    }
    String matchedArtist(CulturalData.Event event) {
        for(String name:artists.keySet()) {
            if(event.searchText.contains(" "+name+" "))return artists.get(name);
            for(String alias:artistAliases.getOrDefault(name,Collections.emptySet()))if(event.searchText.contains(" "+alias+" "))return artists.get(name);
        }
        return "";
    }
    void artist(String name,boolean value) {
        String key=CulturalData.normalise(name);
        if(key.isEmpty() || key.length()>120)return;
        if(value)artists.put(key,name.trim());else {artists.remove(key);artistAliases.remove(key);artistIds.remove(key);}
    }
    void resolvedArtist(String name,String id,Collection<String> aliases,boolean value) {
        artist(name,value);String key=CulturalData.normalise(name);
        if(!value || !artists.containsKey(key))return;
        if(id.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))artistIds.put(key,id);
        Set<String> names=new LinkedHashSet<>();for(String alias:aliases){String normal=CulturalData.normalise(alias);if(normal.length()>=3 && normal.length()<=120)names.add(normal);if(names.size()>=40)break;}
        artistAliases.put(key,names);
    }
    void venue(CulturalData.Event event,boolean exclude,boolean value) {
        if(event.venueId.isEmpty() || event.venueName.isEmpty())return;
        Map<String,String> target=exclude?blockedVenues:venues,opposite=exclude?venues:blockedVenues;
        if(value){target.put(event.venueId,event.venueName);opposite.remove(event.venueId);}else target.remove(event.venueId);
    }
    /** Ask explicitly which of this event's most specific provider tags the user likes. */
    static List<CulturalData.Category> choices(CulturalData.Catalog catalog,CulturalData.Event event) {
        List<CulturalData.Category> result=new ArrayList<>();
        for(CulturalData.Category category:catalog.categories) {
            if(!event.categories.contains(category.key))continue;
            boolean hasChild=false;
            if(category.parent.isEmpty())for(CulturalData.Category child:catalog.categories)
                if(category.key.equals(child.parent) && event.categories.contains(child.key)){hasChild=true;break;}
            if(!hasChild)result.add(category);
        }
        return Collections.unmodifiableList(result);
    }
    static String label(CulturalData.Catalog catalog,CulturalData.Category category) {
        if(!category.parent.isEmpty())for(CulturalData.Category parent:catalog.categories)
            if(category.parent.equals(parent.key))return parent.name+" · "+category.name;
        return category.name;
    }
    String label(CulturalData.Catalog catalog,String key) {
        for(CulturalData.Category category:catalog.categories)if(key.equals(category.key))return label(catalog,category);
        return tags.getOrDefault(key,blocked.getOrDefault(key,less.getOrDefault(key,key)));
    }
    void set(CulturalData.Catalog catalog,CulturalData.Category category,boolean value) {
        if(value && validKey(category.key)){tags.put(category.key,label(catalog,category));blocked.remove(category.key);less.remove(category.key);}else tags.remove(category.key);
    }
    void setBlocked(CulturalData.Catalog catalog,CulturalData.Category category,boolean value) {
        if(value && validKey(category.key)){blocked.put(category.key,label(catalog,category));tags.remove(category.key);less.remove(category.key);}else blocked.remove(category.key);
    }
    void setLess(CulturalData.Catalog catalog,CulturalData.Category category,boolean value) {
        if(value && validKey(category.key)){less.put(category.key,label(catalog,category));tags.remove(category.key);blocked.remove(category.key);}else less.remove(category.key);
    }
    List<CulturalData.Hit> prioritize(List<CulturalData.Hit> filtered) {
        return prioritize(filtered,false);
    }
    List<CulturalData.Hit> prioritize(List<CulturalData.Hit> filtered,boolean groupedMonths) {
        List<CulturalData.Hit> result=new ArrayList<>();
        for(CulturalData.Hit hit:filtered)if(!disliked(hit.event))result.add(hit);
        // Hidden/blocked are removed first. Artists > positive interests > neutral > reduced styles.
        // Stable sorting retains date/relevance order within each priority level.
        Comparator<CulturalData.Hit> preference=Comparator.comparingInt(h->-affinity(h.event));
        if(groupedMonths)preference=Comparator.comparing((CulturalData.Hit h)->java.time.YearMonth.from(h.date)).thenComparing(preference);
        result.sort(preference);
        return Collections.unmodifiableList(result);
    }
    String encode() {
        try { JSONObject resolved=new JSONObject();for(String key:artists.keySet())if(artistIds.containsKey(key))resolved.put(key,new JSONObject().put("id",artistIds.get(key)).put("aliases",new JSONArray(artistAliases.getOrDefault(key,Collections.emptySet()))));
            return new JSONObject().put("version",6).put("tags",new JSONObject(tags)).put("blocked",new JSONObject(blocked)).put("less",new JSONObject(less)).put("hiddenEvents",new JSONObject(hiddenEvents))
                .put("venues",new JSONObject(venues)).put("blockedVenues",new JSONObject(blockedVenues)).put("artists",new JSONObject(artists)).put("resolvedArtists",resolved).toString(); }
        catch(JSONException ignored) { return "{}"; }
    }
    static CulturalLikes decode(String json) {
        CulturalLikes result=new CulturalLikes();
        try {
            JSONObject root=new JSONObject(json);
            // Never guess preferences from the abandoned event/artist prototype.
            int version=root.optInt("version",0);if(version<2 || version>6)return result;
            read(root.optJSONObject("tags"),result.tags);
            if(version>=3)read(root.optJSONObject("blocked"),result.blocked);
            if(version>=6){read(root.optJSONObject("less"),result.less);readNames(root.optJSONObject("hiddenEvents"),result.hiddenEvents,false);}
            if(version>=4) {
                readNames(root.optJSONObject("venues"),result.venues,false);readNames(root.optJSONObject("blockedVenues"),result.blockedVenues,false);
                readNames(root.optJSONObject("artists"),result.artists,true);result.venues.keySet().removeAll(result.blockedVenues.keySet());
                JSONObject resolved=root.optJSONObject("resolvedArtists");if(version>=5 && resolved!=null)for(String key:new ArrayList<>(result.artists.keySet())) {
                    JSONObject entry=resolved.optJSONObject(key);if(entry==null)continue;JSONArray values=entry.optJSONArray("aliases");List<String> aliases=new ArrayList<>();
                    if(values!=null)for(int i=0;i<Math.min(40,values.length());i++)aliases.add(values.optString(i,""));
                    result.resolvedArtist(result.artists.get(key),entry.optString("id"),aliases,true);
                }
            }
            result.tags.keySet().removeAll(result.blocked.keySet());
            result.less.keySet().removeAll(result.blocked.keySet());result.tags.keySet().removeAll(result.less.keySet());
        } catch(Exception ignored) {}
        return result;
    }
    private static void readNames(JSONObject values,Map<String,String> target,boolean artist) {
        if(values==null)return;Iterator<String> keys=values.keys();
        while(keys.hasNext() && target.size()<2000) {
            String key=keys.next(),name=values.optString(key,"");
            if(key.isEmpty() || key.length()>300 || name.trim().isEmpty() || name.length()>300 || key.matches(".*[\\p{Cntrl}].*"))continue;
            if(artist){key=CulturalData.normalise(name);if(key.isEmpty() || key.length()>120)continue;}
            target.put(key,name);
        }
    }
    private static void read(JSONObject values,Map<String,String> target) {
        if(values==null)return;Iterator<String> keys=values.keys();
        while(keys.hasNext() && target.size()<2000) {
            String key=keys.next(),value=values.optString(key,"");
            if(validKey(key) && !value.isEmpty() && value.length()<=500)target.put(key,value);
        }
    }
    private static boolean validKey(String key) { return key.length()<=500 && key.matches("c:[^:\\s]+|s:[^:\\s]+:[^:\\s]+"); }
}
