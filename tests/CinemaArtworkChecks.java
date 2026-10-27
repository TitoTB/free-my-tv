package io.freemytv;

public final class CinemaArtworkChecks {
    private static void check(boolean value,String name) { if(!value) throw new AssertionError(name);System.out.println("PASS "+name); }
    public static void main(String[] args) throws Exception {
        check(!CinemaArtwork.usable(1920,1080,new int[]{0xFF000000,0xFF000000,0xFF000000}),"opaque black placeholder is not movie artwork");
        check(!CinemaArtwork.usable(1920,1080,new int[]{0xFFEEEEEE,0xFFEFEFEF,0xFFECECEC}),"near-solid placeholder is not artwork");
        check(!CinemaArtwork.usable(1920,1080,new int[]{0x00FF0000,0x0000FF00,0x000000FF}),"hidden RGB in a fully transparent image is rejected");
        check(CinemaArtwork.usable(2000,3000,new int[]{0xFF000000,0xFF325B76,0xFFFECD15}),"available colour poster remains eligible as a fallback");
        check(CinemaArtwork.usable(2000,3000,new int[]{0xFF000000,0xFF747474,0xFFEFEFEF}),"black and white photographs remain eligible");
        check(!CinemaArtwork.usable(1,1,new int[]{0xFF000000,0xFFFFFFFF}),"one-pixel tracking/placeholder source is rejected");
        if(args.length>0) {
            java.awt.image.BufferedImage photo=javax.imageio.ImageIO.read(new java.io.File(args[0]));
            int[] sample=new int[64*64];
            for(int y=0;y<64;y++) for(int x=0;x<64;x++) sample[y*64+x]=photo.getRGB(x*photo.getWidth()/64,y*photo.getHeight()/64);
            check(CinemaArtwork.usable(photo.getWidth(),photo.getHeight(),sample),"real La maleta poster is valid fallback artwork");
        }
    }
}
