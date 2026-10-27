package io.freemytv;

import java.time.*;

public final class SkylineLandscapeChecks {
    private static final ZoneId MADRID=ZoneId.of("Europe/Madrid");
    private static int checks;
    private static long at(String time) { return LocalDateTime.parse("2026-10-23T"+time).atZone(MADRID).toInstant().toEpochMilli(); }
    private static void expect(String expected,long now,long rise,long set,int code) {
        String actual=SkylineLandscape.phase(now,rise,set,code,MADRID);
        if(!expected.equals(actual)) throw new AssertionError(expected+" != "+actual+", weather "+code);
        checks++;
    }
    public static void main(String[] args) {
        long rise=at("08:33"),set=at("19:25");
        for(int code:new int[]{-1,0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99}) {
            boolean adverse=code>=3;
            expect("twilight",at("00:00"),rise,set,code);
            expect("twilight",at("23:59"),rise,set,code);
            expect(adverse?"storm":"sunflare",at("08:33"),rise,set,code);
            expect(adverse?"storm":"sunflare",at("19:25"),rise,set,code);
            expect(adverse?"storm":code==2?"cloudy":"blue",at("12:00"),rise,set,code);
        }
        String[][] boundaries={{"07:47:59","twilight"},{"07:48","sunflare"},{"09:17:59","sunflare"},
            {"09:18","blue"},{"18:39:59","blue"},{"18:40","sunflare"},{"20:09:59","sunflare"},{"20:10","twilight"}};
        for(String[] c:boundaries) expect(c[1],at(c[0]),rise,set,0);
        expect("blue",at("12:00"),0,0,-1);
        expect("twilight",at("00:00"),0,0,-1);
        expect("sunflare",at("05:15"),0,0,-1);
        expect("sunflare",at("17:15"),0,0,-1);
        expect("sunflare",Instant.parse("2026-10-23T06:33:00Z").toEpochMilli(),rise,set,0);
        expect("cloudy",at("12:00"),rise-86_400_000L,set-86_400_000L,2);
        long winterRise=ZonedDateTime.of(2026,12,1,8,20,0,0,MADRID).toInstant().toEpochMilli();
        long winterSet=ZonedDateTime.of(2026,12,1,17,50,0,0,MADRID).toInstant().toEpochMilli();
        expect("sunflare",Instant.parse("2026-12-01T07:20:00Z").toEpochMilli(),winterRise,winterSet,1);
        for(String id:new String[]{"blue","cloudy","storm","sunflare","twilight"}) {
            if(!SkylineLandscape.validPhase(id)) throw new AssertionError(id); checks++;
        }
        for(String id:new String[]{null,"../blue","Blue","","dawn","night"}) {
            if(SkylineLandscape.validPhase(id)) throw new AssertionError("Invalid id accepted"); checks++;
        }
        System.out.println("Skyline: "+checks+" checks passed");
    }
}
