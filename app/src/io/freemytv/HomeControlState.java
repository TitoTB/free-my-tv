package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

/** The three living-room controls; no Android dependencies, credentials or optimistic device state. */
final class HomeControlState {
    static final String[] ENTITIES={"input_select.XXXXX_escenas","climate.XXXXX_calefaccion","cover.XXXXX_persianas"};
    static final String[] TITLES={"Escena","Calefacción","Persianas"};
    final boolean available, controllable, on;
    final String value;
    final int position;
    private HomeControlState(boolean available,boolean controllable,boolean on,String value,int position) {
        this.available=available; this.controllable=controllable; this.on=on; this.value=value; this.position=position;
    }
    static HomeControlState missing() { return new HomeControlState(false,false,false,"No disponible",-1); }
    static HomeControlState parse(int index,JSONObject json) {
        if(index<0 || index>=ENTITIES.length || json==null || !ENTITIES[index].equals(json.optString("entity_id"))) return missing();
        String state=json.optString("state");
        if(state.isEmpty() || state.equals("unknown") || state.equals("unavailable")) return missing();
        JSONObject a=json.optJSONObject("attributes");
        if(a==null) return missing();
        if(index==0) {
            JSONArray options=a.optJSONArray("options");
            boolean found=false;
            if(options!=null) for(int i=0;i<options.length();i++) found|=state.equals(options.optString(i));
            return new HomeControlState(true,found && options.length()>1,false,state,-1);
        }
        int features=a.optInt("supported_features",0);
        if(index==1) {
            boolean on=!state.equals("off");
            String value=on?"Encendida":"Apagada";
            Object raw=a.opt("temperature");
            if(on && raw instanceof Number) {
                double target=((Number)raw).doubleValue();
                if(!Double.isNaN(target) && !Double.isInfinite(target) && target>=-100 && target<=150) {
                    NumberFormat format=NumberFormat.getNumberInstance(Locale.getDefault());
                    format.setGroupingUsed(false); format.setMaximumFractionDigits(1);
                    String unit=a.optString("temperature_unit");
                    value="Encendida ("+format.format(target)+("°C".equals(unit)||"°F".equals(unit)?" "+unit:"°")+")";
                }
            }
            return new HomeControlState(true,(features & (on?128:256))!=0,on,value,-1);
        }
        Object raw=a.opt("current_position");
        double p=raw instanceof Number?((Number)raw).doubleValue():Double.NaN;
        boolean valid=!Double.isNaN(p) && p>=0 && p<=100 && p==Math.rint(p);
        return new HomeControlState(true,valid && (features&4)!=0,false,valid?((int)p)+" % abierta":"Posición desconocida",valid?(int)p:-1);
    }
    static int step(int position,int direction) { return Math.max(0,Math.min(100,position+direction*5)); }
    String service(int index) {
        if(!controllable) throw new IllegalStateException("Unavailable control");
        return index==0?"input_select/select_next":index==1?"climate/"+(on?"turn_off":"turn_on"):"cover/set_cover_position";
    }
    static JSONObject payload(int index,int position) throws Exception {
        if(index<0 || index>=ENTITIES.length || (index==2 && (position<0 || position>100))) throw new IllegalArgumentException("Invalid control");
        JSONObject out=new JSONObject().put("entity_id",ENTITIES[index]);
        if(index==0) out.put("cycle",true);
        if(index==2) out.put("position",position);
        return out;
    }
}
