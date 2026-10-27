package io.freemytv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Remote-friendly showtimes: dates across the top, a scrollable three-column grid below. */
final class CinemaSessions extends Dialog {
    private static final ZoneId ZONE=ZoneId.of("Europe/Madrid");
    private static final int INK=0xFFF5F6FA, DIM=0xFFB2B8C4, BLUE=0xFFA5D7FF;
    private final Activity activity;
    private final Cinema.Movie movie;
    private final Backdrop backdrop;
    private final Map<LocalDate,List<Cinema.Session>> grouped=new LinkedHashMap<>();
    private final List<LocalDate> dates=new ArrayList<>();
    private final List<TextView> tabs=new ArrayList<>();
    private final float scale;
    private LinearLayout grid;
    private ScrollView scroll;
    private TextView heading,close;
    private View firstCard;
    private int selected;

    CinemaSessions(Activity a,Cinema.Movie m,List<Cinema.Session> sessions,Backdrop b) {
        super(a);
        activity=a; movie=m; backdrop=b;
        scale=Math.min(a.getResources().getDisplayMetrics().widthPixels/1920f,
                a.getResources().getDisplayMetrics().heightPixels/1080f);
        for(Cinema.Session s:sessions) {
            LocalDate date=Instant.ofEpochMilli(s.time).atZone(ZONE).toLocalDate();
            List<Cinema.Session> day=grouped.get(date);
            if(day==null) { day=new ArrayList<>(); grouped.put(date,day); }
            day.add(s);
        }
        dates.addAll(grouped.keySet());
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=column();
        root.setPadding(px(40),px(34),px(40),px(28));
        root.setBackground(shape(0xFA171B23,0xFF414956,30));
        root.setClipToOutline(true);
        setContentView(root);
        buildHeader(root);
        buildDates(root);
        View line=new View(activity); line.setBackgroundColor(0xFF3A414C);
        LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(-1,px(1));
        rule.topMargin=px(22); rule.bottomMargin=px(18); root.addView(line,rule);
        heading=text("",27,Ui.semibold,INK);
        root.addView(heading,new LinearLayout.LayoutParams(-1,px(44)));
        scroll=new ScrollView(activity);
        scroll.setClipToPadding(false);
        scroll.setPadding(px(5),px(5),px(5),px(5));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        grid=column(); scroll.addView(grid);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=text(Ui.tr("← → Change day · ↓ Showtimes · ↩ Close"),21,Ui.regular,DIM);
        hint.setContentDescription(Ui.tr("← → Change day · ↓ Showtimes · Back Close"));
        LinearLayout.LayoutParams footer=new LinearLayout.LayoutParams(-1,px(36));
        footer.topMargin=px(16); root.addView(hint,footer);
        TextView source=text(Ui.tr("Source: Kinépolis. Showtimes may change."),18,Ui.regular,0xFF8D95A3);
        root.addView(source,new LinearLayout.LayoutParams(-1,px(26)));
        selectDay(0);
    }

