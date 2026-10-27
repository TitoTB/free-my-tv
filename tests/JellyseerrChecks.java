package io.freemytv;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** JVM integration checks using a local fake server and minimal Android test stubs. Never uses real credentials. */
public final class JellyseerrChecks {
    static void check(boolean value,String name) {
        if(!value) throw new AssertionError(name);
        System.out.println("PASS "+name);
    }
    static void waitFor(Cinema.Movie movie,Semaphore events) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        do {
            if(!events.tryAcquire(6,TimeUnit.SECONDS)) throw new AssertionError("callback timeout");
            if(!movie.requestBusy) return;
        } while(System.nanoTime()<until);
        throw new AssertionError("request timeout");
    }
    static Cinema.Movie movie() { Cinema.Movie m=new Cinema.Movie(); m.tmdbId=1337; return m; }
    public static void main(String[] args) throws Exception {
        AtomicInteger status=new AtomicInteger(1), posts=new AtomicInteger(), failure=new AtomicInteger();
        ServerSocket server=new ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"));
        Thread worker=new Thread(()->{
          while(!server.isClosed()) try(Socket socket=server.accept()) {
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            String first=reader.readLine(),line,key=""; int length=0;
            while((line=reader.readLine())!=null && !line.isEmpty()) {
                int colon=line.indexOf(':'); if(colon<0) continue;
                String name=line.substring(0,colon),value=line.substring(colon+1).trim();
                if(name.equalsIgnoreCase("X-Api-Key")) key=value;
                if(name.equalsIgnoreCase("Content-Length")) length=Integer.parseInt(value);
            }
            char[] content=new char[length]; int offset=0;
            while(offset<length) { int n=reader.read(content,offset,length-offset); if(n<0) break; offset+=n; }
            int code=200; String response;
            if(first==null || !first.contains("/prefix/api/v1/")) { code=404; response="{}";
            } else if(!"test-only".equals(key)) {
                code=401; response="{}";
            } else if(first.startsWith("POST ")) {
                JSONObject body=new JSONObject(new String(content));
                if(!body.getString("mediaType").equals("movie") || body.getInt("mediaId")!=1337)
                    throw new AssertionError("wrong movie identity");
                posts.incrementAndGet();
                if(failure.get()!=3) status.set(2);
                code=failure.get()>=2?500:201; response="{\"id\":99}";
            } else if(failure.get()==1) { code=503; response="{}";
            } else {
                response="{\"id\":1337,\"mediaInfo\":{\"status\":"+status.get()+",\"requests\":[]}}";
            }
            byte[] data=response.getBytes(StandardCharsets.UTF_8);
            java.io.OutputStream out=socket.getOutputStream();
            out.write(("HTTP/1.1 "+code+" Test\r\nContent-Type: application/json\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(data); out.flush();
          } catch(Exception e) { if(!server.isClosed()) throw new RuntimeException(e); }
        });
        worker.setDaemon(true); worker.start();
        Config.JELLYSEERR_URL="http://127.0.0.1:"+server.getLocalPort()+"/prefix/";
        Config.JELLYSEERR_KEY="test-only";
        Semaphore events=new Semaphore(0);
        Jellyseerr client=new Jellyseerr(null,events::release);
        try {
            Cinema.Movie m=movie(); client.refresh(m); waitFor(m,events);
            check(Jellyseerr.canRequest(m),"unrequested exact TMDB movie becomes actionable");
            client.request(m); client.request(m); waitFor(m,events);
            check(m.requested && !Jellyseerr.canRequest(m) && posts.get()==1,"direct request and repeated press produce exactly one POST");
            status.set(5); events.drainPermits(); m=movie(); client.refresh(m); waitFor(m,events);
            client.request(m);
            check(m.requested && posts.get()==1,"available movie is disabled without submitting");
            status.set(1); events.drainPermits(); m=movie(); client.refresh(m); waitFor(m,events);
            status.set(2); client.request(m); waitFor(m,events);
            check(m.requested && posts.get()==1,"preflight detects a request from another client");
            status.set(1); events.drainPermits(); m=movie(); client.refresh(m); waitFor(m,events);
            failure.set(2); client.request(m); waitFor(m,events);
            check(m.requested && posts.get()==2,"lost POST response reconciles success without resending");
            failure.set(0); status.set(1); events.drainPermits(); m=movie(); client.refresh(m); waitFor(m,events);
            failure.set(3); client.request(m); waitFor(m,events);
            check(!m.requestKnown && !m.requested && posts.get()==3,"failed POST never claims success or retries automatically");
            failure.set(1); events.drainPermits(); m=movie(); client.refresh(m); waitFor(m,events);
            check(!m.requestKnown && !Jellyseerr.canRequest(m),"server unavailable keeps request disabled");
            check(Jellyseerr.hasRequest(new JSONObject("{\"id\":1337,\"mediaInfo\":{\"status\":1,\"requests\":[{\"id\":5}]}}")),"existing request prevents duplicates independently of media status");
            check(!Jellyseerr.canRequest(movie()),"unverified movie cannot be requested");
        } finally {
            server.close();
            java.lang.reflect.Field field=Jellyseerr.class.getDeclaredField("io"); field.setAccessible(true);
            ((ExecutorService)field.get(client)).shutdownNow();
        }
    }
}
