package io.freemytv;

import java.time.*;
import java.util.*;

/** Affinity, exploration, diversity, local exposure decay and invariant eligibility tests. */
public final class CulturalRecommendationsChecks {
    private static int checks;
    private static final LocalDate TODAY=LocalDate.of(2026,10,24);
    private static final long NOW=TODAY.atStartOfDay(CulturalData.ZONE).toInstant().toEpochMilli();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static CulturalData.Event event(String id,String title,String genre,String venue,String description){return new CulturalData.Event(id,title,description,"https://www.esmadrid.com/agenda/test","","","","",new HashSet<>(Arrays.asList("c:music",genre)),Collections.emptyList(),venue,venue);}
    private static CulturalData.Hit hit(CulturalData.Event event,int day){return new CulturalData.Hit(event,TODAY.plusDays(day));}
    private static List<String> ids(List<CulturalData.Hit> hits){List<String> result=new ArrayList<>();for(CulturalData.Hit hit:hits)result.add(hit.event.id);return result;}
    public static void main(String[] args)throws Exception {
        CulturalData.Category music=new CulturalData.Category("c:music","Música",""),indie=new CulturalData.Category("s:music:indie","Indie","c:music"),jazz=new CulturalData.Category("s:music:jazz","Jazz","c:music");
        CulturalData.Catalog catalog=new CulturalData.Catalog(Arrays.asList(music,indie,jazz),Collections.emptyList());
        CulturalLikes likes=new CulturalLikes();likes.set(catalog,music,true);likes.set(catalog,indie,true);likes.artist("Muse",true);
        CulturalData.Event category=event("general","General","s:music:other","A",""),sub=event("indie","Indie act","s:music:indie","B",""),artist=event("artist","Muse","s:music:other","C","");
        check(likes.affinity(artist)>likes.affinity(sub) && likes.affinity(sub)>likes.affinity(category),"Artist affinity > specific subcategory > broad category");
        CulturalRecommendations.History history=new CulturalRecommendations.History();
        List<CulturalData.Hit> input=Arrays.asList(hit(category,5),hit(sub,6),hit(artist,7));
        CulturalRecommendations.Plan plan=CulturalRecommendations.plan(input,likes,catalog,history,TODAY,NOW,1);
        check(plan.results.get(0).event==artist,"First visit starts with strongest affinity");
        check(plan.reasons.get(input.get(2)).type.equals("artist") && plan.reasons.get(input.get(2)).label.equals("Muse"),"Artist recommendation has a truthful editable name reason");
        check(plan.reasons.get(input.get(1)).type.equals("subcategory") && plan.reasons.get(input.get(1)).label.equals("Indie"),"Subcategory explanation uses provider label");
        check(plan.reasons.get(input.get(0)).type.equals("category"),"Broad preference identified separately");
        CulturalLikes onlyIndie=new CulturalLikes();onlyIndie.set(catalog,indie,true);
        List<CulturalData.Hit> varied=new ArrayList<>();
        for(int i=0;i<8;i++)varied.add(hit(event("indie"+i,"Indie "+i,"s:music:indie","A",""),8));
        for(int i=0;i<6;i++)varied.add(hit(event("neutral"+i,"Jazz "+i,"s:music:jazz","J"+i,""),8));
        plan=CulturalRecommendations.plan(varied,onlyIndie,catalog,history,TODAY,NOW,1);
        check(onlyIndie.affinity(plan.results.get(2).event)==0 && plan.reasons.get(plan.results.get(2)).discovery,"First editorial page reserves a genuine neutral discovery slot");
        check(!plan.results.get(0).event.venueId.equals(plan.results.get(2).event.venueId),"Discovery also varies the venue when alternatives exist");
        check(new HashSet<>(ids(plan.results)).size()==varied.size(),"Mixing neither drops nor duplicates eligible events");
        check(ids(plan.results).equals(ids(CulturalRecommendations.plan(varied,onlyIndie,catalog,history,TODAY,NOW,1).results)),"Same visit/input produces stable ordering");
        CulturalData.Event reduced=event("reduced","Reduced jazz","s:music:jazz","Z","");onlyIndie.setLess(catalog,jazz,true);
        List<CulturalData.Hit> soft=new ArrayList<>(varied);soft.add(hit(reduced,8));
        plan=CulturalRecommendations.plan(soft,onlyIndie,catalog,history,TODAY,NOW,1);
        check(plan.results.subList(0,8).stream().noneMatch(h->onlyIndie.reduced(h.event)),"Diversification never promotes reduced styles ahead of non-reduced results");
        for(Map.Entry<CulturalData.Hit,CulturalRecommendations.Decision> row:plan.reasons.entrySet())if(row.getValue().discovery)check(!onlyIndie.reduced(row.getKey().event),"Discovery never bypasses show-less preference");
        onlyIndie.hide(varied.get(0).event,true);onlyIndie.setBlocked(catalog,jazz,true);
        plan=CulturalRecommendations.plan(soft,onlyIndie,catalog,history,TODAY,NOW,2);
        check(!ids(plan.results).contains(varied.get(0).event.id) && plan.results.stream().noneMatch(h->onlyIndie.disliked(h.event)),"Hidden/style exclusions remain absolute in feature and discovery");
        onlyIndie.hiddenEvents.clear();onlyIndie.blocked.clear();onlyIndie.less.clear();
        CulturalRecommendations.Plan first=CulturalRecommendations.plan(varied,onlyIndie,catalog,history,TODAY,NOW,1),second=CulturalRecommendations.plan(varied,onlyIndie,catalog,history,TODAY,NOW,2);
        check(!first.results.get(0).event.id.equals(second.results.get(0).event.id),"New visit rotates amongst comparable favourites");
        history.record(first.results.get(0).event.id,true,NOW);
        plan=CulturalRecommendations.plan(varied,onlyIndie,catalog,history,TODAY,NOW+1000,3);
        check(!plan.results.get(0).event.id.equals(first.results.get(0).event.id),"Recently featured event yields to another related suggestion");
        check(!history.record(first.results.get(0).event.id,true,NOW+2000),"Repeated redraw/page/tab within30min is not another impression");
        CulturalData.Hit distant=hit(category,7),soon=hit(category,1);
        for(int i=1;i<=4;i++)history.record(category.id,false,NOW+i*31*60_000L);
        long later=NOW+5*31*60_000L;
        check(history.penalty(distant,TODAY,later)>0,"Several real exposures temporarily reduce a distant event");
        check(history.penalty(soon,TODAY,later)==0,"Upcoming performance is protected from repetition penalty");
        check(history.penalty(distant,TODAY,later+8*CulturalRecommendations.DAY)==0,"Repetition penalty expires without hiding the event");
        CulturalRecommendations.History restored=CulturalRecommendations.History.decode(history.encode(),later);
        check(restored.penalty(distant,TODAY,later)==history.penalty(distant,TODAY,later),"Exposure history persists across restart");
        check(restored.featured(first.results.get(0).event.id)==NOW,"Last featured record survives restart");
        CulturalRecommendations.History snapshot=restored.copy();int previousCount=snapshot.entries.get(category.id).count;
        restored.record(category.id,false,later+31*60_000L);check(snapshot.entries.get(category.id).count==previousCount,"Background recommendation history snapshot is independent of later exposures");
        CulturalRecommendations.History few=new CulturalRecommendations.History();few.record(category.id,false,NOW);few.record(category.id,false,NOW+31*60_000L);
        check(few.penalty(distant,TODAY,NOW+32*60_000L)==0,"One or two impressions do not count as excessive repetition");
        check(CulturalRecommendations.History.decode("broken",NOW).entries.isEmpty(),"Corrupt history safely resets independently of explicit preferences");
        check(CulturalRecommendations.series(event("cycle1","Act One","s:music:indie","A","Forma parte del ciclo Villanos del Jazz este mes.")).equals(CulturalRecommendations.series(event("cycle2","Act Two","s:music:jazz","B","En el ciclo Villanos del Jazz actúa este artista."))),"Explicit named cycles recognised without inventing provider IDs");
        check(CulturalRecommendations.series(event("generic","Act","s:music:indie","A","El festival internacional presenta artistas.")).isEmpty(),"Unstructured generic festival wording does not create a fake cycle");
        CulturalLikes broad=new CulturalLikes();broad.set(catalog,music,true);
        List<CulturalData.Hit> venuePool=Arrays.asList(hit(event("a1","One","s:music:indie","A",""),7),hit(event("a2","Two","s:music:indie","A",""),7),hit(event("b1","Three","s:music:jazz","B",""),7));
        CulturalRecommendations.Plan diverse=CulturalRecommendations.plan(venuePool,broad,catalog,new CulturalRecommendations.History(),TODAY,NOW,1);
        check(diverse.results.get(1).event.id.equals("b1") && diverse.reasons.get(diverse.results.get(1)).variety,"Diversity varies style/venue even without neutral discovery candidates");
        List<CulturalData.Hit> cycles=Arrays.asList(hit(event("c1","Ciclo Uno: Act A","s:music:indie","A",""),7),hit(event("c2","Ciclo Uno: Act B","s:music:indie","B",""),7),hit(event("c3","Ciclo Dos: Act C","s:music:indie","C",""),7));
        check(CulturalRecommendations.plan(cycles,broad,catalog,new CulturalRecommendations.History(),TODAY,NOW,1).results.get(1).event.id.equals("c3"),"Recognisable cycles diversify independently of venue");
        check(ids(input).equals(Arrays.asList("general","indie","artist")),"Source date ordering not mutated");
        check(CulturalRecommendations.plan(Collections.emptyList(),likes,catalog,history,TODAY,NOW,1).results.isEmpty(),"Cannot invent candidates outside selected dates/categories");
        for(int size=1;size<150;size++){
            List<CulturalData.Hit> many=new ArrayList<>();for(int i=0;i<size;i++)many.add(hit(event("id"+i,"Act "+i,i%3==0?"s:music:indie":"s:music:jazz","V"+(i%5),""),i%7));
            CulturalRecommendations.Plan mixed=CulturalRecommendations.plan(many,likes,catalog,history,TODAY,NOW,size);
            check(mixed.results.size()==size && new HashSet<>(ids(mixed.results)).size()==size,"Bounded first24 mixing preserves all results and pagination inputs");
        }
        if(args.length>0) {
            CulturalData.Catalog live=CulturalData.monthly(CulturalData.parse(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0]))),TODAY);
            CulturalLikes liveLikes=new CulturalLikes();for(CulturalData.Category c:live.categories)if(c.name.equals("Indie"))liveLikes.set(live,c,true);
            for(int tab=0;tab<4;tab++) {
                List<CulturalData.Hit> eligible=CulturalData.filter(live,Collections.singleton(CulturalData.ALL),tab,TODAY);long start=System.nanoTime();
                CulturalRecommendations.Plan mixed=CulturalRecommendations.plan(eligible,liveLikes,live,new CulturalRecommendations.History(),TODAY,NOW,1);
                System.out.println("Live recommendations tab "+tab+": "+mixed.results.size()+" results, "+((System.nanoTime()-start)/1_000_000)+"ms");
                check(new HashSet<>(ids(mixed.results)).equals(new HashSet<>(ids(eligible))),"Live recommendation mixing preserves exact date/category eligibility");
                check(mixed.reasons.size()==mixed.results.size(),"Every live result has a consultable reason");
            }
        }
        System.out.println("CulturalRecommendationsChecks: "+checks+" checks passed");
    }
}
