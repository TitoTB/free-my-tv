package io.freemytv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Bounded REST requests; never follow redirects, log bodies or retry device commands. */
final class HomeControlApi {
    private final String base,token;
    private String temperatureUnit;
    HomeControlApi(String base,String token) { this.base=base.trim().replaceAll("/+$",""); this.token=token.trim(); }
    HomeControlState read(int index) throws Exception {
        if(index<0 || index>=HomeControlState.ENTITIES.length || (HomeControlState.ENTITIES[index].isEmpty() || HomeControlState.ENTITIES[index].contains("XXXXX"))) throw new IOException("Entity not configured");
        JSONObject json=new JSONObject(request("/api/states/"+HomeControlState.ENTITIES[index],null));
        JSONObject attributes=json.optJSONObject("attributes");
        if(index==1 && attributes!=null && attributes.opt("temperature") instanceof Number && attributes.optString("temperature_unit").isEmpty()) {
            // Climate values use HA's unit system; the launcher's weather preference may differ.
            if(temperatureUnit==null) {
                temperatureUnit="";
                try {
                    JSONObject units=new JSONObject(request("/api/config",null)).optJSONObject("unit_system");
                    if(units!=null) temperatureUnit=units.optString("temperature");
                } catch(Exception ignored) {}
            }
            if(!temperatureUnit.isEmpty()) attributes.put("temperature_unit",temperatureUnit);
        }
        return HomeControlState.parse(index,json);
    }
    HomeControlState command(int index,int position) throws Exception {
        // Toggle against a fresh state, not the potentially stale value painted on screen.
        HomeControlState fresh=read(index);
        request("/api/services/"+fresh.service(index),HomeControlState.payload(index,position));
        return fresh;
    }
    String request(String path,JSONObject payload) throws Exception {
        URL url=new URL(base+path);
        if(!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) throw new IOException("Invalid URL");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        try {
            c.setConnectTimeout(3000); c.setReadTimeout(4000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Authorization","Bearer "+token); c.setRequestProperty("Accept","application/json");
            if(payload!=null) {
                byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
                c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json");
                c.setDoOutput(true); c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()) { out.write(bytes); }
            }
            if(c.getResponseCode()!=200) throw new IOException("Home Assistant request failed");
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[4096]; int n;
                while((n=in.read(b))!=-1) { if(out.size()+n>262144) throw new IOException("Response too large"); out.write(b,0,n); }
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        } finally { c.disconnect(); }
    }
}
