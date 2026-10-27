package io.freemytv;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/** Cached cinema hero text and button geometry in the launcher's 1920-wide coordinate space. */
final class CinemaHero {
    final RectF[] buttons={new RectF(),new RectF(),new RectF()};
    private final TextPaint title=new TextPaint(Paint.ANTI_ALIAS_FLAG), description=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG), meta=new Paint(Paint.ANTI_ALIAS_FLAG), button=new Paint(Paint.ANTI_ALIAS_FLAG), fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressNumbers=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path playIcon=new Path(),ticketIcon=new Path(),requestIcon=new Path(),checkIcon=new Path();
    // Colours sampled from the Jellyseerr server's public logo_full.svg.
    private final LinearGradient requestGradient=new LinearGradient(0,0,1,1,
            new int[]{0xFF4F65F5,0xFF763DCD,0xFFC395FC},null,Shader.TileMode.CLAMP);
    private final Matrix requestMatrix=new Matrix();
    private final RectF progressTrack=new RectF(), progressFill=new RectF();
    private StaticLayout titleLayout, descriptionLayout;
    private String caption="", info="", sessionText="", status="";
    private String watch="", sessions="", focusText="";
    private String currentNumber="", totalNumber="";
    private String requestText="";
    private boolean showRequest, requestEnabled, alreadyRequested;
    private float k=1;
    private int moviePosition, movieCount;
    private boolean hasTrailer, hasSessions;

    CinemaHero() {
        playIcon.moveTo(7,4); playIcon.lineTo(20,12); playIcon.lineTo(7,20); playIcon.close();
        ticketIcon.moveTo(3,4); ticketIcon.lineTo(21,4); ticketIcon.lineTo(21,9);
        ticketIcon.quadTo(16,12,21,15); ticketIcon.lineTo(21,20); ticketIcon.lineTo(3,20);
        ticketIcon.lineTo(3,15); ticketIcon.quadTo(8,12,3,9); ticketIcon.close();
        for(int y=6;y<=16;y+=5) { ticketIcon.moveTo(10,y); ticketIcon.lineTo(10,y+2); }
        requestIcon.moveTo(12,3); requestIcon.lineTo(12,15);
        requestIcon.moveTo(7,10); requestIcon.lineTo(12,15); requestIcon.lineTo(17,10);
        requestIcon.moveTo(4,17); requestIcon.lineTo(4,21); requestIcon.lineTo(20,21); requestIcon.lineTo(20,17);
        checkIcon.moveTo(5,12); checkIcon.lineTo(10,17); checkIcon.lineTo(20,7);
        icon.setStrokeWidth(2); icon.setStrokeCap(Paint.Cap.ROUND); icon.setStrokeJoin(Paint.Join.ROUND);
    }

    void update(Cinema.Movie m,float scale,int position,int total,String error) {
        k=scale;
        movieCount=m==null?0:Math.max(0,total);
        moviePosition=Math.max(0,Math.min(position,movieCount-1));
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0)); title.setTextSize(76*k); title.setColor(0xFFFFFFFF);
        description.setTypeface(android.graphics.Typeface.create("sans-serif",0)); description.setTextSize(27*k); description.setColor(0xE6FFFFFF);
        label.setTextSize(23*k); label.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0)); label.setColor(0xFFB7D6F9);
        meta.setTextSize(24*k); meta.setColor(0xDDFFFFFF);
        button.setTextSize(22*k); button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        watch=Ui.tr("Watch trailer"); sessions=Ui.tr("Check showtimes");
        hasTrailer=m!=null && !m.trailer.isEmpty(); hasSessions=m!=null && m.state==Cinema.READY && !Cinema.available(m).isEmpty();
        float trailerRight=80*k+button.measureText(watch)+76*k;
        buttons[0].set(80*k,659*k,trailerRight,719*k);
        float sessionsLeft=hasTrailer?trailerRight+20*k:80*k;
        float sessionLabelWidth=Math.max(button.measureText(sessions),Math.max(button.measureText(Ui.tr("Checking showtimes…")),button.measureText(Ui.tr("Retry showtimes"))));
        buttons[1].set(sessionsLeft,659*k,sessionsLeft+Math.max(294*k,sessionLabelWidth+76*k),719*k);
        if(!hasTrailer) buttons[0].setEmpty();
        showRequest=m!=null && Jellyseerr.configured();
        alreadyRequested=m!=null && m.requested;
        requestText=alreadyRequested?Ui.tr("Requested"):Ui.tr("Request");
        requestEnabled=Jellyseerr.canRequest(m);
        if(showRequest) buttons[2].set(buttons[1].right+20*k,659*k,
                buttons[1].right+20*k+Math.max(button.measureText(Ui.tr("Requested")),button.measureText(Ui.tr("Request")))+76*k,719*k);
        else buttons[2].setEmpty();
        if(showRequest) {
            requestMatrix.setScale(buttons[2].width(),buttons[2].height());
            requestMatrix.postTranslate(buttons[2].left,buttons[2].top);
            requestGradient.setLocalMatrix(requestMatrix);
        }
        progressNumbers.setTypeface(button.getTypeface()); progressNumbers.setTextSize(22*k);
        progressNumbers.setFontFeatureSettings("tnum");
        if(movieCount>0) {
            currentNumber=String.format(Ui.locale(),"%02d",moviePosition+1);
            totalNumber=String.format(Ui.locale(),"%02d",movieCount);
            float numberWidth=Math.max(progressNumbers.measureText(currentNumber),progressNumbers.measureText(totalNumber));
            float halfHeight=Math.max(2,3*k)/2;
            float cy=buttons[1].centerY();
            progressTrack.set(1450*k+numberWidth+18*k,cy-halfHeight,1840*k-numberWidth-18*k,cy+halfHeight);
            progressFill.set(progressTrack);
            progressFill.right=progressTrack.left+progressTrack.width()*(moviePosition+1f)/movieCount;
        } else {
            currentNumber=totalNumber=""; progressTrack.setEmpty(); progressFill.setEmpty();
        }
        caption=Ui.tr("Now in cinemas");
        if(m==null) {
            titleLayout=layout(Ui.tr("Cinema at home"),title,1100,1);
            descriptionLayout=layout(error.isEmpty()?Ui.tr("Loading your cinema's programme…"):error,description,1000,3);
            info=""; sessionText=""; status=""; return;
        }
        titleLayout=layout(m.title,title,1120,1); descriptionLayout=layout(m.overview,description,1040,2);
        info=m.year+(m.minutes>0?"   ·   "+m.minutes+" min":"")+(m.rating>0?"   ·   TMDB "+String.format(java.util.Locale.US,"%.1f",m.rating)+" / 10":"");
        sessionText=m.state==Cinema.LOADING||m.state==Cinema.UNKNOWN?Ui.tr("Checking showtimes…"):m.state==Cinema.ERROR?Ui.tr("Retry showtimes"):sessions;
        status=m.state==Cinema.EMPTY?Ui.tr("No upcoming showtimes at Ciudad de la Imagen.")
                :m.state==Cinema.ERROR?Ui.tr("Couldn't check showtimes. Try again."):"";
    }
    private StaticLayout layout(String s,TextPaint paint,int width,int lines) {
        return StaticLayout.Builder.obtain(s,0,s.length(),paint,Math.round(width*k)).setMaxLines(lines)
                .setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setLineSpacing(0,1.08f).build();
    }
    boolean enabled(Cinema.Movie m,int index) {
        return index==0?hasTrailer:index==2?requestEnabled:m!=null&&(hasSessions||m.state==Cinema.ERROR);
    }
    int firstButton() { return hasTrailer?0:1; }
    String buttonText(int index) { return index==0?watch:index==2?requestText:sessionText; }
    int lastButton() { return showRequest?2:1; }
    void draw(Canvas c,int focused) {
        c.drawText(caption,80*k,290*k,label);
        if(titleLayout!=null) { c.save(); c.translate(80*k,332*k); titleLayout.draw(c); c.restore(); }
        c.drawText(info,80*k,526*k,meta);
        if(descriptionLayout!=null) { c.save(); c.translate(80*k,554*k); descriptionLayout.draw(c); c.restore(); }
        if(hasTrailer) drawButton(c,0,watch,focused,true);
        if(!sessionText.isEmpty()) drawButton(c,1,sessionText,focused,hasSessions||sessionText.equals(Ui.tr("Retry showtimes")));
        if(showRequest) drawButton(c,2,requestText,focused,requestEnabled);
        meta.setTextSize(20*k); c.drawText(status,80*k,766*k,meta); meta.setTextSize(24*k);
        if(movieCount>0) {
            fill.setStyle(Paint.Style.FILL);
            float radius=progressTrack.height()/2;
            fill.setColor(0x33FFFFFF); c.drawRoundRect(progressTrack,radius,radius,fill);
            fill.setColor(0xFFFFFFFF); c.drawRoundRect(progressFill,radius,radius,fill);
            float baseline=buttons[1].centerY()-(progressNumbers.ascent()+progressNumbers.descent())/2;
            progressNumbers.setTextAlign(Paint.Align.LEFT); progressNumbers.setColor(0xF2FFFFFF);
            c.drawText(currentNumber,1450*k,baseline,progressNumbers);
            progressNumbers.setTextAlign(Paint.Align.RIGHT); progressNumbers.setColor(0xB3FFFFFF);
            c.drawText(totalNumber,1840*k,baseline,progressNumbers);
        }
    }
    private void drawButton(Canvas c,int index,String text,int focused,boolean enabled) {
        boolean selected=focused==index;
        RectF r=buttons[index];
        fill.setStyle(Paint.Style.FILL); fill.setShader(null);
        if(index==2 && selected) {
            fill.setColor(0xFFFFFFFF);
            fill.setShader(requestGradient); c.drawRoundRect(r,30*k,30*k,fill); fill.setShader(null);
            fill.setColor(enabled?0x55000000:0x99000000);
            c.drawRoundRect(r,30*k,30*k,fill);
        } else if(selected) {
            fill.setColor(0xFFFFFFFF);
            c.drawRoundRect(r,30*k,30*k,fill);
        } else if(index==0 && focused!=1 && focused!=2) {
            fill.setColor(0x1AFFFFFF);
            c.drawRoundRect(r,30*k,30*k,fill);
        }
        int color=index==2?(enabled?0xFFF5F5F7:0xFFA5A8B2)
                :selected?(enabled?0xFF17202C:0xFF727782):(enabled?0xFFF5F5F7:0xFF9299A3);
        button.setColor(color);
        float groupLeft=r.centerX()-(button.measureText(text)+32*k)/2;
        icon.setColor(color); icon.setStyle(index==0?Paint.Style.FILL:Paint.Style.STROKE);
        c.save(); c.translate(groupLeft,r.centerY()-12*k); c.scale(k,k);
        c.drawPath(index==0?playIcon:index==1?ticketIcon:alreadyRequested?checkIcon:requestIcon,icon);
        c.restore();
        c.drawText(text,groupLeft+32*k,r.centerY()-(button.ascent()+button.descent())/2,button);
    }
}
