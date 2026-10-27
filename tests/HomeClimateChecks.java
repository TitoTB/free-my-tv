package io.freemytv;

import org.json.JSONObject;
import java.util.Locale;

public final class HomeClimateChecks {
    private static int checks;
    private static final String TEMP=HomeClimate.TEMPERATURE,HUMID=HomeClimate.HUMIDITY;
    private static JSONObject state(String entity,String value,String unit) throws Exception {
        return new JSONObject().put("entity_id",entity).put("state",value)
            .put("attributes",new JSONObject().put("unit_of_measurement",unit));
    }
    private static void expect(String expected,JSONObject state,String entity,Locale locale) {
        String actual=HomeClimate.parse(state,entity,locale);
        if(!expected.equals(actual)) throw new AssertionError(expected+" != "+actual);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Locale es=Locale.forLanguageTag("es-ES");
        expect("21,5 °C",state(TEMP,"21.5366666666667","°C"),TEMP,es);
        expect("59,8 %",state(HUMID,"59.8","%"),HUMID,es);
        expect("70.8 °F",state(TEMP,"70.8","°F"),TEMP,Locale.US);
        expect("295 K",state(TEMP,"295","K"),TEMP,es);
        expect("−5 °C".replace('−','-'),state(TEMP,"-5","°C"),TEMP,Locale.US);
        expect("0 %",state(HUMID,"0","%"),HUMID,es);
        expect("100 %",state(HUMID,"100","%"),HUMID,es);
        for(String bad:new String[]{"unknown","unavailable","NaN","Infinity","-Infinity","","null","10001"})
            expect("—",state(TEMP,bad,"°C"),TEMP,es);
        for(String bad:new String[]{"-0.1","100.1"}) expect("—",state(HUMID,bad,"%"),HUMID,es);
        expect("—",state(TEMP,"25","°C"),HUMID,es);
        expect("—",state(TEMP,"25","<script>"),TEMP,es);
        expect("—",state(HUMID,"50","°C"),HUMID,es);
        expect("—",new JSONObject().put("entity_id",TEMP).put("state","20"),TEMP,es);
        expect("—",new JSONObject(),TEMP,es);
        System.out.println("HomeClimate: "+checks+" checks passed");
    }
}
