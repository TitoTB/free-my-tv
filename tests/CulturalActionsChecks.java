package io.freemytv;

import java.time.*;
import java.util.*;

/** Behavioural contract for the five user actions, their precedence and reversible persistence. */
public final class CulturalActionsChecks {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static CulturalData.Event event(String id,String title,String... categories){return new CulturalData.Event(id,title,"","https://www.esmadrid.com/agenda/test","","","","",new HashSet<>(Arrays.asList(categories)),Collections.emptyList());}
    private static CulturalData.Hit hit(CulturalData.Event event,int day){return new CulturalData.Hit(event,LocalDate.of(2026,10,day));}
    public static void main(String[] args)throws Exception {
        CulturalData.Category music=new CulturalData.Category("c:music","Música",""),indie=new CulturalData.Category("s:music:indie","Indie","c:music"),pop=new CulturalData.Category("s:music:pop","Pop-rock","c:music");
        CulturalData.Catalog catalog=new CulturalData.Catalog(Arrays.asList(music,indie,pop),Collections.emptyList());
        CulturalData.Event reduced=event("one","Indie act","c:music","s:music:indie"),sameStyle=event("two","Another indie act","c:music","s:music:indie"),neutral=event("three","Neutral","c:other"),positive=event("four","Pop act","c:music","s:music:pop"),followed=event("five","Muse","c:music","s:music:indie");
        CulturalLikes prefs=new CulturalLikes();prefs.setLess(catalog,indie,true);
        List<CulturalData.Hit> initial=Arrays.asList(hit(reduced,24),hit(sameStyle,25),hit(neutral,26));
        List<CulturalData.Hit> sorted=prefs.prioritize(initial);
        check(sorted.size()==3 && sorted.get(0).event==neutral,"Show less keeps events available behind neutral results");
        check(sorted.get(1).event==reduced && sorted.get(2).event==sameStyle,"Stable ordering inside reduced styles");
        check(!prefs.disliked(reduced) && !prefs.liked(reduced),"Reduced style is neither blocked nor featured as a favourite");
        prefs.set(catalog,pop,true);prefs.artist("Muse",true);
        sorted=prefs.prioritize(Arrays.asList(hit(reduced,24),hit(neutral,24),hit(positive,24),hit(followed,24)));
        check(sorted.get(0).event==followed && sorted.get(1).event==positive && sorted.get(2).event==neutral && sorted.get(3).event==reduced,"Artists > positive styles > neutral > reduced styles");
        check(prefs.liked(followed),"Followed artist overrides soft reduction");
        prefs.setBlocked(catalog,indie,true);check(prefs.disliked(followed) && !prefs.liked(followed),"Hard exclusion still overrides followed artists");
        check(!prefs.less.containsKey(indie.key),"Hard exclusion replaces same-style reduction");
        prefs.setLess(catalog,indie,true);check(!prefs.blocked.containsKey(indie.key) && !prefs.disliked(followed),"Explicit reduction replaces same-style hard exclusion");
        prefs.set(catalog,indie,true);check(!prefs.less.containsKey(indie.key) && prefs.liked(reduced),"Style interest replaces reduction");
        prefs.setLess(catalog,indie,true);check(!prefs.tags.containsKey(indie.key),"Reduction replaces same-style positive interest");
        prefs.set(catalog,music,true);check(!prefs.liked(reduced),"Specific reduction outweighs broad category interest");
        int artistCount=prefs.artists.size(),tagCount=prefs.tags.size();prefs.hide(followed,true);
        check(prefs.disliked(followed) && !prefs.liked(followed),"Hide one event overrides artist favourite");
        check(!prefs.disliked(sameStyle),"Hiding never hides other events of same style");
        check(prefs.artists.size()==artistCount && prefs.tags.size()==tagCount,"Hiding never changes artist or category preferences");
        check(prefs.hiddenEvents.size()==1 && prefs.hiddenEvents.containsKey("five"),"Hidden identity is provider event ID only");
        check(!prefs.disliked(event("new-edition","Muse","c:music","s:music:indie")),"Different event with same artist/title remains eligible");
        prefs=CulturalLikes.decode(prefs.encode());check(prefs.disliked(followed) && prefs.less.containsKey(indie.key),"Hidden events and reductions survive restart");
        check(prefs.artistMatch(followed) && prefs.tags.containsKey(music.key),"Other preferences preserved through schema6");
        prefs.hide(followed,false);check(prefs.liked(followed),"Restoring event recovers previous artist priority");
        prefs.setLess(catalog,indie,false);check(prefs.liked(reduced),"Removing reduction recovers existing broad interest");
        CulturalLikes migrated=CulturalLikes.decode("{\"version\":5,\"tags\":{\"c:music\":\"Música\"},\"blocked\":{\"c:theatre\":\"Teatro\"},\"artists\":{\"muse\":\"Muse\"}}");
        check(migrated.liked(followed) && migrated.blocked.size()==1 && migrated.less.isEmpty() && migrated.hiddenEvents.isEmpty(),"Version5 migration never infers reductions or hidden events");
        check(initial.get(0).event==reduced,"Ordering does not mutate source results");
        check(prefs.prioritize(Collections.emptyList()).isEmpty(),"New actions cannot override visibility/date filtering");
        System.out.println("CulturalActionsChecks: "+checks+" checks passed");
    }
}
