package io.freemytv;

/** Google's TV recognizer is exported without a service permission on some Katniss releases. */
final class AssistProviderPolicy {
    static boolean usable(String own,String pkg,String name,String permission,boolean enabled,boolean appEnabled,boolean exported){
        if(pkg==null||pkg.equals(own)||!enabled||!appEnabled||!exported)return false;
        if("android.permission.BIND_SPEECH_RECOGNITION_SERVICE".equals(permission))return true;
        return (permission==null||permission.isEmpty())&&"com.google.android.katniss".equals(pkg)
                &&"com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService".equals(name);
    }
}
