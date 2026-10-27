package io.freemytv;

import android.content.Context;
import android.graphics.BitmapFactory;
import java.io.*;
import java.time.*;

/** Five bundled backgrounds selected by local sunlight and weather, with no image service or TMDB dependency. */
final class SkylineLandscape {
    private static final long MINUTE=60_000L,DAY=24*60*MINUTE;
    static String phase(long now,long sunrise,long sunset,int weatherCode,ZoneId zone) {
        long time=time(now,zone),rise=sunrise>0?time(sunrise,zone):6*60*MINUTE;
        long set=sunset>0?time(sunset,zone):18*60*MINUTE;
        long dawn=distance(time,rise),dusk=distance(time,set);
        boolean twilight=(dawn>=-45*MINUTE && dawn<45*MINUTE) || (dusk>=-45*MINUTE && dusk<45*MINUTE);
        boolean day=rise<set?time>=rise && time<set:time>=rise || time<set;
        if(!day && !twilight) return "twilight";
        // Open-Meteo WMO codes: overcast, fog, precipitation and thunderstorms.
        boolean adverse=weatherCode==3 || weatherCode==45 || weatherCode==48
            || (weatherCode>=51 && weatherCode<=67) || (weatherCode>=71 && weatherCode<=77)
            || (weatherCode>=80 && weatherCode<=86) || (weatherCode>=95 && weatherCode<=99);
        if(adverse) return "storm";
        if(twilight) return "sunflare";
        return weatherCode==2?"cloudy":"blue";
    }

    private static long time(long epoch,ZoneId zone) {
        LocalTime local=Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime();
        return local.toSecondOfDay()*1000L;
    }
    private static long distance(long time,long anchor) { return Math.floorMod(time-anchor+DAY/2,DAY)-DAY/2; }
    static boolean validPhase(String phase) { return "blue".equals(phase)||"cloudy".equals(phase)||"storm".equals(phase)||"sunflare".equals(phase)||"twilight".equals(phase); }
    static synchronized File image(Context context,String phase) throws IOException {
        if(!validPhase(phase)) throw new IOException("Unknown landscape phase");
        File directory=new File(context.getCacheDir(),"skyline-v1");
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Landscape cache");
        File file=new File(directory,phase+".jpg");
        if(readable(file)) return file;
        File part=new File(directory,phase+".part");
        try {
            try(InputStream in=context.getAssets().open("landscapes/skyline/"+phase+".jpg"); OutputStream out=new FileOutputStream(part)) {
                byte[] buffer=new byte[8192]; int n;
                while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            }
            if(!readable(part)) throw new IOException("Invalid landscape image");
            if(file.exists() && !file.delete()) throw new IOException("Landscape replacement");
            if(!part.renameTo(file)) throw new IOException("Landscape cache commit");
            return file;
        } finally { if(part.exists()) part.delete(); }
    }
    private static boolean readable(File file) {
        if(!file.isFile()) return false;
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),bounds); return bounds.outWidth>0 && bounds.outHeight>0;
    }
}
