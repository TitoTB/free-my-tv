package io.freemytv;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Runtime-only secret, encrypted with a non-exportable Android Keystore key. Never packaged in the APK. */
final class ElevenLabsCredentials {
    private static final String ALIAS="jarvis-elevenlabs-v1";
    static boolean configured(Context context){return !context.getSharedPreferences("jarvis-secret",Context.MODE_PRIVATE).getString("data","").isEmpty();}
    static void store(Context context,String value)throws Exception{
        if(value==null||!value.trim().matches("[A-Za-z0-9_-]{20,256}"))throw new GeneralSecurityException("Invalid key");
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        if(!keys.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.getKey(ALIAS,null));
        String encrypted=Base64.encodeToString(cipher.doFinal(value.trim().getBytes("UTF-8")),Base64.NO_WRAP);
        if(!context.getSharedPreferences("jarvis-secret",Context.MODE_PRIVATE).edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("data",encrypted).commit())throw new GeneralSecurityException("Key storage unavailable");
    }
    static String read(Context context){
        try{
            android.content.SharedPreferences prefs=context.getSharedPreferences("jarvis-secret",Context.MODE_PRIVATE);
            String encrypted=prefs.getString("data",""),iv=prefs.getString("iv","");if(encrypted.isEmpty()||iv.isEmpty())return "";
            KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);if(!keys.containsAlias(ALIAS))return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.getKey(ALIAS,null),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),"UTF-8");
        }catch(Exception unavailable){return "";}
    }
}
