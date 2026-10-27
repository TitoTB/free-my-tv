package io.freemytv;

import java.time.*;
import java.util.*;
import java.nio.file.*;

/** Reusable provider identities, scoped taxonomy, persistence and strict separation from visibility/date filters. */
public final class CulturalLikesChecks {
    private static int checks;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message);checks++; }
    private static CulturalData.Event event(String id,String title,String... tags) {
        return new CulturalData.Event(id,title,"Indie artist description","https://www.esmadrid.com/agenda/"+id,"","","","",
                new HashSet<>(Arrays.asList(tags)),Collections.singletonList(new CulturalData.Range(LocalDate.of(2026,10,24),LocalDate.of(2026,10,24),Collections.emptySet())));
    }
    public static void main(String[] args) throws Exception {
        CulturalData.Category music=new CulturalData.Category("c:music","Música",""),indie=new CulturalData.Category("s:music:indie","Indie","c:music"),
                pop=new CulturalData.Category("s:music:pop","Pop-rock","c:music"),theatre=new CulturalData.Category("c:theatre","Teatro",""),
                duplicateName=new CulturalData.Category("s:theatre:indie","Indie","c:theatre");
        CulturalData.Catalog catalog=new CulturalData.Catalog(Arrays.asList(music,indie,pop,theatre,duplicateName),Collections.emptyList());
        CulturalData.Event favourite=event("one","First artist","c:music","s:music:indie"),future=event("two","Another artist","c:music","s:music:indie"),
                other=event("three","First artist","c:music","s:music:pop"),multiple=event("four","Festival","c:music","s:music:indie","s:music:pop");
        CulturalLikes likes=new CulturalLikes();check(!likes.liked(favourite),"No invented initial interests");
        likes.set(catalog,indie,true);check(likes.liked(favourite),"Provider subcategory matches");
        check(likes.liked(future),"Preference applies to another artist/event with a new provider event ID");
        check(!likes.liked(other),"Same artist/title and description cannot imply a preference");
        check(!likes.liked(event("same-id", "Indie", "c:theatre","s:theatre:indie")),"Subcategory IDs scoped to parent, not label");
        check(likes.tags.size()==1 && !likes.tags.containsKey("c:music"),"Liking one genre never likes all music");
        List<CulturalData.Category> choices=CulturalLikes.choices(catalog,multiple);
        check(choices.size()==2 && choices.contains(indie) && choices.contains(pop),"Multiple tags offered explicitly without broad parent");
        check(CulturalLikes.choices(catalog,event("broad","Broad listing","c:music")).equals(Collections.singletonList(music)),"Category fallback only when listing has no subcategory");
        check(CulturalLikes.choices(catalog,event("missing","No tags")).isEmpty(),"No fabricated tags for unclassified event");
        check(CulturalLikes.label(catalog,duplicateName).equals("Teatro · Indie"),"Picker labels disambiguate equal names");
        likes=CulturalLikes.decode(likes.encode());check(likes.liked(future),"Reusable preference survives restart");
        check(likes.artists.isEmpty() && !likes.encode().contains("events") && !likes.encode().contains("links"),"Category preferences never infer artists or event identities");
        CulturalData.Category renamed=new CulturalData.Category("s:music:indie","Independiente","c:music");
        CulturalData.Catalog newCatalog=new CulturalData.Catalog(Arrays.asList(music,renamed),Collections.emptyList());
        check(likes.label(newCatalog,indie.key).equals("Música · Independiente"),"Provider renames do not break IDs, display refreshed label");
        check(likes.label(CulturalData.empty(),indie.key).equals("Música · Indie"),"Saved readable label survives missing taxonomy");
        List<CulturalData.Hit> initial=Arrays.asList(new CulturalData.Hit(other,LocalDate.of(2026,10,24)),
                new CulturalData.Hit(favourite,LocalDate.of(2026,10,25)),new CulturalData.Hit(future,LocalDate.of(2026,10,26)));
        List<CulturalData.Hit> ordered=likes.prioritize(initial);
        check(ordered.size()==initial.size() && ordered.get(2).event==other,"Neutral events retained");
        check(ordered.get(0).event==favourite && ordered.get(1).event==future,"Chronological order retained within matching interests");
        check(initial.get(0).event==other,"Source order not mutated");
        check(likes.prioritize(Collections.singletonList(initial.get(0))).get(0).event==other,"Preferences cannot override date/category filters");
        check(likes.prioritize(Collections.emptyList()).isEmpty(),"No matching dates stays empty");
        likes.set(catalog,indie,false);check(!likes.liked(favourite) && !likes.liked(future),"Removing interest affects every matching event");
        likes.set(catalog,music,true);check(likes.liked(other) && likes.liked(future),"Explicit broad category preference is reusable too");
        check(CulturalLikes.decode("broken").tags.isEmpty(),"Corrupt cache fallback");
        check(CulturalLikes.decode("{\"events\":{\"one\":\"First artist\"},\"artists\":{\"indie\":\"Indie\"}}").tags.isEmpty(),"Never infer generic likes from abandoned event/artist data");
        check(CulturalLikes.decode("{\"version\":2,\"tags\":{\"one\":\"Invalid event ID\",\"s:bad\":\"Unscoped\",\"s:music:indie\":\"Indie\"}}").tags.size()==1,"Only valid scoped taxonomy IDs restored");
        CulturalLikes negative=new CulturalLikes();negative.set(catalog,indie,true);negative.setBlocked(catalog,pop,true);
        check(negative.disliked(other) && negative.disliked(multiple),"Excluded tag matches all relevant events");
        check(!negative.liked(multiple),"Exclusions win over another liked tag");
        check(negative.prioritize(initial).size()==2,"Excluded events removed, neutral and liked events retained");
        check(!negative.disliked(event("scoped","Other category","s:theatre:pop")),"Exclusions scoped to category ID");
        negative=CulturalLikes.decode(negative.encode());
        check(negative.disliked(other) && negative.liked(future),"Both preference types survive restart");
        check(negative.label(CulturalData.empty(),pop.key).equals("Música · Pop-rock"),"Excluded label survives absent taxonomy");
        negative.set(catalog,pop,true);check(!negative.disliked(other) && negative.liked(other),"Like replaces same-tag exclusion");
        negative.setBlocked(catalog,pop,true);check(negative.disliked(other) && !negative.tags.containsKey(pop.key),"Exclusion replaces same-tag like");
        negative.setBlocked(catalog,pop,false);check(!negative.disliked(other),"Removing exclusion restores visibility");
        negative.setBlocked(catalog,music,true);check(negative.disliked(future),"Explicit broad exclusion wins over narrow like");
        check(CulturalLikes.decode("{\"version\":2,\"tags\":{\"s:music:indie\":\"Indie\"}}").liked(future),"Version 2 likes migrate intact");
        check(CulturalLikes.decode("{\"version\":3,\"blocked\":{\"one\":\"Bad\",\"s:music:pop\":\"Pop\"}}").blocked.size()==1,"Invalid exclusion identities rejected");
        if(args.length>0) {
            CulturalData.Catalog live=CulturalData.parse(Files.readAllBytes(Paths.get(args[0])));
            int reusable=0;
            for(CulturalData.Category category:live.categories) {
                if(category.parent.isEmpty())continue;
                List<CulturalData.Event> matches=new ArrayList<>();
                for(CulturalData.Event event:live.events)if(event.categories.contains(category.key))matches.add(event);
                if(matches.size()>1) {
                    CulturalLikes interest=new CulturalLikes();interest.set(live,category,true);
                    check(interest.liked(matches.get(0)) && interest.liked(matches.get(1)),"Real provider interest applies to multiple listings");reusable++;
                }
            }
            check(reusable>20,"Many real reusable subcategories available");
            System.out.println("Reusable live subcategories verified: "+reusable);
        }
        System.out.println("CulturalLikesChecks: "+checks+" checks passed");
    }
}
