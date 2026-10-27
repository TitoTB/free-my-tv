package io.freemytv;

import android.speech.RecognitionService;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;

/** Valid system recognizer companion, delegating explicitly to Google to avoid recursion. */
public class AssistRecognitionService extends RecognitionService {
    private SpeechRecognizer recognizer;
    private Callback current;
    private interface Event {void send(Callback callback)throws RemoteException;}
    private void emit(Callback target,Event event){if(current==target){try{event.send(target);}catch(RemoteException ignored){release();}}}
    @Override protected void onStartListening(Intent intent,Callback target){
        release();current=target;
        try{recognizer=AssistRecognizer.create(this);}catch(RuntimeException unavailable){emit(target,c->c.error(SpeechRecognizer.ERROR_CLIENT));release();return;}
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){emit(target,c->c.readyForSpeech(b));}
            public void onBeginningOfSpeech(){emit(target,c->c.beginningOfSpeech());}
            public void onRmsChanged(float rms){emit(target,c->c.rmsChanged(rms));}
            public void onBufferReceived(byte[] buffer){}
            public void onEndOfSpeech(){emit(target,c->c.endOfSpeech());}
            public void onError(int error){emit(target,c->c.error(error));if(current==target)release();}
            public void onResults(Bundle b){emit(target,c->c.results(b));if(current==target)release();}
            public void onPartialResults(Bundle b){emit(target,c->c.partialResults(b));}
            public void onEvent(int type,Bundle b){}
        });
        try{recognizer.startListening(intent);}catch(RuntimeException error){emit(target,c->c.error(SpeechRecognizer.ERROR_CLIENT));release();}
    }
    @Override protected void onStopListening(Callback target){if(recognizer!=null&&current==target)recognizer.stopListening();}
    @Override protected void onCancel(Callback target){if(current==target)release();}
    private void release(){current=null;if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}}
    @Override public void onDestroy(){release();super.onDestroy();}
}
