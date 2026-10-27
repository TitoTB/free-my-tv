package io.freemytv;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Compact remote-friendly tools, using the same dark rounded popup family as the home. */
final class ToolsDialog {
    private static final Paint ICON=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path WRENCH=new Path();
    static {
        WRENCH.moveTo(15,3);WRENCH.cubicTo(10,-1,4,3,6,9);
        WRENCH.lineTo(1,17);WRENCH.cubicTo(-1,21,3,25,6,21);
        WRENCH.lineTo(12,13);WRENCH.cubicTo(18,14,22,8,19,4);
        WRENCH.lineTo(16,8);WRENCH.lineTo(12,7);WRENCH.lineTo(11,4);WRENCH.close();
    }
    static void drawWrench(Canvas canvas,float cx,float cy,float size,int color) {
        canvas.save();canvas.translate(cx-size/2,cy-size/2);canvas.scale(size/24,size/24);
        ICON.setColor(color);ICON.setStyle(Paint.Style.STROKE);ICON.setStrokeWidth(1.6f);
        ICON.setStrokeJoin(Paint.Join.ROUND);ICON.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(WRENCH,ICON);
        canvas.restore();
    }

    private final HomeActivity activity;
    private final Dialog dialog;
    private final LinearLayout root;
    private final float scale;
    private View first;
    private ToolsDialog(HomeActivity activity,String title) {
        this.activity=activity;
        scale=Math.min(activity.getResources().getDisplayMetrics().widthPixels/1920f,activity.getResources().getDisplayMetrics().heightPixels/1080f);
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(px(32),px(25),px(32),px(27));
        root.setBackground(shape(0xFA171B23,0xFF414956,30));root.setClipToOutline(true);
        TextView heading=text(title,34,true);root.addView(heading,new LinearLayout.LayoutParams(-1,px(64)));
        dialog.setContentView(root);
    }

    static void show(HomeActivity activity) {
        ToolsDialog ui=new ToolsDialog(activity,Ui.tr("Tools"));
        ui.row(ui.root,"Assist",()->{ui.dialog.dismiss();AssistActivity.open(activity);},true);
        ui.row(ui.root,Ui.tr("Play Store"),()->{ui.dialog.dismiss();openStore(activity);});
        ui.show(720,0);
    }

    private static void openStore(HomeActivity activity) {
        android.content.pm.PackageManager pm=activity.getPackageManager();
        Intent intent=pm.getLeanbackLaunchIntentForPackage("com.android.vending");
        if(intent==null)intent=pm.getLaunchIntentForPackage("com.android.vending");
        if(intent==null)intent=new Intent(Intent.ACTION_MAIN).setPackage("com.android.vending").addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        try{activity.startActivity(intent);}
        catch(RuntimeException missing){Toast.makeText(activity,Ui.tr("Play Store is not available on this TV."),Toast.LENGTH_LONG).show();}
    }

    private TextView row(LinearLayout parent,String title,Runnable action) {
        return row(parent,title,action,false);
    }
    private TextView row(LinearLayout parent,String title,Runnable action,boolean microphone) {
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(px(20),0,px(20),0);
        row.setFocusable(true);row.setFocusableInTouchMode(true);row.setId(View.generateViewId());row.setContentDescription(title);
        row.setBackground(shape(0xFF242B35,0,18));
        View symbol=new View(activity){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(px(32)/24f,px(32)/24f);c.translate(-12,-12);
                paint.setColor(row.hasFocus()?0xFF102234:0xFFF5F6FA);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeJoin(Paint.Join.ROUND);
                if(microphone){
                    c.drawRoundRect(9,2,15,15,3,3,paint);c.drawArc(5,6,19,19,0,180,false,paint);
                    c.drawLine(12,19,12,23,paint);c.drawLine(8,23,16,23,paint);
                }else{
                    c.drawRoundRect(4,6,20,22,2,2,paint);c.drawArc(8,1,16,11,180,180,false,paint);
                    c.drawLine(10,11,15,14,paint);c.drawLine(15,14,10,17,paint);c.drawLine(10,17,10,11,paint);
                }
                c.restore();
            }
        };
        row.addView(symbol,new LinearLayout.LayoutParams(px(42),px(48)));
        TextView label=text(title,27,true);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelSize=new LinearLayout.LayoutParams(0,-2,1);labelSize.leftMargin=px(20);row.addView(label,labelSize);
        row.setOnFocusChangeListener((v,focused)->{
            row.setBackground(shape(focused?0xFFA5D7FF:0xFF242B35,0,18));label.setTextColor(focused?0xFF102234:0xFFF5F6FA);symbol.invalidate();
        });
        row.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,px(80));size.topMargin=px(10);parent.addView(row,size);
        if(first==null)first=row;
        return label;
    }
    private TextView text(String value,int size,boolean bold){
        TextView text=new TextView(activity);text.setText(value);text.setTextColor(0xFFF5F6FA);
        text.setTypeface(bold?Ui.semibold:Ui.regular);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));text.setGravity(Gravity.CENTER_VERTICAL);return text;
    }
    private GradientDrawable shape(int color,int border,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(px(radius));if(border!=0)shape.setStroke(px(1),border);return shape;
    }
    private void show(int width,int height){dialog.show();resize(width,height);if(first!=null)first.requestFocus();}
    private void resize(int width,int height){
        Window w=dialog.getWindow();if(w==null)return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(.68f);w.setLayout(px(width),height==0?WindowManager.LayoutParams.WRAP_CONTENT:px(height));w.setGravity(Gravity.CENTER);
        w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
    }
    private int px(float value){return Math.max(1,Math.round(value*scale));}
}
