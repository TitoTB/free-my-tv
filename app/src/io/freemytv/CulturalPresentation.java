package io.freemytv;

import java.util.Locale;
import java.util.regex.*;

/** Display-only transformations; never infer free admission from a partial price. */
final class CulturalPresentation {
    static String eventDate(java.time.LocalDate day,java.time.LocalDate today,Locale locale,String todayLabel,String tomorrowLabel) {
        if(day.equals(today))return todayLabel;
        if(day.equals(today.plusDays(1)))return tomorrowLabel;
        String month=day.format(java.time.format.DateTimeFormatter.ofPattern("MMMM",locale));
        return day.getDayOfMonth()+" "+capital(month,locale);
    }
    private static final String AMOUNT="(?:\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)";
    private static final Pattern EURO=Pattern.compile("(?iu)("+AMOUNT+")(?:\\s*[-–—a]\\s*("+AMOUNT+"))?\\s*(?:€|euros?\\b)");
    private static double amount(String value){return Double.parseDouble(value.replaceAll("[.,](?=\\d{3}(?:[.,]|$))","").replace(',','.'));}
    static String line(String value){return value.replaceAll("[\\s\\p{Z}]+"," ").trim();}
    static String capital(String value,Locale locale) {
        if(value.isEmpty())return value;
        int end=value.offsetByCodePoints(0,1);return value.substring(0,end).toUpperCase(locale)+value.substring(end);
    }
    static int priceColor(String value,boolean free) {
        if(free)return 0xFF24633F;
        Matcher match=EURO.matcher(line(value));double minimum=Double.POSITIVE_INFINITY;
        while(match.find()) {
            minimum=Math.min(minimum,amount(match.group(1)));
            if(match.group(2)!=null)minimum=Math.min(minimum,amount(match.group(2)));
        }
        if(!Double.isFinite(minimum))return 0xFF354151;
        if(minimum<=20)return 0xFF24633F;
        if(minimum>50)return 0xFF843E47;
        int start=minimum<=35?0x24633F:0x8C7338,end=minimum<=35?0x8C7338:0x843E47;
        float t=(float)((minimum-(minimum<=35?20:35))/15);
        int color=0xFF000000;
        for(int shift:new int[]{16,8,0})color|=Math.round(((start>>shift)&255)*(1-t)+((end>>shift)&255)*t)<<shift;
        return color;
    }
}
