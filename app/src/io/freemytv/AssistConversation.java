package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.json.JSONObject;

/** One spoken turn, one POST. No retries, transcript history, redirects or background queue. */
final class AssistConversation {
    static final class Reply {
        final String text,conversation;final boolean error;
        Reply(String text,String conversation,boolean error){this.text=text;this.conversation=conversation;this.error=error;}
    }
    static final class HttpFailure extends IOException {
        final int status;HttpFailure(int status){super("Assist HTTP failure");this.status=status;}
    }
    private final String base,token;
    private volatile boolean cancelled;
    private HttpURLConnection connection;
    AssistConversation(String base,String token){this.base=base.trim().replaceAll("/+$","");this.token=token.trim();}
    void cancel(){
        HttpURLConnection old;
        synchronized(this){cancelled=true;old=connection;connection=null;}
        if(old!=null)old.disconnect();
    }
    Reply ask(String spoken,String language,String conversation)throws Exception {
        if(spoken==null||spoken.trim().isEmpty()||spoken.length()>2000||token.isEmpty())throw new IOException("Invalid Assist input");
        String query=spoken.trim();String entity=indoorQuestion(query,language);
        // Keep the two existing home questions working without inventing a Home Assistant area called "casa".
        if(entity!=null){
            try{
                JSONObject state=new JSONObject(request("/api/states/"+entity,null));
                JSONObject attributes=state.optJSONObject("attributes");
                String name=attributes==null?"":attributes.optString("friendly_name").trim();
                if(entity.equals(state.optString("entity_id"))&&!name.isEmpty()&&name.length()<=160)
                    query="¿Cuál es el estado de "+name+"?";
            }catch(IOException|org.json.JSONException unavailable){if(cancelled)throw new IOException("Cancelled");}
        }
        JSONObject payload=new JSONObject().put("text",query).put("language",language).put("agent_id","conversation.home_assistant");
        if(conversation!=null&&!conversation.isEmpty()&&conversation.length()<=512)payload.put("conversation_id",conversation);
        return parse(new JSONObject(request("/api/conversation/process",payload)));
    }
    static String indoorQuestion(String text,String language){
        if(language==null||!language.startsWith("es"))return null;
        String n=Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
        if(n.equals("que temperatura hay en casa")||n.equals("cual es la temperatura de casa"))return HomeClimate.TEMPERATURE.contains("XXXXX")?null:HomeClimate.TEMPERATURE;
        if(n.equals("que humedad hay en casa")||n.equals("cual es la humedad de casa"))return HomeClimate.HUMIDITY.contains("XXXXX")?null:HomeClimate.HUMIDITY;
        return null;
    }
    static Reply parse(JSONObject json)throws IOException {
        JSONObject response=json.optJSONObject("response");
        if(response==null)throw new IOException("Missing Assist response");
        JSONObject speech=response.optJSONObject("speech"),plain=speech==null?null:speech.optJSONObject("plain");
        Object value=plain==null?null:plain.opt("speech");
        String text=value instanceof String?((String)value).trim():"";
        if(text.isEmpty())throw new IOException("Missing Assist speech");
        if(text.length()>4096)text=text.substring(0,4095)+"…";
        Object conversation=json.opt("conversation_id");
        String id=conversation instanceof String?(String)conversation:"";if(id.length()>512)id="";
        return new Reply(text,id,"error".equals(response.optString("response_type")));
    }
    private String request(String path,JSONObject payload)throws Exception {
        URL url=new URL(base+path);
        if(!("http".equals(url.getProtocol())||"https".equals(url.getProtocol()))||url.getHost().isEmpty()||url.getUserInfo()!=null
                ||url.getRef()!=null||url.getQuery()!=null)throw new IOException("Invalid Assist URL");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        synchronized(this){if(cancelled){c.disconnect();throw new IOException("Cancelled");}connection=c;}
        try{
            c.setConnectTimeout(5000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");
            if(payload!=null){
                byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);
                c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                synchronized(this){if(cancelled)throw new IOException("Cancelled");}
                try(OutputStream out=c.getOutputStream()){out.write(body);}
            }
            int status=c.getResponseCode();if(status!=200)throw new HttpFailure(status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] bytes=new byte[4096];int n;
                while((n=in.read(bytes))!=-1){if(cancelled)throw new IOException("Cancelled");if(out.size()+n>65536)throw new IOException("Assist response too large");out.write(bytes,0,n);}
                return out.toString("UTF-8");
            }
        }finally{synchronized(this){if(connection==c)connection=null;}c.disconnect();}
    }
}
