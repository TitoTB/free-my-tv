package io.freemytv;

import android.graphics.*;
import android.text.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Editorial cards: programme heading and episode synopsis overlay the episode still. */
final class InterestPanel {
    static final float TOP=1500, HEIGHT=360;
    private static final float WIDTH=(1760-48)/3f;
    private final RectF[] cards={new RectF(),new RectF(),new RectF()};
    private final Spring[] focus={new Spring(.24f,.92f,0),new Spring(.24f,.92f,0),new Spring(.24f,.92f,0)};
    private final Bitmap[] images=new Bitmap[3];
    private final BitmapShader[] shaders=new BitmapShader[3];
    private final Matrix matrix=new Matrix();
    private final Paint heading=new Paint(Paint.ANTI_ALIAS_FLAG),image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint name=new TextPaint(Paint.ANTI_ALIAS_FLAG),body=new TextPaint(Paint.ANTI_ALIAS_FLAG),meta=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final StaticLayout[] descriptions=new StaticLayout[3];
    private final String[] names=new String[3],dates=new String[3],sources=new String[3];
    private Shader gradient;
    private float k;
    private int selected;
    private String title;
    void update(Interests model,float scale,int selected) {
        this.k=scale; this.selected=selected;
        title=Ui.tr("You might like");
        heading.setTypeface(Ui.medium); heading.setTextSize(30*k); heading.setColor(0xFFFFFFFF);
        name.setTypeface(Ui.semibold); name.setTextSize(40*k); name.setColor(0xFFFFFFFF);
        body.setTypeface(Ui.regular); body.setTextSize(20*k); body.setColor(0xFFE9E9ED);
        meta.setTypeface(Ui.medium); meta.setTextSize(18*k); meta.setColor(0xD9FFFFFF);
        gradient=new LinearGradient(0,TOP*k,0,(TOP+HEIGHT)*k,new int[]{0x18000000,0x40000000,0xEE08090D},new float[]{0,.28f,1},Shader.TileMode.CLAMP);
        SimpleDateFormat date=new SimpleDateFormat("dd/MM/yyyy",Ui.locale());
        for(int i=0;i<3;i++) {
            InterestData.Episode e=model.items.get(i);
            cards[i].set((80+i*(WIDTH+24))*k,TOP*k,(80+i*(WIDTH+24)+WIDTH)*k,(TOP+HEIGHT)*k);
            Bitmap b=model.image(e); if(images[i]!=b) { images[i]=b; shaders[i]=b==null?null:new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP); }
            names[i]=TextUtils.ellipsize(InterestData.NAMES[e.programme],name,(WIDTH-56)*k,TextUtils.TruncateAt.END).toString();
            dates[i]=e.ready()?date.format(new Date(e.date)):"";
            sources[i]=e.programme==2?"RTVE Play":"Atresplayer";
            String description=e.ready()?e.description:Ui.tr("Unavailable");
            description=Html.fromHtml(description.replace("\n","<br>"),Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+"," ").trim();
            if(description.isEmpty()) description=e.title;
            descriptions[i]=StaticLayout.Builder.obtain(description,0,description.length(),body,Math.max(1,Math.round((WIDTH-56)*k)))
                    .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setLineSpacing(3*k,1).build();
        }
    }
    boolean step(float dt,boolean active) {
        boolean moving=false;
        for(int i=0;i<3;i++) { focus[i].target=active && selected==i?1:0; moving|=focus[i].step(dt); }
        return moving;
    }
    void bounds(int i,RectF out) { out.set(cards[i]); }
    void draw(Canvas c) {
        if(title==null) return;
        c.drawText(title,80*k,(TOP-40)*k,heading);
        for(int i=0;i<3;i++) {
            RectF r=cards[i]; float f=Ui.clamp(focus[i].value);
            c.save(); c.scale(1+.025f*f,1+.025f*f,r.centerX(),r.centerY());
            fill.setShader(null); fill.setColor(0xFF25272D); c.drawRoundRect(r,24*k,24*k,fill);
            Bitmap b=images[i];
            if(b!=null) {
                float scale=Math.max(r.width()/b.getWidth(),r.height()/b.getHeight()); matrix.setScale(scale,scale);
                matrix.postTranslate(r.centerX()-b.getWidth()*scale/2,r.centerY()-b.getHeight()*scale/2);
                shaders[i].setLocalMatrix(matrix); image.setShader(shaders[i]); c.drawRoundRect(r,24*k,24*k,image); image.setShader(null);
            }
            fill.setShader(gradient); c.drawRoundRect(r,24*k,24*k,fill); fill.setShader(null);
            c.drawText(sources[i],r.left+28*k,r.top+37*k,meta);
            meta.setTextAlign(Paint.Align.RIGHT); c.drawText(dates[i],r.right-28*k,r.top+37*k,meta); meta.setTextAlign(Paint.Align.LEFT);
            c.drawText(names[i],r.left+28*k,r.bottom-133*k,name);
            c.save(); c.translate(r.left+28*k,r.bottom-105*k); descriptions[i].draw(c); c.restore();
            if(f>.001f) {
                fill.setStyle(Paint.Style.STROKE); fill.setStrokeWidth(3*k); fill.setColor(Ui.alpha(0xFFFFFFFF,f));
                c.drawRoundRect(r,24*k,24*k,fill); fill.setStyle(Paint.Style.FILL);
            }
            c.restore();
        }
    }
}
