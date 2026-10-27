package io.freemytv;

/** Pure quota parsing checks. No real account calls or speech generation. */
public final class ElevenLabsUsageChecks {
    private static int count;
    private static void check(boolean ok){if(!ok)throw new AssertionError("Quota check "+count);count++;}
    public static void main(String[] args)throws Exception{
        ElevenLabsUsage.Snapshot full=ElevenLabsUsage.parse("{\"character_count\":0,\"character_limit\":10000,\"next_character_count_reset_unix\":1794000000}");
        check(full.remainingPercent()==100d);check(full.resetMillis==1794000000000L);
        check(ElevenLabsUsage.parse("{\"character_count\":2500,\"character_limit\":10000}").remainingPercent()==75d);
        check(ElevenLabsUsage.parse("{\"character_count\":10000,\"character_limit\":10000}").remainingPercent()==0d);
        check(ElevenLabsUsage.parse("{\"character_count\":12000,\"character_limit\":10000}").remainingPercent()==0d);
        check(ElevenLabsUsage.parse("{\"character_count\":1,\"character_limit\":3}").remainingPercent()==66.6d);
        check(ElevenLabsUsage.parse("{\"character_count\":1,\"character_limit\":10000}").remainingPercent()==99.9d);
        check(ElevenLabsUsage.parse("{\"character_count\":1,\"character_limit\":0}").remainingPercent()==null);
        check(ElevenLabsUsage.parse("{}").remainingPercent()==null);
        for(String bad:new String[]{"null","-1","\"invalid\"","\"0\"","1.5","true"}){
            check(ElevenLabsUsage.parse("{\"character_count\":"+bad+",\"character_limit\":10000}").remainingPercent()==null);
            check(ElevenLabsUsage.parse("{\"character_count\":0,\"character_limit\":"+bad+"}").remainingPercent()==null);
        }
        for(String bad:new String[]{"null","0","-1","\"invalid\"","9223372036854775807"})
            check(ElevenLabsUsage.parse("{\"next_character_count_reset_unix\":"+bad+"}").resetMillis==null);
        check(ElevenLabsUsage.parse("{}").resetMillis==null);
        check(ElevenLabsUsage.parse("{\"character_count\":9223372036854775807,\"character_limit\":9223372036854775807}").remainingPercent()==0d);
        check(ElevenLabsUsage.parse("{\"character_count\":0,\"character_limit\":9223372036854775807}").remainingPercent()==100d);
        System.out.println("ElevenLabsUsageChecks: "+count+" checks passed");
    }
}
