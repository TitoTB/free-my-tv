package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Only public metadata and thumbnails; never request streams or replay a playback command. */
final class InterestApi {
    InterestData.Episode latest(int programme,long now) throws Exception {
        if(programme==2) {
            JSONObject page=json("https://www.rtve.es/api/programas/1000646/videos.json?size=20&order=desc&criteria=ate&type=39816").getJSONObject("page");
            JSONArray items=page.getJSONArray("items"); InterestData.Episode best=InterestData.empty(2);
            for(int i=0;i<items.length();i++) {
                InterestData.Episode candidate=InterestData.rtve(items.getJSONObject(i),now);
                if(candidate.date>best.date) best=candidate;
            }
            return best;
        }
        String format=InterestData.FORMATS[programme];
        JSONArray rows=json("https://api.atresplayer.com/client/v1/page/format/"+format).getJSONArray("rows");
        String list="";
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            if("EPISODE".equals(row.optString("type")) && "Capítulos".equals(row.optString("title"))) { list=row.optString("href"); break; }
        }
        if(!InterestData.allowed(list,"api.atresplayer.com")) throw new IOException("Missing episode catalogue");
        list=list.replaceAll("([?&])sortType=[^&]*","$1sortType=THE_LAST");
        if(!list.contains("sortType=")) list+=(list.contains("?")?"&":"?")+"sortType=THE_LAST";
        JSONArray items=json(list).getJSONArray("itemRows");
        // The provider orders complete episodes newest first; skip paid/unpublished entries.
        for(int i=0;i<items.length() && i<10;i++) {
            JSONObject row=items.getJSONObject(i);
            if(!"FREE".equals(row.optString("visibility")) || !"EPISODE".equals(row.optString("type"))) continue;
            String id=row.optString("contentId"); if(!id.matches("[a-f0-9]{24}")) continue;
            InterestData.Episode e=InterestData.atres(programme,row,json("https://api.atresplayer.com/client/v1/page/episode/"+id),now);
            if(e.ready()) return e;
        }
        return InterestData.empty(programme);
    }
    private JSONObject json(String url) throws Exception {
        return new JSONObject(new String(read(url,4*1024*1024),StandardCharsets.UTF_8));
    }
    static byte[] read(String url,int limit) throws Exception {
        for(int redirect=0;redirect<4;redirect++) {
            if(!InterestData.allowed(url,"api.atresplayer.com","imagenes.atresplayer.com","www.rtve.es","img.rtve.es")) throw new IOException("Unsupported source");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            try {
                c.setConnectTimeout(4000); c.setReadTimeout(6000); c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent","FreeMyTV/1.0");
                int code=c.getResponseCode();
                if(code==301 || code==302 || code==307 || code==308) { url=new URL(new URL(url),c.getHeaderField("Location")).toString(); continue; }
                if(code!=200) throw new IOException("Programme source unavailable");
                try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192]; int n;
                    while((n=in.read(buffer))!=-1) { if(out.size()+n>limit) throw new IOException("Source too large"); out.write(buffer,0,n); }
                    return out.toByteArray();
                }
            } finally { c.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }
}
