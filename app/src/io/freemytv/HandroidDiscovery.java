package io.freemytv;

import org.json.*;
import java.util.*;
import java.text.Normalizer;

/** Native HA MQTT entities share one stable device, independently of its name or IP. */
final class HandroidDiscovery {
    static JSONObject object(Object... fields) {
        JSONObject json=new JSONObject();
        try { for(int i=0;i<fields.length;i+=2) json.put((String)fields[i],fields[i+1]); }
        catch(JSONException e) { throw new IllegalArgumentException(e); }
        return json;
    }
    static Map<String,String> configs(String id,String name,String manufacturer,String model,String version) {
        Map<String,String> result=new LinkedHashMap<>(); String base="handroid_tv/"+id;
        JSONObject device=object("identifiers",new JSONArray().put("handroid_tv_"+id),"name",name,
            "manufacturer",manufacturer,"model",model,"sw_version",version);
        for(String key:new String[]{"connection","screen_on","home_visible","screensaver_active","overlay_permission","pending_notifications","active_notification","normal","critical","notification"}) {
            String platform=key.equals("normal")||key.equals("critical")?"notify":key.equals("notification")?"event":key.endsWith("notifications")||key.equals("active_notification")?"sensor":"binary_sensor";
            String label=key.equals("normal")?"Notificación normal":key.equals("critical")?"Notificación crítica":
                key.equals("connection")?"Conexión":key.equals("screen_on")?"Pantalla":key.equals("home_visible")?"Inicio visible":key.equals("screensaver_active")?"Salvapantallas":
                key.equals("overlay_permission")?"Permiso de avisos":key.equals("pending_notifications")?"Avisos pendientes":key.equals("active_notification")?"Aviso activo":"Estado de los avisos";
            JSONObject config=object("name",label,"unique_id","handroid_"+id+"_"+key,"device",device,
                "availability_topic",base+"/availability","origin",object("name","Android TV","sw_version",version));
            if(platform.equals("notify")) {
                try {
                    config.put("command_topic",base+"/notify/"+key).put("qos",0).put("retain",false);
                    String slug=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
                        .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");
                    if(slug.isEmpty()) slug="android_tv";
                    config.put("default_entity_id","notify."+slug+"_notificacion_"+(key.equals("normal")?"normal":"critica"));
                    config.put("availability",new JSONArray().put(object("topic",base+"/availability")).put(object("topic",base+"/awake")));
                    config.remove("availability_topic");config.put("availability_mode","all");
                } catch(JSONException e) { throw new IllegalArgumentException(e); }
            } else if(platform.equals("event")) {
                try { config.put("state_topic",base+"/event").put("event_types",new JSONArray(Arrays.asList("shown","queued","acknowledged","expired","discarded","rejected"))); }
                catch(JSONException e) { throw new IllegalArgumentException(e); }
            } else {
                try {
                    config.put("state_topic",base+"/state").put("value_template","{{ value_json."+key+" }}");
                    if(platform.equals("binary_sensor")) config.put("value_template","{{ 'ON' if value_json."+key+" else 'OFF' }}").put("payload_on","ON").put("payload_off","OFF");
                    if(key.equals("connection")) {
                        config.put("state_topic",base+"/availability").put("payload_on","online").put("payload_off","offline").put("device_class","connectivity");
                        config.remove("value_template");config.remove("availability_topic");
                    }
                    if(key.equals("overlay_permission")) config.put("entity_category","diagnostic");
                } catch(JSONException e) { throw new IllegalArgumentException(e); }
            }
            result.put("homeassistant/"+platform+"/handroid_"+id+"/"+key+"/config",config.toString());
        }
        return result;
    }
    static JSONObject command(String topic,String payload,long now) {
        if(payload.length()>4096) throw new IllegalArgumentException("Oversize");
        JSONObject value;
        try {
            // Native notify messages are always literal strings, including those starting with '{'.
            value=topic.endsWith("/set")?new JSONObject(payload):object("message",payload,
                "notification_type",topic.endsWith("/critical")?"critical":"normal");
            if(!value.has("notification_id")) value.put("notification_id",UUID.randomUUID().toString());
            if(!value.has("persistent")) value.put("persistent",false);
            if(!value.has("duration")) value.put("duration",8);
            if(!value.has("expires_at")) value.put("expires_at",now+10000);
            return value;
        } catch(JSONException e) { throw new IllegalArgumentException("Invalid JSON"); }
    }
}
