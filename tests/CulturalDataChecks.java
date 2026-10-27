package io.freemytv;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Date boundaries, provider taxonomy and XML safety; no APK, network or household actions. */
public final class CulturalDataChecks {
    private static int checks;
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); checks++; }
    private static String category(String parent,String name,String sub,String label) {
        return "<categoria><item name='idCategoria'>"+parent+"</item><item name='Categoria'>"+name+"</item>"
            +"<subcategorias><subcategoria><item name='idSubCategoria'>"+sub+"</item><item name='SubCategoria'>"+label
            +"</item></subcategoria></subcategorias></categoria>";
    }
    private static String event(String id,String categories,String ranges) {
        return "<service id='"+id+"'><basicData><name><![CDATA[Prueba &amp; cultura]]></name>"
            +"<body><![CDATA[<p>Descripción segura</p>]]></body><web>https://www.esmadrid.com/agenda/prueba</web></basicData>"
            +"<geoData><address>Calle Prueba 1</address></geoData><multimedia><media type='image'><url>https://estaticos.esmadrid.com/test.jpg</url></media></multimedia>"
            +"<extradata><categorias>"+categories+"</categorias><fechas>"+ranges+"</fechas></extradata></service>";
    }
    private static String range(String start,String end,String days) {
        return "<rango><inicio>"+start+"</inicio><fin>"+end+"</fin><dias>"+days+"</dias></rango>";
    }
    private static CulturalData.Catalog parse(String events) throws Exception {
        return CulturalData.parse(("<serviceList>"+events+"</serviceList>").getBytes(StandardCharsets.UTF_8));
    }
    private static void reject(String xml) {
        try { CulturalData.parse(xml.getBytes(StandardCharsets.UTF_8)); throw new AssertionError("Unsafe feed accepted"); }
        catch(Exception expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        check(CulturalData.plain("&ldquo;De Madrid al cielo&rdquo; &iexcl;M&uacute;sica! &aacute; &ntilde; &euro;").equals("“De Madrid al cielo” ¡Música! á ñ €"),"Provider named HTML entities decoded");
        check(CulturalData.plain("&#x1F3B5; &amp; &unknown;").equals("🎵 & &unknown;"),"Numeric entities and unknown names preserved");
        check(CulturalData.paragraphs("<p>Uno <strong>y dos</strong>.</p><p>Tres<br/>Cuatro.</p>").equals("Uno y dos.\n\nTres\nCuatro."),"Descriptions retain paragraph and explicit break boundaries without spaces before punctuation");
        check(CulturalData.paragraphs(" Uno\r\n Dos\n\n\nTres &#10; Cuatro &amp; cinco ").equals("Uno\nDos\n\nTres\nCuatro & cinco"),"Literal and encoded breaks preserved with bounded empty lines");
        check(CulturalData.paragraphs("<script>bad<br>content</script><p>Seguro</p><style>hidden</style>").equals("Seguro"),"Hidden HTML cannot inject visible paragraphs");
        check(CulturalData.plain("<p>Uno</p><p>Dos<br>Tres</p>").equals("Uno Dos Tres"),"Other metadata still collapses line breaks");
        LocalDate today=LocalDate.of(2026,10,24);
        String music=category("music","Música","indie","Indie"),sport=category("sport","Deporte","other","Otros");
        CulturalData.Catalog catalog=parse(event("today",music,range("24/10/2026","24/10/2026","6"))
            +event("tomorrow",music,range("25/10/2026","25/10/2026","7"))
            +event("next-month",sport,range("01/11/2026","01/11/2026","7"))
            +event("past",music,range("23/10/2026","23/10/2026","5"))
            +event("conflict",music,range("26/10/2026","26/10/2026","5")));
        Set<String> all=Collections.singleton(CulturalData.ALL),onlyMusic=Collections.singleton("c:music");
        check(catalog.events.size()==5,"All provider records parsed");
        check(catalog.categories.size()==4,"Taxonomy includes parent and child categories");
        check(catalog.events.get(0).title.equals("Prueba & cultura"),"HTML entity normalization");
        check(catalog.events.get(0).address.equals("Calle Prueba 1"),"Address extraction");
        check(CulturalData.filter(catalog,all,0,today).size()==1,"Today");
        check(CulturalData.filter(catalog,all,1,today).size()==1,"Tomorrow across Madrid DST");
        check(CulturalData.filter(catalog,all,2,today).size()==2,"Current weekend excludes Friday past");
        check(CulturalData.filter(catalog,all,3,today).size()==2,"Remaining month");
        check(CulturalData.filter(catalog,all,4,today).isEmpty(),"Removed future period returns no events");
        check(CulturalData.filter(catalog,onlyMusic,4,today).isEmpty(),"Category filter excludes other types");
        check(CulturalData.filter(catalog,Collections.singleton("s:music:indie"),0,today).size()==1,"Subcategory filter");
        check(CulturalData.filter(catalog,Collections.singleton("s:sport:indie"),0,today).isEmpty(),"IDs scoped to parent");
        check(CulturalData.filter(catalog,Collections.emptySet(),0,today).isEmpty(),"Empty selection means no events");
        CulturalData.Category indie=catalog.categories.stream().filter(c->c.key.equals("s:music:indie")).findFirst().get();
        CulturalData.Category parent=catalog.categories.stream().filter(c->c.key.equals("c:music")).findFirst().get();
        Set<String> expanded=CulturalData.expandSelection(catalog,all);
        check(expanded.contains("c:music") && expanded.contains("c:sport") && !expanded.contains(CulturalData.ALL),"Default all expands dynamically");
        Set<String> edited=CulturalData.toggle(catalog,all,indie);
        check(!edited.contains("c:music") && edited.contains("c:sport"),"Deselecting subcategory preserves other parents");
        check(all.contains(CulturalData.ALL) && all.size()==1,"Draft edits never mutate original selection");
        check(CulturalData.filter(catalog,edited,0,today).isEmpty(),"Deselected subcategory is filtered");
        edited=CulturalData.toggle(catalog,edited,indie);
        check(CulturalData.filter(catalog,edited,0,today).size()==1,"Reselecting subcategory restores it");
        edited=CulturalData.toggle(catalog,edited,parent);
        check(edited.contains("c:music") && !edited.contains("s:music:indie"),"Selecting parent replaces child choices");
        edited=CulturalData.toggle(catalog,edited,parent);
        check(!edited.contains("c:music") && edited.contains("c:sport"),"Clearing parent preserves unrelated category");
        check(catalog.events.get(4).ranges.isEmpty(),"Inconsistent date and weekday excluded");
        for(int weekday=1;weekday<=7;weekday++) {
            LocalDate day=LocalDate.of(2026,10,19).plusDays(weekday-1);
            LocalDate[] window=CulturalData.window(2,day);
            check(!window[0].isBefore(day),"Weekend does not start in past");
            check(window[1].getDayOfWeek()==DayOfWeek.SUNDAY || window[1].equals(day.withDayOfMonth(day.lengthOfMonth())),"Weekend ends Sunday or month boundary");
            check(window[0].getDayOfWeek().getValue()>=5,"Weekend starts Friday or remaining weekend");
        }
        LocalDate yearEnd=LocalDate.of(2026,12,31);
        check(CulturalData.window(1,yearEnd)[0].isAfter(CulturalData.window(1,yearEnd)[1]),"Tomorrow empty at year/month boundary");
        check(CulturalData.window(3,LocalDate.of(2028,2,2))[1].equals(LocalDate.of(2028,2,29)),"Leap month");
        CulturalData.Catalog cycles=parse(event("cycle",music,range("01/10/2026","30/11/2026","5")));
        check(CulturalData.filter(cycles,all,0,today).isEmpty(),"Cycle is not a daily performance");
        check(CulturalData.filter(cycles,all,3,today).get(0).date.equals(LocalDate.of(2026,10,30)),"Next actual weekday");
        check(CulturalData.filter(cycles,all,3,today).size()==1,"One card per cycle");
        CulturalData.Catalog invalid=parse(event("bad",music,range("31/02/2026","31/02/2026","")));
        check(invalid.events.get(0).ranges.isEmpty(),"Invalid calendar date rejected");
        check(CulturalData.allowed("https://www.esmadrid.com/agenda/test","www.esmadrid.com"),"Provider link");
        check(!CulturalData.allowed("https://www.esmadrid.com.evil.test/test","www.esmadrid.com"),"Host suffix rejected");
        check(!CulturalData.allowed("https://secret@www.esmadrid.com/test","www.esmadrid.com"),"URL credentials rejected");
        check(!CulturalData.allowed("http://www.esmadrid.com/test","www.esmadrid.com"),"HTTP rejected");
        reject("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///private'>]><serviceList>&x;</serviceList>");
        reject("<not-an-agenda/>"); reject("<serviceList/>");
        CulturalData.Catalog more=parse(event("other",category("other-parent","Teatro y danza","other","Otros"),range("24/10/2026","24/10/2026","6")));
        check(CulturalData.filter(more,Collections.singleton("s:sport:other"),0,today).isEmpty(),"Duplicate label does not cross categories");
        if(args.length>0) {
            CulturalData.Catalog live=CulturalData.parse(Files.readAllBytes(Paths.get(args[0])));
            check(live.events.size()>1000,"Real public feed parsed");
            check(live.categories.size()>30,"Real taxonomy discovered dynamically");
            for(int tab=0;tab<4;tab++) {
                List<CulturalData.Hit> hits=CulturalData.filter(live,all,tab,today);
                LocalDate[] window=CulturalData.window(tab,today); Set<String> ids=new HashSet<>();
                for(CulturalData.Hit hit:hits) {
                    check(!hit.date.isBefore(window[0]) && !hit.date.isAfter(window[1]),"Live date window");
                    check(ids.add(hit.event.id),"No duplicate live event");
                }
                System.out.println("Live tab "+tab+": "+hits.size()+" listings");
            }
            System.out.println("Live catalogue: "+live.events.size()+" events, "+live.categories.size()+" categories/subcategories");
        }
        System.out.println("Cultural data checks passed: "+checks);
    }
}
