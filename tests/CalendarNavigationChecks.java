package io.freemytv;

import java.time.*;
import java.util.*;

public final class CalendarNavigationChecks {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args) {
        for(int year=2024;year<=2028;year++)for(int month=1;month<=12;month++) {
            YearMonth ym=YearMonth.of(year,month);
            CalendarNavigation nav=new CalendarNavigation(ym.atDay(1));
            check(nav.count()==ym.lengthOfMonth()+2,"Exactly two month buttons and valid date nodes");
            Set<LocalDate> reached=new HashSet<>();
            for(int i=1;i<=ym.lengthOfMonth();i++) {
                nav.select(i+1);check(nav.selected.equals(ym.atDay(i)) && nav.node()==i+1,"Accessible date maps back to actual day");
                reached.add(nav.selected);
                nav.move(0,-1);check(i<=7?nav.header:nav.selected.equals(ym.atDay(i-7)),"Up reaches prior week or month controls");
                nav.select(i+1);nav.move(0,1);check(nav.selected.equals(ym.atDay(i+7<=ym.lengthOfMonth()?i+7:i)),"Down never points at a blank trailing cell");
            }
            check(reached.size()==ym.lengthOfMonth(),"All days reachable");
            nav.select(2);nav.move(-1,0);check(nav.selected.equals(ym.atDay(1).minusDays(1)),"Left at first day crosses previous month");
            nav=new CalendarNavigation(ym.atEndOfMonth());nav.move(1,0);check(nav.selected.equals(ym.plusMonths(1).atDay(1)),"Right at last day crosses next month/year");
            nav.select(0);YearMonth previous=nav.month();nav.move(1,0);check(nav.month().equals(previous.plusMonths(1)),"Header lateral control advances month");
            check(!nav.move(0,-1),"Up from month controls returns to device row");
            nav.move(0,1);check(!nav.header,"Down returns to focused calendar day");
        }
        CalendarNavigation leap=new CalendarNavigation(LocalDate.of(2024,1,31));leap.shift(1);
        check(leap.selected.equals(LocalDate.of(2024,2,29)),"Month advance clamps to leap day");
        ZoneId zone=ZoneId.of("Europe/Madrid");LocalDate day=LocalDate.of(2026,10,25);
        CalendarAgenda.Event all=new CalendarAgenda.Event("calendar.a","A",1,"All day","a",day.atStartOfDay(zone),day.plusDays(1).atStartOfDay(zone),true);
        CalendarAgenda.Event overnight=new CalendarAgenda.Event("calendar.b","B",2,"Overnight","b",day.minusDays(1).atTime(23,0).atZone(zone),day.atTime(2,0).atZone(zone),false);
        CalendarAgenda agenda=new CalendarAgenda(Arrays.asList(new CalendarAgenda.Source("calendar.a","A",1,Arrays.asList(all),true),new CalendarAgenda.Source("calendar.b","B",2,Arrays.asList(overnight),true)),zone,true);
        check(agenda.on(day).size()==2,"Popup aggregates all calendars including overnight and all-day events across DST");
        check(agenda.on(day.plusDays(1)).isEmpty(),"Exclusive end date cannot leak into following day");
        System.out.println("CalendarNavigationChecks: "+checks+" checks passed");
    }
}
