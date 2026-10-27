package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.*;
import org.json.*;

/** Real local HTTP, fake HA and fake role shell: never contacts a household device. */
public final class AssistChecks {
    private static int checks;
    private static void check(boolean condition){if(!condition)throw new AssertionError("Assist check "+checks);checks++;}
    private interface Work {void run()throws Exception;}
    private static void fails(Work work)throws Exception{try{work.run();throw new AssertionError("Expected failure");}catch(IOException expected){checks++;}}
    private static JSONObject reply(String text,String type)throws Exception{return new JSONObject().put("conversation_id","turn-id").put("response",new JSONObject().put("response_type",type).put("speech",new JSONObject().put("plain",new JSONObject().put("speech",text))));}
    private static String read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8");}
    private static void respond(HttpExchange exchange,int status,String body)throws IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}}
    public static void main(String[] args)throws Exception{
        String own="io.freemytv",google="com.google.android.katniss",engine="com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService",bind="android.permission.BIND_SPEECH_RECOGNITION_SERVICE";
        check(AssistProviderPolicy.usable(own,google,engine,null,true,true,true));
        check(AssistProviderPolicy.usable(own,google,engine,"",true,true,true));
        check(AssistProviderPolicy.usable(own,google,engine,bind,true,true,true));
        check(AssistProviderPolicy.usable(own,"org.example.speech","org.example.Speech",bind,true,true,true));
        check(!AssistProviderPolicy.usable(own,"org.example.speech","org.example.Speech",null,true,true,true));
        check(!AssistProviderPolicy.usable(own,google,"Other",null,true,true,true));
        check(!AssistProviderPolicy.usable(own,own,engine,bind,true,true,true));
        check(!AssistProviderPolicy.usable(own,google,engine,null,false,true,true));
        check(!AssistProviderPolicy.usable(own,google,engine,null,true,false,true));
        check(!AssistProviderPolicy.usable(own,google,engine,null,true,true,false));
        check(!AssistProviderPolicy.usable(own,google,engine,"other.permission",true,true,true));
        AssistTurn turn=new AssistTurn();long a=turn.begin();check(turn.submit(a));check(!turn.submit(a));check(turn.finish(a));check(!turn.finish(a));
        long b=turn.begin();check(!turn.submit(a));turn.cancel();check(!turn.submit(b));check(!turn.finish(b));check(turn.stage()==AssistTurn.Stage.IDLE);
        long c=turn.begin();check(turn.recognitionFailed(c));check(!turn.submit(c));check(!turn.recognitionFailed(c));
        for(int i=0;i<100;i++){long old=turn.begin();long latest=turn.begin();check(!turn.submit(old));check(turn.submit(latest));turn.cancel();check(!turn.finish(latest));}
        check(AssistConversation.indoorQuestion("¿QUÉ temperatura hay en casa?","es-ES").equals("sensor.indoor_temperature"));
        check(AssistConversation.indoorQuestion("¿Qué humedad hay en casa?","es").equals("sensor.indoor_humidity"));
        check(AssistConversation.indoorQuestion("Apaga la calefacción y dime qué temperatura hay en casa","es")==null);
        check(AssistConversation.indoorQuestion("Qué temperatura hay en casa","en")==null);
        check(AssistConversation.parse(reply("23 °C","query_answer")).text.equals("23 °C"));
        check(AssistConversation.parse(reply("No conozco esa entidad","error")).error);
        check(AssistConversation.parse(reply(String.join("",Collections.nCopies(5000,"a")),"query_answer")).text.length()==4096);
        fails(()->AssistConversation.parse(new JSONObject()));fails(()->AssistConversation.parse(reply("","error")));
        JSONObject bad=reply("answer","query_answer");bad.getJSONObject("response").getJSONObject("speech").getJSONObject("plain").put("speech",JSONObject.NULL);
        fails(()->AssistConversation.parse(bad));
        check(AssistConversation.parse(reply("answer","query_answer").put("conversation_id",JSONObject.NULL)).conversation.isEmpty());
        AtomicInteger posts=new AtomicInteger(),redirects=new AtomicInteger(),status=new AtomicInteger(200);
        List<JSONObject> payloads=Collections.synchronizedList(new ArrayList<>());
        List<String> authorization=Collections.synchronizedList(new ArrayList<>());
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService serverThreads=Executors.newCachedThreadPool();server.setExecutor(serverThreads);
        CountDownLatch arrived=new CountDownLatch(1),release=new CountDownLatch(1);
        server.createContext("/api/states/",exchange->{try{check(exchange.getRequestMethod().equals("GET"));String id=exchange.getRequestURI().getPath().substring("/api/states/".length());respond(exchange,200,new JSONObject().put("entity_id",id).put("attributes",new JSONObject().put("friendly_name",id.endsWith("temperatura")?"Sensor de casa":"Humedad de casa")).toString());}catch(Exception e){throw new IOException(e);}});
        server.createContext("/api/conversation/process",exchange->{try{
            posts.incrementAndGet();authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));check(exchange.getRequestMethod().equals("POST"));
            JSONObject body=new JSONObject(read(exchange.getRequestBody()));payloads.add(body);
            if(body.optString("text").equals("slow")){arrived.countDown();release.await(5,TimeUnit.SECONDS);}
            int code=status.get();if(code==0){exchange.close();return;}if(code==302)exchange.getResponseHeaders().add("Location","/exfiltration");
            respond(exchange,code,reply(body.optString("text"),"query_answer").toString());
        }catch(Exception e){exchange.close();}});
        server.createContext("/exfiltration",exchange->{redirects.incrementAndGet();respond(exchange,200,"{}");});server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        try{
            AssistConversation api=new AssistConversation(base,"private-test-token");
            check(api.ask("¿Qué temperatura hay en casa?","es-ES","").text.equals("¿Cuál es el estado de Sensor de casa?"));
            JSONObject first=payloads.get(0);check(first.getString("agent_id").equals("conversation.home_assistant"));check(first.getString("language").equals("es-ES"));check(!first.has("conversation_id"));
            // Arbitrary user commands go through unchanged once, without local duplicate execution.
            check(api.ask("Apaga una luz simulada","es","turn-id").text.equals("Apaga una luz simulada"));
            check(payloads.get(1).getString("conversation_id").equals("turn-id"));check(posts.get()==2);check(authorization.get(0).equals("Bearer private-test-token"));
            status.set(500);int before=posts.get();fails(()->new AssistConversation(base,"key").ask("test","es",""));check(posts.get()==before+1);
            status.set(0);before=posts.get();fails(()->new AssistConversation(base,"key").ask("do not replay","es",""));check(posts.get()==before+1);
            status.set(302);before=posts.get();fails(()->new AssistConversation(base,"key").ask("test","es",""));check(posts.get()==before+1);check(redirects.get()==0);
            status.set(200);AssistConversation cancelled=new AssistConversation(base,"key");cancelled.cancel();before=posts.get();fails(()->cancelled.ask("test","es",""));check(posts.get()==before);
            fails(()->new AssistConversation(base,"key").ask("","es",""));fails(()->new AssistConversation(base,"key").ask(String.join("",Collections.nCopies(2001,"x")),"es",""));
            fails(()->new AssistConversation("http://user:password@127.0.0.1","key").ask("test","es",""));
            AssistConversation slow=new AssistConversation(base,"key");ExecutorService worker=Executors.newSingleThreadExecutor();
            try{Future<?> pending=worker.submit(()->{try{slow.ask("slow","es","");}catch(Exception expected){}});check(arrived.await(3,TimeUnit.SECONDS));before=posts.get();slow.cancel();release.countDown();pending.get(4,TimeUnit.SECONDS);check(posts.get()==before);}
            finally{release.countDown();worker.shutdownNow();}
        }finally{server.stop(0);serverThreads.shutdownNow();}
        roles();System.out.println("AssistChecks: "+checks+" checks passed");
    }
    private static final class MemoryStore implements AssistRoleSelection.Store {
        String value="";boolean failWrite;
        public String read(){return value;}
        public void write(String json)throws IOException{if(failWrite)throw new IOException("disk unavailable");value=json;}
        public void clear(){value="";}
    }
    private static final class RoleShell implements AssistRoleSelection.Shell {
        final Map<String,String> values=new HashMap<>();final List<String> mutations=new ArrayList<>();boolean deny,mismatchRestore,badSelection;
        RoleShell(){google();}
        void google(){values.put("assistant","com.google.android.katniss/.Voice");values.put("voice_interaction_service","com.google.android.katniss/.Voice");values.put("voice_recognition_service","com.google.android.katniss/.Recognition");}
        public String run(String command)throws IOException{
            if(command.startsWith("settings get secure "))return values.get(command.substring("settings get secure ".length()));
            mutations.add(command);check(command.startsWith("cmd role add-role-holder --user 0 android.app.role.ASSISTANT "));
            if(deny)return "Permission denied";
            if(command.endsWith("io.freemytv 0")){values.put("assistant","io.freemytv/.AssistVoiceService");values.put("voice_interaction_service",badSelection?"io.freemytv/.Wrong":"io.freemytv/.AssistVoiceService");values.put("voice_recognition_service","io.freemytv/.AssistRecognitionService");}
            else if(command.endsWith("com.google.android.katniss 0")){google();if(mismatchRestore)values.put("voice_recognition_service","com.google.android.katniss/.Other");}
            else throw new IOException("Unexpected mutation");return "";
        }
    }
    private static void roles()throws Exception{
        RoleShell shell=new RoleShell();MemoryStore store=new MemoryStore();AssistRoleSelection roles=new AssistRoleSelection(shell,store);
        roles.enable();check(shell.values.get("assistant").startsWith("io.freemytv/"));check(!store.value.isEmpty());check(shell.mutations.size()==1);
        roles.enable();check(shell.mutations.size()==1);roles.restore();check(shell.values.get("assistant").startsWith("com.google.android.katniss/"));check(store.value.isEmpty());
        int before=shell.mutations.size();roles.restore();check(shell.mutations.size()==before);
        roles.enable();shell.values.put("assistant","org.other/.Assistant");shell.values.put("voice_interaction_service","org.other/.Assistant");before=shell.mutations.size();roles.restore();check(shell.mutations.size()==before);check(shell.values.get("assistant").startsWith("org.other/"));
        shell.google();store.failWrite=true;before=shell.mutations.size();fails(roles::enable);check(shell.mutations.size()==before);store.failWrite=false;
        shell.deny=true;fails(roles::enable);check(store.value.isEmpty());shell.deny=false;
        shell.badSelection=true;before=shell.mutations.size();fails(roles::enable);check(shell.mutations.size()==before+2);check(shell.values.get("assistant").startsWith("com.google.android.katniss/"));check(store.value.isEmpty());shell.badSelection=false;
        shell.values.put("assistant","bad; touch /tmp/no");before=shell.mutations.size();fails(roles::enable);check(shell.mutations.size()==before);
        shell.google();shell.values.put("assistant","null");before=shell.mutations.size();fails(roles::enable);check(shell.mutations.size()==before);
        shell.google();roles.enable();shell.mismatchRestore=true;fails(roles::restore);check(!store.value.isEmpty());
        shell.mismatchRestore=false;shell.values.put("assistant","io.freemytv/.AssistVoiceService");shell.values.put("voice_interaction_service","io.freemytv/.AssistVoiceService");roles.restore();check(store.value.isEmpty());
        shell.google();roles.enable();store.value="{\"schema\":1,\"assistant\":\"a/.B;bad\"}";before=shell.mutations.size();fails(roles::restore);check(shell.mutations.size()==before);
    }
}
