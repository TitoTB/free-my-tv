package io.freemytv;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Regression checks for rapid navigation while older artwork/details are still loading. */
public final class CinemaLoadChecks {
    private static void check(boolean value,String name) {
        if(!value) throw new AssertionError(name); System.out.println("PASS "+name);
    }
    private static Cinema.Movie movie(int id) { Cinema.Movie m=new Cinema.Movie(); m.id=id; return m; }
    public static void main(String[] args) throws Exception {
        Cinema.LoadWindow window=new Cinema.LoadWindow();
        Cinema.Movie a=movie(1),before=movie(0),next=movie(2),b=movie(9),bBefore=movie(8),bNext=movie(10);
        check(!window.image(a) && !window.details(a),"no background work admitted without a selected title");
        window.set(a,before,next);
        check(window.image(a) && window.image(before) && window.image(next),"artwork work is confined to the current three-title window");
        check(window.details(a) && window.details(next) && !window.details(before),"only current and next title can queue metadata");
        CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
        AtomicBoolean staleAccepted=new AtomicBoolean(true);
        Thread download=new Thread(()->{
            started.countDown();
            try { finish.await(); staleAccepted.set(window.image(a)); }
            catch(InterruptedException e) { throw new AssertionError(e); }
        });
        download.setDaemon(true); download.start(); started.await();
        // The old download remains pending. Selection must advance without waiting for it.
        window.set(b,bBefore,bNext);
        check(window.image(b) && window.details(b) && !window.details(a),"new selection is actionable while the previous download is unfinished");
        finish.countDown(); download.join();
        check(!staleAccepted.get(),"late obsolete artwork cannot enter or evict the visible image cache");
        window.set(a,before,next);
        check(window.image(a) && window.details(a),"returning to a skipped title re-enables its work");
        Cinema.Movie replacement=movie(1);
        window.set(replacement,before,next);
        check(!window.image(a) && window.image(replacement),"programme refresh rejects results belonging to an obsolete movie object");
        window.set(replacement,replacement,replacement);
        check(window.image(replacement) && !window.image(next),"single-title programme stays bounded");
        window.set(null,null,null);
        check(!window.image(replacement) && !window.details(replacement),"empty programme discards pending work");
    }
}
