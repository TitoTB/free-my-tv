package io.freemytv;

import android.graphics.*;
import android.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Preformatted calendar chrome and agenda; drawing never performs network/date queries. */
final class CalendarVisual {
    final RectF panel=new RectF();
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shape=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusHalo=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF icon=new RectF();
    private float k=1,summaryWidth=1200;
    private boolean homeSummary;
    private String summary="",month="",description="Próximos eventos";
    private int page,offset,today;
    private String[] names=new String[0],events=new String[0],days=new String[0];
    private int[] colors=new int[0];
    private final List<List<Integer>> dots=new ArrayList<>();
    private CalendarAgenda data;
    final CalendarNavigation navigation=new CalendarNavigation(LocalDate.now());
    private CalendarAgenda monthData;
    private YearMonth loadedMonth;
    private final String[] weekdays={"L","M","X","J","V","S","D"};
    CalendarVisual() {
        focusHalo.setShader(new RadialGradient(0,0,1,new int[]{0x66FFFFFF,0x42FFFFFF,0x00FFFFFF},
                new float[]{0,.5f,1},Shader.TileMode.CLAMP));
    }
    void size(float scale,float width) {
        k=scale; summaryWidth=width; panel.set(80*k,770*k,1840*k,1150*k);
        if(data!=null) update(data);
    }
    void homeSize(float scale,float width) { homeSummary=true; size(scale,width); }
    private void style(float size,int color) {
        text.setTypeface(Ui.medium); text.setTextSize(size*k); text.setColor(color); text.setTextAlign(Paint.Align.LEFT);
    }
    private String fit(String value,float width) { return TextUtils.ellipsize(value,text,width,TextUtils.TruncateAt.END).toString(); }
    void update(CalendarAgenda data) {
        if(this.data==null)navigation.selected=LocalDate.now(data.zone);
        this.data=data; ZonedDateTime now=ZonedDateTime.now(data.zone);
        CalendarAgenda.Event next=data.next(now);
        style(homeSummary?20:24,0xFFFFFFFF);
        String nextText=next==null?"":CalendarAgenda.format(next,now);
        if(homeSummary)nextText=CalendarAgenda.limit(nextText,70);
        summary=fit(nextText,Math.max(0,summaryWidth-46*k));
        page=Math.min(page,Math.max(0,(data.sources.size()-1)/5));
        int count=Math.min(5,Math.max(0,data.sources.size()-page*5));
        names=new String[count]; events=new String[count]; colors=new int[count];
        StringBuilder spoken=new StringBuilder("Próximos eventos. ");
        for(int i=0;i<count;i++) {
            CalendarAgenda.Source source=data.sources.get(page*5+i); colors[i]=source.color;
            String name=CalendarAgenda.panelName(source.id,source.name);
            style(22,0xFFFFFFFF); names[i]=fit(name,350*k);
            CalendarAgenda.Event event=source.next(now);
            String eventText=!source.available?"Calendario no disponible":event==null?"Sin eventos en los próximos 12 meses":CalendarAgenda.format(event,now);
            events[i]=fit(eventText,730*k);
            spoken.append(name).append(": ").append(eventText).append(". ");
        }
        description=spoken.toString();
        LocalDate first=navigation.month().atDay(1);
        month=first.format(DateTimeFormatter.ofPattern("MMMM yyyy",CalendarAgenda.SPANISH));
        month=month.substring(0,1).toUpperCase(CalendarAgenda.SPANISH)+month.substring(1);
        offset=first.getDayOfWeek().getValue()-1; today=navigation.month().equals(YearMonth.from(now))?now.getDayOfMonth():0;
        days=new String[first.lengthOfMonth()]; dots.clear();
        for(int d=1;d<=days.length;d++) {
            days[d-1]=Integer.toString(d); List<Integer> colors=new ArrayList<>();
            LocalDate date=first.withDayOfMonth(d);
            CalendarAgenda visible=navigation.month().equals(loadedMonth)?monthData:data;
            for(CalendarAgenda.Source source:visible.sources) for(CalendarAgenda.Event event:source.events)
                if(event.on(date)) colors.add(event.color);
            dots.add(colors);
        }
    }
    void monthResult(YearMonth month,CalendarAgenda result) {
        if(!navigation.month().equals(month))return;
        loadedMonth=month;monthData=result;if(data!=null)update(data);
    }
    boolean move(int dx,int dy) {boolean inside=navigation.move(dx,dy);if(data!=null)update(data);return inside;}
    void select(int node){navigation.select(node);}
    void shift(int direction){navigation.shift(direction);if(data!=null)update(data);}
    int count(){return navigation.count();}
    int focused(){return navigation.node();}
    String description(int node) {
        if(node<2)return (node==0?"Mes anterior. ":"Mes siguiente. ")+month;
        LocalDate day=navigation.month().atDay(node-1);
        CalendarAgenda visible=navigation.month().equals(loadedMonth)?monthData:data;
        return day.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",CalendarAgenda.SPANISH))+
                (visible==null?"":", "+visible.on(day).size()+" eventos");
    }
    void bounds(int node,RectF out) {
        float gridX=panel.right-560*k,y=panel.top;
        if(node<2){float cx=gridX+(node==0?15:477)*k;out.set(cx-20*k,y+21*k,cx+20*k,y+61*k);return;}
        int cell=node-2+offset;float cx=gridX+(cell%7*72+30)*k,base=y+(152+cell/7*36)*k;
        // Include the number, event dots and overflow marker with padding on every side.
        out.set(cx-33*k,base-19*k,cx+33*k,base+18*k);
    }
    void page(int direction) {
        if(data==null) return;
        page=Math.max(0,Math.min(Math.max(0,(data.sources.size()-1)/5),page+direction)); update(data);
    }
    String description() { return description; }
    void summary(Canvas c,float anchor,float base,boolean centered) {
        drawSummary(c,anchor,base,centered?1:2);
    }
    void summaryLeft(Canvas c,float left,float base) { drawSummary(c,left,base,0); }
    private void drawSummary(Canvas c,float anchor,float base,int alignment) {
        if(summary.isEmpty()) return;
        style(homeSummary?20:24,0xFFFFFFFF);
        float iconScale=homeSummary?20f/24:1,gap=46*k*iconScale;
        float width=text.measureText(summary)+gap,left=alignment==0?anchor:alignment==1?anchor-width/2:anchor-width;
        float cy=base+(text.ascent()+text.descent())/2;
        c.save(); c.scale(iconScale,iconScale,left,cy); calendarIcon(c,left,cy,0xFFFFFFFF); c.restore();
        text.setShadowLayer(4*k,0,k,0x99000000);
        c.drawText(summary,left+gap,base,text); text.clearShadowLayer();
    }
    private void calendarIcon(Canvas c,float left,float cy,int color) {
        shape.setColor(color); shape.setStyle(Paint.Style.STROKE); shape.setStrokeWidth(2*k);
        icon.set(left,cy-13*k,left+26*k,cy+13*k); c.drawRoundRect(icon,4*k,4*k,shape);
        c.drawLine(left,cy-5*k,left+26*k,cy-5*k,shape);
        c.drawLine(left+7*k,cy-17*k,left+7*k,cy-9*k,shape);
        c.drawLine(left+19*k,cy-17*k,left+19*k,cy-9*k,shape);
        shape.setStyle(Paint.Style.FILL);
    }
    void panel(Canvas c,int ink,boolean focused) {
        float x=panel.left+32*k,y=panel.top;
        if(focused) {
            shape.setStyle(Paint.Style.STROKE); shape.setStrokeWidth(3.5f*k); shape.setColor(0x40FFFFFF);
            c.drawRoundRect(panel,30*k,30*k,shape); shape.setStyle(Paint.Style.FILL);
        }
        style(30,ink);
        calendarIcon(c,x,y+49*k+(text.ascent()+text.descent())/2,ink);
        c.drawText("Próximos eventos",x+46*k,y+49*k,text);
        if(names.length==0) {
            style(22,ink); c.drawText(data==null?"Cargando calendarios…":data.available?"No hay calendarios":"Calendarios no disponibles",x,y+130*k,text);
        }
        for(int i=0;i<names.length;i++) {
            float base=y+(124+51*i)*k;
            shape.setColor(colors[i]); c.drawCircle(x+6*k,base-7*k,6*k,shape);
            style(22,ink); c.drawText(names[i],x+24*k,base,text); c.drawText(events[i],x+390*k,base,text);
        }
        if(data!=null && data.sources.size()>5) {
            style(18,ink); c.drawText("◀  "+(page+1)+" / "+((data.sources.size()+4)/5)+"  ▶",x,y+354*k,text);
        }
        float gridX=panel.right-560*k;
        style(26,ink);text.setTextAlign(Paint.Align.CENTER);c.drawText(month,gridX+246*k,y+49*k,text);
        for(int side=0;side<2;side++) {
            float cx=gridX+(side==0?15:477)*k;
            if(focused && navigation.header && navigation.side==side){shape.setColor(0x40FFFFFF);c.drawCircle(cx,y+41*k,20*k,shape);}
            shape.setStyle(Paint.Style.STROKE);shape.setStrokeWidth(2*k);shape.setColor(Ui.alpha(ink,.5f));
            float sign=side==0?-1:1;c.drawLine(cx-sign*4*k,y+35*k,cx+sign*3*k,y+41*k,shape);
            c.drawLine(cx+sign*3*k,y+41*k,cx-sign*4*k,y+47*k,shape);shape.setStyle(Paint.Style.FILL);
        }
        text.setTextSize(18*k); text.setTextAlign(Paint.Align.CENTER);
        for(int col=0;col<7;col++) c.drawText(weekdays[col],gridX+(col*72+30)*k,y+120*k,text);
        for(int i=0;i<days.length;i++) {
            int cell=i+offset; float cx=gridX+(cell%7*72+30)*k,base=y+(152+cell/7*36)*k;
            if(i+1==today) { shape.setColor(Color.red(ink)>128?0x25FFFFFF:0x181D1D1F); c.drawCircle(cx,base-7*k,15*k,shape); }
            if(focused && !navigation.header && i+1==navigation.selected.getDayOfMonth()) {
                c.save();c.translate(cx,base);c.scale(38*k,24*k);c.drawCircle(0,0,1,focusHalo);c.restore();
            }
            c.drawText(days[i],cx,base,text);
            List<Integer> markers=dots.get(i); int n=Math.min(6,markers.size());
            for(int j=0;j<n;j++) { shape.setColor(markers.get(j)); c.drawCircle(cx+(j-(n-1)/2f)*7*k,base+11*k,2.5f*k,shape); }
            if(markers.size()>6) { shape.setColor(ink); c.drawCircle(cx+27*k,base+11*k,1.5f*k,shape); }
        }
        text.setTextAlign(Paint.Align.LEFT);
    }
}
