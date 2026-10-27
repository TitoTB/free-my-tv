package io.freemytv;

/** The rows expose only complete on-screen cards, with no partial card or scrolling. */
public final class RecentRowChecks {
    public static void main(String[] args) {
        if(RecentRow.visibleCount(-1)!=0 || RecentRow.visibleCount(0)!=0 || RecentRow.visibleCount(2)!=2
                || RecentRow.visibleCount(5)!=5 || RecentRow.visibleCount(100)!=5)
            throw new AssertionError("Only the first five available results should be exposed");
        for(int slot=0;slot<RecentRow.VISIBLE;slot++) {
            float left=80+slot*RecentRow.CARD_STEP;
            float focusOverflow=RecentRow.CARD_WIDTH*0.045f/2+5.5f;
            if(left-focusOverflow<0 || left+RecentRow.CARD_WIDTH+focusOverflow>1920)
                throw new AssertionError("Focused card clipped at the screen edge");
            if(slot>0 && RecentRow.CARD_STEP-RecentRow.CARD_WIDTH<2*focusOverflow-5.5f)
                throw new AssertionError("Focused card overlaps its neighbour");
        }
        float lastRight=80+(RecentRow.VISIBLE-1)*RecentRow.CARD_STEP+RecentRow.CARD_WIDTH;
        if(Math.abs(lastRight-1840)>0.01f) throw new AssertionError("Row must fill the width between the home margins");
        System.out.println("PASS five complete cards fill the home width without focus clipping or overlap");
        if(Math.abs(RecentRow.CARD_WIDTH/RecentRow.CARD_HEIGHT-16f/9)>0.001f)
            throw new AssertionError("Landscape ratio");
        System.out.println("PASS landscape cards use a true 16:9 ratio");
        if(RecentRow.DETAILS_Y>=1080+RecentRow.ROW_GAP-34) throw new AssertionError("Metadata overlaps next heading");
        System.out.println("PASS card metadata clears the following row heading");
    }
}
