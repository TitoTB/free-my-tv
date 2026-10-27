package io.freemytv;

import java.nio.file.*;
import java.util.*;
import org.json.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

/** Programme identity, episode filtering, ordering, safe app links and the actual public-source fixtures. */
public final class InterestChecks {
    private static int checks;
    private static void check(boolean value) { if(!value) throw new AssertionError("Interest check "+checks); checks++; }
    private static JSONObject fixture(Path root,String file) throws Exception { return new JSONObject(Files.readString(root.resolve(file))); }
    public static void main(String[] args) throws Exception {
        Path fixtures=Paths.get(args[0]); long now=1792799999000L;
        JSONObject srow=fixture(fixtures,"salvados-episodes.json").getJSONArray("itemRows").getJSONObject(0);
        JSONObject sd=fixture(fixtures,"salvados-latest-detail.json");
        JSONObject crow=fixture(fixtures,"lasexta-columna-episodes.json").getJSONArray("itemRows").getJSONObject(0);
        JSONObject cd=fixture(fixtures,"columna-latest-detail.json");
        JSONObject rr=fixture(fixtures,"la-revuelta-video.json").getJSONObject("page").getJSONArray("items").getJSONObject(0);
        InterestData.Episode salvados=InterestData.atres(0,srow,sd,now),columna=InterestData.atres(1,crow,cd,now),revuelta=InterestData.rtve(rr,now);
        check(salvados.ready()); check(columna.ready()); check(revuelta.ready());
        check(salvados.description.contains("Ceuta")); check(columna.description.contains("agitadores"));
        check(revuelta.description.contains("Broncano")); check(revuelta.url.equals("https://play.rtve.es/v/17279095"));
        check(InterestData.PACKAGES[salvados.programme].equals("com.antena3.atresplayer.tv"));
        check(InterestData.PACKAGES[revuelta.programme].equals("com.rtve.androidtv"));
        List<InterestData.Episode> sorted=InterestData.sorted(Arrays.asList(columna,revuelta,salvados,salvados));
        check(sorted.size()==3); check(sorted.get(0).programme==2); check(sorted.get(1).programme==0); check(sorted.get(2).programme==1);
        check(InterestData.sorted(Collections.emptyList()).size()==3);
        check(InterestData.sorted(Collections.singleton(salvados)).get(0)==salvados);
        check(!InterestData.atres(0,new JSONObject(srow.toString()).put("type","VIDEO"),sd,now).ready());
        check(!InterestData.atres(0,new JSONObject(srow.toString()).put("visibility","PREMIUM"),sd,now).ready());
        check(!InterestData.atres(0,srow,new JSONObject(sd.toString()).put("id","aaaaaaaaaaaaaaaaaaaaaaaa"),now).ready());
        check(!InterestData.atres(0,srow,sd,1).ready());
        check(!InterestData.rtve(new JSONObject(rr.toString()).put("type",new JSONObject().put("id",123)),now).ready());
        check(!InterestData.rtve(new JSONObject(rr.toString()).put("programInfo",new JSONObject().put("id","1000000")),now).ready());
        check(!InterestData.rtve(rr,1).ready());
        for(String url:new String[]{"http://img.rtve.es/a.jpg","https://img.rtve.es.evil.test/a.jpg","https://user@img.rtve.es/a.jpg","https://img.rtve.es:123/a.jpg","file:///tmp/a.jpg"})
            check(!InterestData.allowed(url,"img.rtve.es"));
        List<InterestData.Episode> restored=InterestData.decode(InterestData.encode(sorted),now);
        check(restored.size()==3); check(restored.get(0).id.equals(revuelta.id)); check(restored.get(1).url.equals(salvados.url));
        JSONArray bad=InterestData.encode(sorted); bad.getJSONObject(0).put("url","https://evil.test/v/17279095");
        check(!InterestData.decode(bad,now).get(2).ready());
        if(args.length>1 && args[1].equals("--live")) {
            InterestApi api=new InterestApi(); List<InterestData.Episode> live=new ArrayList<>();
            for(int i=0;i<3;i++) {
                InterestData.Episode e=api.latest(i,System.currentTimeMillis()); check(e.ready()); check(e.programme==i);
                check(!e.description.isEmpty()); check(!e.image.isEmpty());
                byte[] bytes=InterestApi.read(e.image,5*1024*1024);
                java.awt.image.BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
                check(image!=null && image.getWidth()>300 && image.getHeight()>150); live.add(e);
                System.out.println(InterestData.NAMES[i]+": "+e.title+"; image "+image.getWidth()+"x"+image.getHeight());
            }
            List<InterestData.Episode> ordered=InterestData.sorted(live);
            check(ordered.get(0).date>=ordered.get(1).date && ordered.get(1).date>=ordered.get(2).date);
        }
        System.out.println("InterestChecks: "+checks+" checks passed");
    }
}
