package io.freemytv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Only poll while the drawer is visible. Commands are single-shot and never queued for reconnection. */
final class HomeControls {
    interface Listener { void changed(); void failed(); }
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final HomeControlApi api=new HomeControlApi(Config.HOME_ASSISTANT_URL,Config.HOME_ASSISTANT_TOKEN);
    final HomeControlState[] states={HomeControlState.missing(),HomeControlState.missing(),HomeControlState.missing()};
    private Listener listener;
    private boolean loading;
    int pending=-1;
    private HomeControlPending confirmation;
    private volatile int generation;
    private final Runnable poll=this::refresh;
    static boolean enabled() { return HomeClimate.enabled(); }
    void start(Listener l) {
        stop(); listener=l; generation++;
        l.changed(); refresh();
    }
    void stop() { listener=null; generation++; pending=-1; confirmation=null; main.removeCallbacks(poll); }
    boolean ready(int index) { return listener!=null && pending<0 && states[index].controllable; }
    private void notifyChange() { if(listener!=null) listener.changed(); }
    private void schedule(long delay) { main.removeCallbacks(poll); if(listener!=null) main.postDelayed(poll,delay); }
    private void refresh() {
        if(listener==null || loading || (pending>=0 && confirmation==null)) return;
        loading=true; final int epoch=generation;
        final int only=confirmation==null?-1:confirmation.index;
        IO.execute(()->{
            HomeControlState[] next=new HomeControlState[3];
            for(int i=0;i<3;i++) if(only<0 || only==i)
                try { next[i]=api.read(i); } catch(Exception ignored) { next[i]=HomeControlState.missing(); }
            main.post(()->{
                loading=false;
                if(epoch==generation) {
                    // A poll started before the command cannot confirm it or replace its visible state.
                    for(int i=0;i<3;i++) if(next[i]!=null && i!=pending) states[i]=next[i];
                    if(confirmation!=null && only==pending) {
                        states[pending]=confirmation.visible(states[pending],next[pending]);
                        if(confirmation.confirmed(next[pending])) { pending=-1; confirmation=null; }
                        else if(confirmation.expired(SystemClock.elapsedRealtime())) {
                            pending=-1; confirmation=null; if(listener!=null) listener.failed();
                        }
                    }
                    notifyChange(); schedule(pending>=0?800:5000);
                }
                else if(listener!=null) refresh();
            });
        });
    }
    void send(int index,int position) {
        if(!ready(index)) return;
        pending=index; main.removeCallbacks(poll); notifyChange();
        final int epoch=generation;
        IO.execute(()->{
            HomeControlState before=null;
            // A hidden/paused panel must not submit an action that was still waiting behind a read.
            if(epoch==generation) try { before=api.command(index,position); } catch(Exception ignored) {}
            final HomeControlState prior=before;
            main.post(()->{
                if(epoch==generation && listener!=null) {
                    if(prior!=null) confirmation=new HomeControlPending(index,position,prior,SystemClock.elapsedRealtime());
                    else { pending=-1; listener.failed(); }
                    notifyChange(); schedule(prior!=null?500:5000);
                } else if(listener!=null && pending<0) schedule(1);
            });
        });
    }
}
