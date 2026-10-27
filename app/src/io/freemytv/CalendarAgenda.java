package io.freemytv;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Calendar-only immutable data. Dates without times stay local dates; end dates are exclusive. */
final class CalendarAgenda {
    static final int ATTENTION=0xFFFFD54F;
    static final Locale SPANISH=new Locale("es","ES");
    static final int[] COLORS={0xFF81C784,0xFF64B5F6,0xFFBA9CF4,0xFFFFB74D,0xFF4DD0C8,0xFFF48FB1,0xFFFF8A65};
    static final class Event {
        final String calendar,name,title,key;
        final int color;
        final ZonedDateTime start,end;
        final boolean allDay;
        Event(String calendar,String name,int color,String title,String key,ZonedDateTime start,ZonedDateTime end,boolean allDay) {
            this.calendar=calendar; this.name=name; this.color=color; this.title=title; this.key=key;
            this.start=start; this.end=end; this.allDay=allDay;
        }
        boolean upcoming(ZonedDateTime now) { return allDay?end.isAfter(now):!start.isBefore(now); }
        boolean on(LocalDate day) {
            return !start.toLocalDate().isAfter(day) && end.toInstant().isAfter(day.atStartOfDay(start.getZone()).toInstant());
        }
    }
    static final class Source {
        final String id,name;
        final int color;
        final List<Event> events;
        final boolean available;
        Source(String id,String name,int color,List<Event> events,boolean available) {
            this.id=id; this.name=name; this.color=color;
            this.events=Collections.unmodifiableList(new ArrayList<>(events)); this.available=available;
        }
        Event next(ZonedDateTime now) { for(Event event:events) if(event.upcoming(now)) return event; return null; }
    }
    final List<Source> sources;
    final ZoneId zone;
    final boolean available;
    CalendarAgenda(List<Source> sources,ZoneId zone,boolean available) {
        this.sources=Collections.unmodifiableList(new ArrayList<>(sources)); this.zone=zone; this.available=available;
    }
    Event next(ZonedDateTime now) {
        Event next=null;
        for(Source source:sources) {
            Event event=source.next(now);
            if(event!=null && (next==null || compare(event,next)<0)) next=event;
        }
        return next;
    }
    List<Event> on(LocalDate day) {
        List<Event> result=new ArrayList<>();
        for(Source source:sources)for(Event event:source.events)if(event.on(day))result.add(event);
        Collections.sort(result,CalendarAgenda::compare);return result;
    }
    static int compare(Event a,Event b) {
        int order=a.start.toInstant().compareTo(b.start.toInstant());
        return order!=0?order:(a.calendar+"\n"+a.key).compareTo(b.calendar+"\n"+b.key);
    }
    static String clean(String text) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i),next=i+Character.charCount(cp);
            // Keycaps are emoji sequences whose first character is an ordinary digit, # or *.
            if((cp>='0' && cp<='9') || cp=='#' || cp=='*') {
                int end=next;
                if(end<text.length() && text.codePointAt(end)==0xFE0F) end++;
                if(end<text.length() && text.codePointAt(end)==0x20E3) { i=end+1; continue; }
            }
            boolean emoji=cp>=0x1F000 && cp<=0x1FAFF
                    || cp>=0x2600 && cp<=0x27BF || cp>=0x2194 && cp<=0x2199
                    || cp==0x21A9 || cp==0x21AA || cp==0x200D || cp==0x20E3 || cp==0xFE0F || cp==0xFE0E
                    || cp==0xA9 || cp==0xAE || cp==0x203C || cp==0x2049 || cp==0x2122 || cp==0x2139
                    || cp==0x231A || cp==0x231B || cp==0x2328 || cp==0x23CF || cp>=0x23E9 && cp<=0x23F3
                    || cp>=0x23F8 && cp<=0x23FA || cp==0x24C2 || cp==0x25AA || cp==0x25AB
                    || cp==0x25B6 || cp==0x25C0 || cp>=0x25FB && cp<=0x25FE || cp==0x2934 || cp==0x2935
                    || cp>=0x2B05 && cp<=0x2B07 || cp==0x2B1B || cp==0x2B1C || cp==0x2B50 || cp==0x2B55
                    || cp==0x3030 || cp==0x303D || cp==0x3297 || cp==0x3299
                    || cp>=0xE0020 && cp<=0xE007F;
            if(!emoji) out.appendCodePoint(cp);
            i=next;
        }
        return out.toString().replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
    }
    static String panelName(String id,String name) { return name; }
    static int fixedColor(String id) { return 0; }
    static ZonedDateTime date(JSONObject value,ZoneId zone) {
        String date=value.optString("date");
        if(!date.isEmpty()) return LocalDate.parse(date).atStartOfDay(zone);
        String text=value.optString("dateTime");
        try { return ZonedDateTime.parse(text).withZoneSameInstant(zone); }
        catch(Exception ignored) {
            ZoneId sourceZone=zone;
            if(!value.optString("timeZone").isEmpty()) sourceZone=ZoneId.of(value.optString("timeZone"));
            return LocalDateTime.parse(text).atZone(sourceZone).withZoneSameInstant(zone);
        }
    }
    static List<Event> parse(JSONArray rows,String id,String name,int color,ZoneId zone) {
        List<Event> events=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++) try {
            JSONObject row=rows.getJSONObject(i);
            if("cancelled".equalsIgnoreCase(row.optString("status"))) continue;
            JSONObject from=row.getJSONObject("start"),to=row.getJSONObject("end");
            ZonedDateTime start=date(from,zone),end=date(to,zone);
            if(!end.isAfter(start)) continue;
            String title=clean(row.optString("summary")); if(title.isEmpty()) title="Sin título";
            if(title.length()>500) title=title.substring(0,500);
            String key=row.optString("uid",title)+"/"+start.toInstant();
            if(seen.add(key)) events.add(new Event(id,name,color,title,key,start,end,!from.optString("date").isEmpty()));
        } catch(Exception ignored) {}
        Collections.sort(events,CalendarAgenda::compare); return events;
    }
    static String limit(String value,int maxCharacters) {
        if(value==null || maxCharacters<1)return "";
        if(value.codePointCount(0,value.length())<=maxCharacters)return value;
        return value.substring(0,value.offsetByCodePoints(0,maxCharacters-1)).trim()+"…";
    }
    static String format(Event event,ZonedDateTime now) {
        LocalDate day=event.allDay && event.on(now.toLocalDate())?now.toLocalDate():event.start.toLocalDate();
        long days=java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(),day);
        String when=days==0?"Hoy":days==1?"Mañana":day.format(DateTimeFormatter.ofPattern("EEEE, dd",SPANISH));
        when=when.substring(0,1).toUpperCase(SPANISH)+when.substring(1);
        return when+(event.allDay?" · Todo el día: ":" a las "+event.start.format(DateTimeFormatter.ofPattern("HH:mm"))+": ")+event.title;
    }
    static int color(String id) { return COLORS[Math.floorMod(id.hashCode(),COLORS.length)]; }
    static int unusedColor(String id,Set<Integer> used) {
        int start=Math.floorMod(id.hashCode(),COLORS.length);
        for(int i=0;i<COLORS.length;i++) if(!used.contains(COLORS[(start+i)%COLORS.length])) return COLORS[(start+i)%COLORS.length];
        return color(id);
    }
}
