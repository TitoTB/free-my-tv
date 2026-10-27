package io.freemytv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.*;
import java.io.File;

/** Per-TV credentials and explicit opt-in. Runs in the bridge process so identity preferences have one owner. */
public class HandroidSettingsActivity extends Activity {
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView connection;
    private final Runnable refresh=new Runnable() {
        public void run() {
            if(connection!=null) connection.setText(Ui.tr("Connection")+": "+Ui.tr(HandroidService.connected()?"Connected":HandroidService.enabled(HandroidSettingsActivity.this)?"Waiting for MQTT":"Disabled"));
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);Ui.init(this);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);render();
    }

    @Override public void onResume() { super.onResume();handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh);super.onPause(); }
    private void render() {
        float k=getResources().getDisplayMetrics().widthPixels/1920f;
        LinearLayout column=new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding((int)(100*k),(int)(70*k),(int)(100*k),(int)(50*k));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF192431,0xFF080C14});
        column.setBackground(background);
        ScrollView scroll=new ScrollView(this); scroll.addView(column); setContentView(scroll);
        text(column,"HAndroid TV",44*k);
        text(column,Ui.tr("Set a device name and connect to the same MQTT broker as Home Assistant. Discovery is automatic."),26*k);
        HandroidMqttSettings settings=new HandroidMqttSettings(this);
        EditText name=field(column,"Device name",settings.name,k,false,false);
        EditText host=field(column,"MQTT broker",settings.host,k,false,false);
        EditText port=field(column,"Port",Integer.toString(settings.port),k,false,true);
        EditText user=field(column,"MQTT username",settings.user,k,false,false);
        EditText password=field(column,"MQTT password",settings.password,k,true,false);
        CheckBox tls=new CheckBox(this);tls.setText(Ui.tr("Use TLS (normally port 8883)"));tls.setTextColor(Color.WHITE);tls.setChecked(settings.tls);column.addView(tls);
        text(column,"ID: "+HandroidService.identity(this),22*k);
        connection=text(column,"",24*k);
        text(column,Ui.tr("Display over other apps")+": "+Ui.tr(Settings.canDrawOverlays(this)?"Allowed":"Permission required"),24*k);
        button(column,Ui.tr("Save and connect"),k,()->{
            if(!Settings.canDrawOverlays(this)) { toast("Allow display over other apps first.");return; }
            try {
                HandroidMqttSettings.save(this,host.getText().toString(),port.getText().toString(),user.getText().toString(),password.getText().toString(),name.getText().toString(),tls.isChecked());
                File marker=new File(getFilesDir(),"handroid-enabled");
                if(!marker.isFile() && !marker.createNewFile()) throw new java.io.IOException();
                stopService(new Intent(this,HandroidService.class));HandroidService.start(this);
                toast("MQTT settings saved. Home Assistant will discover this TV when connected.");
            } catch(IllegalArgumentException invalid) { toast("Check the broker, port and device name."); }
            catch(java.io.IOException failure) { toast("Couldn't save the connection setting."); }
        });
        button(column,Ui.tr("Display over other apps"),k,()->{
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); }
            catch(ActivityNotFoundException unavailable) { Toast.makeText(this,Ui.tr("Open the TV's app permissions to allow display over other apps."),Toast.LENGTH_LONG).show(); }
        });
        button(column,Ui.tr("Disable HAndroid TV"),k,()->{
            File marker=new File(getFilesDir(),"handroid-enabled");
            if(marker.exists() && !marker.delete()) { toast("Couldn't save the connection setting.");return; }
            stopService(new Intent(this,HandroidService.class));toast("Disabled");
        });
        text(column,Ui.tr("Messages received while the screen is off are discarded. No notification history is stored."),22*k);
    }
    private void toast(String message) { Toast.makeText(this,Ui.tr(message),Toast.LENGTH_LONG).show(); }
    private EditText field(LinearLayout parent,String label,String value,float k,boolean secret,boolean number) {
        text(parent,Ui.tr(label),22*k);
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(Color.WHITE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX,25*k);
        input.setInputType(number?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT|
            (secret?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        input.setText(value);input.setSaveEnabled(false);parent.addView(input,new LinearLayout.LayoutParams(-1,(int)(62*k)));return input;
    }
    private TextView text(LinearLayout parent,String value,float size) {
        TextView text=new TextView(this); text.setText(value); text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX,size); text.setPadding(0,0,0,18); parent.addView(text); return text;
    }
    private void button(LinearLayout parent,String label,float k,Runnable action) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX,25*k); button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding((int)(22*k),0,(int)(22*k),0);
        button.setOnClickListener(v->action.run());
        GradientDrawable off=new GradientDrawable(); off.setColor(0x22FFFFFF); off.setCornerRadius(14*k);
        GradientDrawable on=new GradientDrawable(); on.setColor(0xFF2775A9); on.setCornerRadius(14*k);
        android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},on); background.addState(new int[0],off); button.setBackground(background);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(66*k)); params.bottomMargin=(int)(12*k); parent.addView(button,params);
    }
}
