package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Optional artwork-only provider. All calls, including persistent-cache access, run on workers. */
final class Fanart {
    interface Transport { JSONObject get(String id,String key) throws Exception; }
    private final String key;
    private final File cache;
    private final Transport transport;
    private final Map<String,Entry> entries=new HashMap<>();
    private boolean loaded;
    private static final class Entry {
        final String image; final long at;
        Entry(String image,long at) { this.image=image; this.at=at; }
    }
    Fanart(String key,File cache) { this(key,cache,Fanart::request); }
    Fanart(String key,File cache,Transport transport) { this.key=key.trim(); this.cache=cache; this.transport=transport; }
    static String identity(int tmdb,String imdb) {
        return imdb!=null && imdb.matches("tt[0-9]+")?imdb:tmdb>0?String.valueOf(tmdb):"";
    }
    synchronized String background(int tmdb,String imdb) throws Exception {
        String id=identity(tmdb,imdb); if(key.isEmpty() || id.isEmpty()) return "";
        load(); long now=System.currentTimeMillis(); Entry saved=entries.get(id);
        if(saved!=null && now>=saved.at && now-saved.at<(saved.image.isEmpty()?15*60_000L:24*60*60_000L)) return saved.image;
        JSONObject response;
        try { response=transport.get(id,key); }
        catch(Exception unavailable) {
            if(saved!=null && !saved.image.isEmpty()) return saved.image;
            throw unavailable;
        }
        String image=select(response,tmdb,imdb);
        entries.put(id,new Entry(image,now)); persist(); return image;
    }
    synchronized void retain(Set<String> active) {
        if(key.isEmpty()) return;
        load(); if(entries.keySet().retainAll(active)) persist();
    }
    static boolean allowed(String source) {
        try {
            URI uri=new URI(source);
            return "https".equals(uri.getScheme()) && "assets.fanart.tv".equals(uri.getHost())
                    && uri.getUserInfo()==null && uri.getPort()==-1 && uri.getPath().startsWith("/fanart/movies/");
        } catch(Exception ignored) { return false; }
    }
    static String select(JSONObject response,int tmdb,String imdb) {
        if(response==null || response.length()==0) return "";
        String foundTmdb=response.optString("tmdb_id"),foundImdb=response.optString("imdb_id");
        if(tmdb>0 && !foundTmdb.isEmpty() && !String.valueOf(tmdb).equals(foundTmdb)) return "";
        if(imdb!=null && imdb.matches("tt[0-9]+") && !foundImdb.isEmpty() && !imdb.equals(foundImdb)) return "";
        if(!(tmdb>0 && String.valueOf(tmdb).equals(foundTmdb)) && !(imdb!=null && imdb.matches("tt[0-9]+") && imdb.equals(foundImdb))) return "";
        JSONArray images=response.optJSONArray("moviebackground"); String best=""; long score=Long.MIN_VALUE;
        for(int i=0;images!=null && i<images.length();i++) {
            JSONObject image=images.optJSONObject(i); if(image==null || !allowed(image.optString("url"))) continue;
            String language=image.optString("lang");
            long rank=(language.isEmpty() || "00".equals(language) || "null".equals(language)?2_000_000L:"es".equals(language)?1_000_000L:0)
                    +Math.min(999_999,Math.max(0,image.optInt("likes")));
            if(rank>score) { score=rank; best=image.optString("url"); }
        }
        return best;
    }
    private void load() {
        if(loaded) return; loaded=true;
        try {
            if(!cache.isFile() || cache.length()>1024*1024) return;
            JSONArray rows=new JSONArray(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8));
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i); if(row==null) continue;
                String id=row.optString("id"),image=row.optString("image");
                if(id.matches("(?:tt)?[0-9]+") && (image.isEmpty() || allowed(image)))
                    entries.put(id,new Entry(image,row.optLong("at")));
            }
        } catch(Exception ignored) { }
    }
    private void persist() {
        File part=new File(cache.getPath()+".part");
        try {
            JSONArray rows=new JSONArray();
            for(Map.Entry<String,Entry> item:entries.entrySet()) rows.put(new JSONObject().put("id",item.getKey())
                    .put("image",item.getValue().image).put("at",item.getValue().at));
            try(OutputStream out=new FileOutputStream(part)) { out.write(rows.toString().getBytes(StandardCharsets.UTF_8)); }
            java.nio.file.Files.move(part.toPath(),cache.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception ignored) { } finally { if(part.exists()) part.delete(); }
    }
    private static JSONObject request(String id,String key) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL("https://webservice.fanart.tv/v3/movies/"+id).openConnection();
        connection.setRequestProperty("api-key",key); connection.setRequestProperty("Accept","application/json");
        connection.setConnectTimeout(5000); connection.setReadTimeout(8000);
        try {
            int status=connection.getResponseCode(); if(status==404) return new JSONObject();
            if(status!=200) throw new IOException("Fanart HTTP "+status);
            try(InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while((n=in.read(buffer))!=-1) { if(out.size()+n>1024*1024) throw new IOException("Fanart response limit"); out.write(buffer,0,n); }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { connection.disconnect(); }
    }
}
