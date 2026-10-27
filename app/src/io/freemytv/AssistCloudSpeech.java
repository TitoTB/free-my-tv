package io.freemytv;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.*;

/** ElevenLabs output only. Audio stays in memory and is discarded on exit or cancellation. */
final class AssistCloudSpeech {
    private final Context context;
    private final AssistSpeech.Status status;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AudioManager audio;
    private final AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioFocusRequest focus;
    private ElevenLabsSynthesis request;
    private Future<?> work;
    private MediaPlayer player;
    private Bytes source;
    private int generation;
    private boolean closed,focused;
    AssistCloudSpeech(Context context,AssistSpeech.Status status){
        this.context=context.getApplicationContext();this.status=status;audio=context.getSystemService(AudioManager.class);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(change->{if(change<0)stop();},main).build();
    }
    void speak(String text){
        if(closed||text==null||text.trim().isEmpty())return;
        stop();final int id=++generation;final String voice=AssistVoices.selected(context);
        ElevenLabsSynthesis call=new ElevenLabsSynthesis();request=call;
        work=io.submit(()->{
            byte[] bytes=null;try{bytes=call.synthesize(text,voice,ElevenLabsCredentials.read(context));}catch(Exception ignored){}
            final byte[] result=bytes;
            main.post(()->{
                if(closed||id!=generation||request!=call)return;
                request=null;work=null;
                if(result==null){status.unavailable();return;}
                play(id,result);
            });
        });
    }
    private void play(int id,byte[] bytes){
        try{
            MediaPlayer output=new MediaPlayer();player=output;source=new Bytes(bytes);
            output.setAudioAttributes(attributes);output.setDataSource(source);
            output.setOnPreparedListener(p->{
                if(closed||id!=generation||player!=p)return;
                if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){fail(id);return;}
                focused=true;
                try{p.start();status.speaking(true);}catch(RuntimeException unavailable){fail(id);}
            });
            output.setOnCompletionListener(p->{if(id==generation&&player==p)stop();});
            output.setOnErrorListener((p,what,extra)->{if(id==generation&&player==p)fail(id);return true;});
            output.prepareAsync();
        }catch(Exception unavailable){fail(id);}
    }
    private void fail(int id){if(closed||id!=generation)return;stop();status.unavailable();}
    void stop(){
        generation++;
        if(request!=null){request.cancel();request=null;}
        if(work!=null){work.cancel(true);work=null;}
        MediaPlayer old=player;player=null;if(old!=null){try{old.release();}catch(RuntimeException ignored){}}
        if(source!=null){source.close();source=null;}
        if(focused){focused=false;audio.abandonAudioFocusRequest(focus);}
        if(!closed)status.speaking(false);
    }
    void close(){stop();closed=true;io.shutdownNow();main.removeCallbacksAndMessages(null);}
    private static final class Bytes extends MediaDataSource {
        private byte[] data;
        Bytes(byte[] data){this.data=data;}
        public synchronized int readAt(long position,byte[] buffer,int offset,int size)throws IOException{
            if(data==null||position<0||offset<0||size<0||offset>buffer.length-size)throw new IOException("Invalid audio read");
            if(size==0)return 0;if(position>=data.length)return -1;
            int count=(int)Math.min(size,data.length-position);System.arraycopy(data,(int)position,buffer,offset,count);return count;
        }
        public synchronized long getSize()throws IOException{if(data==null)throw new IOException("Audio closed");return data.length;}
        public synchronized void close(){if(data!=null){Arrays.fill(data,(byte)0);data=null;}}
    }
}
