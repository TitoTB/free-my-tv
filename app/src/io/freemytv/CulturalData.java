package io.freemytv;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/** Provider taxonomy and local-date filtering; independent of Android and of any selected genres. */
final class CulturalData {
    static final ZoneId ZONE=ZoneId.of("Europe/Madrid");
    static final String ALL="*";
    private static final Map<String,Integer> HTML_ENTITIES=new HashMap<>();
    static {
        HTML_ENTITIES.put("AElig",198);
        HTML_ENTITIES.put("Aacute",193);
        HTML_ENTITIES.put("Acirc",194);
        HTML_ENTITIES.put("Agrave",192);
        HTML_ENTITIES.put("Alpha",913);
        HTML_ENTITIES.put("Aring",197);
        HTML_ENTITIES.put("Atilde",195);
        HTML_ENTITIES.put("Auml",196);
        HTML_ENTITIES.put("Beta",914);
        HTML_ENTITIES.put("Ccedil",199);
        HTML_ENTITIES.put("Chi",935);
        HTML_ENTITIES.put("Dagger",8225);
        HTML_ENTITIES.put("Delta",916);
        HTML_ENTITIES.put("ETH",208);
        HTML_ENTITIES.put("Eacute",201);
        HTML_ENTITIES.put("Ecirc",202);
        HTML_ENTITIES.put("Egrave",200);
        HTML_ENTITIES.put("Epsilon",917);
        HTML_ENTITIES.put("Eta",919);
        HTML_ENTITIES.put("Euml",203);
        HTML_ENTITIES.put("Gamma",915);
        HTML_ENTITIES.put("Iacute",205);
        HTML_ENTITIES.put("Icirc",206);
        HTML_ENTITIES.put("Igrave",204);
        HTML_ENTITIES.put("Iota",921);
        HTML_ENTITIES.put("Iuml",207);
        HTML_ENTITIES.put("Kappa",922);
        HTML_ENTITIES.put("Lambda",923);
        HTML_ENTITIES.put("Mu",924);
        HTML_ENTITIES.put("Ntilde",209);
        HTML_ENTITIES.put("Nu",925);
        HTML_ENTITIES.put("OElig",338);
        HTML_ENTITIES.put("Oacute",211);
        HTML_ENTITIES.put("Ocirc",212);
        HTML_ENTITIES.put("Ograve",210);
        HTML_ENTITIES.put("Omega",937);
        HTML_ENTITIES.put("Omicron",927);
        HTML_ENTITIES.put("Oslash",216);
        HTML_ENTITIES.put("Otilde",213);
        HTML_ENTITIES.put("Ouml",214);
        HTML_ENTITIES.put("Phi",934);
        HTML_ENTITIES.put("Pi",928);
        HTML_ENTITIES.put("Prime",8243);
        HTML_ENTITIES.put("Psi",936);
        HTML_ENTITIES.put("Rho",929);
        HTML_ENTITIES.put("Scaron",352);
        HTML_ENTITIES.put("Sigma",931);
        HTML_ENTITIES.put("THORN",222);
        HTML_ENTITIES.put("Tau",932);
        HTML_ENTITIES.put("Theta",920);
        HTML_ENTITIES.put("Uacute",218);
        HTML_ENTITIES.put("Ucirc",219);
        HTML_ENTITIES.put("Ugrave",217);
        HTML_ENTITIES.put("Upsilon",933);
        HTML_ENTITIES.put("Uuml",220);
        HTML_ENTITIES.put("Xi",926);
        HTML_ENTITIES.put("Yacute",221);
        HTML_ENTITIES.put("Yuml",376);
        HTML_ENTITIES.put("Zeta",918);
        HTML_ENTITIES.put("aacute",225);
        HTML_ENTITIES.put("acirc",226);
        HTML_ENTITIES.put("acute",180);
        HTML_ENTITIES.put("aelig",230);
        HTML_ENTITIES.put("agrave",224);
        HTML_ENTITIES.put("alefsym",8501);
        HTML_ENTITIES.put("alpha",945);
        HTML_ENTITIES.put("amp",38);
        HTML_ENTITIES.put("and",8743);
        HTML_ENTITIES.put("ang",8736);
        HTML_ENTITIES.put("apos",39);
        HTML_ENTITIES.put("aring",229);
        HTML_ENTITIES.put("asymp",8776);
        HTML_ENTITIES.put("atilde",227);
        HTML_ENTITIES.put("auml",228);
        HTML_ENTITIES.put("bdquo",8222);
        HTML_ENTITIES.put("beta",946);
        HTML_ENTITIES.put("brvbar",166);
        HTML_ENTITIES.put("bull",8226);
        HTML_ENTITIES.put("cap",8745);
        HTML_ENTITIES.put("ccedil",231);
        HTML_ENTITIES.put("cedil",184);
        HTML_ENTITIES.put("cent",162);
        HTML_ENTITIES.put("chi",967);
        HTML_ENTITIES.put("circ",710);
        HTML_ENTITIES.put("clubs",9827);
        HTML_ENTITIES.put("cong",8773);
        HTML_ENTITIES.put("copy",169);
        HTML_ENTITIES.put("crarr",8629);
        HTML_ENTITIES.put("cup",8746);
        HTML_ENTITIES.put("curren",164);
        HTML_ENTITIES.put("dArr",8659);
        HTML_ENTITIES.put("dagger",8224);
        HTML_ENTITIES.put("darr",8595);
        HTML_ENTITIES.put("deg",176);
        HTML_ENTITIES.put("delta",948);
        HTML_ENTITIES.put("diams",9830);
        HTML_ENTITIES.put("divide",247);
        HTML_ENTITIES.put("eacute",233);
        HTML_ENTITIES.put("ecirc",234);
        HTML_ENTITIES.put("egrave",232);
        HTML_ENTITIES.put("empty",8709);
        HTML_ENTITIES.put("emsp",8195);
        HTML_ENTITIES.put("ensp",8194);
        HTML_ENTITIES.put("epsilon",949);
        HTML_ENTITIES.put("equiv",8801);
        HTML_ENTITIES.put("eta",951);
        HTML_ENTITIES.put("eth",240);
        HTML_ENTITIES.put("euml",235);
        HTML_ENTITIES.put("euro",8364);
        HTML_ENTITIES.put("exist",8707);
        HTML_ENTITIES.put("fnof",402);
        HTML_ENTITIES.put("forall",8704);
        HTML_ENTITIES.put("frac12",189);
        HTML_ENTITIES.put("frac14",188);
        HTML_ENTITIES.put("frac34",190);
        HTML_ENTITIES.put("frasl",8260);
        HTML_ENTITIES.put("gamma",947);
        HTML_ENTITIES.put("ge",8805);
        HTML_ENTITIES.put("gt",62);
        HTML_ENTITIES.put("hArr",8660);
        HTML_ENTITIES.put("harr",8596);
        HTML_ENTITIES.put("hearts",9829);
        HTML_ENTITIES.put("hellip",8230);
        HTML_ENTITIES.put("iacute",237);
        HTML_ENTITIES.put("icirc",238);
        HTML_ENTITIES.put("iexcl",161);
        HTML_ENTITIES.put("igrave",236);
        HTML_ENTITIES.put("image",8465);
        HTML_ENTITIES.put("infin",8734);
        HTML_ENTITIES.put("int",8747);
        HTML_ENTITIES.put("iota",953);
        HTML_ENTITIES.put("iquest",191);
        HTML_ENTITIES.put("isin",8712);
        HTML_ENTITIES.put("iuml",239);
        HTML_ENTITIES.put("kappa",954);
        HTML_ENTITIES.put("lArr",8656);
        HTML_ENTITIES.put("lambda",955);
        HTML_ENTITIES.put("lang",9001);
        HTML_ENTITIES.put("laquo",171);
        HTML_ENTITIES.put("larr",8592);
        HTML_ENTITIES.put("lceil",8968);
        HTML_ENTITIES.put("ldquo",8220);
        HTML_ENTITIES.put("le",8804);
        HTML_ENTITIES.put("lfloor",8970);
        HTML_ENTITIES.put("lowast",8727);
        HTML_ENTITIES.put("loz",9674);
        HTML_ENTITIES.put("lrm",8206);
        HTML_ENTITIES.put("lsaquo",8249);
        HTML_ENTITIES.put("lsquo",8216);
        HTML_ENTITIES.put("lt",60);
        HTML_ENTITIES.put("macr",175);
        HTML_ENTITIES.put("mdash",8212);
        HTML_ENTITIES.put("micro",181);
        HTML_ENTITIES.put("middot",183);
        HTML_ENTITIES.put("minus",8722);
        HTML_ENTITIES.put("mu",956);
        HTML_ENTITIES.put("nabla",8711);
        HTML_ENTITIES.put("nbsp",160);
        HTML_ENTITIES.put("ndash",8211);
        HTML_ENTITIES.put("ne",8800);
        HTML_ENTITIES.put("ni",8715);
        HTML_ENTITIES.put("not",172);
        HTML_ENTITIES.put("notin",8713);
        HTML_ENTITIES.put("nsub",8836);
        HTML_ENTITIES.put("ntilde",241);
        HTML_ENTITIES.put("nu",957);
        HTML_ENTITIES.put("oacute",243);
        HTML_ENTITIES.put("ocirc",244);
        HTML_ENTITIES.put("oelig",339);
        HTML_ENTITIES.put("ograve",242);
        HTML_ENTITIES.put("oline",8254);
        HTML_ENTITIES.put("omega",969);
        HTML_ENTITIES.put("omicron",959);
        HTML_ENTITIES.put("oplus",8853);
        HTML_ENTITIES.put("or",8744);
        HTML_ENTITIES.put("ordf",170);
        HTML_ENTITIES.put("ordm",186);
        HTML_ENTITIES.put("oslash",248);
        HTML_ENTITIES.put("otilde",245);
        HTML_ENTITIES.put("otimes",8855);
        HTML_ENTITIES.put("ouml",246);
        HTML_ENTITIES.put("para",182);
        HTML_ENTITIES.put("part",8706);
        HTML_ENTITIES.put("permil",8240);
        HTML_ENTITIES.put("perp",8869);
        HTML_ENTITIES.put("phi",966);
        HTML_ENTITIES.put("pi",960);
        HTML_ENTITIES.put("piv",982);
        HTML_ENTITIES.put("plusmn",177);
        HTML_ENTITIES.put("pound",163);
        HTML_ENTITIES.put("prime",8242);
        HTML_ENTITIES.put("prod",8719);
        HTML_ENTITIES.put("prop",8733);
        HTML_ENTITIES.put("psi",968);
        HTML_ENTITIES.put("quot",34);
        HTML_ENTITIES.put("rArr",8658);
        HTML_ENTITIES.put("radic",8730);
        HTML_ENTITIES.put("rang",9002);
        HTML_ENTITIES.put("raquo",187);
        HTML_ENTITIES.put("rarr",8594);
        HTML_ENTITIES.put("rceil",8969);
        HTML_ENTITIES.put("rdquo",8221);
        HTML_ENTITIES.put("real",8476);
        HTML_ENTITIES.put("reg",174);
        HTML_ENTITIES.put("rfloor",8971);
        HTML_ENTITIES.put("rho",961);
        HTML_ENTITIES.put("rlm",8207);
        HTML_ENTITIES.put("rsaquo",8250);
        HTML_ENTITIES.put("rsquo",8217);
        HTML_ENTITIES.put("sbquo",8218);
        HTML_ENTITIES.put("scaron",353);
        HTML_ENTITIES.put("sdot",8901);
        HTML_ENTITIES.put("sect",167);
        HTML_ENTITIES.put("shy",173);
        HTML_ENTITIES.put("sigma",963);
        HTML_ENTITIES.put("sigmaf",962);
        HTML_ENTITIES.put("sim",8764);
        HTML_ENTITIES.put("spades",9824);
        HTML_ENTITIES.put("sub",8834);
        HTML_ENTITIES.put("sube",8838);
        HTML_ENTITIES.put("sum",8721);
        HTML_ENTITIES.put("sup",8835);
        HTML_ENTITIES.put("sup1",185);
        HTML_ENTITIES.put("sup2",178);
        HTML_ENTITIES.put("sup3",179);
        HTML_ENTITIES.put("supe",8839);
        HTML_ENTITIES.put("szlig",223);
        HTML_ENTITIES.put("tau",964);
        HTML_ENTITIES.put("there4",8756);
        HTML_ENTITIES.put("theta",952);
        HTML_ENTITIES.put("thetasym",977);
        HTML_ENTITIES.put("thinsp",8201);
        HTML_ENTITIES.put("thorn",254);
        HTML_ENTITIES.put("tilde",732);
        HTML_ENTITIES.put("times",215);
        HTML_ENTITIES.put("trade",8482);
        HTML_ENTITIES.put("uArr",8657);
        HTML_ENTITIES.put("uacute",250);
        HTML_ENTITIES.put("uarr",8593);
        HTML_ENTITIES.put("ucirc",251);
        HTML_ENTITIES.put("ugrave",249);
        HTML_ENTITIES.put("uml",168);
        HTML_ENTITIES.put("upsih",978);
        HTML_ENTITIES.put("upsilon",965);
        HTML_ENTITIES.put("uuml",252);
        HTML_ENTITIES.put("weierp",8472);
        HTML_ENTITIES.put("xi",958);
        HTML_ENTITIES.put("yacute",253);
        HTML_ENTITIES.put("yen",165);
        HTML_ENTITIES.put("yuml",255);
        HTML_ENTITIES.put("zeta",950);
        HTML_ENTITIES.put("zwj",8205);
        HTML_ENTITIES.put("zwnj",8204);
    }
    static final class Category {
        final String key,name,parent;
        Category(String key,String name,String parent) { this.key=key; this.name=name; this.parent=parent; }
    }
    static final class Range {
        final LocalDate start,end;
        final Set<Integer> weekdays;
        Range(LocalDate start,LocalDate end,Set<Integer> days) { this.start=start; this.end=end; weekdays=days; }
        LocalDate next(LocalDate from,LocalDate until) {
            LocalDate day=start.isAfter(from)?start:from;
            LocalDate last=end.isBefore(until)?end:until;
            // A weekday mask repeats within a week; never expand a long cycle into daily cards.
            for(int i=0;i<7 && !day.isAfter(last);i++,day=day.plusDays(1))
                if(weekdays.isEmpty() || weekdays.contains(day.getDayOfWeek().getValue())) return day;
            return null;
        }
    }
    static final class Event {
        final String id,title,description,url,image,address,hours,price,venueId,venueName,searchText;
        final boolean free;
        final Set<String> categories;
        final List<Range> ranges;
        Event(String id,String title,String description,String url,String image,String address,String hours,String price,
                Set<String> categories,List<Range> ranges) {
            this(id,title,description,url,image,address,hours,price,categories,ranges,"","");
        }
        Event(String id,String title,String description,String url,String image,String address,String hours,String price,
                Set<String> categories,List<Range> ranges,String venueId,String venueName) {
            this.id=id; this.title=title; this.description=description; this.url=url; this.image=image;
            this.address=address; this.hours=hours; this.price=price;
            this.venueId=venueId;this.venueName=venueName;free=freePrice(price);
            searchText=" "+normalise(title)+" | "+normalise(description)+" ";
            this.categories=Collections.unmodifiableSet(categories); this.ranges=Collections.unmodifiableList(ranges);
        }
    }
    static final class Hit {
        final Event event; final LocalDate date,start,end;
        final boolean scheduled;
        Hit(Event event,LocalDate date) { this(event,date,new Range(date,date,Collections.emptySet())); }
        Hit(Event event,LocalDate date,Range range) { this.event=event; this.date=date;start=range.start;end=range.end;scheduled=!range.weekdays.isEmpty() && range.weekdays.size()<7; }
        boolean ongoing(LocalDate today) { return start.isBefore(today); }
        boolean punctual() { return java.time.temporal.ChronoUnit.DAYS.between(start,end)<=3; }
    }
    static final class Catalog {
        final List<Category> categories;
        final List<Event> events;
        Catalog(List<Category> categories,List<Event> events) {
            this.categories=Collections.unmodifiableList(categories); this.events=Collections.unmodifiableList(events);
        }
    }
    static Catalog empty() { return new Catalog(new ArrayList<>(),new ArrayList<>()); }
    static String normalise(String value) {
        return java.text.Normalizer.normalize(value==null?"":value,java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll("\\s+"," ");
    }
    /** Only unequivocal general free admission; never infer from the event description or a concession. */
    static boolean freePrice(String price) {
        String value=normalise(plain(price)).replaceFirst("^(?:actividad|evento) de acceso ","acceso ");
        if(value.matches("(?:entrada |acceso |precio )?0(?: euros?| eur)?"))return true;
        return value.matches("(?:(?:entrada|acceso|evento|actividad|visita) )?(?:libre(?: y gratuit[oa])?|gratuit[oa]|gratis)(?: (?:hasta completar aforo|previa reserva|con reserva previa|con inscripcion previa|previa inscripcion))?");
    }
    static Set<String> expandSelection(Catalog catalog,Set<String> selection) {
        Set<String> result=new HashSet<>(selection);
        if(result.remove(ALL)) for(Category c:catalog.categories)if(c.parent.isEmpty())result.add(c.key);
        return result;
    }
    static Set<String> toggle(Catalog catalog,Set<String> selection,Category c) {
        Set<String> result=expandSelection(catalog,selection);
        boolean wasSelected=result.contains(c.key) || !c.parent.isEmpty() && result.contains(c.parent);
        if(c.parent.isEmpty()) {
            result.remove(c.key); for(Category child:catalog.categories)if(c.key.equals(child.parent))result.remove(child.key);
            if(!wasSelected)result.add(c.key);
        } else {
            if(result.remove(c.parent)) for(Category sibling:catalog.categories)
                if(c.parent.equals(sibling.parent) && !c.key.equals(sibling.key))result.add(sibling.key);
            if(wasSelected)result.remove(c.key); else result.add(c.key);
        }
        return result;
    }
    private static List<Element> children(Element e,String tag) {
        List<Element> out=new ArrayList<>();
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element && tag.equals(n.getNodeName())) out.add((Element)n);
        return out;
    }
    private static Element child(Element e,String tag) {
        List<Element> found=children(e,tag); return found.isEmpty()?null:found.get(0);
    }
    private static String text(Element e,String tag) { Element v=e==null?null:child(e,tag); return v==null?"":v.getTextContent().trim(); }
    private static String item(Element e,String name) {
        if(e!=null) for(Element v:children(e,"item")) if(name.equals(v.getAttribute("name"))) return v.getTextContent().trim();
        return "";
    }
    static String plain(String s) {
        return plain(s,false);
    }
    static String paragraphs(String s) { return plain(s,true); }
    private static String plain(String s,boolean paragraphs) {
        s=s.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>"," ");
        if(paragraphs)s=s.replaceAll("(?i)<br\\b[^>]*>","\n")
                .replaceAll("(?i)</(?:p|div|li|h[1-6]|blockquote|section|article|tr)\\s*>","\n\n");
        s=s.replaceAll("<[^>]+>",paragraphs?"":" ");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(s);
        StringBuffer out=new StringBuffer();
        while(m.find()) {
            String value=" ";
            try { String n=m.group(1); int cp=Integer.parseInt(n.startsWith("x")?n.substring(1):n,n.startsWith("x")?16:10);
                if(Character.isValidCodePoint(cp)) value=new String(Character.toChars(cp)); } catch(Exception ignored) {}
            m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        java.util.regex.Matcher named=java.util.regex.Pattern.compile("&([A-Za-z][A-Za-z0-9]+);").matcher(out.toString());
        StringBuffer decoded=new StringBuffer();
        while(named.find()) {
            Integer cp=HTML_ENTITIES.get(named.group(1));
            named.appendReplacement(decoded,java.util.regex.Matcher.quoteReplacement(cp==null?named.group():new String(Character.toChars(cp))));
        }
        named.appendTail(decoded);
        String value=decoded.toString().replace('\u00a0',' ');
        if(!paragraphs)return value.replaceAll("\\s+"," ").trim();
        return value.replace("\r\n","\n").replace('\r','\n').replace('\u2028','\n').replace("\u2029","\n\n")
                .replaceAll("[\\p{Zs}\\t\\x0B\\f]+"," ").replaceAll(" *\n *","\n").replaceAll("\n{3,}","\n\n").trim();
    }
    static boolean allowed(String value,String... hosts) {
        try { URI u=new URI(value); if(!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo()!=null || u.getPort()!=-1 && u.getPort()!=443) return false;
            for(String host:hosts) if(host.equalsIgnoreCase(u.getHost())) return true; } catch(Exception ignored) {}
        return false;
    }
    static Catalog parse(byte[] bytes) throws Exception {
        if(bytes.length==0 || bytes.length>10*1024*1024) throw new IOException("Invalid cultural catalogue size");
        // DTDs are not part of this feed. Reject before parsing, including non-UTF-8 encodings.
        String xml=new String(bytes,StandardCharsets.UTF_8);
        if(xml.indexOf('\0')>=0 || xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || xml.toUpperCase(Locale.ROOT).contains("<!ENTITY"))
            throw new IOException("Unsupported XML declaration");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities",false); } catch(Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false); } catch(Exception ignored) {}
        javax.xml.parsers.DocumentBuilder builder=factory.newDocumentBuilder();
        builder.setEntityResolver((publicId,systemId)->{ throw new SAXException("External entities disabled"); });
        Element root=builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        if(!"serviceList".equals(root.getTagName())) throw new IOException("Unexpected cultural catalogue");
        Map<String,Category> taxonomy=new LinkedHashMap<>(); Map<String,Event> events=new LinkedHashMap<>();
        DateTimeFormatter format=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
        for(Element service:children(root,"service")) {
            Element basic=child(service,"basicData"),extra=child(service,"extradata"),geo=child(service,"geoData");
            if(basic==null || extra==null) continue;
            String id=service.getAttribute("id"),title=plain(text(basic,"name")),url=text(basic,"web");
            if(id.isEmpty() || title.isEmpty() || !allowed(url,"www.esmadrid.com","esmadrid.com")) continue;
            Set<String> keys=new HashSet<>(); Element cats=child(extra,"categorias");
            if(cats!=null) for(Element category:children(cats,"categoria")) {
                String parentId=item(category,"idCategoria"),name=plain(item(category,"Categoria"));
                if(parentId.isEmpty() || name.isEmpty()) continue;
                String key="c:"+parentId; taxonomy.put(key,new Category(key,name,"")); keys.add(key);
                Element subs=child(category,"subcategorias");
                if(subs!=null) for(Element sub:children(subs,"subcategoria")) {
                    String subId=item(sub,"idSubCategoria"),subName=plain(item(sub,"SubCategoria"));
                    if(subId.isEmpty() || subName.isEmpty()) continue;
                    String subKey="s:"+parentId+":"+subId;
                    taxonomy.put(subKey,new Category(subKey,subName,key)); keys.add(subKey);
                }
            }
            List<Range> ranges=new ArrayList<>(); Element dates=child(extra,"fechas");
            if(dates!=null) for(Element span:children(dates,"rango")) try {
                LocalDate start=LocalDate.parse(text(span,"inicio"),format),end=LocalDate.parse(text(span,"fin"),format);
                if(end.isBefore(start)) continue;
                Set<Integer> days=new HashSet<>(); for(String value:text(span,"dias").split(",")) try {
                    int day=Integer.parseInt(value.trim()); if(day>=1 && day<=7) days.add(day);
                } catch(NumberFormatException ignored) {}
                if(start.equals(end) && !days.isEmpty() && !days.contains(start.getDayOfWeek().getValue())) continue;
                ranges.add(new Range(start,end,days));
            } catch(Exception ignored) {}
            String image=""; Element multimedia=child(service,"multimedia");
            if(multimedia!=null) for(Element media:children(multimedia,"media"))
                if("image".equals(media.getAttribute("type")) && allowed(text(media,"url"),"estaticos.esmadrid.com","www.esmadrid.com")) { image=text(media,"url"); break; }
            Event event=new Event(id,title,paragraphs(text(basic,"body")),url,image,plain(text(geo,"address")),
                    plain(item(extra,"Horario")),plain(item(extra,"Servicios de pago")),keys,ranges,text(basic,"idrt").trim(),plain(text(basic,"nombrert")));
            events.put(id,event);
        }
        if(events.isEmpty()) throw new IOException("Empty cultural catalogue");
        List<Category> categories=new ArrayList<>(taxonomy.values());
        categories.sort(Comparator.comparing((Category c)->c.parent.isEmpty()?c.name:taxonomy.get(c.parent).name)
                .thenComparing(c->c.parent.isEmpty()?"":c.name));
        return new Catalog(categories,new ArrayList<>(events.values()));
    }
    static LocalDate[] window(int tab,LocalDate today) {
        LocalDate end=today.withDayOfMonth(today.lengthOfMonth());
        switch(tab) {
            case 0:return new LocalDate[]{today,today};
            case 1:return new LocalDate[]{today.plusDays(1),today.plusDays(1).isAfter(end)?end:today.plusDays(1)};
            case 2: {
                int weekday=today.getDayOfWeek().getValue();
                LocalDate friday=today.plusDays(5-weekday);
                LocalDate sunday=friday.plusDays(2);
                return new LocalDate[]{today.isAfter(friday)?today:friday,sunday.isAfter(end)?end:sunday};
            }
            case 3:return new LocalDate[]{today,end};
            default:return new LocalDate[]{end.plusDays(1),end};
        }
    }
    /** Keep the taxonomy, but never retain events/ranges without a remaining occurrence this month. */
    static Catalog monthly(Catalog source,LocalDate today) {
        LocalDate end=today.withDayOfMonth(today.lengthOfMonth());List<Event> events=new ArrayList<>();
        for(Event e:source.events) {
            List<Range> ranges=new ArrayList<>();
            for(Range range:e.ranges)if(range.next(today,end)!=null)ranges.add(range);
            if(!ranges.isEmpty())events.add(new Event(e.id,e.title,e.description,e.url,e.image,e.address,e.hours,e.price,e.categories,ranges,e.venueId,e.venueName));
        }
        return new Catalog(source.categories,events);
    }
    static List<Hit> filter(Catalog catalog,Set<String> selected,int tab,LocalDate today) {
        if(tab<0 || tab>3)return Collections.emptyList();
        LocalDate[] window=window(tab,today); List<Hit> hits=new ArrayList<>();
        for(Event e:catalog.events) {
            boolean match=selected.contains(ALL); for(String key:e.categories) match|=selected.contains(key);
            if(!match) continue;
            LocalDate first=null; Range chosen=null;
            for(Range r:e.ranges) {
                LocalDate next=r.next(window[0],window[1]);
                if(next!=null && (first==null || next.isBefore(first) || next.equals(first) &&
                        java.time.temporal.ChronoUnit.DAYS.between(r.start,r.end)<java.time.temporal.ChronoUnit.DAYS.between(chosen.start,chosen.end))) { first=next;chosen=r; }
            }
            if(first!=null)hits.add(new Hit(e,first,chosen));
        }
        hits.sort(Comparator.comparingInt((Hit h)->relevance(h,tab,today)).thenComparing(h->h.date)
                .thenComparing(h->h.event.title,String.CASE_INSENSITIVE_ORDER).thenComparing(h->h.event.id));
        return Collections.unmodifiableList(hits);
    }
    private static int relevance(Hit hit,int tab,LocalDate today) {
        if(hit.punctual())return 0;
        if(tab==3)return hit.ongoing(today)?2:1;
        // Explicit weekday schedules outrank unrestricted multi-week ranges, without inventing genre/type IDs.
        return hit.scheduled?1:2;
    }
}
