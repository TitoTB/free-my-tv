package io.freemytv;

import org.json.JSONObject;
import java.io.*;
import java.net.*;

/** Read-only subscription quota. No speech generation, billing action, persistent cache or account logging. */
final class ElevenLabsUsage {
    static final class Snapshot {
        final Long used,limit,resetMillis;
        Snapshot(Long used,Long limit,Long resetMillis){this.used=used;this.limit=limit;this.resetMillis=resetMillis;}
        Double remainingPercent(){
            if(used==null||limit==null||limit<=0)return null;
            long remaining=Math.max(0,limit-used);
            return Math.floor(remaining*1000d/limit)/10d;
        }
    }
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    static Snapshot parse(String body)throws Exception{
        JSONObject data=new JSONObject(body);
        Long used=nonNegative(data.opt("character_count")),limit=nonNegative(data.opt("character_limit"));
        Long seconds=nonNegative(data.opt("next_character_count_reset_unix"));
        Long reset=seconds==null||seconds==0||seconds>Long.MAX_VALUE/1000?null:seconds*1000;
        return new Snapshot(used,limit,reset);
    }
    private static Long nonNegative(Object value){
        if(!(value instanceof Number))return null;
        try{long number=Long.parseLong(value.toString());return number<0?null:number;}catch(NumberFormatException invalid){return null;}
    }
    Snapshot fetch(String key)throws Exception{
        if(cancelled||key==null||key.trim().isEmpty())throw new IOException("Unavailable");
        HttpURLConnection call=(HttpURLConnection)new URL("https://api.elevenlabs.io/v1/user/subscription").openConnection();
        connection=call;
        try{
            if(cancelled)throw new IOException("Cancelled");
            call.setInstanceFollowRedirects(false);call.setConnectTimeout(8000);call.setReadTimeout(8000);
            call.setRequestMethod("GET");call.setRequestProperty("Accept","application/json");
            call.setRequestProperty("xi-api-key",key);call.setUseCaches(false);
            if(call.getResponseCode()!=200)throw new IOException("Unavailable");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=call.getInputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1){
                    if(cancelled)throw new IOException("Cancelled");
                    if(bytes.size()+count>65536)throw new IOException("Unavailable");
                    bytes.write(buffer,0,count);
                }
            }
            if(cancelled)throw new IOException("Cancelled");
            return parse(bytes.toString("UTF-8"));
        }finally{connection=null;call.disconnect();}
    }
    void cancel(){cancelled=true;HttpURLConnection call=connection;if(call!=null)call.disconnect();}
}
