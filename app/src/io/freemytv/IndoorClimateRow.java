package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** White, compact thermometer/value and droplet/value; reused on Home and the screensaver. */
final class IndoorClimateRow {
    private final Path thermometer=new Path(),droplet=new Path();
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint text;
    private float k=1;
    IndoorClimateRow() {
        thermometer.moveTo(9,14); thermometer.lineTo(9,5);
        thermometer.cubicTo(9,1,15,1,15,5); thermometer.lineTo(15,14);
        thermometer.cubicTo(22,20,12,27,7,21); thermometer.cubicTo(5,18,7,15,9,14); thermometer.close();
        droplet.moveTo(12,2); droplet.cubicTo(10,6,5,11,5,16);
        droplet.cubicTo(5,25,19,25,19,16); droplet.cubicTo(19,11,14,6,12,2); droplet.close();
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(1.8f); line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND); line.setColor(0xEBFFFFFF); fill.setColor(0xEBFFFFFF);
    }
    void size(float scale) {
        k=scale*0.8f; text=Ui.numbers(Ui.medium,30*k,0xEBFFFFFF);
        text.setShadowLayer(8*k,0,1*k,0x99000000);
    }
    private float width(HomeClimate climate) {
        return (28+10+32+28+10)*k+text.measureText(climate.temperature)+text.measureText(climate.humidity);
    }
    void right(Canvas canvas,float right,float cy,HomeClimate climate) { draw(canvas,right-width(climate),cy,climate); }
    void centered(Canvas canvas,float cx,float cy,HomeClimate climate) { draw(canvas,cx-width(climate)/2,cy,climate); }
    private void draw(Canvas canvas,float x,float cy,HomeClimate climate) {
        if(text==null || !HomeClimate.enabled()) return;
        icon(canvas,x,cy,true);
        float base=cy+Ui.halfCap(text);
        x+=38*k; canvas.drawText(climate.temperature,x,base,text);
        x+=text.measureText(climate.temperature)+32*k;
        icon(canvas,x,cy,false);
        canvas.drawText(climate.humidity,x+38*k,base,text);
    }
    private void icon(Canvas canvas,float x,float cy,boolean temp) {
        canvas.save(); canvas.translate(x,cy-14*k); canvas.scale(28*k/24,28*k/24);
        canvas.drawPath(temp?thermometer:droplet,line);
        if(temp) { canvas.drawLine(12,8,12,18,line); canvas.drawCircle(12,18,2,fill); }
        canvas.restore();
    }
}
