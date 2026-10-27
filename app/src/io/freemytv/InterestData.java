package io.freemytv;

import org.json.*;
import java.net.URI;
import java.util.*;

/** Public programme metadata, independent of accounts, playback history and Android. */
final class InterestData {
    static final String[] NAMES={"Salvados","laSexta Columna","La Revuelta"};
    static final String[] FORMATS={"5a78770f986b28066cd50498","5a78768d986b28066d9cdf97"};
    static final String[] PACKAGES={"com.antena3.atresplayer.tv","com.antena3.atresplayer.tv","com.rtve.androidtv"};
    static final class Episode {
        final int programme;
        final String id,title,description,image,url;
        final long date;
        Episode(int programme,String id,String title,String description,String image,String url,long date) {
            this.programme=programme; this.id=id; this.title=title; this.description=description;
            this.image=image; this.url=url; this.date=date;
        }
        boolean ready() { return !id.isEmpty() && date>0 && !url.isEmpty(); }
    }
    static Episode empty(int programme) { return new Episode(programme,"","","","","",0); }
    static List<Episode> sorted(Collection<Episode> episodes) {
        Episode[] unique={empty(0),empty(1),empty(2)};
        for(Episode e:episodes) if(e.programme>=0 && e.programme<3 && e.date>unique[e.programme].date) unique[e.programme]=e;
        List<Episode> out=new ArrayList<>(Arrays.asList(unique));
        out.sort((a,b)->{ int order=Long.compare(b.date,a.date); return order!=0?order:Integer.compare(a.programme,b.programme); });
        return Collections.unmodifiableList(out);
    }
    static boolean allowed(String value,String... hosts) {
        try {
            URI uri=new URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) return false;
            for(String host:hosts) if(host.equalsIgnoreCase(uri.getHost())) return true;
        } catch(Exception ignored) {}
        return false;
    }
    static Episode atres(int programme,JSONObject row,JSONObject detail,long now) {
        if(programme<0 || programme>1 || !"EPISODE".equals(row.optString("type")) || !"FREE".equals(row.optString("visibility"))) return empty(programme);
        String id=row.optString("contentId");
        if(!id.matches("[a-f0-9]{24}") || !id.equals(detail.optString("id"))) return empty(programme);
        JSONObject format=detail.optJSONObject("format");
        if(format!=null && !FORMATS[programme].equals(format.optString("id"))) return empty(programme);
        long date=detail.optLong("publicationDate");
        JSONObject link=row.optJSONObject("link"),art=detail.optJSONObject("image");
        String path=link==null?"":link.optString("url"),base=art==null?"":art.optString("pathHorizontal");
        String prefix=programme==0?"/lasexta/programas/salvados/":"/lasexta/programas/lasexta-columna/";
        if(date<=0 || date>now || !path.startsWith(prefix) || !path.contains(id)) return empty(programme);
        String image=base.replaceAll("/+$","")+"/640x360.jpg?optimize=medium";
        if(!allowed(image,"imagenes.atresplayer.com")) image="";
        return new Episode(programme,id,detail.optString("title",row.optString("title")),detail.optString("description",row.optString("description")),
                image,"https://www.atresplayer.com"+path,date);
    }
    static Episode rtve(JSONObject item,long now) {
        JSONObject type=item.optJSONObject("type"),programme=item.optJSONObject("programInfo");
        String id=item.optString("id"); long date=item.optLong("publicationDateTimestamp");
        if(type==null || type.optInt("id")!=39816 || programme==null || !"1000646".equals(programme.optString("id"))
                || !id.matches("[0-9]+") || date<=0 || date>now) return empty(2);
        String image=item.optString("thumbnail"); if(image.isEmpty()) image=item.optString("imageSEO");
        if(!allowed(image,"img.rtve.es")) image="";
        String description=item.optString("shortDescription"); if(description.isEmpty()) description=item.optString("description");
        return new Episode(2,id,item.optString("title"),description,image,"https://play.rtve.es/v/"+id,date);
    }
    static JSONArray encode(List<Episode> episodes) throws Exception {
        JSONArray out=new JSONArray();
        for(Episode e:episodes) if(e.ready()) out.put(new JSONObject().put("programme",e.programme).put("id",e.id).put("title",e.title)
                .put("description",e.description).put("image",e.image).put("url",e.url).put("date",e.date));
        return out;
    }
    static List<Episode> decode(JSONArray rows,long now) {
        List<Episode> episodes=new ArrayList<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i); if(r==null) continue;
            int p=r.optInt("programme",-1); long date=r.optLong("date");
            String id=r.optString("id"),url=r.optString("url");
            if(p<0 || p>2 || date<=0 || date>now || !allowed(url,p==2?"play.rtve.es":"www.atresplayer.com")) continue;
            String prefix=p==2?"https://play.rtve.es/v/":p==0?"https://www.atresplayer.com/lasexta/programas/salvados/":"https://www.atresplayer.com/lasexta/programas/lasexta-columna/";
            if(!url.startsWith(prefix) || !url.contains(id) || !id.matches(p==2?"[0-9]+":"[a-f0-9]{24}")) continue;
            String image=r.optString("image");
            if(!allowed(image,p==2?"img.rtve.es":"imagenes.atresplayer.com")) image="";
            episodes.add(new Episode(p,id,r.optString("title"),r.optString("description"),image,url,date));
        }
        return sorted(episodes);
    }
}
