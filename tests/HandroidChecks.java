package io.freemytv;

import org.json.JSONObject;

public final class HandroidChecks {
    private static int checks;
    private static long now=1_000_000;
    private static HandroidNotice notice(String id,String type,boolean persistent) throws Exception {
        return new HandroidNotice(json(id,type,persistent),now);
    }
    private static JSONObject json(String id,String type,boolean persistent) throws Exception {
        return new JSONObject().put("notification_id",id).put("notification_type",type).put("message","Mensaje de prueba")
            .put("persistent",persistent).put("duration",8).put("expires_at",now+10000);
    }
    private static void check(boolean yes) { if(!yes) throw new AssertionError("Check "+(checks+1)); checks++; }
    public static void main(String[] args) throws Exception {
        HandroidNotice.Queue q=new HandroidNotice.Queue();
        HandroidNotice normal=notice("normal","normal",true),critical=notice("critical","critical",false);
        check(critical.persistent); // Critical severity always overrides persistent=false.
        check(!notice("temporary","normal",false).persistent);
        check(q.offer(normal,false).equals("discarded")); check(q.pending()==0);
        check(q.offer(normal,true).equals("shown")); check(q.current.persistent);
        check(q.offer(notice("second","normal",false),true).equals("queued"));
        check(q.offer(critical,true).equals("shown")); check(q.current==critical); check(q.pending()==3);
        check(q.next()==normal); check(q.current.persistent);
        check(q.offer(notice("normal","normal",false),true).equals("shown")); check(q.pending()==2); check(!q.current.persistent);
        check(q.next().id.equals("second")); check(q.next()==null);
        q.offer(normal,true); q.offer(critical,true);
        check(q.clear().size()==2); check(q.pending()==0); check(q.next()==null);
        check(q.offer(notice("off_persistent","critical",true),false).equals("discarded")); check(q.next()==null);
        q.offer(normal,true);
        for(int i=0;i<10;i++) check(q.offer(notice("n"+i,"normal",false),true).equals("queued"));
        check(q.offer(notice("overflow","normal",false),true).equals("queue_full"));
        check(q.offer(notice("n9","critical",true),true).equals("shown")); check(q.pending()==11);
        q.clear(); check(q.pending()==0);
        for(JSONObject invalid:new JSONObject[]{json("bad id","normal",false),json("a","invalid",false),
                json("a","normal",false).put("message","   "),json("a","normal",false).put("persistent","true"),
                json("a","normal",false).put("duration",1),json("a","normal",false).put("duration",8.5),
                json("a","normal",false).put("duration",121),json("a","normal",false).put("expires_at",now-1),
                json("a","normal",false).put("expires_at",now+60000),json("a","normal",false).put("message",new String(new char[501]).replace('\0','x'))}) {
            boolean rejected=false; try { new HandroidNotice(invalid,now); } catch(IllegalArgumentException expected) { rejected=true; }
            check(rejected);
        }
        HandroidNotice preserved=new HandroidNotice(json("unicode","critical",true).put("message","La puerta está abierta 🏠"),now);
        check(preserved.message.equals("La puerta está abierta 🏠")); check(preserved.critical());
        System.out.println("HAndroid policy: "+checks+" checks passed");
    }
}
