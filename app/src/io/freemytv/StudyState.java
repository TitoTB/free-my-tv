package io.freemytv;

import org.json.JSONObject;

/** Explicit entity allowlist for the study's all-off control. Unknown never means off. */
final class StudyState {
    static final String[] ENTITIES={Config.HA_STUDY_LIGHT,Config.HA_STUDY_FAN,Config.HA_STUDY_CLIMATE};
    static final String[] NAMES={"Luz","Ventilador","Aire acondicionado"};
    static final String BEDROOM=Config.HA_BEDROOM;
    static final class Member {
        final boolean available,on,canOff;
        Member(boolean available,boolean on,boolean canOff) { this.available=available; this.on=on; this.canOff=canOff; }
    }
    final Member[] members;
    final String[] labels;
    final String[] actions;
    final String actionDescription;
    final boolean anyOn,allOff;
    StudyState(Member[] members) {
        this.members=members.clone();
        labels=new String[members.length];
        boolean active=false,off=true;
        for(int i=0;i<members.length;i++) {
            Member m=members[i]; active|=m.available && m.on; off&=m.available && !m.on;
            labels[i]=NAMES[i]+": "+(!m.available?"No disponible":i==0?(m.on?"Encendida":"Apagada"):(m.on?"Encendido":"Apagado"));
        }
        anyOn=active; allOff=off;
        java.util.ArrayList<String> visible=new java.util.ArrayList<>();
        String[] devices={"luz","ventilador","aire AC"};
        for(int i=0;i<members.length;i++) if(members[i].available && members[i].on) visible.add("Apagar "+devices[i]);
        if(visible.isEmpty()) visible.add(allOff?"Todo apagado":"No disponible");
        actions=visible.toArray(new String[0]);
        actionDescription=String.join(", ",actions);
    }
    static Member missing() { return new Member(false,false,false); }
    static StudyState empty() { return new StudyState(new Member[]{missing(),missing(),missing()}); }
    static Member parseBedroom(JSONObject json) {
        if(json==null || !BEDROOM.equals(json.optString("entity_id"))) return missing();
        String state=json.optString("state");
        return state.equals("on")?new Member(true,true,true):state.equals("off")?new Member(true,false,true):missing();
    }
    static Member parse(int index,JSONObject json) {
        if(index<0 || index>=3 || json==null || !ENTITIES[index].equals(json.optString("entity_id"))) return missing();
        String state=json.optString("state");
        if(state.equals("off")) return new Member(true,false,true);
        if(index<2) return state.equals("on")?new Member(true,true,true):missing();
        if(!state.matches("heat|cool|auto|dry|fan_only|heat_cool")) return missing();
        JSONObject a=json.optJSONObject("attributes");
        return new Member(true,true,a!=null && (a.optInt("supported_features")&128)!=0);
    }
    static StudyState visible(StudyState previous,StudyState next) {
        Member[] merged=new Member[previous.members.length];
        for(int i=0;i<merged.length;i++) merged[i]=next.members[i].available?next.members[i]:previous.members[i];
        return new StudyState(merged);
    }
}
