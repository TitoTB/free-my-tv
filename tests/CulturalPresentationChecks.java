package io.freemytv;

import java.util.Locale;

public final class CulturalPresentationChecks {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args) {
        int green=0xFF24633F,red=0xFF843E47,neutral=0xFF354151;
        check(CulturalPresentation.priceColor("Entrada gratuita",true)==green,"Known free price is green");
        check(CulturalPresentation.priceColor("Desde 20 €",false)==green,"20 euros inclusive is green");
        check(CulturalPresentation.priceColor("20–60 €",false)==green,"Range uses advertised minimum");
        check(CulturalPresentation.priceColor("De 25 a 60 euros",false)!=red,"Range minimum is not its upper endpoint");
        check(CulturalPresentation.priceColor("50 €",false)==red,"Gradient reaches red at 50 euros");
        check(CulturalPresentation.priceColor("51 €",false)==red,"Above 50 is red");
        check(CulturalPresentation.priceColor("19,99 €",false)==green,"Spanish decimal price");
        check(CulturalPresentation.priceColor("19.99 EUR",false)==neutral,"Unknown currency notation remains neutral");
        check(CulturalPresentation.priceColor("1.020,00 €",false)==red,"Spanish thousands are not parsed as twenty euros");
        check(CulturalPresentation.priceColor("1,020.00 €",false)==red,"English thousands and decimal accepted");
        check(CulturalPresentation.priceColor("Consultar precios. Sesión 18:30",false)==neutral,"Hours do not become prices");
        check(CulturalPresentation.priceColor("",false)==neutral,"Missing price is neutral");
        check(CulturalPresentation.line(" Sala\n\r\n Calle\tde\u00a0Madrid ").equals("Sala Calle de Madrid"),"Address whitespace collapses onto one line");
        check(CulturalPresentation.capital("sábado 24 octubre",new Locale("es")).equals("Sábado 24 octubre"),"Weekday starts uppercase with accents retained");
        check(CulturalPresentation.capital("",Locale.ROOT).isEmpty(),"Empty date safe");
        java.time.LocalDate today=java.time.LocalDate.of(2026,3,23);Locale es=new Locale("es");
        check(CulturalPresentation.eventDate(today,today,es,"Hoy","Mañana").equals("Hoy"),"Today's occurrence uses Hoy");
        check(CulturalPresentation.eventDate(today.plusDays(1),today,es,"Hoy","Mañana").equals("Mañana"),"Tomorrow's occurrence uses Mañana");
        check(CulturalPresentation.eventDate(today.plusDays(2),today,es,"Hoy","Mañana").equals("25 Marzo"),"Other occurrences use day and capitalised month only");
        System.out.println("CulturalPresentationChecks: "+checks+" checks passed");
    }
}
