package io.freemytv;

import android.graphics.*;
import android.text.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Near-term editorial, monthly posters and a month-grouped planning agenda; one shared focus/index model. */
final class CulturalPanel {
    static final float TOP=2380,CARDS_TOP=TOP+62,GAP=530;
    static final int VISIBLE=10;
    private static final float FEATURE_WIDTH=519,SECONDARY_WIDTH=590.5f;
    private final RectF[] cards=new RectF[VISIBLE],tabs=new RectF[5];
    private final Spring[] focus=new Spring[VISIBLE+5];
    private final Bitmap[] bitmaps=new Bitmap[VISIBLE];
    private final BitmapShader[] shaders=new BitmapShader[VISIBLE];
    private final Matrix matrix=new Matrix();
    private final Path posterClip=new Path();
    private final RectF thumb=new RectF(),tag=new RectF();
    private final Paint fill=new Paint(3),image=new Paint(3);
    private final TextPaint title=new TextPaint(3),text=new TextPaint(3),small=new TextPaint(3),featureText=new TextPaint(3);
    private final TextPaint posterText=new TextPaint(3),dayText=new TextPaint(3);
    private final CulturalPaging pages=new CulturalPaging();
    private final String[] months=new String[VISIBLE],dayNumbers=new String[VISIBLE],weekdays=new String[VISIBLE];
    private final boolean[] monthStart=new boolean[VISIBLE];
    private StaticLayout[] names=new StaticLayout[VISIBLE];
    private LinearGradient featureShade,glass;
    private String heading="",empty="",featuredLabel="";
    private final String[] tabNames=new String[5];
    private final boolean[] liked=new boolean[VISIBLE],free=new boolean[VISIBLE];
    private final TextPaint badgeText=new TextPaint(3);
    private String freeLabel="";
    private final String[][] badgeNames=new String[VISIBLE][3];
    private final float[][] badgeWidths=new float[VISIBLE][3];
    private int count,selected,tab,headerIndex,mode;
    private float k;
    private final android.content.Context context;
    CulturalPanel(android.content.Context context) {
        this.context=context;
        for(int i=0;i<focus.length;i++)focus[i]=new Spring(.24f,.92f,0);
        for(int i=0;i<cards.length;i++)cards[i]=new RectF();
        for(int i=0;i<tabs.length;i++)tabs[i]=new RectF();
    }
    int count() { return count; }
    int resultIndex(int slot) { return pages.index(slot); }
    int slot(int index) { return pages.slot(index); }
    boolean featured(int slot) { return mode==CulturalPaging.EDITORIAL && slot==0; }
    List<CulturalData.Hit> visible(List<CulturalData.Hit> results) {
        List<CulturalData.Hit> hits=new ArrayList<>();for(int i=0;i<count;i++)hits.add(results.get(resultIndex(i)));return hits;
    }
    int horizontal(int direction) { return pages.horizontal(direction); }
    int vertical(int direction) { return pages.vertical(direction); }
    static String tabName(int tab) {
        switch(tab) {
            case 0:return Ui.tr("Today"); case 1:return Ui.tr("Tomorrow"); case 2:return Ui.tr("This weekend");
            case 3:return Ui.tr("This month"); default:return Ui.tr("Event settings");
        }
    }
    private String fit(String value,float width,TextPaint paint) { return TextUtils.ellipsize(value,paint,width,TextUtils.TruncateAt.END).toString(); }
    void update(CulturalEvents model,List<CulturalData.Hit> results,float scale,int selected,int tab,int headerIndex) {
        boolean resized=k!=scale;
        k=scale;this.selected=selected;this.tab=tab;this.headerIndex=headerIndex;
        pages.update(results,selected,tab,!results.isEmpty() && model.likes.liked(results.get(0).event));
        count=pages.count;mode=pages.mode;
        heading=Ui.tr("Events in Madrid");
        empty=model.planning(tab)?Ui.tr("Preparing recommendations…"):model.selection.isEmpty()?Ui.tr("Choose categories in this block's settings."):
                model.catalog.events.isEmpty()?(model.fetching?Ui.tr("Loading events…"):Ui.tr("Events could not be loaded.")):
                Ui.tr("No events for these dates and categories.");
        if(!model.error.isEmpty() && count==0)empty=model.error;
        title.setTypeface(Ui.medium); title.setTextSize(34*k); title.setColor(Color.WHITE);
        text.setTypeface(Ui.semibold); text.setTextSize(28*k); text.setColor(Color.WHITE);
        posterText.setTypeface(Ui.semibold);posterText.setTextSize((mode==CulturalPaging.POSTERS?22:25)*k);posterText.setColor(Color.WHITE);
        dayText.setTypeface(Ui.regular);dayText.setTextSize(32*k);dayText.setColor(Color.WHITE);
        featureText.setTypeface(Ui.semibold); featureText.setTextSize(46*k); featureText.setColor(Color.WHITE);
        small.setTypeface(Ui.regular); small.setTextSize(20*k); small.setColor(0xFFDEE0E7);
        freeLabel=Ui.tr("Free admission");
        badgeText.setTypeface(Ui.medium);badgeText.setTextSize(16*k);badgeText.setColor(Color.WHITE);
        // Anchor the period group beside settings and centre it on the heading's visible capitals.
        float headerCenter=TOP*k-Ui.halfCap(title);
        for(int i=0;i<tabs.length;i++) {
            tabNames[i]=tabName(i); float x=i==4?1776:856+i*230;
            tabs[i].set(x*k,headerCenter-28*k,(i==4?1840:x+214)*k,headerCenter+28*k);
        }
        if(mode==CulturalPaging.EDITORIAL) {
            cards[0].set(80*k,CARDS_TOP*k,(80+FEATURE_WIDTH)*k,(CARDS_TOP+340)*k);
            for(int i=1;i<5;i++) {
                float x=110+FEATURE_WIDTH+(i-1)/2*(SECONDARY_WIDTH+30),y=CARDS_TOP+(i-1)%2*180;
                cards[i].set(x*k,y*k,(x+SECONDARY_WIDTH)*k,(y+160)*k);
            }
        } else if(mode==CulturalPaging.POSTERS)for(int i=0;i<count;i++) {
            float x=80+i%5*357,y=CARDS_TOP+i/5*230;
            cards[i].set(x*k,y*k,(x+332)*k,(y+210)*k);
        }
        else {
            java.time.YearMonth previous=null;float y=0;
            DateTimeFormatter month=DateTimeFormatter.ofPattern("LLLL yyyy",Ui.locale());
            for(int i=0;i<count;i++) {
                CulturalData.Hit hit=results.get(resultIndex(i));java.time.YearMonth current=java.time.YearMonth.from(hit.date);
                monthStart[i]=!current.equals(previous);if(monthStart[i])y+=28;
                String label=month.format(hit.date);months[i]=label.substring(0,1).toUpperCase(Ui.locale())+label.substring(1);
                dayNumbers[i]=Integer.toString(hit.date.getDayOfMonth());weekdays[i]=DateTimeFormatter.ofPattern("EEE",Ui.locale()).format(hit.date);
                cards[i].set(80*k,(CARDS_TOP+y)*k,1840*k,(CARDS_TOP+y+66)*k);y+=74;previous=current;
            }
        }
        if(resized || featureShade==null) {
            featureShade=new LinearGradient(0,CARDS_TOP*k,0,(CARDS_TOP+340)*k,
                    new int[]{0x08080C13,0x34080C13,0xF5080C13},new float[]{0,.28f,1},Shader.TileMode.CLAMP);
            glass=new LinearGradient(1080*k,CARDS_TOP*k,1840*k,(CARDS_TOP+340)*k,0xD9323740,0xC0161B23,Shader.TileMode.CLAMP);
        }
        java.time.LocalDate today=java.time.LocalDate.now(CulturalData.ZONE);
        for(int i=0;i<count;i++) {
            CulturalData.Hit hit=results.get(resultIndex(i)); liked[i]=model.likes.liked(hit.event);free[i]=hit.event.free;
            Bitmap b=model.image(hit.event.image);
            if(bitmaps[i]!=b) { bitmaps[i]=b; shaders[i]=b==null?null:new BitmapShader(b,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP); }
            TextPaint paint=featured(i)?featureText:mode==CulturalPaging.POSTERS || mode==CulturalPaging.EDITORIAL?posterText:text;
            float titleWidth=featured(i)?FEATURE_WIDTH-60:mode==CulturalPaging.POSTERS?292:mode==CulturalPaging.AGENDA?1250:SECONDARY_WIDTH-48;
            names[i]=StaticLayout.Builder.obtain(hit.event.title,0,hit.event.title.length(),paint,Math.max(1,Math.round(titleWidth*k)))
                    .setMaxLines(mode==CulturalPaging.AGENDA || mode==CulturalPaging.POSTERS?1:2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
            String date=CulturalPresentation.eventDate(hit.date,today,Ui.locale(),Ui.tr("Today"),Ui.tr("Tomorrow"));
            String label=model.decision(hit).label;
            if(label.isEmpty()) {
                for(CulturalData.Category category:model.catalog.categories)if(!category.parent.isEmpty() && hit.event.categories.contains(category.key)){label=category.name;break;}
                if(label.isEmpty())for(CulturalData.Category category:model.catalog.categories)if(hit.event.categories.contains(category.key)){label=category.name;break;}
            }
            prepareBadges(i,date,label,titleWidth*k);
        }
        featuredLabel=Ui.tr("Discover Madrid");
        // Drop hidden references on a partial final page.
        for(int i=count;i<VISIBLE;i++) { bitmaps[i]=null; shaders[i]=null; names[i]=null; }
    }
    boolean step(float dt,boolean header,boolean body) {
        boolean moving=false;
        for(int i=0;i<5;i++) { focus[i].target=header && headerIndex==i?1:0; moving|=focus[i].step(dt); }
        for(int i=0;i<VISIBLE;i++) { focus[i+5].target=body && slot(selected)==i?1:0; moving|=focus[i+5].step(dt); }
        return moving;
    }
    void bounds(boolean header,int slot,RectF out) { out.set(header?tabs[Math.max(0,Math.min(4,slot))]:cards[Math.max(0,Math.min(VISIBLE-1,slot))]); }
    private void photo(Canvas canvas,int slot,RectF rect,float radius) {
        Bitmap b=bitmaps[slot]; if(b==null)return;
        float scale=Math.max(rect.width()/b.getWidth(),rect.height()/b.getHeight()); matrix.setScale(scale,scale);
        matrix.postTranslate(rect.centerX()-b.getWidth()*scale/2,rect.centerY()-b.getHeight()*scale/2);
        shaders[slot].setLocalMatrix(matrix); image.setShader(shaders[slot]); canvas.drawRoundRect(rect,radius,radius,image); image.setShader(null);
    }
    private void drawPoster(Canvas canvas,int i,RectF r) {
        // A single outer silhouette: image and text meet at a straight shared edge.
        posterClip.rewind();posterClip.addRoundRect(r,26*k,26*k,Path.Direction.CW);
        canvas.save();canvas.clipPath(posterClip);
        thumb.set(r.left,r.top,r.right,r.top+105*k);photo(canvas,i,thumb,0);
        thumb.set(r.left,r.top+105*k,r.right,r.bottom);fill.setShader(glass);canvas.drawRect(thumb,fill);fill.setShader(null);
        canvas.restore();
        canvas.save();canvas.translate(r.left+20*k,r.top+117*k);names[i].draw(canvas);canvas.restore();
        drawBadges(canvas,i,r.left+20*k,r.bottom-44*k);
    }
    private void prepareBadges(int i,String date,String label,float width) {
        float gap=8*k,padding=20*k,freeWidth=free[i]?badgeText.measureText(freeLabel)+padding:0;
        float dateRoom=width-freeWidth-(free[i]?gap:0)-(label.isEmpty()?0:60*k+gap);
        badgeNames[i][0]=fit(date,Math.max(20*k,dateRoom-padding),badgeText);
        badgeWidths[i][0]=badgeText.measureText(badgeNames[i][0])+padding;
        float labelRoom=width-badgeWidths[i][0]-freeWidth-gap-(free[i]?gap:0);
        badgeNames[i][1]=label.isEmpty() || labelRoom<padding+16*k?"":fit(label,labelRoom-padding,badgeText);
        badgeWidths[i][1]=badgeNames[i][1].isEmpty()?0:badgeText.measureText(badgeNames[i][1])+padding;
        badgeNames[i][2]=free[i]?freeLabel:"";badgeWidths[i][2]=freeWidth;
    }
    private void drawBadges(Canvas canvas,int i,float x,float y) {
        for(int j=0;j<3;j++) {
            if(badgeNames[i][j].isEmpty())continue;
            tag.set(x,y,x+badgeWidths[i][j],y+28*k);fill.setColor(j==2?0xF019653B:0xB5323945);
            canvas.drawRoundRect(tag,8*k,8*k,fill);badgeText.setColor(j==2?0xFFDEFFE8:Color.WHITE);
            canvas.drawText(badgeNames[i][j],x+10*k,y+14*k+Ui.halfCap(badgeText),badgeText);
            x+=badgeWidths[i][j]+8*k;
        }
    }
    private void drawAgenda(Canvas canvas,int i,RectF r) {
        if(monthStart[i])canvas.drawText(months[i],r.left,r.top-8*k,small);
        fill.setShader(glass);canvas.drawRoundRect(r,18*k,18*k,fill);fill.setShader(null);
        canvas.drawText(dayNumbers[i],r.left+17*k,r.top+32*k,dayText);
        small.setTextSize(17*k);canvas.drawText(weekdays[i],r.left+17*k,r.top+54*k,small);small.setTextSize(20*k);
        thumb.set(r.left+98*k,r.top+6*k,r.left+192*k,r.bottom-6*k);photo(canvas,i,thumb,10*k);
        canvas.save();canvas.translate(r.left+218*k,r.top+6*k);names[i].draw(canvas);canvas.restore();
        drawBadges(canvas,i,r.left+218*k,r.bottom-28*k);
    }
    void draw(Canvas canvas) {
        if(k<=0)return;
        canvas.drawText(heading,80*k,TOP*k,title);
        for(int i=0;i<5;i++) {
            float f=Ui.clamp(focus[i].value);
            fill.setColor(f>.01f?Ui.alpha(0xFFFFFFFF,.12f+.15f*f):i==tab?0x24FFFFFF:Color.TRANSPARENT);
            canvas.drawRoundRect(tabs[i],22*k,22*k,fill);
            if(i==4)Symbols.draw(context,canvas,"settings",tabs[i].centerX(),tabs[i].centerY(),27*k,Color.WHITE);
            else { text.setTextSize(20*k); canvas.drawText(tabNames[i],tabs[i].centerX()-text.measureText(tabNames[i])/2,tabs[i].centerY()+Ui.halfCap(text),text); }
        }
        text.setTextSize(28*k);
        if(count==0) { canvas.drawText(empty,80*k,(CARDS_TOP+70)*k,small); return; }
        for(int i=0;i<count;i++) {
            RectF r=cards[i]; float f=Ui.clamp(focus[i+5].value);
            fill.setColor(0xDD20252F); canvas.drawRoundRect(r,26*k,26*k,fill);
            if(mode==CulturalPaging.POSTERS) {
                drawPoster(canvas,i,r);
            } else if(mode==CulturalPaging.AGENDA) {
                drawAgenda(canvas,i,r);
            } else if(i==0) {
                photo(canvas,i,r,26*k); fill.setShader(featureShade); canvas.drawRoundRect(r,26*k,26*k,fill); fill.setShader(null);
                if(!liked[i]) {
                    fill.setColor(0xA51A2029); tag.set(r.left+24*k,r.top+22*k,r.left+215*k,r.top+62*k); canvas.drawRoundRect(tag,12*k,12*k,fill);
                    canvas.drawText(featuredLabel,r.left+38*k,r.top+49*k,small);
                }
                float base=r.bottom-64*k; canvas.save(); canvas.translate(r.left+30*k,base-names[i].getHeight()); names[i].draw(canvas); canvas.restore();
                drawBadges(canvas,i,r.left+30*k,r.bottom-44*k);
            } else {
                photo(canvas,i,r,26*k);fill.setColor(0xB5080C13);canvas.drawRoundRect(r,26*k,26*k,fill);
                float x=r.left+24*k;
                canvas.save(); canvas.translate(x,r.top+24*k); names[i].draw(canvas); canvas.restore();
                drawBadges(canvas,i,x,r.bottom-44*k);
            }
            if(f>.01f) { fill.setStyle(Paint.Style.STROKE); fill.setStrokeWidth(3*k); fill.setColor(Ui.alpha(Color.WHITE,f));
                float radius=(mode==CulturalPaging.AGENDA?18:26)*k;
                canvas.drawRoundRect(r,radius,radius,fill); fill.setStyle(Paint.Style.FILL); }
        }
    }
}
