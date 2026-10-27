package io.freemytv;

import java.io.*;
import java.util.*;
import org.json.*;

/** Host-only driver uses the exact production Paho transport and Discovery payloads. */
public class AndroidMqttHarness {
    public static void main(String[] args) throws Exception {
        String id=args[1],base="android_tv/"+id;
        AndroidMqtt mqtt=new AndroidMqtt("tcp://127.0.0.1:"+args[0],"","",id,(topic,payload,retained)->{
            try {
                AndroidNotice notice=new AndroidNotice(AndroidDiscovery.command(topic,payload,System.currentTimeMillis()),System.currentTimeMillis());
                System.out.println(AndroidDiscovery.object("received",notice.id,"message",notice.message,"type",notice.type,"persistent",notice.persistent));
            } catch(IllegalArgumentException invalid) { System.out.println("{\"invalid\":true}"); }
        },transport->{
            for(Map.Entry<String,String> config:AndroidDiscovery.configs(id,"TV de prueba","Test","Host","source").entrySet())
                transport.publish(config.getKey(),config.getValue(),true);
            transport.publish(base+"/state","{\"screen_on\":true}",true);
            transport.publish(base+"/awake","online",true);
            transport.publish(base+"/availability","online",true);
            System.out.println("{\"connected\":true}");
        });
        mqtt.start();
        try(BufferedReader input=new BufferedReader(new InputStreamReader(System.in))) {
            String line;while((line=input.readLine())!=null) {
                if(line.equals("stop")) break;
                if(line.equals("event")) mqtt.publish(base+"/event","{\"event_type\":\"acknowledged\",\"notification_id\":\"test\"}",false);
            }
        } finally { mqtt.close(); }
    }
}
