package io.freemytv;

import android.content.*;

/** Runtime credentials live only in Android private preferences; never in a community APK. */
final class AndroidMqttSettings {
    final String host,user,password,name; final int port; final boolean tls;
    AndroidMqttSettings(Context context) {
        SharedPreferences p=context.getSharedPreferences("android-mqtt",Context.MODE_PRIVATE);
        host=p.getString("host","");user=p.getString("user","");password=p.getString("password","");
        name=p.getString("name","Android TV");port=p.getInt("port",1883);tls=p.getBoolean("tls",false);
    }
    boolean configured() { return !host.isEmpty(); }
    String uri() { return (tls?"ssl://":"tcp://")+host+":"+port; }
    static void save(Context c,String host,String port,String user,String password,String name,boolean tls) {
        host=host.trim();name=name.trim();
        if(host.isEmpty() || host.length()>253 || !host.matches("[a-zA-Z0-9._-]+") || name.isEmpty() || name.length()>80 || user.length()>256 || password.length()>512)
            throw new IllegalArgumentException("Invalid MQTT settings");
        int number;try { number=Integer.parseInt(port.trim()); } catch(NumberFormatException e) { throw new IllegalArgumentException("Invalid port"); }
        if(number<1 || number>65535 || (user.isEmpty() && !password.isEmpty())) throw new IllegalArgumentException("Invalid MQTT settings");
        if(!c.getSharedPreferences("android-mqtt",Context.MODE_PRIVATE).edit().putString("host",host).putInt("port",number)
            .putString("user",user).putString("password",password).putString("name",name).putBoolean("tls",tls).commit()) throw new IllegalArgumentException("Storage unavailable");
    }
}
