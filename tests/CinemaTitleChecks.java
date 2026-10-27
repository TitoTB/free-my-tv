package io.freemytv;

public final class CinemaTitleChecks {
    private static int checks;
    private static void check(String source,String expected){
        String actual=CinemaTitle.clean(source);
        if(!expected.equals(actual))throw new AssertionError(source+" -> "+actual);
        if(!actual.equals(CinemaTitle.clean(actual)))throw new AssertionError("Not idempotent: "+source);
        checks++;
    }
    public static void main(String[] args){
        check("La bola negra (sin subtitulos)","La bola negra");
        check("La bola negra (sin subtítulos)","La bola negra");
        check("La bola negra (SIN SUBTÍTULOS)","La bola negra");
        check("La bola negra (con subtítulos)","La bola negra");
        check("  VOSE La bola negra ( sin subtítulos )  ","La bola negra");
        check("VOSI La bola negra (subtitulos en inglés)","La bola negra");
        check("VOSI La bola negra (subtítulos en ingles)","La bola negra");
        check("VOSE Test","Test");check("VOS Test","Test");check("VO Test","Test");
        check("CAT Test","Test");check("VAL Test","Test");
        check("La bola negra","La bola negra");
        check("La bola negra (2026)","La bola negra (2026)");
        check("La bola negra (Parte II)","La bola negra (Parte II)");
        check("(500) días juntos","(500) días juntos");
        check("Birdman (o la inesperada virtud de la ignorancia)","Birdman (o la inesperada virtud de la ignorancia)");
        check("Una historia sin subtítulos","Una historia sin subtítulos");
        check("(sin subtítulos)","(sin subtítulos)");
        check("","");check(null,"");
        System.out.println("CinemaTitleChecks: "+checks+" passed");
    }
}
