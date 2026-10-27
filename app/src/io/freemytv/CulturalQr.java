package io.freemytv;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local-only QR, with integer modules and a four-module white quiet zone. No tracking/shortening service. */
final class CulturalQr {
    static int[] pixels(String url,int size) throws WriterException {
        if(url==null || !CulturalData.allowed(url,"www.esmadrid.com","esmadrid.com") ||
                url.getBytes(StandardCharsets.UTF_8).length>2048 || size<128 || size>1024)throw new IllegalArgumentException("Invalid event QR");
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(EncodeHintType.MARGIN,4);
        hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);
        BitMatrix modules=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,0,0,hints);
        if(modules.getWidth()*2>size)throw new IllegalArgumentException("Event QR too dense for this screen");
        BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,size,size,hints);
        int[] result=new int[size*size];
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)result[y*size+x]=matrix.get(x,y)?0xFF000000:0xFFFFFFFF;
        return result;
    }
}