    @Override public void show() {
        super.show();
        Window w=getWindow();
        if(w!=null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(.68f);
            w.setLayout(px(1400),px(870));
            w.setGravity(Gravity.CENTER);
            w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        }
        tabs.get(0).requestFocus();
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout row=new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        if(backdrop!=null && backdrop.image!=null) {
            ImageView art=new ImageView(activity);
            art.setImageBitmap(backdrop.image); art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setBackground(shape(0xFF242A34,0,12)); art.setClipToOutline(true);
            art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams thumb=new LinearLayout.LayoutParams(px(116),px(146));
            thumb.rightMargin=px(30); row.addView(art,thumb);
        }
        LinearLayout info=column();
        TextView title=text(movie.title,42,Ui.displaySemibold,INK);
        title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);
        TextView cinema=text("Kinépolis · Ciudad de la Imagen",27,Ui.medium,INK);
        cinema.setPadding(0,px(9),0,px(8)); info.addView(cinema);
        String meta=movie.year;
        if(movie.minutes>0) meta+=(meta.isEmpty()?"":" · ")+Ui.trf("{n} min","n",movie.minutes);
        info.addView(text(meta,23,Ui.regular,DIM));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        close=text("×",40,Ui.regular,INK); close.setGravity(Gravity.CENTER);
        close.setId(View.generateViewId()); close.setContentDescription(Ui.tr("Close"));
        decorate(close,false,32); close.setOnClickListener(v->dismiss());
        LinearLayout.LayoutParams closeSize=new LinearLayout.LayoutParams(px(64),px(64));
        closeSize.leftMargin=px(20); closeSize.gravity=Gravity.TOP; row.addView(close,closeSize);
        LinearLayout.LayoutParams header=new LinearLayout.LayoutParams(-1,px(158));
        header.bottomMargin=px(24); root.addView(row,header);
    }

    private void buildDates(LinearLayout root) {
        HorizontalScrollView strip=new HorizontalScrollView(activity);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false); strip.setPadding(px(3),px(3),px(3),px(3));
        LinearLayout row=new LinearLayout(activity); strip.addView(row);
        LocalDate today=LocalDate.now(ZONE);
        DateTimeFormatter weekday=DateTimeFormatter.ofPattern("EEEE",Ui.locale());
        DateTimeFormatter date=DateTimeFormatter.ofPattern("d MMM",Ui.locale());
        for(int i=0;i<dates.size();i++) {
            final int at=i; LocalDate day=dates.get(i);
            String label=day.equals(today)?Ui.tr("Today"):day.equals(today.plusDays(1))?Ui.tr("Tomorrow"):weekday.format(day);
            TextView tab=text(label+"\n"+date.format(day),24,Ui.medium,INK);
            tab.setGravity(Gravity.CENTER); tab.setId(View.generateViewId());
            tab.setFocusable(true); tab.setFocusableInTouchMode(true);
            tab.setOnFocusChangeListener((v,focused)->{ if(focused) selectDay(at); paintTabs(); });
            tab.setOnClickListener(v->{ selectDay(at); focusFirstCard(); });
            tab.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_DOWN) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN) focusFirstCard();
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_UP) {
                    if(event.getAction()==KeyEvent.ACTION_DOWN) close.requestFocus();
                    return true;
                }
                return false;
            });
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(px(244),px(88));
            if(i>0) size.leftMargin=px(14); row.addView(tab,size); tabs.add(tab);
        }
        root.addView(strip,new LinearLayout.LayoutParams(-1,px(96)));
        close.setNextFocusDownId(tabs.get(0).getId());
    }

    private void paintTabs() {
        for(int i=0;i<tabs.size();i++) {
            TextView tab=tabs.get(i); boolean active=i==selected;
            tab.setTextColor(active?0xFF102234:INK);
            tab.setBackground(shape(active?BLUE:0xFF242B35,tab.hasFocus()?0xFFFFFFFF:0,20));
        }
    }

    private void selectDay(int index) {
        if(grid==null || (index==selected && grid.getChildCount()>0)) return;
        selected=index; paintTabs();
        LocalDate day=dates.get(index);
        heading.setText(day.equals(LocalDate.now(ZONE))?Ui.tr("Today's showtimes"):
                Ui.trf("Showtimes · {date}","date",DateTimeFormatter.ofPattern("EEEE d MMM",Ui.locale()).format(day)));
        grid.removeAllViews(); firstCard=null; scroll.scrollTo(0,0);
        List<Cinema.Session> sessions=new ArrayList<>();
        long now=System.currentTimeMillis();
        for(Cinema.Session s:grouped.get(day)) if(s.time>now) sessions.add(s);
        LinearLayout row=null;
        DateTimeFormatter clock=DateTimeFormatter.ofPattern("HH:mm");
        for(int i=0;i<sessions.size();i++) {
            if(i%3==0) {
                row=new LinearLayout(activity);
                LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,px(146));
                rowSize.bottomMargin=px(16); grid.addView(row,rowSize);
            }
            Cinema.Session s=sessions.get(i);
            LinearLayout card=column(); card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(px(24),px(12),px(18),px(12));
            int color=s.soldOut?0xFF7F8793:INK;
            String time=clock.format(Instant.ofEpochMilli(s.time).atZone(ZONE));
            card.addView(text(time,36,Ui.semibold,color));
            String detail=s.language+(s.language.isEmpty()||s.format.isEmpty()?"":" · ")+s.format;
            TextView format=text(detail,22,Ui.regular,s.soldOut?0xFF7F8793:DIM);
            format.setSingleLine(true); format.setEllipsize(TextUtils.TruncateAt.END); card.addView(format);
            String hall=Ui.trf("Screen {n}","n",s.hall);
            card.addView(text(s.soldOut?Ui.tr("Sold out"):hall,22,Ui.regular,s.soldOut?0xFF7F8793:DIM));
            card.setContentDescription(time+", "+detail+", "+hall+(s.soldOut?", "+Ui.tr("Sold out"):""));
            card.setId(View.generateViewId()); decorate(card,s.soldOut,20);
            if(!s.soldOut && firstCard==null) firstCard=card;
            if(i<3) card.setNextFocusUpId(tabs.get(index).getId());
            LinearLayout.LayoutParams cardSize=new LinearLayout.LayoutParams(0,-1,1);
            if(i%3>0) cardSize.leftMargin=px(16); row.addView(card,cardSize);
        }
        // Empty cells keep the last row's cards the same width as all preceding rows.
        if(row!=null) for(int i=sessions.size()%3;i>0 && i<3;i++) {
            View empty=new View(activity);
            LinearLayout.LayoutParams blank=new LinearLayout.LayoutParams(0,-1,1);
            blank.leftMargin=px(16); row.addView(empty,blank);
        }
        if(sessions.isEmpty()) grid.addView(text(Ui.tr("No more showtimes today."),26,Ui.regular,DIM));
        close.setNextFocusDownId(tabs.get(index).getId());
    }

    private void focusFirstCard() { if(firstCard!=null) firstCard.requestFocus(); }

    private void decorate(View view,boolean disabled,int radius) {
        view.setBackground(shape(disabled?0xFF20252D:0xFF202630,0xFF414956,radius));
        view.setFocusable(!disabled); view.setFocusableInTouchMode(!disabled);
        if(disabled) return;
        view.setOnFocusChangeListener((v,focused)->{
            v.setBackground(shape(focused?0xFF283546:0xFF202630,focused?BLUE:0xFF414956,radius));
            v.setElevation(focused?px(5):0);
        });
    }

    private GradientDrawable shape(int fill,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(px(radius));
        if(stroke!=0) d.setStroke(px(stroke==BLUE?3:1),stroke);
        return d;
    }
    private TextView text(String value,int size,Typeface type,int color) {
        TextView v=new TextView(activity); v.setText(value); v.setTextColor(color); v.setTypeface(type);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size)); v.setIncludeFontPadding(false);
        return v;
    }
    private LinearLayout column() { LinearLayout v=new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private int px(float value) { return Math.max(1,Math.round(value*scale)); }
}
