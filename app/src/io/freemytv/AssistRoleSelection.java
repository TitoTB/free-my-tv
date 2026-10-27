package io.freemytv;

import java.io.IOException;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Verified, reversible role change. Its shell only accepts fixed commands and validated package names. */
final class AssistRoleSelection {
    interface Shell {String run(String command)throws IOException;}
    interface Store {String read()throws IOException;void write(String json)throws IOException;void clear()throws IOException;}
    private static final String[] KEYS={"assistant","voice_interaction_service","voice_recognition_service"};
    private static final Pattern COMPONENT=Pattern.compile("[a-zA-Z][a-zA-Z0-9_.]*/[a-zA-Z0-9_.$]+"),PACKAGE=Pattern.compile("[a-zA-Z][a-zA-Z0-9_.]*");
    private final Shell shell;private final Store store;
    AssistRoleSelection(Shell shell,Store store){this.shell=shell;this.store=store;}
    private JSONObject current()throws IOException {
        JSONObject out=new JSONObject();
        try{for(String key:KEYS){String value=shell.run("settings get secure "+key).trim();if(!valid(value))throw new IOException("Invalid assistant component");out.put(key,value);}}
        catch(org.json.JSONException e){throw new IOException("Invalid assistant state");}
        return out;
    }
    static boolean valid(String value){return value!=null&&(value.isEmpty()||"null".equals(value)||COMPONENT.matcher(value).matches());}
    private static boolean ours(JSONObject state){return state.optString("assistant").startsWith("io.freemytv/")||state.optString("voice_interaction_service").startsWith("io.freemytv/");}
    private static boolean selected(JSONObject state){String c=state.optString("voice_interaction_service");return c.equals("io.freemytv/.AssistVoiceService")||c.equals("io.freemytv/io.freemytv.AssistVoiceService");}
    private static JSONObject snapshot(String text)throws IOException {
        try{
            JSONObject s=new JSONObject(text);if(s.optInt("schema")!=1)throw new IOException("Unsupported assistant backup");
            for(String key:KEYS)if(!s.has(key)||!valid(s.getString(key)))throw new IOException("Invalid assistant backup");
            String assistant=s.optString("assistant");
            if(!COMPONENT.matcher(assistant).matches()||ours(s))throw new IOException("No previous assistant to restore");
            return s;
        }catch(org.json.JSONException e){throw new IOException("Invalid assistant backup");}
    }
    void enable()throws IOException {
        JSONObject before=current();
        if(selected(before)){snapshot(store.read());return;}
        if(ours(before))throw new IOException("Unknown assistant configuration");
        try{before.put("schema",1);}catch(org.json.JSONException e){throw new IOException("Invalid assistant state");}
        snapshot(before.toString()); // Refuse to replace an unknown/absent original assistant.
        store.write(before.toString());
        try{
            shell.run("cmd role add-role-holder --user 0 android.app.role.ASSISTANT io.freemytv 0");
            if(!selected(current()))throw new IOException("Voice assistant not selected");
        }catch(IOException failed){
            try{JSONObject now=current();if(ours(now))restoreSnapshot(before);else if(same(before,now))store.clear();}
            catch(IOException restoration){failed.addSuppressed(restoration);}
            throw failed;
        }
    }
    void restore()throws IOException {
        String saved=store.read();
        if(saved==null||saved.isEmpty())return;
        JSONObject previous=snapshot(saved),now=current();
        // A different assistant chosen later belongs to the user; never overwrite it.
        if(!ours(now)){store.clear();return;}
        restoreSnapshot(previous);
    }
    private void restoreSnapshot(JSONObject saved)throws IOException {
        String component=saved.optString("assistant"),pkg=component.substring(0,component.indexOf('/'));
        if(!PACKAGE.matcher(pkg).matches())throw new IOException("Invalid previous assistant");
        shell.run("cmd role add-role-holder --user 0 android.app.role.ASSISTANT "+pkg+" 0");
        if(!same(saved,current()))throw new IOException("Previous assistant restoration not verified");
        store.clear();
    }
    private static boolean same(JSONObject a,JSONObject b){for(String key:KEYS)if(!a.optString(key).equals(b.optString(key)))return false;return true;}
}
