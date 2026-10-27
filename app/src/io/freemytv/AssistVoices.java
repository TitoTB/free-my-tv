package io.freemytv;

import android.content.Context;

final class AssistVoices {
    static String selected(Context context){return AssistVoiceOptions.effective(context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).getString("voice",""),Config.ELEVENLABS_VOICE_ID);}
    static void select(Context context,String id){if(AssistVoiceOptions.allowed(id))context.getSharedPreferences("jarvis",Context.MODE_PRIVATE).edit().putString("voice",id).apply();}
}
