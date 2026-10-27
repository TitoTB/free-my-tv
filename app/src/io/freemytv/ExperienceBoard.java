package io.freemytv;

import android.content.*;
import android.graphics.*;
import android.text.*;
import java.util.Random;

/** Cached chalk lettering and slate; focus and completion animate without rebuilding glyphs. */
final class ExperienceBoard {
    static final float TOP=CulturalPanel.CARDS_TOP+340+84, HEIGHT=500, HEADER_GAP=40;
    private static final int PAGE=ExperienceData.PAGE, ROWS=ExperienceData.ROWS;
    private static final float TEXT_OFFSET=32;
    private final Context context;
    private final android.content.SharedPreferences prefs;
    final ExperienceData data;
    final ExperiencePhotos photos;
    String album(){return prefs.getString("photo-album",Config.EXPERIENCE_PHOTO_ALBUM);}
    void album(String value){prefs.edit().putString("photo-album",value).apply();}
    private final RectF photoArea=new RectF(),photoCard=new RectF(),photoImage=new RectF();
    private final Paint fill=new Paint(3),chalk=new Paint(3);
    private final TextPaint heading=new TextPaint(3),writing=new TextPaint(3);
    private final RectF board=new RectF(),gear=new RectF();
    private final RectF[] rows=new RectF[PAGE];
    private final Spring[] focus=new Spring[PAGE+1],strikes=new Spring[PAGE];
    private final Spring blockFocus=new Spring(.27f,.95f,0);
    private final Spring pageSlide=new Spring(.32f,.9f,0);
    private final String[] labels=new String[PAGE];
    private final Bitmap[] lettering=new Bitmap[PAGE];
    private final Path[] underlines=new Path[PAGE],crossings=new Path[PAGE];
    private final Path checkbox=new Path(),tick=new Path();
    private final Bitmap slateTexture;
    private final float[] widths=new float[PAGE];
    private final Typeface hand;
    private BitmapShader slate;
    private float k,top=TOP;
    private int first,count;
    private String title="";
    ExperienceBoard(Context context){
        this.context=context;slateTexture=loadSlate(context);prefs=context.getSharedPreferences("experience-board",Context.MODE_PRIVATE);
        photos=new ExperiencePhotos(context);
        boolean existing=prefs.contains("items");
        data=existing?ExperienceData.decode(prefs.getString("items","[]")):ExperienceData.defaults();
        if(!existing || data.replaceLegacyExamples())save();
        hand=Typeface.createFromAsset(context.getAssets(),"fonts/ArchitectsDaughter-Regular.ttf");
        for(int i=0;i<PAGE;i++){rows[i]=new RectF();strikes[i]=new Spring(.26f,.92f,0);underlines[i]=new Path();crossings[i]=new Path();labels[i]="";}
        for(int i=0;i<focus.length;i++)focus[i]=new Spring(.27f,.95f,0);
    }
    private static Bitmap loadSlate(Context context){
        try(java.io.InputStream input=context.getAssets().open("experience-board/slate-texture.png")){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(input,null,options);
        }catch(java.io.IOException ignored){return null;}
    }
    float top(){return top;}
    float scrollGap(){return top-CulturalPanel.TOP;}
    void save(){prefs.edit().putString("items",data.encode()).apply();}
    void update(float scale,int selected,int tab){
        float nextTop=TOP+(tab==3?100:0);boolean resized=k!=scale,moved=top!=nextTop;
        k=scale;top=nextTop;title=Ui.tr("The experience chalkboard");
        heading.setTypeface(Ui.medium);heading.setTextSize(34*k);heading.setColor(Color.WHITE);
        writing.setTypeface(hand);writing.setFakeBoldText(true);writing.setTextSize(32*k);writing.setColor(Color.WHITE);
        board.set(80*k,(top+HEADER_GAP)*k,1840*k,(top+HEIGHT)*k);
        float headerCenter=top*k-Ui.halfCap(heading);
        gear.set(1776*k,headerCenter-28*k,1840*k,headerCenter+28*k);
        photoArea.set(board.right-290*k,board.top+16*k,board.right-16*k,board.top+255*k);
        int next=ExperienceData.pageStart(selected);boolean paged=first!=next;
        if(paged){pageSlide.snap(next>first?120:-120);pageSlide.target=0;for(Spring s:focus)s.snap(0);}
        first=next;count=Math.min(PAGE,Math.max(0,data.items.size()-first));
        float pad=36*k,rowStep=(board.height()-2*pad-32*k)/(ROWS-1);
        for(int i=0;i<PAGE;i++){
            int col=i/ROWS,row=i%ROWS;
            float left=board.left+pad+col*board.width()/2,cy=board.top+pad+16*k+row*rowStep;
            rows[i].set(left,cy-34*k,left+board.width()/2-2*pad,cy+34*k);
            if(col==1 && row<3 && !album().isEmpty())rows[i].right=Math.min(rows[i].right,photoArea.left-18*k);
            if(i<count){
                String label=TextUtils.ellipsize(data.items.get(first+i).text.toUpperCase(Ui.locale()),writing,Math.max(1,rows[i].width()-(TEXT_OFFSET+16)*k),TextUtils.TruncateAt.END).toString();
                if(resized || !labels[i].equals(label) || lettering[i]==null){labels[i]=label;prepareLetters(i);}
                strikes[i].target=data.items.get(first+i).done?1:0;
                if(paged || resized)strikes[i].snap(strikes[i].target);
            }else{labels[i]="";lettering[i]=null;widths[i]=0;strikes[i].snap(0);}
        }
        if(resized || moved || slate==null)prepareSlate();
    }
    private void prepareLetters(int slot){
        String text=labels[slot];float padding=4*k;
        widths[slot]=writing.measureText(text)+padding*2;
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(widths[slot])),Math.max(1,(int)Math.ceil(64*k)),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);Random random=new Random(text.hashCode());
        float baseline=bitmap.getHeight()/2f-(writing.ascent()+writing.descent())/2;
        boolean shaped=false;
        for(int i=0;i<text.length();){int cp=text.codePointAt(i);if(Character.isLetter(cp) && cp>0x024f)shaped=true;i+=Character.charCount(cp);}
        if(shaped)c.drawText(text,padding,baseline,writing);
        else{
            float x=padding;
            for(int i=0;i<text.length();){
                int end=i+Character.charCount(text.codePointAt(i));float advance=writing.measureText(text,i,end);
                c.save();c.translate(x,baseline+(random.nextFloat()-.5f)*1.8f*k);
                c.rotate((random.nextFloat()-.5f)*3);c.scale(1, .97f+random.nextFloat()*.06f);
                c.drawText(text,i,end,0,0,writing);c.restore();x+=advance;i=end;
            }
        }
        Paint dust=new Paint(3);dust.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        for(int i=0;i<bitmap.getWidth()*bitmap.getHeight()/12;i++){
            dust.setAlpha(30+random.nextInt(90));
            c.drawCircle(random.nextInt(bitmap.getWidth()),random.nextInt(bitmap.getHeight()),(.2f+random.nextFloat()*.55f)*Math.max(.7f,k),dust);
        }
        lettering[slot]=bitmap;
        chalkPath(underlines[slot],widths[slot],text.hashCode(),false);
        chalkPath(crossings[slot],widths[slot],text.hashCode()+1,true);
    }
    private void chalkPath(Path path,float width,int seed,boolean strike){
        Random random=new Random(seed);path.rewind();path.moveTo(0,0);
        int segments=Math.max(2,(int)(width/Math.max(1,12*k)));
        for(int j=1;j<=segments;j++)path.lineTo(width*j/segments,((random.nextFloat()-.5f)*1.6f+(strike?-1:1)*j/(float)segments)*k);
    }
    private void prepareSlate(){
        if(slateTexture!=null){
            slate=new BitmapShader(slateTexture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix mapping=new Matrix();mapping.setScale(board.width()/slateTexture.getWidth(),board.height()/slateTexture.getHeight());
            mapping.postTranslate(board.left,board.top);slate.setLocalMatrix(mapping);
        }
        checkbox.rewind();checkbox.moveTo(k,-9*k);checkbox.lineTo(19*k,-8*k);
        checkbox.lineTo(18*k,10*k);checkbox.lineTo(0,9*k);checkbox.close();
        tick.rewind();tick.moveTo(3*k,0);tick.lineTo(8*k,6*k);tick.lineTo(21*k,-10*k);
    }
    boolean step(float dt,boolean header,boolean body,boolean block,int selected){
        blockFocus.target=block?1:0;
        boolean moving=pageSlide.step(dt) | blockFocus.step(dt) | photos.step(android.os.SystemClock.uptimeMillis());
        for(int i=0;i<focus.length;i++){
            float target=i==PAGE?header?1:0:body && first+i==selected?1:0;

            focus[i].target=target;moving|=focus[i].step(dt);
        }
        for(Spring strike:strikes)moving|=strike.step(dt);
        return moving;
    }
    int count(){return count;}
    int slot(int index){return index-first;}
    void blockBounds(RectF out){out.set(board);}
    void bounds(boolean header,int slot,RectF out){out.set(header?gear:rows[Math.max(0,Math.min(PAGE-1,slot))]);}
    private void stroke(Canvas c,Path path,float x,float y,float width,float progress){
        if(progress<=.001f)return;
        c.save();c.clipRect(x-2*k,y-5*k,x+width*progress+2*k,y+5*k);c.translate(x,y);c.drawPath(path,chalk);c.restore();
    }
    void draw(Canvas c){
        if(k<=0)return;
        c.drawText(title,80*k,top*k,heading);
        fill.setColor(Ui.alpha(Color.WHITE,.22f*Ui.clamp(focus[PAGE].value)));c.drawRoundRect(gear,20*k,20*k,fill);
        Symbols.draw(context,c,"settings",gear.centerX(),gear.centerY(),27*k,Color.WHITE);
        fill.setColor(slate==null?0xFF101819:Color.WHITE);fill.setAlpha(255);fill.setShader(slate);c.drawRoundRect(board,22*k,22*k,fill);fill.setShader(null);
        chalk.setStyle(Paint.Style.STROKE);chalk.setStrokeWidth((1+1.5f*Ui.clamp(blockFocus.value))*k);chalk.setColor(Ui.alpha(Color.WHITE,.14f+.28f*Ui.clamp(blockFocus.value)));c.drawRoundRect(board,22*k,22*k,chalk);
        chalk.setStrokeWidth(k);chalk.setColor(0x25FFFFFF);
        c.drawLine(board.centerX(),board.top+36*k,board.centerX(),board.bottom-36*k,chalk);
        c.save();c.clipRect(board);c.translate(pageSlide.value*k,0);
        drawPhotos(c);
        chalk.setColor(0xEFFFFFFF);chalk.setStrokeWidth(2.1f*k);chalk.setStrokeCap(Paint.Cap.ROUND);chalk.setStrokeJoin(Paint.Join.ROUND);
        for(int i=0;i<count;i++){
            RectF r=rows[i];float cy=r.centerY();Bitmap letters=lettering[i];
            float done=Ui.clamp(strikes[i].value),x=r.left+TEXT_OFFSET*k;
            int alpha=Math.round(255*(.70f+.30f*Ui.clamp(focus[i].value)));fill.setAlpha(alpha);chalk.setAlpha(alpha);
            c.save();c.translate(r.left,cy);c.drawPath(checkbox,chalk);
            if(done>.001f){c.save();c.clipRect(0,-20*k,32*k*done,20*k);c.drawPath(tick,chalk);c.restore();}c.restore();
            if(letters!=null)c.drawBitmap(letters,x,cy-letters.getHeight()/2f,fill);
            stroke(c,crossings[i],x,cy,widths[i],done);

        }
        c.restore();fill.setAlpha(255);
    }
    private void drawPhotos(Canvas c){
        for(int i=0;i<3;i++){
            Bitmap image=photos.images[i];if(image==null)continue;
            float x=photoArea.left+(i==0?6:i==1?95:48)*k,y=photoArea.top+(i==0?10:i==1?35:95)*k;
            photoCard.set(x,y,x+144*k,y+151*k);photoImage.set(x+6*k,y+6*k,x+138*k,y+138*k);
            c.save();c.rotate(i==0?-13:i==1?11:-3,photoCard.centerX(),photoCard.centerY());
            fill.setColor(0x55000000);c.drawRoundRect(x+4*k,y+5*k,x+148*k,y+156*k,2*k,2*k,fill);
            fill.setColor(0xFFF3F0E8);c.drawRect(photoCard,fill);fill.setColor(Color.WHITE);
            if(i==photos.fadingSlot && photos.previous!=null){
                c.drawBitmap(photos.previous,null,photoImage,fill);fill.setAlpha(Math.round(255*photos.fade(android.os.SystemClock.uptimeMillis())));
            }
            c.drawBitmap(image,null,photoImage,fill);fill.setAlpha(255);fill.setColor(0xFF202122);c.drawCircle(photoCard.centerX(),y+3*k,7*k,fill);c.restore();
        }fill.setColor(Color.WHITE);
    }
}
