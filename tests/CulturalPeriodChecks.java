package io.freemytv;

import java.time.*;
import java.util.*;
import java.nio.file.*;

/** Temporal meaning, month ordering and complete remote traversal across every layout/page boundary. */
public final class CulturalPeriodChecks {
    private static int checks;
    private static final LocalDate TODAY=LocalDate.of(2026,10,24);
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static CulturalData.Event event(String id,String from,String until,Integer... weekdays) {
        return new CulturalData.Event(id,id,"","https://www.esmadrid.com/agenda/"+id,"","","","",Collections.singleton("c:all"),
                Collections.singletonList(new CulturalData.Range(LocalDate.parse(from),LocalDate.parse(until),new HashSet<>(Arrays.asList(weekdays)))));
    }
    private static boolean has(List<CulturalData.Hit> hits,String id){return hits.stream().anyMatch(h->h.event.id.equals(id));}
    private static void traversal(List<CulturalData.Hit> results,int tab,boolean liked) {
        CulturalPaging start=new CulturalPaging();start.update(results,0,tab,liked);
        check(start.count>0,"Nonempty page");
        Queue<int[]> queue=new ArrayDeque<>();queue.add(new int[]{0,start.first});
        Set<String> visited=new HashSet<>();Set<Integer> reachable=new HashSet<>();
        while(!queue.isEmpty()) {
            int[] state=queue.remove();String key=state[0]+":"+state[1];if(!visited.add(key))continue;
            check(visited.size()<10000,"Navigation terminates");
            for(int action=0;action<4;action++) {
                CulturalPaging page=new CulturalPaging();page.update(results,state[1],tab,liked);page.update(results,state[0],tab,liked);
                check(page.index(page.slot(state[0]))==state[0],"Focused result maps back to its visible slot");
                check(page.count<=(tab==3?10:5) && page.count>0,"Complete bounded visible page");
                Set<Integer> unique=new HashSet<>();int height=0;YearMonth month=null;
                for(int slot=0;slot<page.count;slot++) {
                    int index=page.index(slot);check(index>=0 && index<results.size(),"Visible index in range");check(unique.add(index),"No duplicate feature in companions");
                    if(page.mode==CulturalPaging.AGENDA){YearMonth next=YearMonth.from(results.get(index).date);height+=74+(next.equals(month)?0:28);month=next;}
                }
                if(page.mode==CulturalPaging.AGENDA)check(height<=340,"Month headings and rows fit on screen");
                if(tab==3)check(page.count<=10 && page.first==state[0]/10*10,"Monthly grid fits ten results per page");
                if(tab<3)check(page.index(0)==state[0]/5*5,"Editorial feature advances with its complete page, including favourites");
                reachable.add(state[0]);int next=action<2?page.horizontal(action==0?-1:1):page.vertical(action==2?-1:1);
                if(next>=0){check(next<results.size(),"Remote result in range");page.update(results,next,tab,liked);queue.add(new int[]{next,page.first});}
            }
        }
        check(reachable.size()==results.size(),"All results reachable by remote without skipping pages");
    }
    public static void main(String[] args) throws Exception {
        CulturalData.Event old=event("old-exhibition","2026-08-01","2027-01-31"),show=event("show","2026-10-24","2026-10-24"),
                opening=event("opening","2026-10-29","2027-01-31"),nov=event("november","2026-11-02","2026-11-02"),
                weekly=event("weekly","2026-10-01","2026-12-31",6),dec=event("december","2026-12-03","2026-12-03");
        CulturalData.Catalog catalog=new CulturalData.Catalog(Collections.emptyList(),Arrays.asList(old,show,opening,nov,weekly,dec));
        Set<String> all=Collections.singleton(CulturalData.ALL);
        List<CulturalData.Hit> today=CulturalData.filter(catalog,all,0,TODAY),tomorrow=CulturalData.filter(catalog,all,1,TODAY),
                weekend=CulturalData.filter(catalog,all,2,TODAY),month=CulturalData.filter(catalog,all,3,TODAY),future=CulturalData.filter(catalog,all,4,TODAY);
        check(today.get(0).event==show,"Today prioritises an actual one-off performance");
        check(!new CulturalData.Hit(old,TODAY,new CulturalData.Range(TODAY.minusMonths(1),TODAY.plusMonths(1),new HashSet<>(Arrays.asList(1,2,3,4,5,6,7)))).scheduled,"Every-day range is continuous, not an individual weekday schedule");
        check(!has(tomorrow,"show") && !has(tomorrow,"weekly"),"Tomorrow respects date/weekday instead of reusing nearest event");
        check(weekend.get(0).event==show && weekend.get(1).event==weekly,"Weekend punctual then scheduled before continuous activity");
        check(month.indexOf(month.stream().filter(h->h.event==opening).findFirst().get())<month.indexOf(month.stream().filter(h->h.event==old).findFirst().get()),"Monthly opening prioritised over long-running exhibition");
        CulturalData.Hit running=month.stream().filter(h->h.event==old).findFirst().get();
        check(running.ongoing(TODAY) && running.end.equals(LocalDate.of(2027,1,31)),"Ongoing activity retains real ending for Until label");
        check(future.isEmpty(),"Removed coming-up period cannot return future events");
        check(CulturalData.filter(catalog,Collections.emptySet(),4,TODAY).isEmpty(),"Temporal changes never bypass category visibility");
        CulturalLikes likes=new CulturalLikes();likes.tags.put("c:all","All");
        List<CulturalData.Hit> grouped=likes.prioritize(Arrays.asList(new CulturalData.Hit(dec,LocalDate.of(2026,12,3)),new CulturalData.Hit(nov,LocalDate.of(2026,11,2))),true);
        check(grouped.get(0).event==nov,"Month chronology outranks interest priority");
        CulturalData.Catalog year=new CulturalData.Catalog(Collections.emptyList(),Arrays.asList(event("dec","2026-12-31","2027-02-01"),event("jan","2027-01-01","2027-01-01")));
        check(CulturalData.filter(year,all,1,LocalDate.of(2026,12,31)).isEmpty(),"Tomorrow cannot cross December/January boundary");
        for(int size:new int[]{1,2,3,4,5,6,7,9,10,11,15,19,20,21}) {
            List<CulturalData.Hit> hits=new ArrayList<>();for(int i=0;i<size;i++)hits.add(new CulturalData.Hit(show,LocalDate.of(2026,11,1).plusMonths(i/3).plusDays(i%3)));
            for(int tab=0;tab<4;tab++){traversal(hits,tab,false);if(tab<3)traversal(hits,tab,true);}
            CulturalPaging page=new CulturalPaging();page.update(hits,0,3,false);page.update(hits,0,4,false);check(page.count>0,"Same result list can switch from posters to agenda");
        }
        CulturalPaging empty=new CulturalPaging();empty.update(Collections.emptyList(),0,4,false);check(empty.count==0 && empty.index(0)==-1,"Empty agenda has no phantom card");
        List<CulturalData.Hit> editorial=new ArrayList<>();
        for(int i=0;i<12;i++)editorial.add(new CulturalData.Hit(show,TODAY));
        for(boolean favourite:new boolean[]{false,true}) {
            CulturalPaging page=new CulturalPaging();page.update(editorial,0,0,favourite);
            check(page.count==5,"Editorial shows feature and four companions");
            check(page.horizontal(1)==1,"Feature moves right to upper first companion");
            page.update(editorial,1,0,favourite);check(page.vertical(1)==2 && page.horizontal(1)==3,"First companion column follows visible geometry");
            page.update(editorial,2,0,favourite);check(page.vertical(-1)==1 && page.horizontal(1)==4,"Lower row moves across companion columns");
            page.update(editorial,3,0,favourite);check(page.vertical(1)==4 && page.horizontal(1)==5,"Right edge advances to next feature");
            page.update(editorial,5,0,favourite);check(page.index(0)==5 && page.count==5,"Next page replaces featured event too");
            check(page.horizontal(-1)==4,"Left edge returns to previous page");
            page.update(editorial,10,0,favourite);check(page.count==2 && page.index(0)==10,"Partial last page has a fresh feature");
            page.update(editorial,11,0,favourite);check(page.horizontal(1)==11 && page.vertical(1)==-2,"Partial last page never targets absent companion");
        }
        CulturalPaging monthly=new CulturalPaging();monthly.update(editorial,0,3,false);
        check(monthly.count==10 && monthly.vertical(1)==5,"Monthly grid shows two rows of five and moves down by column");
        monthly.update(editorial,9,3,false);
        check(monthly.vertical(-1)==4 && monthly.horizontal(1)==10,"Monthly lower row moves up and advances to next page");
        monthly.update(editorial,10,3,false);
        check(monthly.count==2 && monthly.index(0)==10 && monthly.vertical(1)==-2,"Partial monthly page has no phantom lower row");
        check(monthly.horizontal(-1)==9,"Monthly navigation returns to previous page");
        CulturalData.Catalog october=CulturalData.monthly(catalog,TODAY);
        check(october.events.size()==4,"Monthly active catalogue discards November/December-only events");
        check(october.categories==catalog.categories || october.categories.equals(catalog.categories),"Monthly horizon preserves full category choices");
        CulturalData.Catalog november=CulturalData.monthly(catalog,LocalDate.of(2026,11,1));
        check(november.events.stream().anyMatch(e->e.id.equals("november")),"Saved source re-evaluates new month without changing preferences");
        for(int offset=0;offset<800;offset++) {
            LocalDate day=LocalDate.of(2026,1,1).plusDays(offset),end=day.withDayOfMonth(day.lengthOfMonth());
            CulturalData.Catalog boundary=new CulturalData.Catalog(Collections.emptyList(),Arrays.asList(
                event("now",day.toString(),end.toString()),event("later",end.plusDays(1).toString(),end.plusDays(8).toString())));
            CulturalData.Catalog bounded=CulturalData.monthly(boundary,day);
            check(bounded.events.size()==1 && bounded.events.get(0).id.equals("now"),"Future-only listing not loaded into active catalogue");
            for(int tab=0;tab<4;tab++)for(CulturalData.Hit hit:CulturalData.filter(boundary,all,tab,day))
                check(!hit.date.isBefore(day) && !hit.date.isAfter(end),"Every displayed date stays in current month, including leap year/weekends");
        }
        if(args.length>0) {
            CulturalData.Catalog live=CulturalData.parse(Files.readAllBytes(Paths.get(args[0])));
            for(int tab=0;tab<4;tab++) {
                List<CulturalData.Hit> hits=CulturalData.filter(live,all,tab,TODAY);
                System.out.println("Live tab "+tab+": "+hits.size()+" listings");
                for(CulturalData.Hit hit:hits)if(tab==4)check(!hit.start.isBefore(LocalDate.of(2026,11,1)),"Real upcoming event starts next month or later");
            }
        }
        System.out.println("CulturalPeriodChecks: "+checks+" checks passed");
    }
}
