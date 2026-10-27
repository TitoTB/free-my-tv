package io.freemytv;

import org.json.JSONObject;

/** Informational cycle cards; entity identities are checked before using values. */
final class ApplianceState {
    static final String[] KEYS={"washer","dishwasher","printer3d"},TITLES={"Lavadora","Lavavajillas","Impresora 3D"};
    static final String[] ACTIVE={"binary_sensor.XXXXX_lavadora","binary_sensor.XXXXX_lavavajillas","sensor.XXXXX_impresora_estado"};
    static final String[] REMAINING={"sensor.XXXXX_lavadora_tiempo","sensor.XXXXX_lavavajillas_tiempo","sensor.XXXXX_impresora_fin"};
    static final String[] PROGRESS={"sensor.XXXXX_lavadora_progreso","sensor.XXXXX_lavavajillas_progreso","sensor.XXXXX_impresora_progreso"};
    final boolean active;
    final String remaining;
    final float progress;
    ApplianceState(boolean active,String remaining,float progress){this.active=active;this.remaining=remaining;this.progress=progress;}
    static ApplianceState off(){return new ApplianceState(false,"Tiempo no disponible",-1);}
    static boolean active(int index,JSONObject json) {
        if(json==null || !ACTIVE[index].equals(json.optString("entity_id")))return false;
        String value=json.optString("state").toLowerCase(java.util.Locale.ROOT);
        return index==2?value.equals("printing") || value.equals("printing_sd") || value.equals("printing_streaming"):value.equals("on");
    }
    static ApplianceState parse(int index,JSONObject on,JSONObject remaining,JSONObject progress) {
        return parse(index,on,remaining,progress,java.time.Instant.now());
    }
    static ApplianceState parse(int index,JSONObject on,JSONObject remaining,JSONObject progress,java.time.Instant now) {
        if(!active(index,on))return off();
        String time="Tiempo no disponible";float percent=-1;
        if(remaining!=null && REMAINING[index].equals(remaining.optString("entity_id")))try {
            double value;
            if(index==2) {
                java.time.Instant finish=java.time.OffsetDateTime.parse(remaining.optString("state")).toInstant();
                value=Math.max(0,java.time.Duration.between(now,finish).toMillis()/60000d);
            } else {
                value=Double.parseDouble(remaining.optString("state"));
                JSONObject attributes=remaining.optJSONObject("attributes");
                String unit=attributes==null?"min":attributes.optString("unit_of_measurement","min");
                if(unit.equals("s"))value/=60;else if(unit.equals("h"))value*=60;else if(!unit.equals("min"))value=Double.NaN;
            }
            if(Double.isFinite(value) && value>=0 && value<=525600) {
                long minutes=(long)Math.ceil(value),hours=minutes/60;
                time=hours>0?hours+" h"+(minutes%60>0?" "+minutes%60+" min":""):minutes+" min";
                if(index==2)time="Queda "+time;
            }
        } catch(Exception ignored) {}
        if(progress!=null && PROGRESS[index].equals(progress.optString("entity_id")))try {
            double value=Double.parseDouble(progress.optString("state"));
            if(Double.isFinite(value))percent=(float)Math.max(0,Math.min(100,value));
        } catch(Exception ignored) {}
        return new ApplianceState(true,time,percent);
    }
}
