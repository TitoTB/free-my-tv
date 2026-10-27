package io.freemytv;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** One scoped return request shared with the remote service's separate process. */
final class TrailerReturn {
    private static final long MAX_AGE = 6 * 60 * 60_000L;
    static final class Request {
        final String pkg;
        final long until;
        Request(String p,long u) { pkg=p; until=u; }
    }
    private static File file(Context c) { return new File(c.getFilesDir(),"trailer-return.txt"); }
    static void arm(Context c,String pkg) {
        try (OutputStream out=new FileOutputStream(file(c))) {
            out.write((pkg+"\n"+(System.currentTimeMillis()+MAX_AGE)).getBytes(StandardCharsets.UTF_8));
        } catch(IOException e) { AppLog.w("couldn't prepare the trailer return action"); }
    }
    static Request read(Context c) {
        File f=file(c);
        if(!f.isFile() || f.length()>256) return null;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))) {
            String pkg=in.readLine(); long until=Long.parseLong(in.readLine());
            long remaining=until-System.currentTimeMillis();
            if(!"com.google.android.youtube.tv".equals(pkg) || remaining<=0 || remaining>MAX_AGE) return null;
            return new Request(pkg,until);
        } catch(Exception e) { return null; }
    }
    static void clear(Context c) { file(c).delete(); }
}
