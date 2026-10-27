package io.freemytv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.*;
import android.os.SystemClock;
import android.widget.*;

/** System overlay: critical cards receive focus directly; normal persistent notices have an acknowledgement button. */
final class AndroidBanner {
    private final Context context;
    private final WindowManager windows;
    private final Runnable acknowledge;
    private LinearLayout view;
    private Runnable motion;
    AndroidBanner(Context context,Runnable acknowledge) { this.context=context; this.acknowledge=acknowledge; windows=context.getSystemService(WindowManager.class); }
    void show(AndroidNotice notice) {
        hideImmediately();
        float k=context.getResources().getDisplayMetrics().widthPixels/1920f;
        LinearLayout root=new LinearLayout(context); view=root;
        root.setOrientation(LinearLayout.HORIZONTAL); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(Math.round(30*k),Math.round(22*k),Math.round(30*k),Math.round(22*k));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            notice.critical()?new int[]{0xF2C83248,0xF270142F}:new int[]{0xF22AA8E8,0xF215438B});
        background.setCornerRadius(26*k); root.setBackground(background); root.setElevation(18*k);
        ImageView icon=new ImageView(context); icon.setImageResource(R.drawable.android_icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(Color.WHITE,android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(Math.round(48*k),Math.round(48*k)); iconParams.rightMargin=Math.round(22*k); root.addView(icon,iconParams);
        LinearLayout column=new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        TextView message=new TextView(context); message.setText(notice.message); message.setTextColor(Color.WHITE);
        message.setTextSize(TypedValue.COMPLEX_UNIT_PX,26*k); message.setMaxLines(8); column.addView(message);
        if(notice.critical()) {
            root.setFocusable(true); root.setFocusableInTouchMode(true);
            root.setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
            root.setContentDescription(notice.message);
            root.setOnClickListener(v->{ if(root==view) acknowledge.run(); });
            boolean[] pressed={false};
            root.setOnFocusChangeListener((v,focused)->{ if(!focused) pressed[0]=false; });
            root.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_BACK) return true;
                if(key!=KeyEvent.KEYCODE_DPAD_CENTER && key!=KeyEvent.KEYCODE_ENTER && key!=KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) pressed[0]=true;
                else if(event.getAction()==KeyEvent.ACTION_UP) {
                    boolean confirm=pressed[0] && !event.isCanceled(); pressed[0]=false;
                    if(confirm && root==view) root.performClick();
                }
                return true;
            });
        } else if(notice.persistent) {
            Button understood=new Button(context); understood.setText(Ui.tr("Understood")); understood.setTextColor(Color.WHITE);
            understood.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*k); understood.setAllCaps(false);
            understood.setPadding(Math.round(20*k),0,Math.round(20*k),0);
            understood.setMinHeight(0); understood.setMinimumHeight(0); understood.setIncludeFontPadding(false);
            understood.setGravity(Gravity.CENTER); understood.setFocusable(true); understood.setFocusableInTouchMode(true);
            GradientDrawable focus=new GradientDrawable(); focus.setColor(0x33FFFFFF); focus.setCornerRadius(12*k); understood.setBackground(focus);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,Math.round(48*k)); params.topMargin=Math.round(14*k); column.addView(understood,params);
            understood.setOnClickListener(v->acknowledge.run());
            understood.setOnKeyListener((v,key,event)->key==KeyEvent.KEYCODE_BACK);
            root.setOnClickListener(v->acknowledge.run());
        }
        int flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if(!notice.persistent && !notice.critical()) flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(Math.round(Math.min(1000*k,context.getResources().getDisplayMetrics().widthPixels-48*k)),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        int top=Math.round(64*k);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; params.y=-getScreenHeight(); params.setTitle("Android TV");
        // Be transparent before attaching, so the first frame cannot flash at full opacity.
        root.setAlpha(0);
        windows.addView(root,params);
        // TV animator_duration_scale may be zero. Drive this explicit UI effect using frame callbacks instead.
        motion=new Runnable() {
            long started;
            int from;
            public void run() {
                if(motion!=this || root!=view || !root.isAttachedToWindow()) return;
                if(!root.isLaidOut()) { root.postOnAnimation(this);return; }
                long now=SystemClock.uptimeMillis();
                if(started==0) { started=now;from=-root.getHeight()-Math.round(24*k); }
                float t=Math.min(1,(now-started)/550f),ease=1-(1-t)*(1-t)*(1-t);
                root.setAlpha(t*t*(3-2*t));params.y=Math.round(from+(top-from)*ease);
                try { windows.updateViewLayout(root,params); } catch(IllegalArgumentException removed) { return; }
                if(t<1) root.postOnAnimation(this);else motion=null;
            }
        };
        root.postOnAnimation(motion);
        if(notice.critical()) root.post(()->{ if(root==view) root.requestFocus(); });
        else if(notice.persistent) root.post(()->{ if(root==view && column.getChildCount()>1) column.getChildAt(1).requestFocus(); });
        root.announceForAccessibility(notice.message);
    }
    void hide() {
        LinearLayout old=view; view=null;
        if(old==null) return;
        cancelMotion(old);
        retiring=old;
        WindowManager.LayoutParams params=(WindowManager.LayoutParams)old.getLayoutParams();
        int from=params.y,to=-old.getHeight()-24;float alpha=old.getAlpha();
        motion=new Runnable() {
            final long started=SystemClock.uptimeMillis();
            public void run() {
                if(motion!=this || !old.isAttachedToWindow()) return;
                float t=Math.min(1,(SystemClock.uptimeMillis()-started)/300f),ease=t*t;
                old.setAlpha(alpha*(1-t));params.y=Math.round(from+(to-from)*ease);
                try { windows.updateViewLayout(old,params); } catch(IllegalArgumentException removed) { return; }
                if(t<1) old.postOnAnimation(this);else { motion=null;remove(old);if(retiring==old) retiring=null; }
            }
        };
        old.postOnAnimation(motion);
    }
    private int getScreenHeight() { return context.getResources().getDisplayMetrics().heightPixels; }
    private LinearLayout retiring;
    private void cancelMotion(LinearLayout root) { if(motion!=null) root.removeCallbacks(motion);motion=null; }
    void hideImmediately() {
        LinearLayout old=view;view=null;
        if(old!=null) { cancelMotion(old);remove(old); }
        if(retiring!=null) { cancelMotion(retiring);remove(retiring);retiring=null; }
    }
    private void remove(View old) { try { windows.removeViewImmediate(old); } catch(IllegalArgumentException ignored) {} }
}
