package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JellyfinRecentChecks {
    private static final String A="0123456789abcdef0123456789abcdef", B="1123456789abcdef0123456789abcdef";
    private static void check(boolean condition,String name) {
        if(!condition) throw new AssertionError(name); System.out.println("PASS "+name);
    }
    private static JSONObject row(String id,String type) throws Exception {
        return new JSONObject().put("Id",id).put("Type",type).put("Name","Example").put("ProductionYear",2026);
    }
    public static void main(String[] args) throws Exception {
        JSONArray rows=new JSONArray().put(row(A,"Movie")).put(row(A,"Movie"))
                .put(row("invalid/path?key=value","Movie")).put(row(B,"Audio"));
        List<JellyfinRecent.Item> items=JellyfinRecent.parse(rows);
        check(items.size()==1 && items.get(0).id.equals(A),"deduplicate titles and reject invalid IDs and non-video items");
        JSONObject series=row(B,"Series").put("ImageTags",new JSONObject().put("Primary","poster-tag"));
        rows=new JSONArray().put(series).put(row(A,"Movie"));
        items=JellyfinRecent.parse(rows);
        check(items.size()==2 && items.get(0).type.equals("Series"),"preserve server recency order and grouped series");
        check(items.get(0).imageId.equals(B) && items.get(0).imageTag.equals("poster-tag"),"use the actual item's primary poster");
        JSONObject wide=row(A,"Movie").put("ImageTags",new JSONObject().put("Primary","portrait"))
                .put("BackdropImageTags",new JSONArray().put("wide-frame"));
        JellyfinRecent.Item wideItem=JellyfinRecent.parse(new JSONArray().put(wide)).get(0);
        check(wideItem.landscapeType.equals("Backdrop") && wideItem.landscapeId.equals(A),"prefer a genuine landscape backdrop without changing the launch identity");
        check(JellyfinRecent.parse(JellyfinRecent.encode(java.util.Collections.singletonList(wideItem))).get(0).landscapeTag.equals("wide-frame"),"landscape artwork survives the metadata cache round trip");
        wide.getJSONObject("ImageTags").put("Thumb","landscape-thumb");
        check(JellyfinRecent.parse(new JSONArray().put(wide)).get(0).landscapeType.equals("Thumb"),"prefer the title's dedicated landscape thumbnail");
        JSONObject episode=row(A,"Episode").put("SeriesId",B).put("SeriesPrimaryImageTag","series-poster");
        JellyfinRecent.Item item=JellyfinRecent.parse(new JSONArray().put(episode)).get(0);
        check(item.imageId.equals(B) && item.id.equals(A),"fallback series artwork preserves the clicked episode's identity");
        List<JellyfinRecent.Item> restored=JellyfinRecent.parse(JellyfinRecent.encode(java.util.Collections.singletonList(item)));
        check(restored.get(0).imageId.equals(B) && restored.get(0).imageTag.equals("series-poster"),"display-only cache round trip preserves artwork");
        String saved=JellyfinRecent.encode(items).toString();
        check(!saved.contains("UserData") && !saved.contains("Token") && !saved.contains("apiKey"),"metadata cache excludes credentials and user data");
        check(JellyfinRecent.intentId(A).equals("01234567-89ab-cdef-0123-456789abcdef"),"native TV handoff uses a canonical UUID ItemId");
        check(JellyfinRecent.intentId(JellyfinRecent.intentId(A)).equals(JellyfinRecent.intentId(A)),"handoff accepts both Jellyfin GUID forms");
        if(args.length>0) {
            items=JellyfinRecent.parse(new JSONArray(Files.readString(Path.of(args[0]))));
            check(items.size()==20,"real profile metadata yields the latest twenty titles");
            check(items.stream().anyMatch(i->i.type.equals("Movie")) && items.stream().anyMatch(i->i.type.equals("Series")),"real library includes both movies and grouped series");
            check(items.stream().allMatch(i->!i.landscapeTag.isEmpty()),"all twenty real recent titles expose usable landscape artwork");
        }
    }
}
