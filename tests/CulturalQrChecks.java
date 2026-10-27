package io.freemytv;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** Decode the actual generated pixels, including the smaller 720p TV rendering. */
public final class CulturalQrChecks {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args) throws Exception {
        String[] urls={"https://www.esmadrid.com/agenda/concierto",
            "https://www.esmadrid.com/agenda/música-en-madrid?fecha=2026-10-24&idioma=es",
            "https://esmadrid.com/agenda/una-noche-especial-en-el-teatro-de-madrid"};
        for(String url:urls)for(int size:new int[]{200,300,600}) {
            int[] pixels=CulturalQr.pixels(url,size);
            check(pixels.length==size*size,"Exact dimensions");
            for(int x=0;x<size;x++)check(pixels[x]==0xFFFFFFFF && pixels[(size-1)*size+x]==0xFFFFFFFF,"White quiet zone");
            Result result=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size,size,pixels))));
            check(url.equals(result.getText()),"QR decodes to original URL");
        }
        for(String invalid:new String[]{null,"http://www.esmadrid.com/agenda/a","https://evil.example/","https://www.esmadrid.com.evil.example/","https://www.esmadrid.com@evil.example/","https://www.esmadrid.com/"+new String(new char[2100]).replace('\0','a')}) {
            boolean rejected=false;try{CulturalQr.pixels(invalid,300);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"Reject unsupported/oversized URL");
        }
        for(int invalid:new int[]{0,127,1025}) {
            boolean rejected=false;try{CulturalQr.pixels(urls[0],invalid);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"Reject invalid dimensions");
        }
        System.out.println("CulturalQrChecks: "+checks+" checks passed");
    }
}
