package io.freemytv;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.Locale;

/** TTS is output only: no replay after leaving the panel, no volume changes or capture while speaking. */
final class AssistSpeech {
    interface Status {void speaking(boolean value);void unavailable();}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final Status status;
    private TextToSpeech tts;
    private final AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioFocusRequest focus;
    private boolean ready,closed,failed,focused;
    private String pending="",utterance="";
    private int generation;
    AssistSpeech(Context context,Status status){
        this.status=status;audio=context.getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change->{if(change<0)stop();},main).build();
        tts=new TextToSpeech(context,code->main.post(()->{
            if(closed)return;
            ready=code==TextToSpeech.SUCCESS;failed=!ready;
            if(ready){
                tts.setAudioAttributes(attributes);
                // Selecting the locale also lets the engine prepare its voice before the reply arrives.
                int language=tts.setLanguage(Ui.locale());
                if(language==TextToSpeech.LANG_MISSING_DATA||language==TextToSpeech.LANG_NOT_SUPPORTED){
                    ready=false;failed=true;
                    if(!pending.isEmpty()){pending="";status.unavailable();}
                    return;
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    public void onStart(String id){main.post(()->{if(!closed&&id.equals(utterance)){AppLog.w("Assist speech output started");status.speaking(true);}});}
                    public void onDone(String id){main.post(()->done(id,false));}
                    public void onError(String id){main.post(()->done(id,true));}
                });
                if(!pending.isEmpty()){String text=pending;pending="";speak(text);}
            }else if(!pending.isEmpty()){pending="";status.unavailable();}
        }));
    }
    void speak(String text){
        if(closed||text==null||text.isEmpty())return;
        stop();
        if(failed){status.unavailable();return;}
        if(!ready){pending=text;return;}
        Locale language=Ui.locale();int available=tts.setLanguage(language);
        if(available==TextToSpeech.LANG_MISSING_DATA||available==TextToSpeech.LANG_NOT_SUPPORTED){status.unavailable();return;}
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){status.unavailable();return;}
        focused=true;utterance="assist-"+(++generation);
        if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,new Bundle(),utterance)==TextToSpeech.ERROR)done(utterance,true);
    }
    private void done(String id,boolean error){if(closed||!id.equals(utterance))return;AppLog.w("Assist speech output finished error="+error);utterance="";abandon();status.speaking(false);if(error)status.unavailable();}
    void stop(){pending="";utterance="";if(ready)tts.stop();abandon();if(!closed)status.speaking(false);}
    private void abandon(){if(focused){focused=false;audio.abandonAudioFocusRequest(focus);}}
    void close(){stop();closed=true;tts.shutdown();main.removeCallbacksAndMessages(null);}
}
