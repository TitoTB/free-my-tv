package io.freemytv;

import java.io.*;
import java.util.*;
import org.json.*;

/** Host-only driver uses the exact production Paho transport and Discovery payloads. */
public class HandroidMqttHarness {
    public static void main(String[] args) throws Exception {
        String id=args[1],base="handroid_tv/"+id;
        HandroidMqtt mqtt=new HandroidMqtt("tcp://127.0.0.1:"+args[0],"","",id,(topic,payload,retained)->{
            try {
                HandroidNotice notice=new HandroidNotice(HandroidDiscovery.command(topic,payload,System.currentTimeMillis()),System.currentTimeMillis());
                System.out.println(HandroidDiscovery.object("received",notice.id,"message",notice.message,"type",notice.type,"persistent",notice.persistent));
            } catch(IllegalArgumentException invalid) { System.out.println("{\"invalid\":true}"); }
        },transport->{
            for(Map.Entry<String,String> config:HandroidDiscovery.configs(id,"TV de prueba","Test","Host","source").entrySet())
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
