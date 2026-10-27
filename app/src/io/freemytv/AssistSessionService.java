package io.freemytv;

import android.service.voice.VoiceInteractionSessionService;
import android.service.voice.VoiceInteractionSession;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class AssistSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args){return new Session(this);}
    private static class Session extends VoiceInteractionSession {
        private final Context context;
        Session(Context context){super(context);this.context=context;}
        @Override public void onCreate(){super.onCreate();setUiEnabled(false);}
        @Override public void onShow(Bundle args,int flags){
            super.onShow(args,flags);
            Log.i("FreeMyTV","voice_session_shown flags="+flags);
            try{
                Intent i=new Intent(context,AssistActivity.class).setAction(Intent.ACTION_ASSIST)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startAssistantActivity(i);
                Log.i("FreeMyTV","voice_session_activity_started");
            }catch(RuntimeException error){Log.i("FreeMyTV","voice_session_launch_failed="+error.getClass().getSimpleName());}
            finally{finish();}
        }
    }
}
