package io.freemytv;

import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;

public final class CulturalPreferenceChecks {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static CulturalData.Event event(String title,String description,String price,String id,String venue) {
        return new CulturalData.Event(title,title,description,"https://www.esmadrid.com/agenda/test","","", "",price,
                Collections.singleton("c:music"),Collections.singletonList(new CulturalData.Range(LocalDate.of(2026,10,24),LocalDate.of(2026,10,24),Collections.emptySet())),id,venue);
    }
    public static void main(String[] args) throws Exception {
        for(String price:new String[]{"Gratuito","Gratis","Entrada gratuita","Entrada libre","Entrada libre y gratuita","Acceso libre hasta completar aforo","Actividad de acceso libre","0 €","Entrada gratuita previa reserva"}) {
            check(event("x","",price,"","").free,"Unambiguous free price: "+price);
        }
        for(String price:new String[]{"", "Consultar página oficial","Desde 20 €","General: 15 €. Entrada gratuita para menores","Entrada gratuita de 19 a 20 h","No gratuito","Gratuito para socios","Entrada libre; conciertos 10 €","Desde 0 €","0 € a 25 €","Entrada reducida 0 €"}) {
            check(!event("Entrada gratuita","Acceso libre",price,"","").free,"Mixed/conditional/unknown prices never marked free: "+price);
        }
        CulturalData.Event title=event("¡Vetústa—Mórla!", "Live","Entrada gratuita","room-1","Sala Uno"),
                description=event("Festival","Actúan VETUSTA MORLA y otros.","20 €","room-2","Sala Dos"),
                substring=event("Museum night","No live music","","room-3","Sala Tres");
        CulturalLikes prefs=new CulturalLikes();prefs.artist("Vetusta Morla",true);
        check(prefs.liked(title) && prefs.liked(description),"Artist matches title or description with case/accents/punctuation normalised");
        prefs.artist("Muse",true);check(!prefs.liked(substring),"Whole-name boundaries prevent Muse matching Museum");
        check(!prefs.liked(event("Vetusta","Morla","","","")),"Artist phrase never spans title and description boundary");
        prefs.artist("AC/DC",true);check(prefs.liked(event("AC — DC", "","","","")),"Punctuation separators canonicalised");
        prefs.venue(description,true,true);check(prefs.disliked(description) && !prefs.liked(description),"Excluded venue wins over favourite artist");
        check(prefs.prioritize(Arrays.asList(new CulturalData.Hit(description,LocalDate.of(2026,10,24)),new CulturalData.Hit(title,LocalDate.of(2026,10,24)))).size()==1,"Excluded venue removed from all result lists");
        prefs.venue(description,false,true);check(!prefs.disliked(description) && prefs.venues.containsKey("room-2"),"Venue like replaces same-ID exclusion");
        prefs.venue(title,false,true);check(prefs.liked(event("Unknown artist","","","room-1","Renamed venue")),"Venue identity survives name change");
        check(!prefs.liked(event("Unknown artist","","","other-id","Sala Uno")),"Same venue label never overrides distinct provider ID");
        prefs.venue(description,true,true);prefs=CulturalLikes.decode(prefs.encode());
        check(prefs.artists.size()==3 && prefs.venues.size()==1 && prefs.blockedVenues.size()==1,"Venue and artist preferences survive restart");
        check(prefs.liked(title) && prefs.disliked(description),"Restored preferences preserve decisions");
        prefs.venue(description,true,false);check(prefs.liked(description),"Removing venue exclusion restores artist result");
        prefs.artist("VETÚSTA MORLA",false);check(!prefs.artistMatch(description),"Artist removable with normalised identity");
        check(CulturalLikes.decode("{\"version\":3,\"tags\":{\"c:music\":\"Música\"},\"blocked\":{\"c:theatre\":\"Teatro\"}}").tags.size()==1,"Version 3 taxonomy migrates");
        String xml="<serviceList><service id='1'><basicData><name>Example</name><web>https://www.esmadrid.com/agenda/test</web><idrt>123</idrt><nombrert>Sala &amp; Teatro</nombrert></basicData><extradata><item name='Servicios de pago'>Entrada gratuita</item><fechas><rango><inicio>24/10/2026</inicio><fin>24/10/2026</fin></rango></fechas></extradata></service></serviceList>";
        CulturalData.Event parsed=CulturalData.monthly(CulturalData.parse(xml.getBytes(StandardCharsets.UTF_8)),LocalDate.of(2026,10,24)).events.get(0);
        check(parsed.venueId.equals("123") && parsed.venueName.equals("Sala & Teatro") && parsed.free,"Provider venue and free status survive monthly active catalogue");
        System.out.println("CulturalPreferenceChecks: "+checks+" checks passed");
    }
}
