package io.freemytv;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Compressed artwork on disk; per-file locks deduplicate foreground and background transfers. */
final class CinemaImageCache {
    interface Downloader { void download(String source,File destination) throws Exception; }
    private final File directory;
    private final ConcurrentHashMap<String,Object> locks=new ConcurrentHashMap<>();
    private java.util.Set<Integer> activeIds;
    CinemaImageCache(File directory) { this.directory=directory; }
    // Keep the existing filename scheme, so installed versions' downloaded images remain reusable.
    File file(int id,String source) { return new File(directory,id+"-"+Integer.toHexString(source.hashCode())+".jpg"); }
    File ensure(int id,String source,Downloader downloader) throws Exception {
        File target=file(id,source);
        Object lock=locks.computeIfAbsent(target.getName(),key->new Object());
        synchronized(lock) {
            if(valid(target)) return target;
            if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Artwork cache directory");
            File part=new File(directory,target.getName()+".part");
            try {
                downloader.download(source,part);
                if(!valid(part)) throw new IOException("Invalid cached artwork");
                synchronized(this) {
                    if(activeIds!=null && !activeIds.contains(id)) throw new IOException("Artwork no longer in programme");
                    if(target.exists() && !target.delete()) throw new IOException("Artwork replacement");
                    if(!part.renameTo(target)) throw new IOException("Artwork commit");
                }
                return target;
            } finally { if(part.exists()) part.delete(); }
        }
    }
    /** Only call with a successfully checked programme. Failed/offline checks never evict existing artwork. */
    synchronized void reconcile(Map<Integer,String> programme) {
        Map<Integer,java.util.Set<String>> sources=new java.util.HashMap<>();
        for(Map.Entry<Integer,String> entry:programme.entrySet()) sources.put(entry.getKey(),java.util.Collections.singleton(entry.getValue()));
        reconcileSources(sources);
    }
    synchronized void reconcileSources(Map<Integer,java.util.Set<String>> programme) {
        activeIds=new java.util.HashSet<>(programme.keySet());
        File[] files=directory.listFiles((dir,name)->name.endsWith(".jpg"));
        if(files==null) return;
        for(File existing:files) {
            String name=existing.getName(); int separator=name.indexOf('-',name.startsWith("-")?1:0);
            if(separator<0) continue;
            int id;
            try { id=Integer.parseInt(name.substring(0,separator)); } catch(NumberFormatException ignored) { continue; }
            java.util.Set<String> sources=programme.get(id);
            if(sources==null) existing.delete();
            else {
                boolean keep=false,replacement=false;
                for(String source:sources) {
                    if(source.isEmpty()) continue;
                    File expected=file(id,source); keep|=existing.equals(expected); replacement|=valid(expected);
                }
                // Keep both the selected raw poster and composition; preserve old files until a replacement exists.
                if(!keep && replacement) existing.delete();
            }
        }
    }
    private static boolean valid(File file) {
        if(!file.isFile() || file.length()<12 || file.length()>8*1024*1024) return false;
        byte[] header=new byte[12];
        try(FileInputStream in=new FileInputStream(file)) {
            if(in.read(header)!=header.length) return false;
            return ((header[0]&255)==255 && (header[1]&255)==216 && (header[2]&255)==255)
                    || (header[0]==(byte)137 && header[1]=='P' && header[2]=='N' && header[3]=='G')
                    || (header[0]=='R' && header[1]=='I' && header[2]=='F' && header[3]=='F'
                        && header[8]=='W' && header[9]=='E' && header[10]=='B' && header[11]=='P');
        } catch(IOException ignored) { return false; }
    }
}
