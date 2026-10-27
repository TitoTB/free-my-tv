package io.freemytv;

import java.util.*;
import org.json.*;

/** Poster ranking and composition geometry, independent of Android and network access. */
final class CinemaPoster {
    static List<String> candidates(JSONArray images,String seed) {
        List<JSONObject> ranked=new ArrayList<>();
        for(int i=0;images!=null && i<images.length();i++) {
            JSONObject image=images.optJSONObject(i);
            if(image!=null && path(image.optString("file_path"))) ranked.add(image);
        }
        ranked.sort((a,b)->Long.compare(area(b),area(a)));
        Set<String> unique=new LinkedHashSet<>();
        for(JSONObject image:ranked) unique.add(image.optString("file_path"));
        if(path(seed)) unique.add(seed);
        return new ArrayList<>(unique);
    }
    private static long area(JSONObject image) { return Math.max(0,image.optLong("width"))*Math.max(0,image.optLong("height")); }
    static boolean path(String path) { return path.matches("/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)"); }
    static String key(String source,int width,int height) { return "poster-cover-v2:"+width+"x"+height+":"+source; }
    /** The original poster fills the screen proportionally, with a centred crop and no generated content. */
    static float[] bounds(int width,int height,int screenWidth,int screenHeight) {
        float scale=Math.max(screenWidth/(float)width,screenHeight/(float)height);
        float w=width*scale,h=height*scale;
        return new float[]{(screenWidth-w)/2,(screenHeight-h)/2,(screenWidth+w)/2,(screenHeight+h)/2};
    }
}
