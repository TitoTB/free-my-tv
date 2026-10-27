package io.freemytv;

/** Only the four stock voices auditioned and chosen by the user. */
final class AssistVoiceOptions {
    static final String DEFAULT="XrExE9yKIg1WjnnlVkGX";
    static final String MODEL="eleven_multilingual_v2";
    private static final String[] NAMES={"Adam","Bill","Lily","Matilda"};
    private static final String[] IDS={"pNInz6obpgDQGcFmaJgB","pqHfZKP75CvOlQylNhV4","pFZP5JQG7iQjIQuC4Bku",DEFAULT};
    static int count(){return IDS.length;}
    static String name(int index){return NAMES[index];}
    static String id(int index){return IDS[index];}
    static boolean allowed(String id){for(String value:IDS)if(value.equals(id))return true;return false;}
    static String effective(String saved,String configured){return allowed(saved)?saved:allowed(configured)?configured:DEFAULT;}
}
