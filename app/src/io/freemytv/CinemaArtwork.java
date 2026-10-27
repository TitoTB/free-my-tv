package io.freemytv;

/** Reject transparent/solid placeholders, not just files with a readable image header. */
final class CinemaArtwork {
    static boolean usable(int width,int height,int[] sample) {
        if(width<2 || height<2 || sample.length==0) return false;
        int minR=255,minG=255,minB=255,maxR=0,maxG=0,maxB=0,visible=0;
        for(int pixel:sample) {
            if((pixel>>>24)<16) continue;
            visible++;
            int r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
            minR=Math.min(minR,r);maxR=Math.max(maxR,r);
            minG=Math.min(minG,g);maxG=Math.max(maxG,g);
            minB=Math.min(minB,b);maxB=Math.max(maxB,b);
        }
        return visible>0 && (maxR-minR>3 || maxG-minG>3 || maxB-minB>3);
    }
}
