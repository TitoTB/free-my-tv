package io.freemytv;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Small animated ribbon cloud. Cached paths/paints; active only while Jarvis is visible. */
final class AssistNebula extends View {
    private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG),edge=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] paints=new Paint[5];
    private final Path[] paths=new Path[5];
    private boolean running;
    private float level,target;
    private final long start=SystemClock.uptimeMillis();
    AssistNebula(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);for(int i=0;i<5;i++){paints[i]=new Paint(Paint.ANTI_ALIAS_FLAG);paths[i]=new Path();}edge.setStyle(Paint.Style.STROKE);}
    void running(boolean value){running=value;if(value)invalidate();}
    void level(float value){target=Math.max(0,Math.min(1,value));}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        int[][] colors={{0x0019BFFF,0xB234D8FF,0x663D60FF,0x003B5EFF},{0x001B82FF,0x925C63FF,0xC19E54FF,0x009358FF},{0x001A8CFF,0x8A3DBAFF,0xC0C95CDF,0x006D65FF},{0x004DDFFF,0xB357D8F5,0x866B5AFF,0x003B80FF},{0x003379FF,0x855E9FFF,0x85CB78FA,0x004774FC}};
        for(int i=0;i<5;i++)paints[i].setShader(new LinearGradient(w*.18f,0,w*.82f,h,colors[i],new float[]{0,.35f,.7f,1},Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(w*.5f,h*.5f,Math.max(1,w*.28f),new int[]{0x332F94FF,0x154D56D3,0x002F63B0},null,Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        level+=(target-level)*.12f;target*=.96f;
        float time=(SystemClock.uptimeMillis()-start)/1000f;
        c.drawOval(w*.15f,h*.07f,w*.85f,h*.93f,glow);
        for(int layer=0;layer<5;layer++){
            Path p=paths[layer];p.reset();
            for(int j=0;j<=48;j++){
                float x=j/48f,fade=(float)Math.pow(Math.sin(Math.PI*x),1.2),wave=(float)(Math.sin(x*Math.PI*3.6+time*.85+layer*.8)*.19+Math.sin(x*Math.PI*6-time*.6+layer)*.055);
                float y=h*(.5f+fade*(wave*(.78f+level*.3f)-(.07f+layer*.013f)));
                if(j==0)p.moveTo(w*(.15f+.7f*x),y);else p.lineTo(w*(.15f+.7f*x),y);
            }
            for(int j=48;j>=0;j--){float x=j/48f,fade=(float)Math.pow(Math.sin(Math.PI*x),1.2),wave=(float)(Math.sin(x*Math.PI*3.6+time*.85+layer*.8)*.19+Math.sin(x*Math.PI*6-time*.6+layer)*.055);p.lineTo(w*(.15f+.7f*x),h*(.5f+fade*(wave*(.78f+level*.3f)+(.07f+layer*.013f))));}
            p.close();c.drawPath(p,paints[layer]);edge.setShader(paints[layer].getShader());edge.setStrokeWidth(Math.max(1,w/1200f));c.drawPath(p,edge);
        }
        if(running)postInvalidateOnAnimation();
    }
}
