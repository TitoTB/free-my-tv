package io.freemytv;

import android.app.Dialog;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.concurrent.*;

/** Jarvis settings: fetch usage only when opened; never generates paid speech. */
final class AssistSettingsDialog {
    private final Dialog dialog;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ElevenLabsUsage query=new ElevenLabsUsage();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private boolean dismissed;
    private AlertDialog keyDialog;
    AssistSettingsDialog(AssistActivity activity,float scale){
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(32,scale),px(28,scale),px(32,scale),px(32,scale));
        GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFC202A37,0xFC12171F});glass.setCornerRadius(px(28,scale));content.setBackground(glass);
        TextView title=label(activity,"Jarvis · ElevenLabs",28,scale);title.setTypeface(Ui.semibold);content.addView(title);
        TextView voiceTitle=label(activity,Ui.tr("Voice"),21,scale);
        LinearLayout.LayoutParams voiceTitleSize=new LinearLayout.LayoutParams(-1,-2);voiceTitleSize.topMargin=px(20,scale);content.addView(voiceTitle,voiceTitleSize);
        RadioGroup voices=new RadioGroup(activity);voices.setOrientation(LinearLayout.HORIZONTAL);
        String chosen=AssistVoices.selected(activity);
        for(int i=0;i<AssistVoiceOptions.count();i++){
            RadioButton option=new RadioButton(activity);option.setId(View.generateViewId());option.setTag(AssistVoiceOptions.id(i));option.setText(AssistVoiceOptions.name(i));
            option.setTypeface(Ui.medium);option.setTextColor(0xFFF5F6FA);option.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(21,scale));
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF8DD5EF));
            GradientDrawable resting=new GradientDrawable();resting.setColor(Color.TRANSPARENT);resting.setCornerRadius(px(12,scale));option.setBackground(resting);
            option.setOnFocusChangeListener((v,focused)->{GradientDrawable bg=new GradientDrawable();bg.setColor(focused?0xFF30485E:Color.TRANSPARENT);bg.setCornerRadius(px(12,scale));option.setBackground(bg);});
            voices.addView(option,new RadioGroup.LayoutParams(0,px(58,scale),1));
            if(chosen.equals(AssistVoiceOptions.id(i)))voices.check(option.getId());
        }
        voices.setOnCheckedChangeListener((group,id)->{View selected=group.findViewById(id);if(selected!=null&&selected.getTag() instanceof String)AssistVoices.select(activity,(String)selected.getTag());});
        content.addView(voices,new LinearLayout.LayoutParams(-1,-2));
        Button credentials=new Button(activity);credentials.setText(Ui.tr("ElevenLabs API key"));credentials.setAllCaps(false);credentials.setTextColor(0xFFF5F6FA);credentials.setTypeface(Ui.medium);credentials.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(20,scale));
        credentials.setPadding(px(12,scale),0,px(12,scale),0);credentials.setMinHeight(0);credentials.setMinimumHeight(0);credentials.setGravity(Gravity.CENTER);
        GradientDrawable keyBackground=new GradientDrawable();keyBackground.setColor(0xFF18232F);keyBackground.setCornerRadius(px(12,scale));credentials.setBackground(keyBackground);
        credentials.setOnFocusChangeListener((v,focused)->{GradientDrawable bg=new GradientDrawable();bg.setColor(focused?0xFF30485E:0xFF18232F);bg.setCornerRadius(px(12,scale));credentials.setBackground(bg);});
        credentials.setOnClickListener(v->keyInput(activity,scale));LinearLayout.LayoutParams credentialsSize=new LinearLayout.LayoutParams(-1,px(54,scale));credentialsSize.topMargin=px(10,scale);content.addView(credentials,credentialsSize);
        TextView percent=label(activity,Ui.tr("Credits remaining")+": …",27,scale);
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,-2);gap.topMargin=px(24,scale);content.addView(percent,gap);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF8DD5EF));bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams barSize=new LinearLayout.LayoutParams(-1,px(8,scale));barSize.topMargin=px(16,scale);content.addView(bar,barSize);
        TextView reset=label(activity,Ui.tr("Credit renewal")+": …",21,scale);reset.setTextColor(0xFFB3BFCE);
        LinearLayout.LayoutParams resetSize=new LinearLayout.LayoutParams(-1,-2);resetSize.topMargin=px(20,scale);content.addView(reset,resetSize);
        LinearLayout.LayoutParams aloudSize=new LinearLayout.LayoutParams(-1,px(56,scale));aloudSize.topMargin=px(16,scale);content.addView(activity.aloudControl(),aloudSize);
        LinearLayout.LayoutParams roleSize=new LinearLayout.LayoutParams(-1,px(60,scale));roleSize.topMargin=px(10,scale);content.addView(activity.roleControl(),roleSize);
        TextView disclosure=label(activity,Ui.tr("Google transcribes your voice. Assist can control exposed devices."),17,scale);disclosure.setTextColor(0xFFB3BFCE);
        LinearLayout.LayoutParams disclosureSize=new LinearLayout.LayoutParams(-1,-2);disclosureSize.topMargin=px(18,scale);content.addView(disclosure,disclosureSize);
        dialog.setContentView(content);
        dialog.setOnDismissListener(d->{dismissed=true;if(keyDialog!=null)keyDialog.dismiss();query.cancel();io.shutdownNow();main.removeCallbacksAndMessages(null);});
        dialog.show();Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount(.68f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setLayout(Math.min(px(650,scale),activity.getResources().getDisplayMetrics().widthPixels-px(80,scale)),-2);window.setGravity(Gravity.CENTER);
            window.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());}
        io.execute(()->{
            ElevenLabsUsage.Snapshot result=null;try{result=query.fetch(ElevenLabsCredentials.read(activity));}catch(Exception ignored){}
            final ElevenLabsUsage.Snapshot snapshot=result;
            main.post(()->{
                if(dismissed||!dialog.isShowing()||activity.isFinishing()||activity.isDestroyed())return;
                Double available=snapshot==null?null:snapshot.remainingPercent();
                String amount=Ui.tr("Unavailable");
                if(available!=null){NumberFormat number=NumberFormat.getPercentInstance(Ui.locale());number.setMaximumFractionDigits(1);amount=number.format(available/100d);bar.setProgress((int)Math.round(available*10));bar.setVisibility(View.VISIBLE);}
                percent.setText(Ui.tr("Credits remaining")+": "+amount);
                String renewal=snapshot==null||snapshot.resetMillis==null?Ui.tr("Unavailable"):
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Ui.locale()).format(new Date(snapshot.resetMillis));
                reset.setText(Ui.tr("Credit renewal")+": "+renewal);
            });
        });
    }
    void dismiss(){dialog.dismiss();}
    boolean isShowing(){return dialog.isShowing();}
    private void keyInput(AssistActivity activity,float scale){
        EditText input=new EditText(activity);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setSaveEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);input.setContentDescription(Ui.tr("ElevenLabs API key"));
        AlertDialog entry=new AlertDialog.Builder(activity).setTitle(Ui.tr("ElevenLabs API key")).setView(input).setPositiveButton(Ui.tr("Save"),null).setNegativeButton(Ui.tr("Cancel"),null).create();
        keyDialog=entry;
        entry.setOnDismissListener(d->input.setText(""));entry.show();if(entry.getWindow()!=null)entry.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        input.requestFocus();
        entry.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=input.getText().toString();input.setText("");entry.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            io.execute(()->{
                boolean success=false;try{ElevenLabsCredentials.store(activity,value);success=true;}catch(Exception ignored){}
                final boolean stored=success;
                main.post(()->{if(dismissed||activity.isFinishing()||activity.isDestroyed())return;
                    if(stored){entry.dismiss();activity.credentialsUpdated();}
                    else{entry.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);input.setError(Ui.tr("Unavailable"));}
                });
            });
        });
    }
    private static TextView label(AssistActivity activity,String text,int size,float scale){TextView view=new TextView(activity);view.setText(text);view.setTextColor(0xFFF5F6FA);view.setTypeface(Ui.regular);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size,scale));view.setSaveEnabled(false);return view;}
    private static int px(float value,float scale){return Math.max(1,Math.round(value*scale));}
}
