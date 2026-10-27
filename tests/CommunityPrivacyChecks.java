package io.freemytv;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;

/** Run against empty generic Config. No real server, account or household devices. */
public final class CommunityPrivacyChecks {
    private static int checks;
    private static void check(boolean condition){if(!condition)throw new AssertionError("Community check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        AtomicInteger calls=new AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(500,-1);exchange.close();});
        server.start();
        try{
            check(Config.HOME_ASSISTANT_TOKEN.isEmpty());check(Config.JELLYFIN_KEY.isEmpty());check(Config.JELLYSEERR_KEY.isEmpty());
            check(Config.TMDB_KEY.isEmpty());check(Config.FANART_KEY.isEmpty());check(Config.EXPERIENCE_PHOTO_ALBUM.isEmpty());
            HomeControlApi api=new HomeControlApi("http://127.0.0.1:"+server.getAddress().getPort(),"fake-token");
            for(int i=0;i<3;i++){
                boolean rejected=false;try{api.read(i);}catch(IOException expected){rejected=true;}check(rejected);
                rejected=false;try{api.command(i,50);}catch(IOException expected){rejected=true;}check(rejected);
            }
            StudyApi study=new StudyApi(api);check(!study.read().anyOn);check(study.turnOff(()->true).failed);
            StudyApi bedroom=new StudyApi(api,true);check(!bedroom.read().anyOn);check(bedroom.turnOff(()->true).failed);
            ApplianceApi appliances=new ApplianceApi(id->{calls.incrementAndGet();throw new IOException("Unconfigured reader must not be called");});
            for(int i=0;i<3;i++)check(!appliances.read(i,()->true).active);
            check(AssistConversation.indoorQuestion("¿Qué temperatura hay en casa?","es-ES")==null);
            check(AssistConversation.indoorQuestion("¿Qué humedad hay en casa?","es-ES")==null);
            check(ExperienceData.defaults().items.isEmpty());
            check(CalendarAgenda.panelName("calendar.example","My calendar").equals("My calendar"));
            check(CalendarAgenda.fixedColor("calendar.example")==0);
            check(calls.get()==0);
        } finally {server.stop(0);}
        System.out.println("CommunityPrivacyChecks: "+checks+" passed");
    }
}
