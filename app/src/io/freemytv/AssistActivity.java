package io.freemytv;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.service.voice.VoiceInteractionService;
import android.speech.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.*;

/** Short-lived, remote-friendly voice panel. Words and conversation IDs exist only in this activity's memory. */
public class AssistActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AssistTurn turn=new AssistTurn();
    private SpeechRecognizer recognizer;
    private AssistConversation request;
    private AssistSpeech speech;
    private AssistCloudSpeech cloudSpeech;
    private AssistSettingsDialog settingsDialog;
    private String conversation="";
    private boolean resumed,automatic,closing,setupBusy;
    private float scale;
    private LinearLayout panel;
    private TextView status,transcript,response;
    private Button speak,role;
    private Button speaker;
    private AssistNebula nebula;
    private CheckBox aloud;
    private Runnable deadline,motion;
    static void open(Activity activity){activity.startActivity(new Intent(activity,AssistActivity.class).putExtra("listen",true));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);Ui.init(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDimAmount(.48f);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        scale=Math.min(getResources().getDisplayMetrics().widthPixels/1920f,getResources().getDisplayMetrics().heightPixels/1080f);
        FrameLayout root=new FrameLayout(this);setContentView(root);
        panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(px(34),px(28),px(34),px(28));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFA202A37,0xFC101923});background.setCornerRadius(px(34));background.setStroke(px(1),0x403BA5CE);panel.setBackground(background);panel.setClipToOutline(true);
        FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(Math.min(px(980),getResources().getDisplayMetrics().widthPixels-px(80)),px(790),Gravity.TOP|Gravity.CENTER_HORIZONTAL);bounds.topMargin=px(64);root.addView(panel,bounds);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);panel.addView(heading,new LinearLayout.LayoutParams(-1,px(56)));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.handroid_icon);icon.setColorFilter(Color.WHITE);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);heading.addView(icon,new LinearLayout.LayoutParams(px(40),px(40)));
        TextView title=text("Jarvis",32,true);LinearLayout.LayoutParams titleSize=new LinearLayout.LayoutParams(0,-2,1);titleSize.leftMargin=px(18);heading.addView(title,titleSize);
        speaker=iconButton("speaker",Ui.tr("Read responses aloud"));heading.addView(speaker,new LinearLayout.LayoutParams(px(56),px(56)));
        speaker.setOnClickListener(v->aloud.setChecked(!aloud.isChecked()));
        Button settings=iconButton("settings",Ui.tr("Settings"));
        settings.setOnClickListener(v->{automatic=false;cancelTurn();if(settingsDialog==null||!settingsDialog.isShowing())settingsDialog=new AssistSettingsDialog(this,scale);});
        LinearLayout.LayoutParams settingsSize=new LinearLayout.LayoutParams(px(52),px(52));settingsSize.leftMargin=px(18);heading.addView(settings,settingsSize);
        transcript=text("",27,false);transcript.setTextColor(0xFFA5C5DE);transcript.setGravity(Gravity.CENTER);transcript.setMaxLines(2);transcript.setEllipsize(TextUtils.TruncateAt.END);transcript.setSaveEnabled(false);
        LinearLayout.LayoutParams transcriptSize=new LinearLayout.LayoutParams(-1,px(74));transcriptSize.topMargin=px(45);panel.addView(transcript,transcriptSize);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setSaveEnabled(false);
        response=text("",42,false);response.setGravity(Gravity.CENTER);response.setSaveEnabled(false);response.setTextIsSelectable(false);scroll.addView(response);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,px(140)));
        nebula=new AssistNebula(this);panel.addView(nebula,new LinearLayout.LayoutParams(-1,px(205)));
        status=text(Ui.tr("Speak"),21,false);status.setGravity(Gravity.CENTER);status.setMaxLines(2);status.setTextColor(0xFFA5C5DE);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(status,new LinearLayout.LayoutParams(-1,px(56)));
        panel.addView(new Space(this),new LinearLayout.LayoutParams(-1,0,1));
        aloud=new CheckBox(this);aloud.setText(Ui.tr("Read responses aloud"));aloud.setTextColor(0xFFE0E7F0);aloud.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(21));
        aloud.setChecked(getPreferences(MODE_PRIVATE).getBoolean("aloud",true));aloud.setSaveEnabled(false);
        aloud.setOnCheckedChangeListener((button,checked)->{getPreferences(MODE_PRIVATE).edit().putBoolean("aloud",checked).apply();speaker.invalidate();if(checked)prepareSpeech();else{if(speech!=null)speech.stop();if(cloudSpeech!=null)cloudSpeech.stop();}});
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);panel.addView(actions,new LinearLayout.LayoutParams(-1,px(60)));
        speak=iconButton("microphone",Ui.tr("Speak"));actions.addView(speak,new LinearLayout.LayoutParams(px(60),px(60)));speak.setOnClickListener(v->{if(turn.stage()==AssistTurn.Stage.LISTENING){cancelTurn();status.setText(Ui.tr("Speak"));}else listen();});
        Button close=iconButton("close",Ui.tr("Close"));LinearLayout.LayoutParams closeSize=new LinearLayout.LayoutParams(px(60),px(60));closeSize.leftMargin=px(16);actions.addView(close,closeSize);close.setOnClickListener(v->onBackPressed());
        automatic=Intent.ACTION_ASSIST.equals(getIntent().getAction())||getIntent().getBooleanExtra("listen",false);
        speak.requestFocus();animateIn();
        if(aloud.isChecked())prepareSpeech();
    }
    @Override protected void onResume(){super.onResume();resumed=true;nebula.running(true);startAutomatic();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);automatic=Intent.ACTION_ASSIST.equals(intent.getAction())||intent.getBooleanExtra("listen",false);startAutomatic();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(nebula!=null)nebula.running(focused&&resumed);if(focused)startAutomatic();else if(resumed){cancelTurn();}}
    private void startAutomatic(){if(automatic&&resumed&&hasWindowFocus()&&!closing&&(settingsDialog==null||!settingsDialog.isShowing())){automatic=false;main.postDelayed(()->{if(resumed&&hasWindowFocus()&&!closing)listen();},350);}}
    private void listen(){
        if(!resumed||closing||setupBusy||settingsDialog!=null&&settingsDialog.isShowing())return;
        cancelTurn();response.setText("");transcript.setText("");
        if(!HomeClimate.enabled()){status.setText(Ui.tr("Configure Home Assistant first."));return;}
        if(checkSelfPermission("android.permission.RECORD_AUDIO")!=PackageManager.PERMISSION_GRANTED){status.setText(Ui.tr("Microphone permission required."));requestPermissions(new String[]{"android.permission.RECORD_AUDIO"},81);return;}
        final long id=turn.begin();status.setText(Ui.tr("Listening…"));speak.setContentDescription(Ui.tr("Cancel"));speak.invalidate();if(role!=null)role.setEnabled(false);
        try{
            recognizer=AssistRecognizer.create(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){if(active(id)){AppLog.w("Assist recognition ready");status.setText(Ui.tr("Listening…"));}}
                public void onBeginningOfSpeech(){}
                public void onRmsChanged(float rms){if(active(id))nebula.level((rms+2)/12);}
                public void onBufferReceived(byte[] bytes){}
                public void onEndOfSpeech(){if(active(id))status.setText(Ui.tr("Processing…"));}
                public void onError(int error){if(!active(id)||!turn.recognitionFailed(id))return;AppLog.w("Assist recognition error="+error);stopCapture();idleButtons();status.setText(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?Ui.tr("Microphone permission required."):Ui.tr("No speech detected. Try again."));}
                public void onResults(Bundle b){if(!active(id))return;ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String phrase=words==null||words.isEmpty()?"":words.get(0);if(phrase==null||phrase.trim().isEmpty()){onError(SpeechRecognizer.ERROR_NO_MATCH);return;}send(id,phrase);}
                public void onPartialResults(Bundle b){if(!active(id)||turn.stage()!=AssistTurn.Stage.LISTENING)return;ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(words!=null&&!words.isEmpty())transcript.setText(words.get(0));}
                public void onEvent(int type,Bundle b){}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE,Ui.locale().toLanguageTag()).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            recognizer.startListening(intent);
            deadline=()->{if(active(id)&&turn.recognitionFailed(id)){stopCapture();idleButtons();status.setText(Ui.tr("No speech detected. Try again."));}};main.postDelayed(deadline,20000);
        }catch(RuntimeException unavailable){AppLog.w("Assist recognizer creation failed="+unavailable.getClass().getSimpleName());turn.recognitionFailed(id);stopCapture();idleButtons();status.setText(Ui.tr("Speech recognition is unavailable."));}
    }
    private void send(long id,String phrase){
        if(!turn.submit(id))return;AppLog.w("Assist recognition result accepted");stopCapture();transcript.setText(phrase);status.setText(Ui.tr("Processing…"));speak.setEnabled(false);
        AssistConversation call=new AssistConversation(Config.HOME_ASSISTANT_URL,Config.HOME_ASSISTANT_TOKEN);request=call;
        String previous=conversation,language=Ui.locale().toLanguageTag();
        io.execute(()->{
            AssistConversation.Reply answer=null;try{answer=call.ask(phrase,language,previous);}catch(Exception ignored){}
            final AssistConversation.Reply reply=answer;
            main.post(()->{if(!active(id)||!turn.finish(id))return;request=null;idleButtons();
                if(reply==null){status.setText(Ui.tr("Couldn't contact Home Assistant."));return;}
                conversation=reply.conversation;status.setText("");response.setText(reply.text);
                AppLog.w("Assist response received error="+reply.error);
                if(aloud.isChecked())speakAnswer(reply.text);
            });
        });
    }
    /** Warm the output engine without audio focus, playback or microphone access. */
    private boolean prepareSpeech(){
        try{
        AssistSpeech.Status listener=new AssistSpeech.Status(){
            public void speaking(boolean value){if(resumed&&!closing&&turn.stage()==AssistTurn.Stage.REPLY){status.setText(value?Ui.tr("Speaking…"):"");nebula.level(value?.65f:0);}}
            public void unavailable(){if(resumed&&!closing)status.setText(Ui.tr("Speech output is unavailable."));}
        };
        if(ElevenLabsCredentials.configured(this)){if(cloudSpeech==null)cloudSpeech=new AssistCloudSpeech(this,listener);}
        else if(speech==null)speech=new AssistSpeech(this,listener);
        return true;
        }catch(RuntimeException unavailable){return false;}
    }
    private void speakAnswer(String text){
        if(!prepareSpeech()){status.setText(Ui.tr("Speech output is unavailable."));return;}
        try{if(cloudSpeech!=null)cloudSpeech.speak(text);else speech.speak(text);}catch(RuntimeException unavailable){status.setText(Ui.tr("Speech output is unavailable."));}
    }
    private boolean active(long id){return resumed&&!closing&&turn.active(id);}
    private void stopCapture(){if(deadline!=null){main.removeCallbacks(deadline);deadline=null;}if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}}
    private void cancelTurn(){turn.cancel();stopCapture();if(request!=null){request.cancel();request=null;}if(speech!=null)speech.stop();if(cloudSpeech!=null)cloudSpeech.stop();idleButtons();}
    private void idleButtons(){if(speak!=null){speak.setEnabled(!setupBusy);speak.setContentDescription(Ui.tr("Speak"));speak.invalidate();}if(role!=null)refreshRole();}
    CheckBox aloudControl(){if(aloud.getParent() instanceof ViewGroup)((ViewGroup)aloud.getParent()).removeView(aloud);return aloud;}
    void credentialsUpdated(){
        cancelTurn();if(speech!=null){speech.close();speech=null;}if(cloudSpeech!=null){cloudSpeech.close();cloudSpeech=null;}
        if(aloud.isChecked())prepareSpeech();
        if(settingsDialog!=null)settingsDialog.dismiss();
        if(resumed&&!closing)settingsDialog=new AssistSettingsDialog(this,scale);
    }
    Button roleControl(){role=button("");role.setOnClickListener(v->configureRole());refreshRole();return role;}
    private boolean selected(){return VoiceInteractionService.isActiveService(this,new ComponentName(this,AssistVoiceService.class));}
    private void refreshRole(){boolean active=selected();role.setText(active?Ui.tr("Restore previous assistant"):Ui.tr("Use Assist as default"));role.setEnabled(Build.VERSION.SDK_INT>=29&&!setupBusy&&(active||HomeClimate.enabled()&&!Config.DISABLE_ASSISTANT));}
    private void configureRole(){
        if(setupBusy)return;cancelTurn();boolean restore=selected();setupBusy=true;idleButtons();status.setText(Ui.tr("Processing…"));
        io.execute(()->{boolean ok=false;try{if(restore)AssistRoleManager.restore(this);else AssistRoleManager.enable(this);ok=true;}catch(Exception ignored){}
            final boolean success=ok;main.post(()->{if(isFinishing()||isDestroyed())return;setupBusy=false;idleButtons();refreshRole();status.setText(success?restore?Ui.tr("Previous assistant restored."):Ui.tr("Voice button enabled."):Ui.tr("Voice button setup failed."));});});
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==81){automatic=grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED;if(automatic)startAutomatic();else status.setText(Ui.tr("Microphone permission required."));}}
    @Override protected void onPause(){resumed=false;automatic=false;nebula.running(false);if(settingsDialog!=null)settingsDialog.dismiss();cancelTurn();conversation="";super.onPause();}
    @Override protected void onDestroy(){closing=true;cancelTurn();if(speech!=null)speech.close();if(cloudSpeech!=null)cloudSpeech.close();if(motion!=null)panel.removeCallbacks(motion);main.removeCallbacksAndMessages(null);io.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){closing=true;automatic=false;cancelTurn();finish();}
    private void animateIn(){
        panel.setAlpha(0);panel.setTranslationY(-px(90));
        motion=new Runnable(){long start;public void run(){if(!panel.isAttachedToWindow()||closing)return;long now=SystemClock.uptimeMillis();if(start==0)start=now;float t=Math.min(1,(now-start)/450f),ease=1-(1-t)*(1-t)*(1-t);panel.setAlpha(t*t*(3-2*t));panel.setTranslationY(-px(90)*(1-ease));if(t<1)panel.postOnAnimation(this);else motion=null;}};
        panel.postOnAnimation(motion);
    }
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(0xFFF5F6FA);t.setTypeface(bold?Ui.semibold:Ui.regular);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));t.setSaveEnabled(false);return t;}
    private void space(View view,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(top);panel.addView(view,p);}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(22));b.setTypeface(Ui.medium);b.setPadding(px(12),0,px(12),0);b.setBackground(shape(false));b.setOnFocusChangeListener((v,focused)->b.setBackground(shape(focused)));return b;}
    private Button iconButton(String type,String label){
        Button b=new Button(this){
            final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);c.save();float size=px(28);c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
                ink.setColor(isEnabled()?0xFFEAF0F7:0x66899AAA);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.7f);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);
                if(type.equals("close")){c.drawLine(5,5,19,19,ink);c.drawLine(19,5,5,19,ink);}
                else if(type.equals("microphone")){if(turn.stage()==AssistTurn.Stage.LISTENING)c.drawRoundRect(6,6,18,18,2,2,ink);else{c.drawRoundRect(9,2,15,15,3,3,ink);c.drawArc(5,6,19,19,0,180,false,ink);c.drawLine(12,19,12,23,ink);c.drawLine(8,23,16,23,ink);}}
                else if(type.equals("speaker")){path.reset();path.moveTo(3,9);path.lineTo(7,9);path.lineTo(12,4);path.lineTo(12,20);path.lineTo(7,15);path.lineTo(3,15);path.close();c.drawPath(path,ink);if(aloud==null||aloud.isChecked()){c.drawArc(10,6,20,18,-70,140,false,ink);c.drawArc(10,2,24,22,-65,130,false,ink);}else{c.drawLine(17,8,22,16,ink);c.drawLine(22,8,17,16,ink);}}
                else{path.reset();for(int i=0;i<64;i++){double a=i*Math.PI/32;float r=i%8>=2&&i%8<=5?10:8;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();c.drawPath(path,ink);c.drawCircle(12,12,3.2f,ink);}
                c.restore();
            }
        };
        b.setText("");b.setContentDescription(label);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setBackground(iconShape(false));b.setOnFocusChangeListener((v,focused)->b.setBackground(iconShape(focused)));return b;
    }
    private GradientDrawable iconShape(boolean focused){GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(px(30));bg.setColor(focused?0xFF345469:0x223A5063);return bg;}
    private GradientDrawable shape(boolean focused){GradientDrawable d=new GradientDrawable();d.setCornerRadius(px(16));d.setColor(focused?0xFF356787:0x332F465F);return d;}
    private int px(float n){return Math.max(1,Math.round(n*scale));}
}
