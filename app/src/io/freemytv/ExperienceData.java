package io.freemytv;

import org.json.*;
import java.util.*;

/** Local checklist, stable IDs keep selection independent of editable wording. */
final class ExperienceData {
    static final int ROWS=5, PAGE=ROWS*2, MAX=400, TEXT_LIMIT=140;
    static final class Item {
        final String id,text;
        boolean done;
        Item(String id,String text,boolean done){this.id=id;this.text=text;this.done=done;}
    }
    final List<Item> items=new ArrayList<>();
    static ExperienceData defaults(){ return new ExperienceData(); }
    /** User-owned lists are never replaced by bundled examples. */
    boolean replaceLegacyExamples(){ return false; }
    static String clean(String text){
        if(text==null)return "";
        String value=text.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
        int length=value.codePointCount(0,value.length());
        return length>TEXT_LIMIT?value.substring(0,value.offsetByCodePoints(0,TEXT_LIMIT)):value;
    }
    boolean add(String text){
        String value=clean(text);if(value.isEmpty() || items.size()>=MAX)return false;
        items.add(new Item(UUID.randomUUID().toString(),value,false));return true;
    }
    boolean toggle(int index){if(index<0 || index>=items.size())return false;Item item=items.get(index);item.done=!item.done;return true;}
    boolean remove(String id){return items.removeIf(item->item.id.equals(id));}
    String encode(){
        JSONArray list=new JSONArray();
        try{for(Item item:items)list.put(new JSONObject().put("id",item.id).put("text",item.text).put("done",item.done));}
        catch(JSONException ignored){}
        return list.toString();
    }
    static ExperienceData decode(String json){
        ExperienceData data=new ExperienceData();Set<String> seen=new HashSet<>();
        try{JSONArray list=new JSONArray(json);for(int i=0;i<list.length() && data.items.size()<MAX;i++){
            JSONObject value=list.optJSONObject(i);if(value==null)continue;
            String id=value.optString("id","").trim(),text=clean(value.optString("text",""));
            if(id.isEmpty() || text.isEmpty() || !seen.add(id))continue;
            data.items.add(new Item(id,text,value.optBoolean("done",false)));
        }}catch(JSONException ignored){}
        return data;
    }
    static int pageStart(int selected){return Math.max(0,selected)/PAGE*PAGE;}
    static int navigate(int selected,int dx,int dy,int size){
        if(size==0)return -1;
        selected=Math.max(0,Math.min(size-1,selected));int start=pageStart(selected),slot=selected-start;
        if(dy<0)return slot%ROWS==0?-1:selected-1;
        if(dy>0)return slot%ROWS==ROWS-1 || selected+1>=size?-2:selected+1;
        if(dx>0){int column=(selected/ROWS+1)*ROWS;return column<size?Math.min(column+slot%ROWS,size-1):selected;}
        if(dx<0){int next=selected-ROWS;return next>=0?next:selected;}
        return selected;
    }
}
