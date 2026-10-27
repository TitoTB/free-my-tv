package io.freemytv;

/** Shared-album parsing must reject unrelated assets and authenticated/arbitrary hosts. */
public final class PhotoAlbumChecks {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Photo check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        check(ExperiencePhotos.valid(""));
        check(ExperiencePhotos.valid("https://photos.app.goo.gl/example"));
        check(ExperiencePhotos.valid("https://photos.google.com/share/example"));
        for(String value:new String[]{"http://photos.google.com/share/a","https://example.org/a","https://photos.google.com.example.org/a","https://user@photos.google.com/a","https://photos.google.com:8123/a","file:///tmp/a"})check(!ExperiencePhotos.valid(value));
        String photo="https://lh3.googleusercontent.com/photo";
        String json="[null,[[\"not a URL\",400,400],[\""+photo+"\",600,400],[\""+photo+"\",600,400],[\"https://example.org/other\",600,400],[\"https://lh4.googleusercontent.com/icon\",64,64]]]";
        check(ExperiencePhotos.sources("AF_initDataCallback({key:'x',data:"+json+",sideChannel:{}});").size()==1);
        check(ExperiencePhotos.sources("No photo data").isEmpty());
        check(ExperiencePhotos.sources("AF_initDataCallback({data:broken}").isEmpty());
        if(args.length>0){String html=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),"UTF-8");check(ExperiencePhotos.sources(html).size()>=3);}
        System.out.println("Photo album checks: "+checks+" passed");
    }
}
