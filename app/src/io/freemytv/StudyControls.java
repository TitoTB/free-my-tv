package io.freemytv;

import android.os.*;
import java.util.concurrent.*;

/** Keep the home indicator live even when the device drawer is closed. Stop with the Home lifecycle. */
final class StudyControls {
    interface Listener { void changed(); void failed(); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final StudyApi api;
    private Listener listener;
    private volatile int generation;
    private boolean loading,awaiting,warned;
    private long deadline;
    boolean pending;
    StudyState state;
    StudyControls() { this(false); }
    StudyControls(boolean bedroom) {
        api=new StudyApi(new HomeControlApi(Config.HOME_ASSISTANT_URL,Config.HOME_ASSISTANT_TOKEN),bedroom);
        state=api.empty();
    }
    private final Runnable poll=this::refresh;
    void start(Listener listener) {
        stop(); this.listener=listener; listener.changed(); refresh();
    }
    void stop() { listener=null; generation++; pending=awaiting=warned=false; main.removeCallbacks(poll); }
    boolean ready() { return listener!=null && !pending && state.anyOn; }
    private void changed() { if(listener!=null) listener.changed(); }
    private void schedule(long delay) { main.removeCallbacks(poll); if(listener!=null) main.postDelayed(poll,delay); }
    private void refresh() {
        if(listener==null || loading || (pending && !awaiting)) return;
        loading=true; final int epoch=generation; final boolean confirming=awaiting;
        IO.execute(()->{
            StudyState next=api.read();
            main.post(()->{
                loading=false;
                if(epoch!=generation) { if(listener!=null) refresh(); return; }
                if(!pending) state=next;
                else if(confirming) {
                    state=StudyState.visible(state,next);
                    if(next.allOff) { state=next; pending=awaiting=false; }
                    else if(SystemClock.elapsedRealtime()>=deadline) {
                        state=next; pending=awaiting=false;
                        if(listener!=null && !warned) { warned=true; listener.failed(); }
                    }
                }
                changed(); schedule(pending?800:5000);
            });
        });
    }
    void turnOff() {
        if(!ready()) return;
        pending=true; awaiting=warned=false; main.removeCallbacks(poll); changed(); final int epoch=generation;
        IO.execute(()->{
            StudyApi.Result result=api.turnOff(()->epoch==generation);
            main.post(()->{
                if(epoch!=generation || listener==null) return;
                state=StudyState.visible(state,result.before);
                if(result.cancelled) { pending=false; changed(); schedule(1); return; }
                if(result.failed) { warned=true; listener.failed(); }
                if(result.before.allOff) pending=false;
                else { awaiting=true; deadline=SystemClock.elapsedRealtime()+20_000; }
                changed(); schedule(pending?500:5000);
            });
        });
    }
}
