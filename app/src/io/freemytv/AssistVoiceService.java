package io.freemytv;

import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

/** System voice-button entry point. Does not listen until the system opens a session. */
public class AssistVoiceService extends VoiceInteractionService {
    @Override public void onReady(){
        super.onReady();
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST|VoiceInteractionSession.SHOW_WITH_SCREENSHOT);
        Log.i("FreeMyTV","voice_service_ready");
    }
    @Override public void onShutdown(){Log.i("FreeMyTV","voice_service_shutdown");super.onShutdown();}
}
