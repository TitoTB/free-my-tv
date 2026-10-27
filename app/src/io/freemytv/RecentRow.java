package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

/** Google TV-style landscape cards limited to one complete screen row. */
final class RecentRow {
    static final int VISIBLE=5;
    static final float ROW_GAP=380, CARD_WIDTH=332, CARD_HEIGHT=186.75f, CARD_STEP=357;
    static final float NAME_Y=1120+CARD_HEIGHT+45, DETAILS_Y=NAME_Y+32;
    private final RectF[] cards=new RectF[VISIBLE];
    private final String[] names=new String[VISIBLE],details=new String[VISIBLE];
    private final Bitmap[] bitmaps=new Bitmap[VISIBLE];
    private final BitmapShader[] shaders=new BitmapShader[VISIBLE];
    private final Matrix[] matrices=new Matrix[VISIBLE];
    private final Spring[] focus=new Spring[VISIBLE];
    private final Paint heading=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nameMeasure=new TextPaint(),detailMeasure=new TextPaint();
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF visual=new RectF(),outline=new RectF(),track=new RectF();
    private float k;
    private int first,count,selected;
    private String title="",empty="";
    private JellyfinRecent library;
    private final float offset;

    RecentRow() { this(0); }
    RecentRow(float offset) {
        this.offset=offset;
        for(int i=0;i<VISIBLE;i++) { cards[i]=new RectF(); matrices[i]=new Matrix(); focus[i]=new Spring(0.24f,0.92f,0); }
    }
    int first() { return first; }
    static int visibleCount(int total) { return Math.max(0,Math.min(VISIBLE,total)); }
    void update(JellyfinRecent library,float scale,int selected) {
        this.library=library; this.k=scale; this.selected=selected;
        first=0; count=visibleCount(library.items.size());
        title=library.resumeMode?Ui.tr("Continue watching"):Ui.tr("Recently added");
        empty=!library.error.isEmpty()?library.error:library.fetching?Ui.tr("Loading your Jellyfin library…")
                :library.resumeMode?Ui.tr("No titles in progress."):Ui.tr("No recent additions in Jellyfin.");
        heading.setTypeface(Ui.medium); heading.setTextSize(34*k); heading.setColor(0xFFFFFFFF);
        text.setTypeface(Ui.regular); text.setTextSize(25*k);
        nameMeasure.set(text); detailMeasure.set(text); detailMeasure.setTextSize(21*k);
        for(int i=0;i<VISIBLE;i++) {
            float x=(80+i*CARD_STEP)*k;
            cards[i].set(x,1120*k,x+CARD_WIDTH*k,(1120+CARD_HEIGHT)*k);
            Bitmap bitmap=i<count?library.poster(library.items.get(first+i)):null;
            if(bitmaps[i]!=bitmap) {
                bitmaps[i]=bitmap;
                shaders[i]=bitmap==null?null:new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            }
            if(i>=count) continue;
            JellyfinRecent.Item item=library.items.get(first+i);
            names[i]=TextUtils.ellipsize(item.name,nameMeasure,CARD_WIDTH*k,TextUtils.TruncateAt.END).toString();
            String info=library.resumeMode?item.subtitle+" · "+Ui.trf("{n} min left","n",Math.max(1,(item.durationTicks-item.positionTicks+599_999_999L)/600_000_000L)):item.year;
            details[i]=TextUtils.ellipsize(info,detailMeasure,CARD_WIDTH*k,TextUtils.TruncateAt.END).toString();
        }
    }
    boolean step(float dt,boolean focused) {
        boolean moving=false;
        for(int i=0;i<VISIBLE;i++) {
            focus[i].target=focused && i<count && first+i==selected?1:0;
            moving|=focus[i].step(dt);
        }
        return moving;
    }
    void bounds(int index,RectF out) {
        int slot=index-first;
        if(slot<0 || slot>=count) out.set(80*k,1120*k,(80+CARD_WIDTH)*k,DETAILS_Y*k);
        else { out.set(cards[slot]); out.bottom=DETAILS_Y*k; }
        out.offset(0,offset*k);
    }
    void draw(Canvas c,boolean focused) {
        c.save(); c.translate(0,offset*k);
        c.drawText(title,80*k,1080*k,heading);
        if(count==0) { text.setColor(0xCCFFFFFF); text.setTextSize(25*k); c.drawText(empty,80*k,1140*k,text); c.restore(); return; }
        for(int i=0;i<count;i++) {
            RectF base=cards[i]; float f=Ui.clamp(focus[i].value),scale=1+0.045f*f;
            visual.set(base); visual.inset(-base.width()*(scale-1)/2,-base.height()*(scale-1)/2);
            float radius=18*k*scale;
            fill.setStyle(Paint.Style.FILL); fill.setColor(0xFF25272D); c.drawRoundRect(visual,radius,radius,fill);
            Bitmap bitmap=bitmaps[i];
            if(bitmap!=null) {
                float size=Math.max(visual.width()/bitmap.getWidth(),visual.height()/bitmap.getHeight());
                matrices[i].setScale(size,size);
                matrices[i].postTranslate(visual.centerX()-bitmap.getWidth()*size/2,visual.centerY()-bitmap.getHeight()*size/2);
                shaders[i].setLocalMatrix(matrices[i]); image.setShader(shaders[i]);
                c.drawRoundRect(visual,radius,radius,image); image.setShader(null);
            }
            if(library.resumeMode) {
                JellyfinRecent.Item item=library.items.get(first+i);
                track.set(visual.left+12*k,visual.bottom-12*k,visual.right-12*k,visual.bottom-8*k);
                fill.setColor(0x99000000); c.drawRoundRect(track,2*k,2*k,fill);
                float progress=item.durationTicks>0?Ui.clamp(item.positionTicks/(float)item.durationTicks):0;
                track.right=track.left+track.width()*progress;
                fill.setColor(0xFFF5F5F7); c.drawRoundRect(track,2*k,2*k,fill);
            }
            if(f>0.005f) {
                outline.set(visual); outline.inset(-4*k,-4*k);
                fill.setStyle(Paint.Style.STROKE); fill.setStrokeWidth(3*k); fill.setColor(Ui.alpha(0xFFFFFFFF,f));
                c.drawRoundRect(outline,radius+4*k,radius+4*k,fill); fill.setStyle(Paint.Style.FILL);
            }
            text.setTextSize(25*k); text.setColor(focused && first+i==selected?0xFFFFFFFF:0xFFE5E5E5);
            c.drawText(names[i],base.left,NAME_Y*k,text);
            text.setTextSize(21*k); text.setColor(0xFFB8BAC0); c.drawText(details[i],base.left,DETAILS_Y*k,text);
        }
        c.restore();
    }
}
