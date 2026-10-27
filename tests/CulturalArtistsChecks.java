package io.freemytv;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Candidate/identity checks and real cache/rate/cancellation behaviour with a fake HTTP transport. */
public final class CulturalArtistsChecks {
    private static int checks;
    private static final String ID="a1111111-1111-1111-1111-111111111111";
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static CulturalData.Event event(String title,String description) {return new CulturalData.Event(title,title,description,"https://www.esmadrid.com/agenda/test","","","","",Collections.emptySet(),Collections.emptyList());}
    private static String response(String name,String alias) throws Exception {
        return new JSONObject().put("artists",new JSONArray().put(new JSONObject().put("id",ID).put("name",name).put("score",100).put("disambiguation","Spanish group")
                .put("aliases",new JSONArray().put(new JSONObject().put("name",alias))))).toString();
    }
    private static final class FakeClock implements CulturalArtistSearch.Clock {
        long millis=1_000_000;public long now(){return millis;}public void sleep(long wait){millis+=wait;}
    }
    public static void main(String[] args) throws Exception {
        CulturalData.Event vetusta=event("Vetústa Morla — Gira 2026","La banda Vetusta Morla presenta su concierto.");
        check(CulturalArtists.candidates(vetusta).contains("vetusta morla"),"Title artist extracted before tour text");
        check(CulturalArtists.candidates(event("Festival de otoño","El músico Colin James ofrece una actuación.")).contains("colin james"),"Description proper name extracted");
        check(CulturalArtists.candidates(event("Festival de otoño","Sala Clamores, Madrid. Entradas disponibles.")).stream().noneMatch(s->s.equals("madrid") || s.equals("sala clamores")),"Generic places not treated as artists");
        check(CulturalArtists.candidates(event("Uno + Dos + Tres + Cuatro + Cinco + Seis + Siete","")).size()<=6,"Bounded queries per popup");
        String json=response("Vetusta Morla","Vetústa Mórla");List<CulturalArtists.Artist> resolved=CulturalArtists.parse(json,"vetusta morla",vetusta);
        check(resolved.size()==1 && resolved.get(0).id.equals(ID),"Exact normalised provider identity accepted");
        check(CulturalArtists.parse(response("Vetusta Museum","Other"),"vetusta morla",vetusta).isEmpty(),"Search score alone cannot validate unrelated names");
        check(CulturalArtists.parse(json,"vetusta morla",event("Other band","")).isEmpty(),"Artist must actually be present in event text");
        check(CulturalArtists.parse(json.replace(ID,"invalid"),"vetusta morla",vetusta).isEmpty(),"Malformed IDs rejected");
        check(CulturalArtists.parse(json.replace("100","40"),"vetusta morla",vetusta).isEmpty(),"Low-score results rejected");
        check(CulturalArtists.parse(response("Stage Name","Alias Name"),"alias name",event("Alias Name","")).size()==1,"Provider aliases can validate a name");
        check(!CulturalArtists.query("Muse\" OR *:* ").contains("*"),"Query syntax injection removed before quoting");
        CulturalLikes likes=new CulturalLikes();CulturalArtists.Artist artist=resolved.get(0);
        likes.resolvedArtist(artist.name,artist.id,Arrays.asList("Vetusta Morla","VM Band"),true);
        likes=CulturalLikes.decode(likes.encode());check(likes.artistMatch(event("VM BAND", "")) && likes.artistIds.get("vetusta morla").equals(ID),"Chosen identity and aliases persist");
        likes.artist("Vetústa Morla",false);check(!likes.artistMatch(event("VM Band","")) && likes.artistAliases.isEmpty(),"Removing favourite removes aliases too");
        check(CulturalLikes.decode("{\"version\":4,\"artists\":{\"muse\":\"Muse\"}}").artistMatch(event("Muse","")),"Manual version4 artists migrate");
        Path root=Files.createTempDirectory(Paths.get(args.length>0?args[0]:"."),"artists-check-");
        FakeClock time=new FakeClock();List<Long> requests=new ArrayList<>();AtomicInteger calls=new AtomicInteger();
        CulturalArtistSearch search=new CulturalArtistSearch(root.resolve("cache").toFile(),time,q->{requests.add(time.now());calls.incrementAndGet();try{return response("Vetusta Morla","Vetústa Morla");}catch(Exception failure){throw new IOException(failure);}});
        CulturalArtistSearch.Result first=search.search(vetusta,()->true,r->{});check(first.artists.size()==1 && first.done,"Suggestions resolved without saving a preference");
        int before=calls.get();search.search(vetusta,()->true,r->{});check(calls.get()==before,"Repeated popup uses persistent query cache");
        CulturalArtistSearch restarted=new CulturalArtistSearch(root.resolve("cache").toFile(),time,q->{throw new AssertionError("Restart should use cache");});
        check(restarted.search(vetusta,()->true,r->{}).artists.size()==1,"Cache survives service restart");
        CulturalData.Event multi=event("Muse + Radiohead + Coldplay","");search.search(multi,()->true,r->{});
        for(int i=1;i<requests.size();i++)check(requests.get(i)-requests.get(i-1)>=1100,"Network request starts spaced beyond one second");
        check(!search.search(multi,()->true,r->{}).incomplete,"Valid empty results are successful cached negatives");
        time.millis+=8*86_400_000L;before=calls.get();search.search(multi,()->true,r->{});check(calls.get()>before,"Negative cache expires");
        before=calls.get();search.search(event("Never queried",""),()->false,r->{throw new AssertionError("Cancelled popup received progress");});check(calls.get()==before,"Cancelled popup sends no requests");
        AtomicBoolean active=new AtomicBoolean(true);before=calls.get();search.search(event("Cancel One + Cancel Two", ""),active::get,r->active.set(false));check(calls.get()==before+1,"Dismissal stops subsequent queries");
        AtomicInteger failures=new AtomicInteger();CulturalArtistSearch broken=new CulturalArtistSearch(root.resolve("fail").toFile(),time,q->{failures.incrementAndGet();throw new IOException("offline");});
        check(broken.search(multi,()->true,r->{}).incomplete && failures.get()==1,"Failure triggers cooldown, not one failed request per candidate");
        broken.search(multi,()->true,r->{});check(failures.get()==1,"Repeated popup respects failure cooldown");
        time.millis+=16*60_000L;broken.search(multi,()->true,r->{});check(failures.get()==2,"Failure retriable after cooldown");
        time.millis+=31*86_400_000L;
        CulturalArtistSearch offline=new CulturalArtistSearch(root.resolve("cache").toFile(),time,q->{throw new IOException("offline");});
        CulturalArtistSearch.Result stale=offline.search(vetusta,()->true,r->{});check(stale.incomplete && stale.artists.size()==1,"Expired positive cache remains usable offline");
        System.out.println("CulturalArtistsChecks: "+checks+" checks passed; fixtures: "+root);
    }
}
