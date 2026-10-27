package io.freemytv;

import android.os.*;
import org.json.*;
import java.time.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;

/** Read-only calendar aggregation shared by Home and Dream; no event titles on disk or in logs. */
final class HomeCalendars {
    private static HomeCalendars instance;
    static synchronized HomeCalendars get() { if(instance==null) instance=new HomeCalendars(); return instance; }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners=new HashSet<>();
    private final HomeControlApi api=new HomeControlApi(Config.HOME_ASSISTANT_URL,Config.HOME_ASSISTANT_TOKEN);
    CalendarAgenda agenda=new CalendarAgenda(Collections.emptyList(),ZoneId.systemDefault(),false);
    private boolean busy;
    private long checkedAt;
    private final Runnable poll=this::refresh;
    private final Runnable minute=this::tick;
    private Future<?> monthTask;
    private int monthGeneration;
    private final Map<YearMonth,CalendarAgenda> months=new LinkedHashMap<>();
    private final Map<YearMonth,Long> monthChecked=new HashMap<>();
    void month(YearMonth month,java.util.function.Consumer<CalendarAgenda> callback) {
        final int generation=++monthGeneration;
        if(monthTask!=null)monthTask.cancel(true);
        Long checked=monthChecked.get(month);
        if(checked!=null && SystemClock.elapsedRealtime()-checked<300_000){callback.accept(months.get(month));return;}
        final CalendarAgenda snapshot=agenda;
        monthTask=io.submit(()->{
            List<CalendarAgenda.Source> sources=new ArrayList<>();
            try {
                String query="?start="+URLEncoder.encode(month.atDay(1).atStartOfDay(snapshot.zone).toOffsetDateTime().toString(),"UTF-8")
                        +"&end="+URLEncoder.encode(month.plusMonths(1).atDay(1).atStartOfDay(snapshot.zone).toOffsetDateTime().toString(),"UTF-8");
                for(CalendarAgenda.Source source:snapshot.sources) {
                    if(Thread.currentThread().isInterrupted())return;
                    try {
                        List<CalendarAgenda.Event> events=CalendarAgenda.parse(new JSONArray(api.request("/api/calendars/"+source.id+query,null)),source.id,source.name,source.color,snapshot.zone);
                        sources.add(new CalendarAgenda.Source(source.id,source.name,source.color,events,true));
                    } catch(Exception ignored){sources.add(new CalendarAgenda.Source(source.id,source.name,source.color,Collections.emptyList(),false));}
                }
            } catch(Exception ignored) {}
            CalendarAgenda result=new CalendarAgenda(sources,snapshot.zone,snapshot.available);
            main.post(()->{
                if(generation!=monthGeneration)return;
                boolean complete=result.available && result.sources.stream().allMatch(source->source.available);
                if(complete){months.put(month,result);monthChecked.put(month,SystemClock.elapsedRealtime());
                    while(months.size()>12){YearMonth oldest=months.keySet().iterator().next();months.remove(oldest);monthChecked.remove(oldest);}}
                callback.accept(result);
            });
        });
    }
    void addListener(Runnable listener) {
        if(!HomeClimate.enabled()) return;
        listeners.add(listener); listener.run();
        if(checkedAt==0 || SystemClock.elapsedRealtime()-checkedAt>=300_000) refresh();
        else schedule();
        main.removeCallbacks(minute); main.postDelayed(minute,60_000-System.currentTimeMillis()%60_000);
    }
    void removeListener(Runnable listener) {
        listeners.remove(listener);
        if(listeners.isEmpty()) { main.removeCallbacks(poll); main.removeCallbacks(minute); }
    }
    private void tick() {
        for(Runnable listener:new ArrayList<>(listeners)) listener.run();
        if(!listeners.isEmpty()) main.postDelayed(minute,60_000-System.currentTimeMillis()%60_000);
    }
    private void schedule() {
        main.removeCallbacks(poll);
        if(!listeners.isEmpty()) main.postDelayed(poll,Math.max(1000,300_000-(SystemClock.elapsedRealtime()-checkedAt)));
    }
    private void refresh() {
        if(busy || listeners.isEmpty()) return;
        busy=true; final CalendarAgenda previous=agenda;
        io.execute(()->{
            CalendarAgenda result=read(previous);
            main.post(()->{
                busy=false; checkedAt=SystemClock.elapsedRealtime(); agenda=result;
                for(Runnable listener:new ArrayList<>(listeners)) listener.run();
                schedule();
            });
        });
    }
    private CalendarAgenda read(CalendarAgenda previous) {
        ZoneId zone=previous.zone;
        try { zone=ZoneId.of(new JSONObject(api.request("/api/config",null)).optString("time_zone")); }
        catch(Exception ignored) {}
        List<CalendarAgenda.Source> sources=new ArrayList<>();
        try {
            JSONArray calendars=new JSONArray(api.request("/api/calendars",null));
            ZonedDateTime now=ZonedDateTime.now(zone),start=now.withDayOfMonth(1).toLocalDate().atStartOfDay(zone);
            String query="?start="+URLEncoder.encode(start.toOffsetDateTime().toString(),"UTF-8")
                    +"&end="+URLEncoder.encode(now.plusYears(1).toOffsetDateTime().toString(),"UTF-8");
            Set<String> seen=new HashSet<>();
            Map<String,Integer> assigned=new HashMap<>(); Set<Integer> used=new HashSet<>();
            for(CalendarAgenda.Source source:previous.sources) { assigned.put(source.id,source.color); used.add(source.color); }
            for(int i=0;i<calendars.length();i++) {
                JSONObject calendar=calendars.getJSONObject(i);
                String id=calendar.optString("entity_id");
                if(!id.matches("calendar\\.[a-z0-9_]+") || !seen.add(id)) continue;
                String name=CalendarAgenda.clean(calendar.optString("name",id));
                int color=CalendarAgenda.fixedColor(id);
                if(color==0) color=assigned.containsKey(id)?assigned.get(id):CalendarAgenda.unusedColor(id,used);
                used.add(color);
                try {
                    List<CalendarAgenda.Event> events=CalendarAgenda.parse(new JSONArray(api.request("/api/calendars/"+id+query,null)),id,name,color,zone);
                    sources.add(new CalendarAgenda.Source(id,name,color,events,true));
                } catch(Exception ignored) { sources.add(new CalendarAgenda.Source(id,name,color,Collections.emptyList(),false)); }
            }
            return new CalendarAgenda(sources,zone,true);
        } catch(Exception ignored) { return new CalendarAgenda(Collections.emptyList(),zone,false); }
    }
}
