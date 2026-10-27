package io.freemytv;

import java.time.YearMonth;
import java.util.*;

/** Remote and accessibility share the same visible indices for editorial, posters and month-grouped agenda. */
final class CulturalPaging {
    static final int EDITORIAL=0,POSTERS=1,AGENDA=2;
    int mode,first,count,selected,total;
    private int tab=-1;
    private List<CulturalData.Hit> previous;
    private final List<Integer> agendaPages=new ArrayList<>();
    void update(List<CulturalData.Hit> results,int selected,int tab,boolean favourite) {
        int nextMode=tab==3?POSTERS:tab==4?AGENDA:EDITORIAL;
        boolean tabChanged=this.tab!=tab;
        this.tab=tab;this.selected=selected;this.total=results.size();mode=nextMode;
        if(mode==AGENDA && (previous!=results || tabChanged)) {
            agendaPages.clear();int start=0;
            while(start<total) {
                agendaPages.add(start);int height=0,n=0;YearMonth month=null;
                while(start+n<total && n<4) {
                    YearMonth next=YearMonth.from(results.get(start+n).date);
                    int extra=(next.equals(month)?0:28)+74;
                    if(height+extra>340)break;
                    height+=extra;month=next;n++;
                }
                start+=Math.max(1,n);
            }
        }
        previous=results;
        if(total==0){first=count=0;return;}
        if(mode==AGENDA) {
            int page=Collections.binarySearch(agendaPages,selected);if(page<0)page=-page-2;page=Math.max(0,page);
            first=agendaPages.get(page);count=(page+1<agendaPages.size()?agendaPages.get(page+1):total)-first;
        } else {int size=mode==POSTERS?10:5;first=selected/size*size;count=Math.min(size,total-first);}
    }
    int index(int slot) {return slot<0 || slot>=count?-1:first+slot;}
    int slot(int index) {return index-first;}
    int horizontal(int direction) {
        if(mode==POSTERS)return Math.max(0,Math.min(total-1,selected+direction));
        if(mode==AGENDA) {
            int page=agendaPages.indexOf(first),next=page+(direction>0?1:-1);
            return next>=0 && next<agendaPages.size()?agendaPages.get(next):selected;
        }
        int slot=slot(selected);
        if(direction>0) {
            if(slot==0)return count>1?index(1):selected;
            if(slot<=2 && slot+2<count)return index(slot+2);
            int next=first+5;return next<total?next:selected;
        }
        if(slot>=3)return index(slot-2);
        if(slot>0)return index(0);
        return first>0?first-1:selected;
    }
    /** -1: tabs, -2: device controls. */
    int vertical(int direction) {
        int slot=slot(selected);
        if(mode==AGENDA)return direction<0?(slot>0?index(slot-1):-1):(slot+1<count?index(slot+1):-2);
        if(mode==POSTERS)return direction<0?(slot>=5?index(slot-5):-1):
                (slot<5 && slot+5<count?index(slot+5):-2);
        return direction<0?(slot>0 && slot%2==0?index(slot-1):-1):
                (slot>0 && slot%2==1 && slot+1<count?index(slot+1):-2);
    }
}
