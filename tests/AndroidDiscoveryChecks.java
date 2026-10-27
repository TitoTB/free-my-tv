package io.freemytv;
import java.util.*;
import org.json.*;

public class AndroidDiscoveryChecks {
    private static int checks;
    private static void check(boolean value) { if(!value) throw new AssertionError("Discovery check "+checks);checks++; }
    public static void main(String[] args) throws Exception {
        String id="ec371d0e-1b23-4d00-a548-d9e6f72ce1d0";
        Map<String,String> configs=AndroidDiscovery.configs(id,"Salón","TCL","TV","test");
        check(configs.size()==10);
        Set<String> identifiers=new HashSet<>(),unique=new HashSet<>();
        for(Map.Entry<String,String> item:configs.entrySet()) {
            JSONObject c=new JSONObject(item.getValue());
            identifiers.add(c.getJSONObject("device").getJSONArray("identifiers").getString(0));
            check(unique.add(c.getString("unique_id")));
            check(c.getJSONObject("origin").getString("name").equals("Android TV"));
            if(item.getKey().contains("/notify/")) {
                check(!c.getBoolean("retain"));check(c.getInt("qos")==0);
                check(c.getString("availability_mode").equals("all"));check(c.getJSONArray("availability").length()==2);
            }
        }
        check(identifiers.size()==1);
        check(identifiers.contains("android_tv_"+id));
        for(String topic:configs.keySet()) check(topic.contains("/android_"+id+"/"));
        check(AndroidDiscovery.configs(id,"Otro nombre","TCL","TV","test").keySet().equals(configs.keySet()));
        check(!AndroidDiscovery.configs("another-tv","Salón","TCL","TV","test").keySet().equals(configs.keySet()));
        long now=1000000;
        AndroidNotice normal=new AndroidNotice(AndroidDiscovery.command("x/notify/normal","{no es JSON}",now),now);
        check(normal.message.equals("{no es JSON}"));check(!normal.persistent);check(normal.duration==8);check(!normal.critical());
        AndroidNotice critical=new AndroidNotice(AndroidDiscovery.command("x/notify/critical","Atención",now),now);
        check(critical.critical());check(critical.persistent);
        JSONObject forced=AndroidDiscovery.command("x/notify/set","{\"message\":\"Alerta\",\"notification_type\":\"critical\",\"persistent\":false}",now);
        check(new AndroidNotice(forced,now).persistent);
        Map<String,String> defaults=AndroidDiscovery.configs(id,"Android TV","TCL","TV","test");
        check(new JSONObject(defaults.get("homeassistant/notify/android_"+id+"/normal/config")).getString("default_entity_id").equals("notify.android_tv_notificacion_normal"));
        check(new JSONObject(defaults.get("homeassistant/notify/android_"+id+"/critical/config")).getString("default_entity_id").equals("notify.android_tv_notificacion_critica"));
        JSONObject custom=AndroidDiscovery.command("x/notify/set","{\"message\":\"Alerta\",\"persistent\":true,\"notification_type\":\"critical\",\"notification_id\":\"my-id\",\"duration\":15}",now);
        AndroidNotice rich=new AndroidNotice(custom,now);check(rich.persistent);check(rich.critical());check(rich.id.equals("my-id"));check(rich.duration==15);
        try { new AndroidNotice(custom,now+11000);throw new AssertionError("Stale command accepted"); } catch(IllegalArgumentException expected) { checks++; }
        try { AndroidDiscovery.command("x/notify/set","broken",now);throw new AssertionError("Invalid JSON"); } catch(IllegalArgumentException expected) { checks++; }
        System.out.println(checks+" Discovery/command checks passed");
    }
}
