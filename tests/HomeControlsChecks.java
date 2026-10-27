package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Fake HA: verifies exact service calls without changing real scenes, heating or blinds. */
public final class HomeControlsChecks {
    private static int checks;
    private static void check(boolean value) { if(!value) throw new AssertionError("Home control check "+checks); checks++; }
    private static JSONObject state(int i,String value,JSONObject attributes) throws Exception {
        return new JSONObject().put("entity_id",HomeControlState.ENTITIES[i]).put("state",value).put("attributes",attributes);
    }
    public static void main(String[] args) throws Exception {
        JSONObject scene=state(0,"Ambiente",new JSONObject().put("options",new JSONArray("[\"Ambiente\",\"Normal\"]")));
        JSONObject heat=state(1,"off",new JSONObject().put("supported_features",401));
        JSONObject cover=state(2,"open",new JSONObject().put("supported_features",15).put("current_position",35));
        Locale.setDefault(Locale.US);
        check(HomeControlState.TITLES[0].equals("Escena"));
        JSONObject warm=state(1,"heat",new JSONObject().put("supported_features",401).put("temperature",21.5).put("temperature_unit","°C"));
        check(HomeControlState.parse(1,warm).value.equals("Encendida (21.5 °C)"));
        check(HomeControlState.parse(1,state(1,"heat",new JSONObject().put("temperature",22).put("temperature_unit","°C"))).value.equals("Encendida (22 °C)"));
        check(HomeControlState.parse(1,state(1,"heat",new JSONObject().put("temperature",72).put("temperature_unit","°F"))).value.equals("Encendida (72 °F)"));
        check(HomeControlState.parse(1,new JSONObject(warm.toString()).put("state","off")).value.equals("Apagada"));
        check(HomeControlState.parse(1,state(1,"heat",new JSONObject().put("temperature","invalid"))).value.equals("Encendida"));
        HomeControlState prior=HomeControlState.parse(0,scene);
        HomeControlPending waiting=new HomeControlPending(0,0,prior,1000);
        check(!waiting.confirmed(prior));
        check(!waiting.confirmed(HomeControlState.missing()));
        check(waiting.visible(prior,HomeControlState.missing())==prior);
        check(waiting.confirmed(HomeControlState.parse(0,new JSONObject(scene.toString()).put("state","Normal"))));
        check(!waiting.expired(20999)); check(waiting.expired(21000));
        waiting=new HomeControlPending(1,0,HomeControlState.parse(1,heat),1000);
        check(!waiting.confirmed(HomeControlState.parse(1,heat)));
        check(waiting.confirmed(HomeControlState.parse(1,warm)));
        waiting=new HomeControlPending(2,70,HomeControlState.parse(2,cover),1000);
        HomeControlState halfway=HomeControlState.parse(2,state(2,"opening",new JSONObject().put("supported_features",15).put("current_position",55)));
        check(!waiting.confirmed(halfway));
        check(waiting.visible(prior,halfway)==halfway);
        check(waiting.visible(halfway,HomeControlState.missing())==halfway);
        check(waiting.confirmed(HomeControlState.parse(2,state(2,"open",new JSONObject().put("supported_features",15).put("current_position",70)))));
        check(!waiting.expired(90999)); check(waiting.expired(91000));
        check(HomeControlState.parse(0,scene).controllable);
        check(HomeControlState.parse(0,scene).value.equals("Ambiente"));
        check(HomeControlState.parse(1,heat).service(1).equals("climate/turn_on"));
        check(HomeControlState.parse(1,new JSONObject(heat.toString()).put("state","heat")).service(1).equals("climate/turn_off"));
        check(HomeControlState.parse(1,new JSONObject(heat.toString()).put("state","auto")).on);
        check(HomeControlState.parse(2,cover).position==35);
        check(HomeControlState.parse(2,cover).controllable);
        check(!HomeControlState.parse(0,new JSONObject(scene.toString()).put("state","Other")).controllable);
        for(int i=0;i<3;i++) {
            check(!HomeControlState.parse(i,new JSONObject(scene.toString()).put("entity_id","sensor.other")).available);
            check(!HomeControlState.parse(i,state(i,"unavailable",new JSONObject())).controllable);
            check(!HomeControlState.parse(i,state(i,"unknown",new JSONObject())).available);
        }
        check(!HomeControlState.parse(1,state(1,"off",new JSONObject().put("supported_features",1))).controllable);
        check(!HomeControlState.parse(2,state(2,"open",new JSONObject().put("supported_features",3).put("current_position",30))).controllable);
        for(Object p:new Object[]{-1,101,3.5,"35",JSONObject.NULL})
            check(!HomeControlState.parse(2,state(2,"open",new JSONObject().put("supported_features",15).put("current_position",p))).controllable);
        check(HomeControlState.step(0,-1)==0); check(HomeControlState.step(100,1)==100);
        check(HomeControlState.step(35,1)==40); check(HomeControlState.step(35,-1)==30);
        check(HomeControlState.payload(0,0).getBoolean("cycle"));
        check(HomeControlState.payload(2,100).getInt("position")==100);
        for(int p:new int[]{-1,101}) {
            boolean rejected=false; try { HomeControlState.payload(2,p); } catch(IllegalArgumentException expected) { rejected=true; }
            check(rejected);
        }
        ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
        List<String> posts=new ArrayList<>(); List<JSONObject> bodies=new ArrayList<>();
        JSONObject[] states={scene,heat,cover}; int[] response={200};
        Thread worker=new Thread(()->{
            while(!server.isClosed()) try(Socket socket=server.accept()) {
                socket.setSoTimeout(4000);
                InputStream in=socket.getInputStream();
                String request=line(in),header; int length=0; boolean auth=false;
                while(!(header=line(in)).isEmpty()) {
                    if(header.startsWith("Content-Length:")) length=Integer.parseInt(header.substring(15).trim());
                    if(header.equals("Authorization: Bearer test-only")) auth=true;
                }
                if(!auth) throw new AssertionError("Auth missing");
                byte[] body; String path=request.split(" ")[1];
                if(request.startsWith("POST ")) {
                    posts.add(path); bodies.add(new JSONObject(new String(in.readNBytes(length),StandardCharsets.UTF_8))); body="[]".getBytes(StandardCharsets.UTF_8);
                } else if(path.equals("/api/config")) {
                    body="{\"unit_system\":{\"temperature\":\"°C\"}}".getBytes(StandardCharsets.UTF_8);
                } else {
                    int i=Arrays.asList(HomeControlState.ENTITIES).indexOf(path.substring("/api/states/".length()));
                    body=states[i].toString().getBytes(StandardCharsets.UTF_8);
                }
                OutputStream out=socket.getOutputStream();
                out.write(("HTTP/1.1 "+response[0]+" Test\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nLocation: /api/services/should_not_follow\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body); out.flush();
            } catch(Exception e) { if(!server.isClosed()) throw new RuntimeException(e); }
        });
        worker.setDaemon(true); worker.start();
        try {
            HomeControlApi api=new HomeControlApi("http://127.0.0.1:"+server.getLocalPort()+"/","test-only");
            check(api.read(2).position==35);
            api.command(0,0);
            check(posts.get(0).equals("/api/services/input_select/select_next"));
            check(bodies.get(0).getBoolean("cycle"));
            check(bodies.get(0).getString("entity_id").equals(HomeControlState.ENTITIES[0]));
            api.command(1,0); check(posts.get(1).endsWith("/climate/turn_on"));
            states[1]=state(1,"heat",new JSONObject().put("supported_features",401));
            api.command(1,0); check(posts.get(2).endsWith("/climate/turn_off"));
            check(bodies.get(2).length()==1); // No thermostat temperature/preset changes.
            states[1]=state(1,"heat",new JSONObject().put("supported_features",401).put("temperature",22));
            check(api.read(1).value.equals("Encendida (22 °C)"));
            api.command(2,70); check(posts.get(3).endsWith("/cover/set_cover_position"));
            check(bodies.get(3).getInt("position")==70);
            states[2]=state(2,"unavailable",new JSONObject());
            boolean rejected=false; try { api.command(2,60); } catch(Exception expected) { rejected=true; }
            check(rejected && posts.size()==4);
            for(int code:new int[]{401,500,302}) {
                response[0]=code; rejected=false;
                try { api.command(0,0); } catch(Exception expected) { rejected=true; }
                check(rejected && posts.size()==4); // No redirect, retry or offline command.
            }
        } finally { server.close(); worker.join(2000); }
        System.out.println("HomeControlsChecks: "+checks+" checks passed");
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int b;
        while((b=in.read())!=-1 && b!='\n') if(b!='\r') out.write(b);
        if(b==-1 && out.size()==0) throw new EOFException();
        return out.toString("US-ASCII");
    }
}
