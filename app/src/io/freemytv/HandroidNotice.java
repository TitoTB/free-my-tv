package io.freemytv;

import org.json.JSONObject;
import java.util.*;

/** In-memory notification policy. Power-off always destroys the current message and the complete queue. */
final class HandroidNotice {
    final String id,message,type;
    final boolean persistent;
    final int duration;
    final long expiresAt;
    HandroidNotice(JSONObject json,long now) {
        id=json.optString("notification_id",""); message=json.optString("message","").trim(); type=json.optString("notification_type","normal");
        Object sticky=json.opt("persistent"),seconds=json.opt("duration");
        if(!id.matches("[a-zA-Z0-9_-]{1,64}") || message.isEmpty() || message.length()>500
                || !(type.equals("normal")||type.equals("critical")) || !(sticky instanceof Boolean)
                || !(seconds instanceof Number)) throw new IllegalArgumentException("Invalid notification");
        persistent=critical() || (Boolean)sticky; double value=((Number)seconds).doubleValue();
        if(value<2 || value>120 || value!=Math.floor(value)) throw new IllegalArgumentException("Invalid duration");
        duration=(int)value; expiresAt=json.optLong("expires_at",0);
        if(expiresAt<now || expiresAt>now+30_000) throw new IllegalArgumentException("Expired notification");
    }
    boolean critical() { return "critical".equals(type); }
    static final class Queue {
        HandroidNotice current;
        private final LinkedList<HandroidNotice> waiting=new LinkedList<>();
        String offer(HandroidNotice notice,boolean screenOn) {
            if(!screenOn) { clear(); return "discarded"; }
            if(current!=null && current.id.equals(notice.id)) { current=notice; return "shown"; }
            waiting.removeIf(old->old.id.equals(notice.id));
            if(current==null) { current=notice; return "shown"; }
            if(waiting.size()>=10) return "queue_full";
            if(notice.critical() && !current.critical()) {
                waiting.addFirst(current); current=notice; return "shown";
            }
            if(notice.critical()) waiting.addFirst(notice); else waiting.addLast(notice);
            return "queued";
        }
        HandroidNotice next() { current=waiting.pollFirst(); return current; }
        List<HandroidNotice> clear() {
            List<HandroidNotice> removed=new ArrayList<>(waiting);
            if(current!=null) removed.add(0,current);
            current=null; waiting.clear(); return removed;
        }
        int pending() { return waiting.size()+(current==null?0:1); }
    }
}
