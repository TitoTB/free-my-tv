package io.freemytv;

import android.os.*;
import java.util.concurrent.*;

/** Poll cycle sensors only while the device drawer is visible. No commands or persisted sensor values. */
final class ApplianceControls {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HomeControlApi transport=new HomeControlApi(Config.HOME_ASSISTANT_URL,Config.HOME_ASSISTANT_TOKEN);
    private final ApplianceApi api=new ApplianceApi(entity->transport.request("/api/states/"+entity,null));
    final ApplianceState[] states=new ApplianceState[ApplianceState.KEYS.length];
    ApplianceControls(){for(int i=0;i<states.length;i++)states[i]=ApplianceState.off();}
    private Runnable listener;
    private volatile int generation;
    private boolean loading;
    private final Runnable poll=this::refresh;
    void start(Runnable listener){stop();this.listener=listener;listener.run();refresh();}
    void stop(){generation++;listener=null;main.removeCallbacks(poll);for(int i=0;i<states.length;i++)states[i]=ApplianceState.off();}
    private void refresh() {
        if(listener==null || loading)return;
        loading=true;int epoch=generation;
        IO.execute(()->{
            ApplianceState[] next=new ApplianceState[states.length];
            for(int i=0;i<next.length;i++)next[i]=api.read(i,()->epoch==generation);
            main.post(()->{
                loading=false;
                if(epoch!=generation){if(listener!=null)refresh();return;}
                for(int i=0;i<next.length;i++)states[i]=next[i];
                if(listener!=null){listener.run();main.postDelayed(poll,5000);}
            });
        });
    }
}
