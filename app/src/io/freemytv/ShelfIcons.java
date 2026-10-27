package io.freemytv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.HashMap;
import java.util.Map;

/** Small white app silhouettes, prepared by the shelf's background loader. */
final class ShelfIcons {
    private static final Map<String,Bitmap> CACHE=new HashMap<>();
    private static final Map<String,Long> STAMPS=new HashMap<>();
    private ShelfIcons() {}

    static synchronized Bitmap get(Context c,String pkg,boolean load) {
        if(!load) return CACHE.get(pkg);
        PackageManager pm=c.getPackageManager();
        try {
            long stamp=pm.getPackageInfo(pkg,0).lastUpdateTime;
            if(STAMPS.containsKey(pkg) && STAMPS.get(pkg)==stamp) return CACHE.get(pkg);
            Bitmap result;
            if("com.google.android.youtube.tv".equals(pkg)) result=youtube();
            else if("com.netflix.ninja".equals(pkg)) result=netflix();
            else if(pkg.contains("atresplayer")) result=Symbols.get(c,"atresplayer");
            else {
                Drawable icon=pm.getApplicationIcon(pkg);
                boolean monochrome=false;
                if(icon instanceof AdaptiveIconDrawable) {
                    AdaptiveIconDrawable adaptive=(AdaptiveIconDrawable)icon;
                    Drawable mono=Build.VERSION.SDK_INT>=33?adaptive.getMonochrome():null;
                    monochrome=mono!=null;
                    icon=monochrome?mono:adaptive.getForeground();
                }
                Bitmap raw=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
                icon.setBounds(0,0,128,128); icon.draw(new Canvas(raw));
                result=white(raw,monochrome);
            }
            CACHE.put(pkg,result); STAMPS.put(pkg,stamp); return result;
        } catch(PackageManager.NameNotFoundException | RuntimeException e) {
            return CACHE.get(pkg);
        }
    }

    private static Bitmap netflix() {
        Bitmap b=Bitmap.createBitmap(72,112,Bitmap.Config.ARGB_8888);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xFFFFFFFF);
        Path mark=new Path();
        mark.addRect(3,2,21,110,Path.Direction.CW);
        mark.addRect(51,2,69,110,Path.Direction.CW);
        mark.moveTo(3,2); mark.lineTo(23,2); mark.lineTo(69,110); mark.lineTo(49,110); mark.close();
        new Canvas(b).drawPath(mark,p); return b;
    }

    private static Bitmap youtube() {
        Bitmap b=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xFFFFFFFF);
        Path mark=new Path(); mark.setFillType(Path.FillType.EVEN_ODD);
        mark.addRoundRect(new RectF(2,7,126,89),22,22,Path.Direction.CW);
        mark.moveTo(52,28); mark.lineTo(52,68); mark.lineTo(85,48); mark.close();
        new Canvas(b).drawPath(mark,p); return b;
    }

    private static Bitmap white(Bitmap raw,boolean monochrome) {
        int w=raw.getWidth(),h=raw.getHeight();
        int[] pixels=new int[w*h]; raw.getPixels(pixels,0,w,0,0,w,h);
        int background=pixels[0];
        boolean solidEdge=(background>>>24)>240;
        for(int sample:new int[]{w-1,(h-1)*w,w*h-1})
            solidEdge&=(pixels[sample]>>>24)>240 && distance(background,pixels[sample])<45;
        int ink=0;
        for(int i=0;i<pixels.length;i++) {
            int color=pixels[i],alpha=color>>>24;
            if(!monochrome) {
                if(solidEdge) alpha=Math.round(alpha*Math.min(1f,distance(background,color)/90f));
                else {
                    int brightest=Math.max((color>>16)&255,Math.max((color>>8)&255,color&255));
                    alpha=Math.round(alpha*Math.min(1f,brightest/80f));
                }
            }
            if(alpha>24) ink++;
            pixels[i]=(alpha<<24)|0xFFFFFF;
        }
        // A plain dark icon still keeps its silhouette when there is no contrast to extract.
        if(ink<32) {
            raw.getPixels(pixels,0,w,0,0,w,h);
            for(int i=0;i<pixels.length;i++) pixels[i]=(pixels[i]&0xFF000000)|0xFFFFFF;
        }
        Bitmap result=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        raw.recycle();
        int left=w,top=h,right=-1,bottom=-1;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) if((pixels[y*w+x]>>>24)>24) {
            left=Math.min(left,x); right=Math.max(right,x); top=Math.min(top,y); bottom=Math.max(bottom,y);
        }
        if(right>=left && bottom>=top) {
            Bitmap cropped=Bitmap.createBitmap(result,left,top,right-left+1,bottom-top+1);
            if(cropped!=result) result.recycle(); result=cropped;
        }
        result.setHasMipMap(true); return result;
    }
    private static int distance(int a,int b) {
        return Math.max(Math.abs(((a>>16)&255)-((b>>16)&255)),
                Math.max(Math.abs(((a>>8)&255)-((b>>8)&255)),Math.abs((a&255)-(b&255))));
    }
}
