package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class ContinueWatchingChecks {
    private static final String ID="0123456789abcdef0123456789abcdef",USER="1123456789abcdef0123456789abcdef";
    private static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); System.out.println("PASS "+name); }
    private static JSONObject movie(long position,long duration,boolean played) throws Exception {
        return new JSONObject().put("Id",ID).put("Name","Movie").put("Type","Movie").put("RunTimeTicks",duration)
                .put("UserData",new JSONObject().put("PlaybackPositionTicks",position).put("Played",played).put("LastPlayedDate","2026-10-23T10:00:00Z"));
    }
    private static JSONObject session(String id,String user,String ip,boolean control) throws Exception {
        return new JSONObject().put("Id",id).put("UserId",user).put("Client","Android TV").put("RemoteEndPoint",ip).put("SupportsRemoteControl",control);
    }
    public static void main(String[] args) throws Exception {
        JSONArray rows=new JSONArray().put(movie(123456789,900000000,false)).put(movie(0,900000000,false))
                .put(movie(900000000,900000000,false)).put(movie(123456789,900000000,true)).put(movie(1,0,false));
        List<JellyfinRecent.Item> items=JellyfinRecent.parseResume(rows);
        check(items.size()==1,"only genuinely unfinished content is shown");
        check(items.get(0).positionTicks==123456789 && items.get(0).durationTicks==900000000,"preserve exact resume ticks and duration");
        check(items.get(0).lastPlayed>0,"retain last-played time for merged ordering");
        JSONObject episode=movie(123456789,900000000,false).put("Type","Episode").put("Name","Episode").put("SeriesName","Series");
        check(JellyfinRecent.parseResume(new JSONArray().put(episode)).get(0).name.equals("Series · Episode"),"identify the exact in-progress episode and series");
        List<String> local=Collections.singletonList("192.0.2.10");
        JSONArray sessions=new JSONArray().put(session("OTHERDEVICE",USER,"192.0.2.11",true))
                .put(session("OTHERUSER",ID,"192.0.2.10",true)).put(session("NOCTRL",USER,"192.0.2.10",false))
                .put(session("RIGHT",USER,"192.0.2.10:12345",true));
        check("RIGHT".equals(ContinuePlayback.findSession(sessions,USER,local)),"select only this TV with the configured profile and remote-control support");
        sessions.put(session("SECOND",USER,"192.0.2.10",true));
        check(ContinuePlayback.findSession(sessions,USER,local)==null,"ambiguous TV sessions receive no playback command");
        check(ContinuePlayback.findSession(new JSONArray().put(session("OTHERDEVICE",USER,"192.0.2.100",true)),USER,local)==null,"IP prefix collisions never target another device");
        check(ContinuePlayback.playPath("RIGHT",ID,123456789).endsWith("StartPositionTicks=123456789"),"PlayNow preserves the precise saved position rather than restarting at zero");
        boolean invalid=false;
        try { ContinuePlayback.playPath("../other",ID,123); } catch(java.io.IOException expected) { invalid=true; }
        check(invalid,"reject malformed session identifiers");
        if(args.length>0) {
            items=JellyfinRecent.parseResume(new JSONArray(Files.readString(Path.of(args[0]))));
            check(items.size()==2 && items.stream().allMatch(i->i.positionTicks>0 && i.durationTicks>i.positionTicks),"fixture resume metadata yields two playable unfinished titles");
        }
    }
}
