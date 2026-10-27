package io.freemytv;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** One synthesis POST, never retried or redirected. No credential/text/audio logging. */
final class ElevenLabsSynthesis {
    private final String base;
    private volatile boolean cancelled;
    private HttpURLConnection connection;
    ElevenLabsSynthesis(){base="https://api.elevenlabs.io";}
    /** Loopback-only endpoint for fake-server tests. */
    ElevenLabsSynthesis(String testBase)throws Exception{
        URL url=new URL(testBase);
        if(!"http".equals(url.getProtocol())||!"127.0.0.1".equals(url.getHost())||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null||!url.getPath().isEmpty())throw new IOException("Invalid test endpoint");
        base=testBase;
    }
    byte[] synthesize(String text,String voice,String key)throws Exception{
        if(text==null||text.trim().isEmpty()||text.length()>4096||!AssistVoiceOptions.allowed(voice)||key==null||key.trim().isEmpty()||key.indexOf('\r')>=0||key.indexOf('\n')>=0)throw new IOException("Invalid speech input");
        JSONObject settings=new JSONObject().put("stability",.5).put("similarity_boost",.75).put("style",0).put("use_speaker_boost",true);
        byte[] body=new JSONObject().put("text",text).put("model_id",AssistVoiceOptions.MODEL).put("voice_settings",settings).toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection call=(HttpURLConnection)new URL(base+"/v1/text-to-speech/"+voice+"?output_format=mp3_44100_128").openConnection();
        synchronized(this){if(cancelled){call.disconnect();throw new IOException("Cancelled");}connection=call;}
        try{
            call.setInstanceFollowRedirects(false);call.setConnectTimeout(8000);call.setReadTimeout(30000);call.setUseCaches(false);
            call.setRequestMethod("POST");call.setDoOutput(true);call.setFixedLengthStreamingMode(body.length);
            call.setRequestProperty("xi-api-key",key.trim());call.setRequestProperty("Accept","audio/mpeg");call.setRequestProperty("Content-Type","application/json; charset=utf-8");
            if(cancelled)throw new IOException("Cancelled");
            try(OutputStream out=call.getOutputStream()){out.write(body);}
            if(call.getResponseCode()!=200)throw new IOException("Speech unavailable");
            String type=call.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("audio/mpeg"))throw new IOException("Invalid speech response");
            try(InputStream in=call.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){if(cancelled)throw new IOException("Cancelled");if(out.size()+n>4*1024*1024)throw new IOException("Speech too large");out.write(buffer,0,n);}
                if(cancelled||out.size()<128)throw new IOException("Invalid speech response");
                return out.toByteArray();
            }
        }finally{synchronized(this){if(connection==call)connection=null;}call.disconnect();}
    }
    void cancel(){HttpURLConnection call;synchronized(this){cancelled=true;call=connection;connection=null;}if(call!=null)call.disconnect();}
}
