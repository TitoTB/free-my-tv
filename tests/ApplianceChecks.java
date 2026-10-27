package io.freemytv;

import org.json.JSONObject;
import java.util.*;

/** Fake read-only sensors; never connects to Home Assistant or actuates a device. */
public final class ApplianceChecks {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static JSONObject sensor(String id,String value,String unit)throws Exception {
        return new JSONObject().put("entity_id",id).put("state",value).put("attributes",new JSONObject().put("unit_of_measurement",unit));
    }
    public static void main(String[] args)throws Exception {
        for(int i=0;i<2;i++) {
            JSONObject on=sensor(ApplianceState.ACTIVE[i],"on",""),remaining=sensor(ApplianceState.REMAINING[i],"83","min"),progress=sensor(ApplianceState.PROGRESS[i],"42.5","%");
            ApplianceState state=ApplianceState.parse(i,on,remaining,progress);
            check(state.active && state.remaining.equals("1 h 23 min") && state.progress==42.5f,"Running card uses its own remaining/progress sensors");
            for(String inactive:new String[]{"off","unknown","unavailable","", "true"}) {
                check(!ApplianceState.parse(i,sensor(ApplianceState.ACTIVE[i],inactive,""),remaining,progress).active,"Only native binary on may show a card");
            }
            check(!ApplianceState.parse(i,null,remaining,progress).active,"Missing active sensor hides card");
            check(!ApplianceState.parse(i,sensor(ApplianceState.ACTIVE[1-i],"on",""),remaining,progress).active,"Other appliance cannot activate card");
            check(ApplianceState.parse(i,on,sensor(ApplianceState.REMAINING[1-i],"60","min"),progress).remaining.equals("Tiempo no disponible"),"Wrong identity cannot supply remaining time");
            check(ApplianceState.parse(i,on,remaining,sensor(ApplianceState.PROGRESS[1-i],"90","%")).progress==-1,"Other appliance cannot supply progress");
            for(String invalid:new String[]{"unknown","unavailable","NaN","Infinity","-4"}) {
                check(ApplianceState.parse(i,on,sensor(ApplianceState.REMAINING[i],invalid,"min"),progress).remaining.equals("Tiempo no disponible"),"Invalid duration has no invented countdown");
            }
            check(ApplianceState.parse(i,on,sensor(ApplianceState.REMAINING[i],"12.2","min"),progress).remaining.equals("13 min"),"Fractional duration rounds upwards");
            check(ApplianceState.parse(i,on,sensor(ApplianceState.REMAINING[i],"120","min"),progress).remaining.equals("2 h"),"Exact hours compactly formatted");
            check(ApplianceState.parse(i,on,sensor(ApplianceState.REMAINING[i],"90","s"),progress).remaining.equals("2 min"),"Seconds normalised to minutes");
            check(ApplianceState.parse(i,on,sensor(ApplianceState.REMAINING[i],"1.5","h"),progress).remaining.equals("1 h 30 min"),"Hours supported");
            check(ApplianceState.parse(i,on,remaining,sensor(ApplianceState.PROGRESS[i],"102","%")).progress==100,"Fill never exceeds card width");
            check(ApplianceState.parse(i,on,remaining,null).progress==-1,"Missing progress leaves fill absent");
            List<String> reads=new ArrayList<>();Map<String,JSONObject> values=new HashMap<>();
            values.put(ApplianceState.ACTIVE[i],on);values.put(ApplianceState.REMAINING[i],remaining);values.put(ApplianceState.PROGRESS[i],progress);
            ApplianceApi api=new ApplianceApi(id->{reads.add(id);return values.get(id).toString();});
            check(api.read(i,()->true).active,"Read-only API produces active state");
            check(reads.equals(Arrays.asList(ApplianceState.ACTIVE[i],ApplianceState.REMAINING[i],ApplianceState.PROGRESS[i])),"Only explicit sensor entities are read");
            reads.clear();values.put(ApplianceState.ACTIVE[i],sensor(ApplianceState.ACTIVE[i],"off",""));
            check(!api.read(i,()->true).active && reads.equals(Collections.singletonList(ApplianceState.ACTIVE[i])),"Off skips duration/progress reads");
            reads.clear();check(!api.read(i,()->false).active && reads.isEmpty(),"Cancelled/hidden view performs no reads");
            ApplianceApi failed=new ApplianceApi(id->{throw new java.io.IOException();});
            check(!failed.read(i,()->true).active,"Network failure cannot invent running state");
        }
        check(ApplianceState.PROGRESS[1].equals("sensor.dishwasher_progress"),"User-confirmed dishwasher mapping");
        java.time.Instant now=java.time.Instant.parse("2026-10-24T12:00:00Z");
        JSONObject printing=sensor(ApplianceState.ACTIVE[2],"printing",""),finish=sensor(ApplianceState.REMAINING[2],"2026-10-24T16:14:00+02:00",""),percent=sensor(ApplianceState.PROGRESS[2],"2.09","%");
        ApplianceState printer=ApplianceState.parse(2,printing,finish,percent,now);
        check(printer.active && printer.remaining.equals("Queda 2 h 14 min") && Math.abs(printer.progress-2.09)<.001,"Printer labels timezone-aware remaining duration with Queda");
        check(ApplianceState.parse(2,printing,finish,percent,now.plusSeconds(60)).remaining.equals("Queda 2 h 13 min"),"Countdown changes even if finish sensor timestamp is unchanged");
        for(String state:new String[]{"printing","printing_sd","printing_streaming"})check(ApplianceState.active(2,sensor(ApplianceState.ACTIVE[2],state,"")),"All actual OctoPrint printing modes visible");
        for(String state:new String[]{"operational","paused","pausing","resuming","starting","starting_sd","starting_streaming","cancelling","finishing","offline","error","offline_after_error","transferring_file","unknown","unavailable","on"})
            check(!ApplianceState.active(2,sensor(ApplianceState.ACTIVE[2],state,"")),"Non-printing OctoPrint state hidden");
        check(!ApplianceState.active(2,sensor(ApplianceState.ACTIVE[0],"printing","")),"Printer state identity required");
        for(String invalid:new String[]{"unknown","unavailable","2026-10-24T13:00:00","120"})
            check(ApplianceState.parse(2,printing,sensor(ApplianceState.REMAINING[2],invalid,""),percent,now).remaining.equals("Tiempo no disponible"),"Invalid or timezone-less estimate does not invent duration");
        check(ApplianceState.parse(2,printing,sensor(ApplianceState.REMAINING[2],"2026-10-24T11:00:00Z",""),percent,now).remaining.equals("Queda 0 min"),"Past estimate never produces a negative countdown");
        check(ApplianceState.parse(2,printing,finish,null,now).active,"Missing percentage does not hide known printing state");
        List<String> printerReads=new ArrayList<>();
        ApplianceApi printerApi=new ApplianceApi(id->{printerReads.add(id);return(id.equals(ApplianceState.ACTIVE[2])?printing:id.equals(ApplianceState.REMAINING[2])?finish:percent).toString();});
        check(printerApi.read(2,()->true).active && printerReads.equals(Arrays.asList("sensor.printer_state","sensor.printer_finish_time","sensor.printer_progress")),"Printer reads only its three sensors, with no commands");
        System.out.println("ApplianceChecks: "+checks+" checks passed");
    }
}
