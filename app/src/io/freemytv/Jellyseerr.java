package io.freemytv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Direct requests to the user's local server. Credentials and response bodies never enter logs. */
final class Jellyseerr {
    private static final long STATUS_TTL=60_000;
    private final Context context;
    private final Runnable changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    Jellyseerr(Context context,Runnable changed) { this.context=context; this.changed=changed; }
    static boolean configured() { return !Config.JELLYSEERR_URL.trim().isEmpty(); }
    static boolean authenticated() { return configured() && !Config.JELLYSEERR_KEY.trim().isEmpty(); }
    static boolean canRequest(Cinema.Movie m) {
        return authenticated() && m!=null && m.tmdbId>0 && m.requestKnown && !m.requested && !m.requestBusy;
    }

    void refresh(Cinema.Movie m) {
        long now=System.currentTimeMillis();
        if(!authenticated() || m.tmdbId<=0 || m.requestBusy
                || (m.requestCheckedAt>0 && now>=m.requestCheckedAt && now-m.requestCheckedAt<STATUS_TTL)) return;
        m.requestBusy=true; m.requestCheckedAt=now;
        io.execute(()->{
            try {
                boolean found=hasRequest(call("GET","/movie/"+m.tmdbId,null));
                main.post(()->{ m.requested=found; m.requestKnown=true; finish(m); });
            } catch(Exception e) {
                main.post(()->{ m.requestKnown=false; finish(m); });
            }
        });
    }

    void request(Cinema.Movie m) {
        if(!canRequest(m)) return;
        m.requestBusy=true; changed.run();
        io.execute(()->{
            try {
                // Recheck immediately before writing: another client may have requested it since refresh.
                if(hasRequest(call("GET","/movie/"+m.tmdbId,null))) {
                    main.post(()->complete(m,false)); return;
                }
                JSONObject body=new JSONObject().put("mediaType","movie").put("mediaId",m.tmdbId);
                call("POST","/request",body);
                main.post(()->complete(m,true));
            } catch(Exception e) {
                // A lost POST response can still mean success. Never automatically resend the POST.
                try {
                    if(hasRequest(call("GET","/movie/"+m.tmdbId,null))) {
                        main.post(()->complete(m,false)); return;
                    }
                } catch(Exception ignored) { }
                main.post(()->{
                    m.requestKnown=false; finish(m);
                    Toast.makeText(context,Ui.tr("Couldn't confirm the request. Check Jellyseerr."),Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void complete(Cinema.Movie m,boolean created) {
        m.requestKnown=true; m.requested=true; finish(m);
        Toast.makeText(context,created?Ui.tr("Request sent"):Ui.tr("Already requested"),Toast.LENGTH_SHORT).show();
    }
    private void finish(Cinema.Movie m) {
        m.requestBusy=false; m.requestCheckedAt=System.currentTimeMillis(); changed.run();
    }
    static boolean hasRequest(JSONObject movie) throws IOException {
        if(movie.optInt("id",0)<=0) throw new IOException("Invalid movie response");
        JSONObject media=movie.optJSONObject("mediaInfo");
        if(media==null) return false;
        int status=media.optInt("status",0);
        JSONArray requests=media.optJSONArray("requests");
        return (status>=2 && status<=5) || (requests!=null && requests.length()>0);
    }
    private JSONObject call(String method,String path,JSONObject body) throws Exception {
        String base=Config.JELLYSEERR_URL.trim().replaceAll("/+$","");
        HttpURLConnection c=(HttpURLConnection)new URL(base+"/api/v1"+path).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method); c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("X-Api-Key",Config.JELLYSEERR_KEY.trim());
        try {
            if(body!=null) {
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                c.setRequestProperty("Content-Type","application/json"); c.setDoOutput(true);
                c.setFixedLengthStreamingMode(bytes.length);
                try(java.io.OutputStream out=c.getOutputStream()) { out.write(bytes); }
            }
            int code=c.getResponseCode();
            if(code<200 || code>=300) throw new IOException("Jellyseerr HTTP "+code);
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=in.read(buffer))!=-1) {
                    if(out.size()+count>2*1024*1024) throw new IOException("Response too large");
                    out.write(buffer,0,count);
                }
                return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        } finally { c.disconnect(); }
    }
}
