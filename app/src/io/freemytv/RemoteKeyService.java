package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 */
public class RemoteKeyService extends AccessibilityService {
    private long trailerUntil;
    private boolean trailerVisible, sawTrailer, trailerBackDown;

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK) {
            if(event.getAction()==KeyEvent.ACTION_UP && trailerBackDown) {
                trailerBackDown=false;
                trailerVisible=false;
                TrailerReturn.clear(this);
                if(!performGlobalAction(GLOBAL_ACTION_HOME)) {
                    startActivity(new android.content.Intent(this,HomeActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
                }
                return true;
            }
            if(trailerVisible && System.currentTimeMillis()<trailerUntil) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) trailerBackDown=true;
                return true;
            }
        }
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null) return;
        TrailerReturn.Request request=TrailerReturn.read(this);
        if(request==null) { trailerVisible=sawTrailer=trailerBackDown=false; trailerUntil=0; return; }
        if(request.until!=trailerUntil) { sawTrailer=false; trailerUntil=request.until; }
        String pkg=event.getPackageName().toString();
        trailerVisible=request.pkg.equals(pkg);
        if(trailerVisible) sawTrailer=true;
        else if(sawTrailer && !"android".equals(pkg) && !"com.android.systemui".equals(pkg)) {
            TrailerReturn.clear(this); sawTrailer=trailerBackDown=false; trailerUntil=0;
        }
    }

    @Override public void onInterrupt() {}
}
