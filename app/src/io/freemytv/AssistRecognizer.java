package io.freemytv;

import android.content.*;
import android.content.pm.*;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import java.util.List;

/** Never delegate back to our own recognition service after becoming the system assistant. */
final class AssistRecognizer {
    static SpeechRecognizer create(Context context){
        PackageManager pm=context.getPackageManager();
        ComponentName current=ComponentName.unflattenFromString(value(Settings.Secure.getString(context.getContentResolver(),"voice_recognition_service")));
        if(usable(context,current))return SpeechRecognizer.createSpeechRecognizer(context,current);
        ComponentName google=new ComponentName("com.google.android.katniss","com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService");
        if(usable(context,google))return SpeechRecognizer.createSpeechRecognizer(context,google);
        List<ResolveInfo> services=pm.queryIntentServices(new Intent("android.speech.RecognitionService"),0);
        for(ResolveInfo info:services){ServiceInfo s=info.serviceInfo;if(s!=null){ComponentName candidate=new ComponentName(s.packageName,s.name);if(usable(context,candidate))return SpeechRecognizer.createSpeechRecognizer(context,candidate);}}
        throw new IllegalStateException("No external speech recognizer");
    }
    private static boolean usable(Context context,ComponentName component){
        if(component==null||component.getPackageName().equals(context.getPackageName()))return false;
        try{ServiceInfo info=context.getPackageManager().getServiceInfo(component,0);
            return AssistProviderPolicy.usable(context.getPackageName(),info.packageName,info.name,info.permission,info.enabled,info.applicationInfo.enabled,info.exported);
        }catch(PackageManager.NameNotFoundException absent){return false;}
    }
    private static String value(String text){return text==null?"":text;}
}
