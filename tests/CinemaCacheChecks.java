package io.freemytv;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Disk-cache lifecycle tests with fake transfers: no Android runtime or live network required. */
public final class CinemaCacheChecks {
    private static int passed;
    private static void check(boolean value,String name) {
        if(!value) throw new AssertionError(name);
        passed++; System.out.println("PASS "+name);
    }
    private static void artwork(File target) throws Exception {
        byte[] bytes=new byte[32]; bytes[0]=(byte)255; bytes[1]=(byte)216; bytes[2]=(byte)255;
        Files.write(target.toPath(),bytes);
    }
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory(Paths.get(args[0]),"cinema-cache-check-");
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            CinemaImageCache cache=new CinemaImageCache(directory.toFile());
            AtomicInteger transfers=new AtomicInteger();
            CinemaImageCache.Downloader download=(source,file)->{ transfers.incrementAndGet(); artwork(file); };
            File first=cache.ensure(1,"/one.jpg",download);
            cache.ensure(1,"/one.jpg",download);
            check(transfers.get()==1 && first.isFile(),"unchanged artwork is downloaded only once");
            CinemaImageCache restarted=new CinemaImageCache(directory.toFile());
            restarted.ensure(1,"/one.jpg",download);
            check(transfers.get()==1,"disk artwork is reused after launcher restart");
            CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
            CinemaImageCache.Downloader slow=(source,file)->{
                transfers.incrementAndGet(); started.countDown();
                if(!release.await(5,TimeUnit.SECONDS)) throw new IOException("test timeout"); artwork(file);
            };
            Future<File> warm=workers.submit(()->cache.ensure(2,"/two.jpg",slow));
            check(started.await(5,TimeUnit.SECONDS),"background transfer starts");
            Future<File> foreground=workers.submit(()->cache.ensure(2,"/two.jpg",download));
            release.countDown();
            check(warm.get(5,TimeUnit.SECONDS).equals(foreground.get(5,TimeUnit.SECONDS)) && transfers.get()==2,
                    "foreground and preload share one download");
            try { cache.ensure(3,"/three.jpg",(source,file)->{ Files.write(file.toPath(),new byte[2]); throw new IOException("offline"); }); }
            catch(IOException expected) { }
            check(!cache.file(3,"/three.jpg").exists() && !new File(cache.file(3,"/three.jpg").getPath()+".part").exists(),
                    "interrupted transfers leave no reusable partial file");
            cache.ensure(3,"/three.jpg",download);
            Map<Integer,String> programme=new HashMap<>(); programme.put(1,"/one.jpg"); programme.put(2,"/two-new.jpg");
            cache.reconcile(programme);
            check(!cache.file(3,"/three.jpg").exists() && first.exists(),"reconciliation removes departed titles and retains active artwork");
            check(cache.file(2,"/two.jpg").exists(),"previous artwork survives until a replacement succeeds");
            try { cache.ensure(2,"/two-new.jpg",(source,file)->Files.write(file.toPath(),new byte[32])); }
            catch(IOException expected) { }
            cache.reconcile(programme);
            check(cache.file(2,"/two.jpg").exists() && !cache.file(2,"/two-new.jpg").exists(),"invalid downloads cannot replace valid artwork");
            cache.ensure(2,"/two-new.jpg",download); cache.reconcile(programme);
            check(!cache.file(2,"/two.jpg").exists() && cache.file(2,"/two-new.jpg").exists(),"successful replacement removes the old version");
            programme.put(-4,"/four.jpg"); cache.reconcile(programme); cache.ensure(-4,"/four.jpg",download);
            programme.remove(-4); cache.reconcile(programme);
            check(!cache.file(-4,"/four.jpg").exists(),"negative cinema identifiers are reconciled correctly");
            programme.put(6,"poster-source"); cache.reconcile(programme);
            cache.ensure(6,"poster-source",download); cache.ensure(6,"poster-composition",download);
            java.util.Map<Integer,java.util.Set<String>> retained=new java.util.HashMap<>();
            for(java.util.Map.Entry<Integer,String> item:programme.entrySet()) retained.put(item.getKey(),new java.util.HashSet<>(java.util.Collections.singleton(item.getValue())));
            retained.get(6).add("poster-composition"); cache.reconcileSources(retained);
            check(cache.file(6,"poster-source").exists() && cache.file(6,"poster-composition").exists(),
                    "raw selected poster and its processed composition are both retained without another download");
            programme.remove(6); cache.reconcile(programme);
            check(!cache.file(6,"poster-source").exists() && !cache.file(6,"poster-composition").exists(),
                    "departed titles remove both raw posters and processed compositions");
            programme.put(5,"/five.jpg"); cache.reconcile(programme);
            CountDownLatch pending=new CountDownLatch(1),finish=new CountDownLatch(1);
            Future<Boolean> obsolete=workers.submit(()->{
                try { cache.ensure(5,"/five.jpg",(source,file)->{
                    pending.countDown(); if(!finish.await(5,TimeUnit.SECONDS)) throw new IOException("test timeout"); artwork(file);
                }); return false; } catch(IOException expected) { return true; }
            });
            if(!pending.await(5,TimeUnit.SECONDS)) throw new AssertionError("pending obsolete transfer");
            programme.remove(5); cache.reconcile(programme); finish.countDown();
            check(obsolete.get(5,TimeUnit.SECONDS) && !cache.file(5,"/five.jpg").exists(),"late downloads cannot restore artwork for a departed title");
            long now=1_800_000_000_000L;
            CinemaMetadata metadata=new CinemaMetadata(1,"tt123",42,120,"Sinopsis","/one.jpg","abcdefghijk",7.5,now);
            CinemaMetadata restored=CinemaMetadata.decode(metadata.encode());
            check(restored.matches(1,"tt123") && !restored.matches(1,"tt456") && !restored.matches(2,"tt123")
                    && restored.tmdbId==42 && restored.minutes==120 && restored.image.equals("/one.jpg")
                    && restored.overview.equals("Sinopsis") && restored.trailer.equals("abcdefghijk") && restored.rating==7.5,
                    "metadata survives restart and is tied to the exact film identity");
            check(restored.fresh(now+86_399_999L) && !restored.fresh(now+86_400_000L) && !restored.fresh(now-1),
                    "positive metadata refreshes after 24 hours without discarding cached artwork");
            CinemaMetadata missing=new CinemaMetadata(1,"tt123",0,0,"","","",0,now);
            check(missing.fresh(now+899_999L) && !missing.fresh(now+900_000L),"unmatched films retry TMDB after 15 minutes");
            check(!metadata.encode().has("sessions") && !metadata.encode().has("requested") && !metadata.encode().has("apiKey"),
                    "persistent metadata excludes session and request state");
            System.out.println("Passed "+passed+" cinema cache checks");
        } finally {
            workers.shutdownNow();
            try(java.util.stream.Stream<Path> paths=Files.walk(directory)) {
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }
}
