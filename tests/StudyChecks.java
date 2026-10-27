package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Exact allowlisted all-off calls; never use real HA or household devices for these checks. */
public final class StudyChecks {
    private static int checks;
    private static void check(boolean value) { if(!value) throw new AssertionError("Study check "+checks); checks++; }
    private static JSONObject entity(int i,String state) throws Exception {
        return new JSONObject().put("entity_id",StudyState.ENTITIES[i]).put("state",state)
                .put("attributes",new JSONObject().put("supported_features",i==2?425:0).put("hvac_action","idle"));
    }
    private static StudyState state(int mask) throws Exception {
        StudyState.Member[] members=new StudyState.Member[3];
        for(int i=0;i<3;i++) members[i]=StudyState.parse(i,entity(i,(mask&(1<<i))==0?"off":i==2?"cool":"on"));
        return new StudyState(members);
    }
    public static void main(String[] args) throws Exception {
        for(int mask=0;mask<8;mask++) {
            StudyState s=state(mask); check(s.anyOn==(mask!=0)); check(s.allOff==(mask==0));
        }
        check(!StudyState.empty().allOff); check(!StudyState.empty().anyOn);
        check(state(4).anyOn); // Climate mode cool counts as on even while hvac_action says idle.
        for(int i=0;i<3;i++) {
            check(!StudyState.parse(i,entity(i,"unknown")).available);
            check(!StudyState.parse(i,entity(i,"unavailable")).available);
            check(!StudyState.parse(i,entity(i,"off").put("entity_id","light.other")).available);
        }
        StudyState unknown=StudyState.empty(),previous=state(7);
        check(StudyState.visible(previous,unknown).anyOn);
        check(StudyState.visible(previous,state(0)).allOff);
        check(!StudyState.parse(2,entity(2,"cool").put("attributes",new JSONObject().put("supported_features",1))).canOff);
        check(state(0).labels[0].equals("Luz: Apagada"));
        check(state(2).labels[1].equals("Ventilador: Encendido"));
        check(state(4).labels[2].equals("Aire acondicionado: Encendido"));

        ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
        JSONObject[] states={entity(0,"off"),entity(1,"off"),entity(2,"off")};
        JSONObject[] bedroom={new JSONObject().put("entity_id",StudyState.BEDROOM).put("state","off")};
        List<String> posts=new ArrayList<>(); List<JSONObject> bodies=new ArrayList<>();
        int[] getCode={200},failedPost={-1};
        Thread worker=new Thread(()->{
            while(!server.isClosed()) try(Socket socket=server.accept()) {
                socket.setSoTimeout(4000); InputStream in=socket.getInputStream();
                String request=line(in),header; int length=0; boolean auth=false;
                while(!(header=line(in)).isEmpty()) {
                    if(header.startsWith("Content-Length:")) length=Integer.parseInt(header.substring(15).trim());
                    if(header.equals("Authorization: Bearer test-only")) auth=true;
                }
                if(!auth) throw new AssertionError("Missing auth");
                String path=request.split(" ")[1]; int code; byte[] body;
                if(request.startsWith("POST ")) {
                    posts.add(path); JSONObject payload=new JSONObject(new String(in.readNBytes(length),StandardCharsets.UTF_8)); bodies.add(payload);
                    int i=StudyState.BEDROOM.equals(payload.getString("entity_id"))?3:Arrays.asList(StudyState.ENTITIES).indexOf(payload.getString("entity_id"));
                    code=i==failedPost[0]?503:200; body="[]".getBytes(StandardCharsets.UTF_8);
                } else {
                    String id=path.substring("/api/states/".length());
                    int i=Arrays.asList(StudyState.ENTITIES).indexOf(id);
                    body=(StudyState.BEDROOM.equals(id)?bedroom[0]:states[i]).toString().getBytes(StandardCharsets.UTF_8); code=getCode[0];
                }
                OutputStream out=socket.getOutputStream();
                out.write(("HTTP/1.1 "+code+" Test\r\nConnection: close\r\nContent-Length: "+body.length+"\r\nLocation: /should-not-follow\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body); out.flush();
            } catch(Exception e) { if(!server.isClosed()) throw new RuntimeException(e); }
        });
        worker.setDaemon(true); worker.start();
        try {
            StudyApi api=new StudyApi(new HomeControlApi("http://127.0.0.1:"+server.getLocalPort(),"test-only"));
            StudyApi.Result result=api.turnOff(()->true);
            check(posts.isEmpty()); check(result.before.allOff); check(!result.failed);
            for(int i=0;i<3;i++) states[i]=entity(i,i==2?"cool":"on");
            result=api.turnOff(()->true);
            check(!result.failed); check(posts.size()==3);
            check(posts.equals(Arrays.asList("/api/services/light/turn_off","/api/services/fan/turn_off","/api/services/climate/turn_off")));
            for(int i=0;i<3;i++) { check(bodies.get(i).length()==1); check(bodies.get(i).getString("entity_id").equals(StudyState.ENTITIES[i])); }
            posts.clear(); bodies.clear(); states[0]=entity(0,"off"); states[2]=entity(2,"off");
            api.turnOff(()->true); check(posts.size()==1 && posts.get(0).equals("/api/services/fan/turn_off"));
            posts.clear(); bodies.clear(); states[0]=entity(0,"on"); states[1]=entity(1,"unavailable");
            result=api.turnOff(()->true); check(result.failed); check(posts.size()==1 && posts.get(0).equals("/api/services/light/turn_off"));
            posts.clear(); bodies.clear(); for(int i=0;i<3;i++) states[i]=entity(i,i==2?"heat":"on");
            failedPost[0]=0; result=api.turnOff(()->true);
            check(result.failed); check(posts.size()==3); // Other devices still get one attempt, never retry a failed POST.
            posts.clear(); bodies.clear(); failedPost[0]=-1;
            result=api.turnOff(()->false); check(result.cancelled); check(posts.isEmpty());
            for(int code:new int[]{401,302,500}) {
                getCode[0]=code; result=api.turnOff(()->true); check(result.failed); check(posts.isEmpty());
            }
            getCode[0]=200; states[0]=entity(0,"off"); states[1]=entity(1,"off");
            states[2]=entity(2,"cool").put("attributes",new JSONObject().put("supported_features",1));
            result=api.turnOff(()->true); check(result.failed); check(posts.isEmpty());
            states[2]=entity(2,"off"); result=api.turnOff(()->true);
            check(result.before.allOff && !result.failed && posts.isEmpty()); // Fresh state wins over any old UI state.
            StudyApi bedroomApi=new StudyApi(new HomeControlApi("http://127.0.0.1:"+server.getLocalPort(),"test-only"),true);
            check(bedroomApi.empty().members.length==1 && !bedroomApi.empty().anyOn);
            check(bedroomApi.read().allOff);
            result=bedroomApi.turnOff(()->true); check(!result.failed && posts.isEmpty());
            bedroom[0].put("state","on");
            check(bedroomApi.read().anyOn);
            check(bedroomApi.read().actionDescription.equals("Apagar luz"));
            result=bedroomApi.turnOff(()->true);
            check(!result.failed && posts.equals(Arrays.asList("/api/services/switch/turn_off")));
            check(bodies.get(0).length()==1 && bodies.get(0).getString("entity_id").equals(StudyState.BEDROOM));
            posts.clear(); bodies.clear(); failedPost[0]=3;
            result=bedroomApi.turnOff(()->true); check(result.failed && posts.size()==1);
            posts.clear(); bodies.clear(); failedPost[0]=-1;
            for(String value:new String[]{"unknown","unavailable","unexpected"}) {
                bedroom[0].put("state",value); result=bedroomApi.turnOff(()->true);
                check(result.failed && posts.isEmpty() && !result.before.allOff);
            }
            bedroom[0].put("state","on").put("entity_id","switch.other");
            check(!bedroomApi.read().anyOn);
            check(bedroomApi.turnOff(()->true).failed && posts.isEmpty());
            bedroom[0].put("entity_id",StudyState.BEDROOM);
            check(bedroomApi.turnOff(()->false).cancelled && posts.isEmpty());
            StudyState activeBedroom=bedroomApi.read(); bedroom[0].put("state","unavailable");
            check(StudyState.visible(activeBedroom,bedroomApi.read()).anyOn);
            bedroom[0].put("state","off");
            check(StudyState.visible(activeBedroom,bedroomApi.read()).allOff);
        } finally { server.close(); worker.join(2000); }
        System.out.println("StudyChecks: "+checks+" checks passed");
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int b;
        while((b=in.read())!=-1 && b!='\n') if(b!='\r') out.write(b);
        if(b==-1 && out.size()==0) throw new EOFException();
        return out.toString("US-ASCII");
    }
}
