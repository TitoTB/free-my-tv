package io.freemytv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

/** Read-only indoor sensors. Poll only while Home or the screensaver is visible; never log credentials or bodies. */
final class HomeClimate {
    static final String TEMPERATURE="sensor.XXXXX_temperatura",HUMIDITY="sensor.XXXXX_humedad";
    private static final long INTERVAL=30_000;
    private static HomeClimate instance;
    static synchronized HomeClimate get() { if(instance==null) instance=new HomeClimate(); return instance; }
    static boolean enabled() { return !Config.HOME_ASSISTANT_URL.trim().isEmpty() && !Config.HOME_ASSISTANT_TOKEN.trim().isEmpty(); }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners=new HashSet<>();
    String temperature="—",humidity="—";
    private long checkedAt;
    private boolean busy;
    private final Runnable poll=this::refresh;

    void addListener(Runnable listener) {
        if(!enabled()) return;
        listeners.add(listener);
        if(SystemClock.elapsedRealtime()-checkedAt>=INTERVAL || checkedAt==0) {
            temperature=humidity="—";
            refresh();
        } else schedule(INTERVAL-(SystemClock.elapsedRealtime()-checkedAt));
        listener.run();
    }
    void removeListener(Runnable listener) { listeners.remove(listener); if(listeners.isEmpty()) main.removeCallbacks(poll); }
    private void schedule(long delay) {
        main.removeCallbacks(poll);
        if(!listeners.isEmpty()) main.postDelayed(poll,Math.max(1,delay));
    }
    private void refresh() {
        if(busy || listeners.isEmpty() || !enabled()) return;
        busy=true;
        io.execute(()->{
            String temp=read(TEMPERATURE),humid=read(HUMIDITY);
            main.post(()->{
                temperature=temp; humidity=humid; checkedAt=SystemClock.elapsedRealtime(); busy=false;
                for(Runnable listener:new ArrayList<>(listeners)) listener.run();
                schedule(INTERVAL);
            });
        });
    }
    private String read(String entity) {
        if(entity==null || entity.isEmpty() || entity.contains("XXXXX")) return "—";
        HttpURLConnection connection=null;
        try {
            URL url=new URL(Config.HOME_ASSISTANT_URL.trim().replaceAll("/+$","")+"/api/states/"+entity);
            if(!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol())) return "—";
            connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(4000); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization","Bearer "+Config.HOME_ASSISTANT_TOKEN.trim());
            connection.setRequestProperty("Accept","application/json");
            if(connection.getResponseCode()!=200) return "—";
            try(InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while((n=in.read(buffer))!=-1) { if(out.size()+n>65536) return "—"; out.write(buffer,0,n); }
                return parse(new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)),entity,Locale.getDefault());
            }
        } catch(Exception ignored) { return "—"; }
        finally { if(connection!=null) connection.disconnect(); }
    }
    static String parse(JSONObject json,String entity,Locale locale) {
        if(!entity.equals(json.optString("entity_id"))) return "—";
        try {
            double value=Double.parseDouble(json.optString("state"));
            if(Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value)>10000) return "—";
            JSONObject attributes=json.optJSONObject("attributes");
            String unit=attributes==null?"":attributes.optString("unit_of_measurement");
            if(HUMIDITY.equals(entity)) { if(value<0 || value>100 || !"%".equals(unit)) return "—"; }
            else if(!TEMPERATURE.equals(entity) || !("°C".equals(unit)||"°F".equals(unit)||"K".equals(unit))) return "—";
            NumberFormat format=NumberFormat.getNumberInstance(locale);
            format.setGroupingUsed(false); format.setMaximumFractionDigits(1);
            return format.format(value)+("%".equals(unit)?" %":" "+unit);
        } catch(NumberFormatException ignored) { return "—"; }
    }
}
