package io.freemytv;

import java.time.*;

/** Date-based remote cursor; month lengths and leap years need no special UI cases. */
final class CalendarNavigation {
    LocalDate selected;
    boolean header;
    int side=1;
    CalendarNavigation(LocalDate today){selected=today;}
    YearMonth month(){return YearMonth.from(selected);}
    int node(){return header?side:selected.getDayOfMonth()+1;}
    int count(){return month().lengthOfMonth()+2;}
    void select(int node){header=node<2;if(header)side=node;else selected=month().atDay(node-1);}
    void shift(int direction){selected=selected.plusMonths(direction);}
    /** False means leave the calendar upwards for device controls. */
    boolean move(int dx,int dy) {
        if(header) {
            if(dy<0)return false;
            if(dy>0)header=false;
            if(dx!=0){side=dx<0?0:1;shift(dx);}
        } else if(dx!=0)selected=selected.plusDays(dx);
        else if(dy<0) {
            if(selected.getDayOfMonth()<=7)header=true;else selected=selected.minusDays(7);
        } else if(dy>0 && selected.getDayOfMonth()+7<=month().lengthOfMonth())selected=selected.plusDays(7);
        return true;
    }
}
