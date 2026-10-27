package io.freemytv;

import org.json.JSONObject;
import java.util.function.BooleanSupplier;

/** Native domain-specific turn_off calls, only for freshly observed active members; no toggle or retry. */
final class StudyApi {
    private final HomeControlApi api;
    private final boolean bedroom;
    StudyApi(HomeControlApi api) { this(api,false); }
    StudyApi(HomeControlApi api,boolean bedroom) { this.api=api; this.bedroom=bedroom; }
    private String entity(int index) { return bedroom?StudyState.BEDROOM:StudyState.ENTITIES[index]; }
    StudyState empty() { return bedroom?new StudyState(new StudyState.Member[]{StudyState.missing()}):StudyState.empty(); }
    StudyState read() {
        StudyState.Member[] members=new StudyState.Member[bedroom?1:3];
        for(int i=0;i<members.length;i++) try {
            if(entity(i).isEmpty() || entity(i).contains("XXXXX")) { members[i]=StudyState.missing(); continue; }
            JSONObject json=new JSONObject(api.request("/api/states/"+entity(i),null));
            members[i]=bedroom?StudyState.parseBedroom(json):StudyState.parse(i,json);
        } catch(Exception ignored) { members[i]=StudyState.missing(); }
        return new StudyState(members);
    }
    static final class Result {
        final StudyState before;
        final boolean failed,cancelled;
        Result(StudyState before,boolean failed,boolean cancelled) { this.before=before; this.failed=failed; this.cancelled=cancelled; }
    }
    Result turnOff(BooleanSupplier active) {
        if(!active.getAsBoolean()) return new Result(empty(),false,true);
        StudyState before=read(); boolean failed=false;
        for(int i=0;i<before.members.length;i++) {
            if(!active.getAsBoolean()) return new Result(before,failed,true);
            StudyState.Member m=before.members[i];
            if(!m.available) { failed=true; continue; }
            if(!m.on) continue;
            if(!m.canOff) { failed=true; continue; }
            try {
                String entity=entity(i),domain=entity.substring(0,entity.indexOf('.'));
                api.request("/api/services/"+domain+"/turn_off",new JSONObject().put("entity_id",entity));
            } catch(Exception ignored) { failed=true; }
        }
        return new Result(before,failed,false);
    }
}
