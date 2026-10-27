package io.freemytv;

import java.time.*;
import java.util.*;
import org.json.*;

/** Synthetic fixtures only: no household calendar content or network calls. */
public final class CalendarAgendaChecks {
    private static int checks;
    private static void check(boolean value) { if(!value) throw new AssertionError("Calendar check "+checks); checks++; }
    private static JSONObject event(String title,String from,String to,boolean allDay) throws Exception {
        String field=allDay?"date":"dateTime";
        return new JSONObject().put("summary",title).put("uid",title)
                .put("start",new JSONObject().put(field,from)).put("end",new JSONObject().put(field,to));
    }
    private static List<CalendarAgenda.Event> parse(JSONArray rows) {
        return CalendarAgenda.parse(rows,"calendar.synthetic","Pruebas",0xFF123456,ZoneId.of("Europe/Madrid"));
    }
    public static void main(String[] args) throws Exception {
        ZoneId zone=ZoneId.of("Europe/Madrid"); ZonedDateTime now=ZonedDateTime.parse("2026-10-24T08:00:00+02:00[Europe/Madrid]");
        List<CalendarAgenda.Event> events=parse(new JSONArray()
                .put(event("Pasado","2026-10-24T06:00:00+02:00","2026-10-24T07:00:00+02:00",false))
                .put(event("Prueba Android TV","2026-10-24T12:30:00+02:00","2026-10-24T13:30:00+02:00",false))
                .put(event("Mañana","2026-10-25T12:30:00+01:00","2026-10-25T13:30:00+01:00",false))
                .put(event("Lunes","2026-10-26T12:30:00+01:00","2026-10-26T13:30:00+01:00",false)));
        check(events.size()==4);
        CalendarAgenda.Source source=new CalendarAgenda.Source("calendar.synthetic","Pruebas",0,events,true);
        CalendarAgenda.Event next=source.next(now);
        check(next==events.get(1));
        check(CalendarAgenda.format(next,now).equals("Hoy a las 12:30: Prueba Android TV"));
        check(CalendarAgenda.format(events.get(2),now).equals("Mañana a las 12:30: Mañana"));
        check(CalendarAgenda.format(events.get(3),now).equals("Lunes, 26 a las 12:30: Lunes"));
        check(events.get(2).start.getOffset().equals(ZoneOffset.ofHours(1))); // Madrid DST boundary.
        List<CalendarAgenda.Event> allDay=parse(new JSONArray().put(event("Todo el día","2026-10-24","2026-10-26",true)));
        CalendarAgenda.Event day=allDay.get(0);
        check(day.upcoming(now)); check(day.allDay);
        check(CalendarAgenda.format(day,now).equals("Hoy · Todo el día: Todo el día"));
        check(day.on(LocalDate.of(2026,10,24))); check(day.on(LocalDate.of(2026,10,25)));
        check(!day.on(LocalDate.of(2026,10,26))); check(!day.upcoming(now.plusDays(2)));
        check(day.end.toEpochSecond()-day.start.toEpochSecond()==49*3600); // Exclusive end across DST.
        List<CalendarAgenda.Event> timezone=parse(new JSONArray().put(event("UTC","2026-10-24T22:30:00Z","2026-10-24T23:30:00Z",false)));
        check(timezone.get(0).start.getDayOfMonth()==25);
        check(CalendarAgenda.format(timezone.get(0),now).startsWith("Mañana a las 00:30:"));
        JSONArray bad=new JSONArray().put(event("Cancelado","2026-10-24","2026-10-25",true).put("status","cancelled"))
                .put(event("Inválido","2026-10-26","2026-10-25",true)).put(new JSONObject())
                .put(event("Duplicado","2026-10-27","2026-10-28",true))
                .put(event("Duplicado","2026-10-27","2026-10-28",true))
                .put(event("Duplicado","2026-11-27","2026-11-28",true));
        check(parse(bad).size()==2); // Deduplicate occurrence, preserve recurring instances.
        CalendarAgenda.Source second=new CalendarAgenda.Source("calendar.second","Segundo",0,allDay,true);
        CalendarAgenda agenda=new CalendarAgenda(Arrays.asList(source,second),zone,true);
        check(agenda.next(now)==day);
        check(agenda.next(now.plusYears(1))==null);
        check(new CalendarAgenda(Collections.emptyList(),zone,true).next(now)==null);
        check(CalendarAgenda.color("calendar.synthetic")==CalendarAgenda.color("calendar.synthetic"));
        check(CalendarAgenda.color("calendar.synthetic")>>>24==255);
        Set<Integer> used=new HashSet<>();
        for(String id:Arrays.asList("calendar.one","calendar.two","calendar.three","calendar.four","calendar.five"))
            check(used.add(CalendarAgenda.unusedColor(id,used)));
        JSONObject floating=event("Otra zona","2026-10-24T08:00:00","2026-10-24T09:00:00",false);
        floating.getJSONObject("start").put("timeZone","America/New_York");
        floating.getJSONObject("end").put("timeZone","America/New_York");
        check(parse(new JSONArray().put(floating)).get(0).start.getHour()==14);
        check(CalendarAgenda.clean("  Prueba\n Android \tTV ").equals("Prueba Android TV"));
        check(CalendarAgenda.clean("Trabajo 🥑").equals("Trabajo"));
        check(CalendarAgenda.clean("🎁 Evento de ejemplo").equals("Evento de ejemplo"));
        check(CalendarAgenda.clean("👨‍👩‍👧‍👦 Reunión 👍🏽 🇪🇸").equals("Reunión"));
        check(CalendarAgenda.clean("1️⃣ 2️⃣ #️⃣ *️⃣ 12:30 860€ 22°C").equals("12:30 860€ 22°C"));
        check(CalendarAgenda.clean("❤️ ☀️ ✅ Prueba").equals("Prueba"));
        check(CalendarAgenda.panelName("calendar.personal","Personal").equals("Personal"));
        check(CalendarAgenda.panelName("calendar.other","Otro").equals("Otro"));
        check(CalendarAgenda.fixedColor("calendar.example")==0);
        check(CalendarAgenda.fixedColor("calendar.other")==0);
        check(parse(new JSONArray().put(event("🎁","2026-10-24","2026-10-25",true))).get(0).title.equals("Sin título"));
        check(CalendarAgenda.format(parse(new JSONArray().put(event("Enero","2027-01-02","2027-01-03",true))).get(0),now).equals("Sábado, 02 · Todo el día: Enero"));
        check(source.next(now.plusMinutes(280)).title.equals("Mañana")); // Timed event already started is not next.
        check(CalendarAgenda.limit("Breve",70).equals("Breve"));
        String longTitle=String.join("",Collections.nCopies(90,"á"));
        String shortTitle=CalendarAgenda.limit(longTitle,70);
        check(shortTitle.codePointCount(0,shortTitle.length())==70);
        check(shortTitle.endsWith("…"));
        check(CalendarAgenda.limit("A𝄞BC",3).equals("A𝄞…"));
        check(CalendarAgenda.limit("Texto",1).equals("…"));
        check(CalendarAgenda.limit("Texto",0).isEmpty());
        System.out.println("CalendarAgendaChecks: "+checks+" checks passed");
    }
}
