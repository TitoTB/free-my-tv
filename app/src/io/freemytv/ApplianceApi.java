package io.freemytv;

import org.json.JSONObject;
import java.util.function.BooleanSupplier;

/** GET-only transport interface; no household commands exist for these cards. */
final class ApplianceApi {
    interface Reader { String get(String entity) throws Exception; }
    private final Reader reader;
    ApplianceApi(Reader reader){this.reader=reader;}
    private JSONObject read(String id,BooleanSupplier active) {
        if(id==null || id.isEmpty() || !active.getAsBoolean())return null;
        try{return new JSONObject(reader.get(id));}catch(Exception ignored){return null;}
    }
    ApplianceState read(int index,BooleanSupplier active) {
        JSONObject on=read(ApplianceState.ACTIVE[index],active);
        if(!ApplianceState.active(index,on))return ApplianceState.off();
        return ApplianceState.parse(index,on,read(ApplianceState.REMAINING[index],active),read(ApplianceState.PROGRESS[index],active));
    }
}
