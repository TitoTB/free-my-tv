package io.freemytv;

import com.sun.net.httpserver.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Local fake synthesis endpoint. Never uses a real key or consumes credits. */
public final class ElevenLabsSpeechChecks {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("Speech check "+checks);checks++;}
    private interface Work{void run()throws Exception;}
    private static void fails(Work work)throws Exception{try{work.run();throw new AssertionError("Expected failure");}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        check(AssistVoiceOptions.count()==4);check(AssistVoiceOptions.name(0).equals("Adam"));check(AssistVoiceOptions.name(1).equals("Bill"));
        check(AssistVoiceOptions.name(2).equals("Lily"));check(AssistVoiceOptions.name(3).equals("Matilda"));
        check(AssistVoiceOptions.DEFAULT.equals("XrExE9yKIg1WjnnlVkGX"));
        for(int i=0;i<4;i++)check(AssistVoiceOptions.effective(AssistVoiceOptions.id(i),"").equals(AssistVoiceOptions.id(i)));
        check(AssistVoiceOptions.effective(null,null).equals(AssistVoiceOptions.DEFAULT));
        check(AssistVoiceOptions.effective("unknown","").equals(AssistVoiceOptions.DEFAULT));
        check(AssistVoiceOptions.effective("","bsEDAkNZWaEolZ7vEeVJ").equals(AssistVoiceOptions.DEFAULT));
        check(AssistVoiceOptions.effective("",AssistVoiceOptions.id(1)).equals(AssistVoiceOptions.id(1)));
        check(AssistVoiceOptions.effective(AssistVoiceOptions.id(2),AssistVoiceOptions.id(1)).equals(AssistVoiceOptions.id(2)));
        check(!AssistVoiceOptions.allowed("../bad"));check(!AssistVoiceOptions.allowed(null));
        for(String base:new String[]{"http://example.com","https://example.com","http://127.0.0.1/path","http://key@127.0.0.1","http://127.0.0.1?x=1","http://127.0.0.1#x"})fails(()->new ElevenLabsSynthesis(base));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger mode=new AtomicInteger(),calls=new AtomicInteger(),forwarded=new AtomicInteger();
        String[] body={""},path={""},key={""},query={""};
        server.createContext("/v1/text-to-speech/",exchange->{
            calls.incrementAndGet();body[0]=new String(read(exchange.getRequestBody()),"UTF-8");path[0]=exchange.getRequestURI().getPath();query[0]=exchange.getRequestURI().getQuery();key[0]=exchange.getRequestHeaders().getFirst("xi-api-key");
            int current=mode.get();
            if(current==6){exchange.close();return;}
            int status=current==1?402:current==2?302:200;
            if(current==2)exchange.getResponseHeaders().set("Location","/forwarded");
            exchange.getResponseHeaders().set("Content-Type",current==3?"application/json":"audio/mpeg");
            int size=current==4?0:current==5?4*1024*1024+1:1024;
            exchange.sendResponseHeaders(status,size==0?-1:size);
            try(OutputStream out=exchange.getResponseBody()){
                byte[] bytes=new byte[8192];bytes[0]='I';bytes[1]='D';bytes[2]='3';
                while(size>0){int n=Math.min(size,bytes.length);out.write(bytes,0,n);size-=n;}
            }catch(IOException cancelled){}finally{exchange.close();}
        });
        server.createContext("/forwarded",exchange->{forwarded.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();String sample="Hola, ¿qué temperatura hay en casa?";
        try{
            for(int i=0;i<4;i++){
                int before=calls.get();byte[] mp3=new ElevenLabsSynthesis(base).synthesize(sample,AssistVoiceOptions.id(i),"test-key");
                check(mp3.length==1024);check(mp3[0]=='I');check(calls.get()==before+1);check(key[0].equals("test-key"));
                check(path[0].endsWith("/"+AssistVoiceOptions.id(i)));check(query[0].equals("output_format=mp3_44100_128"));
                JSONObject payload=new JSONObject(body[0]);check(payload.getString("text").equals(sample));check(payload.getString("model_id").equals("eleven_multilingual_v2"));
                check(!payload.has("language_code"));check(payload.getJSONObject("voice_settings").getDouble("similarity_boost")==.75);
            }
            for(int i=1;i<=6;i++){
                mode.set(i);int before=calls.get();fails(()->new ElevenLabsSynthesis(base).synthesize(sample,AssistVoiceOptions.DEFAULT,"test-key"));check(calls.get()==before+1);
            }
            check(forwarded.get()==0);mode.set(0);int before=calls.get();
            ElevenLabsSynthesis cancelled=new ElevenLabsSynthesis(base);cancelled.cancel();fails(()->cancelled.synthesize(sample,AssistVoiceOptions.DEFAULT,"test-key"));check(calls.get()==before);
            for(String invalid:new String[]{""," ",null})fails(()->new ElevenLabsSynthesis(base).synthesize(invalid,AssistVoiceOptions.DEFAULT,"test-key"));
            fails(()->new ElevenLabsSynthesis(base).synthesize(sample,"bsEDAkNZWaEolZ7vEeVJ","test-key"));
            fails(()->new ElevenLabsSynthesis(base).synthesize(sample,AssistVoiceOptions.DEFAULT,""));
            fails(()->new ElevenLabsSynthesis(base).synthesize(sample,AssistVoiceOptions.DEFAULT,"key\nheader"));
            fails(()->new ElevenLabsSynthesis(base).synthesize(new String(new char[4097]).replace('\0','a'),AssistVoiceOptions.DEFAULT,"test-key"));check(calls.get()==before);
        }finally{server.stop(0);}
        System.out.println("ElevenLabsSpeechChecks: "+checks+" checks passed");
    }
    private static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] data=new byte[1024];int n;while((n=in.read(data))!=-1)out.write(data,0,n);return out.toByteArray();}
}
