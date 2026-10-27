package io.freemytv;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

/** Serialises setup with Original/watchdog across app processes; backups contain components, never credentials. */
final class AssistRoleManager {
    private static final Object LOCK=new Object();
    static void enable(Context context)throws IOException {
        if(Build.VERSION.SDK_INT<29||Config.DISABLE_ASSISTANT)throw new IOException("Voice setup unavailable");
        try(Adb adb=Adb.connect(context,10000,null)){select(context,adb,true);}
    }
    static void restore(Context context)throws IOException {
        try(Adb adb=Adb.connect(context,10000,null)){restore(context,adb);}
    }
    static void restore(Context context,Adb adb)throws IOException {
        if(!new File(context.getFilesDir(),"assist-original.json").exists()&&!new File(context.getFilesDir(),"assist-original.json.bak").exists())return;
        select(context,adb,false);
    }
    private static void select(Context context,Adb adb,boolean enable)throws IOException {
        synchronized(LOCK){
            try(RandomAccessFile lock=new RandomAccessFile(new File(context.getFilesDir(),"assist-role.lock"),"rw");FileLock held=lock.getChannel().lock()){
                AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"assist-original.json"));
                AssistRoleSelection.Store store=new AssistRoleSelection.Store(){
                    public String read()throws IOException{
                        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                            byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(out.size()+n>8192)throw new IOException("Invalid backup size");out.write(b,0,n);}
                            return out.toString("UTF-8");
                        }catch(FileNotFoundException missing){return "";}
                    }
                    public void write(String json)throws IOException{
                        FileOutputStream out=null;
                        try{out=file.startWrite();out.write(json.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
                        catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
                    }
                    public void clear(){file.delete();}
                };
                if(enable){
                    if(!HomeMode.FREE.equals(HomeMode.state(context).optString("mode")))throw new IOException("Original mode is active");
                    String home=adb.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
                    if(!home.contains("io.freemytv/.HomeActivity")&&!home.contains("io.freemytv/io.freemytv.HomeActivity"))throw new IOException("Home screen is not active");
                }
                AssistRoleSelection selection=new AssistRoleSelection(adb::shell,store);
                if(enable)selection.enable();else selection.restore();
            }
        }
    }
}
