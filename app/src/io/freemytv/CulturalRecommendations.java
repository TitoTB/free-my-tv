package io.freemytv;

import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Local recommendation policy. Input has already passed date/category filters; exclusions are absolute. */
final class CulturalRecommendations {
    static final long DAY=86_400_000L;
    static final class Exposure {
        int count;long seen,featured;
        Exposure(int count,long seen,long featured){this.count=count;this.seen=seen;this.featured=featured;}
    }
    static final class History {
        final Map<String,Exposure> entries=new LinkedHashMap<>();
        History copy(){History result=new History();for(Map.Entry<String,Exposure> row:entries.entrySet()){Exposure e=row.getValue();result.entries.put(row.getKey(),new Exposure(e.count,e.seen,e.featured));}return result;}
        boolean record(String id,boolean featured,long now) {
            if(id.isEmpty() || id.length()>300)return false;
            Exposure old=entries.get(id);
            if(old!=null && now>=old.seen && now-old.seen<30*60_000L && (!featured || old.featured>0 && now-old.featured<30*60_000L))return false;
            int count=old==null || now<old.seen || now-old.seen>=7*DAY?0:old.count;
            boolean impression=old==null || now<old.seen || now-old.seen>=30*60_000L;
            entries.put(id,new Exposure(Math.min(30,count+(impression?1:0)),impression?now:old.seen,featured?now:old==null?0:old.featured));
            prune(now);return true;
        }
        void prune(long now) {
            entries.entrySet().removeIf(e->e.getValue().seen>now+DAY || now-e.getValue().seen>30*DAY);
            if(entries.size()>1000){List<String> ids=new ArrayList<>(entries.keySet());ids.sort(Comparator.comparingLong(id->entries.get(id).seen));for(int i=0;i<ids.size()-1000;i++)entries.remove(ids.get(i));}
        }
        int penalty(CulturalData.Hit hit,LocalDate today,long now) {
            if(urgent(hit,today))return 0;Exposure old=entries.get(hit.event.id);
            if(old==null || old.seen>now || now-old.seen>=7*DAY)return 0;
            int effective=Math.max(0,old.count-2-(int)((now-old.seen)/DAY));return Math.min(30,effective*6);
        }
        long featured(String id){Exposure e=entries.get(id);return e==null?0:e.featured;}
        String encode(){try{JSONObject rows=new JSONObject();for(Map.Entry<String,Exposure> row:entries.entrySet()){Exposure e=row.getValue();rows.put(row.getKey(),new JSONObject().put("count",e.count).put("seen",e.seen).put("featured",e.featured));}return new JSONObject().put("version",1).put("entries",rows).toString();}catch(Exception ignored){return "{}";}}
        static History decode(String json,long now){History result=new History();try{JSONObject root=new JSONObject(json);if(root.optInt("version")!=1)return result;JSONObject rows=root.getJSONObject("entries");Iterator<String> keys=rows.keys();int read=0;
            while(keys.hasNext() && read++<2000){String id=keys.next();JSONObject e=rows.optJSONObject(id);if(e==null || id.isEmpty() || id.length()>300)continue;long seen=e.optLong("seen");if(seen<=0 || seen>now+DAY || now-seen>30*DAY)continue;
                result.entries.put(id,new Exposure(Math.max(0,Math.min(30,e.optInt("count"))),seen,Math.max(0,Math.min(now,e.optLong("featured")))));}
        }catch(Exception ignored){}result.prune(now);return result;}
    }
    static final class Decision {
        final String type,label;final boolean repeated,urgent;
        boolean discovery,variety,rotated;
        Decision(String type,String label,boolean repeated,boolean urgent){this.type=type;this.label=label;this.repeated=repeated;this.urgent=urgent;}
    }
    static final class Plan {
        final List<CulturalData.Hit> results;
        final Map<CulturalData.Hit,Decision> reasons;
        Plan(List<CulturalData.Hit> results,Map<CulturalData.Hit,Decision> reasons){this.results=Collections.unmodifiableList(results);this.reasons=Collections.unmodifiableMap(reasons);}
    }
    private static final class Node {
        final CulturalData.Hit hit;final int score,index,affinity;final Set<String> styles;final String series;final Decision decision;
        Node(CulturalData.Hit hit,int index,CulturalLikes likes,CulturalData.Catalog catalog,History history,LocalDate today,long now) {
            this.hit=hit;this.index=index;affinity=likes.affinity(hit.event);int penalty=history.penalty(hit,today,now);score=affinity-penalty;
            styles=new HashSet<>();for(String key:hit.event.categories)if(key.startsWith("s:"))styles.add(key);
            if(styles.isEmpty())styles.addAll(hit.event.categories);
            series=series(hit.event);decision=describe(hit,likes,catalog,penalty>0,today);
        }
    }
    static boolean urgent(CulturalData.Hit hit,LocalDate today) {
        LocalDate soon=today.plusDays(3);
        return !hit.end.isAfter(soon) || ((hit.punctual() || hit.scheduled || !hit.ongoing(today)) && !hit.date.isAfter(soon));
    }
    static Decision describe(CulturalData.Hit hit,CulturalLikes likes,CulturalData.Catalog catalog,boolean repeated,LocalDate today) {
        CulturalData.Event e=hit.event;String artist=likes.matchedArtist(e);
        if(!artist.isEmpty())return new Decision("artist",artist,repeated,urgent(hit,today));
        if(likes.reduced(e)){for(CulturalData.Category c:catalog.categories)if(e.categories.contains(c.key) && likes.less.containsKey(c.key))return new Decision("less",c.name,repeated,urgent(hit,today));return new Decision("less","",repeated,urgent(hit,today));}
        for(CulturalData.Category c:catalog.categories)if(!c.parent.isEmpty() && e.categories.contains(c.key) && likes.tags.containsKey(c.key))return new Decision("subcategory",c.name,repeated,urgent(hit,today));
        if(likes.venues.containsKey(e.venueId))return new Decision("venue",e.venueName,repeated,urgent(hit,today));
        for(CulturalData.Category c:catalog.categories)if(e.categories.contains(c.key) && likes.tags.containsKey(c.key))return new Decision("category",c.name,repeated,urgent(hit,today));
        return new Decision("neutral","",repeated,urgent(hit,today));
    }
    static Plan plan(List<CulturalData.Hit> input,CulturalLikes likes,CulturalData.Catalog catalog,History history,LocalDate today,long now,long visit) {
        List<Node> pool=new ArrayList<>();int index=0;for(CulturalData.Hit hit:input){if(!likes.disliked(hit.event))pool.add(new Node(hit,index,likes,catalog,history,today,now));index++;}
        pool.sort(Comparator.comparingInt((Node n)->-n.score).thenComparingInt(n->n.index));
        List<Node> chosen=new ArrayList<>();
        // Rotate only within an affinity cohort, and only when a new visit recomputes this plan.
        Node positive=null;for(Node n:pool)if(n.affinity>0){positive=n;break;}
        if(positive!=null) {
            int best=positive.score;List<Node> options=new ArrayList<>();
            for(Node n:pool)if(n.affinity>0 && n.score>=best-40 && options.size()<5)options.add(n);
            options.sort(Comparator.comparingLong((Node n)->history.featured(n.hit.event.id)).thenComparingInt(n->-n.score).thenComparingInt(n->n.index));
            long oldest=history.featured(options.get(0).hit.event.id);List<Node> fresh=new ArrayList<>();for(Node n:options)if(history.featured(n.hit.event.id)==oldest)fresh.add(n);
            Node feature=fresh.get((int)Math.floorMod(Math.max(0,visit-1),fresh.size()));feature.decision.rotated=options.size()>1;
            chosen.add(feature);pool.remove(feature);
        }
        while(!pool.isEmpty() && chosen.size()<24) {
            int slot=chosen.size();boolean discover=slot%5==2 && pool.stream().anyMatch(n->n.affinity==0);
            boolean nonReduced=pool.stream().anyMatch(n->n.affinity>=0);
            Node best=null;int bestValue=Integer.MIN_VALUE,firstValue=Integer.MIN_VALUE;
            for(Node n:pool) {
                if(discover && n.affinity!=0 || nonReduced && n.affinity<0)continue;int value=n.score-diversity(n,chosen);
                if(firstValue==Integer.MIN_VALUE)firstValue=value;
                if(best==null || value>bestValue){best=n;bestValue=value;}
            }
            best.decision.discovery=discover;best.decision.variety=bestValue>firstValue;
            chosen.add(best);pool.remove(best);
        }
        // Every remaining eligible result is still reachable; expensive mixing is bounded to the first24.
        chosen.addAll(pool);List<CulturalData.Hit> results=new ArrayList<>();Map<CulturalData.Hit,Decision> reasons=new IdentityHashMap<>();
        for(Node n:chosen){results.add(n.hit);reasons.put(n.hit,n.decision);}return new Plan(results,reasons);
    }
    private static int diversity(Node node,List<Node> chosen) {
        int penalty=0;for(int i=Math.max(0,chosen.size()-3);i<chosen.size();i++){Node recent=chosen.get(i);
            if(!Collections.disjoint(node.styles,recent.styles))penalty+=22;
            if(!node.hit.event.venueId.isEmpty() && node.hit.event.venueId.equals(recent.hit.event.venueId))penalty+=26;
            if(!node.series.isEmpty() && node.series.equals(recent.series))penalty+=34;
        }return penalty;
    }
    private static final Pattern CYCLE=Pattern.compile("(?iu:ciclo|festival)\\s+(?iu:de\\s+)?([\\p{Lu}][\\p{L}\\p{N}]+(?:\\s+(?:(?:de|del|la|las|los|y)\\s+)?[\\p{Lu}][\\p{L}\\p{N}]+){0,4})");
    static String series(CulturalData.Event e) {
        // Only explicit named cycle phrases/title prefixes; never invent a provider cycle ID.
        Matcher match=CYCLE.matcher(e.description);if(match.find()){String name=CulturalData.normalise(match.group(1));if(name.length()>=4)return "cycle:"+name;}
        String[] parts=e.title.split("\\s*[|:]\\s*|\\s+[—–-]\\s+",2);
        if(parts.length>1){String prefix=CulturalData.normalise(parts[0]);if(prefix.length()>=4 && prefix.length()<=60)return "prefix:"+prefix;}
        return "";
    }
}
