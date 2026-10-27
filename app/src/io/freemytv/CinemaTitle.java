package io.freemytv;

import java.util.regex.Pattern;

/** Programme version labels belong to showtimes, not the film's display/search title. */
final class CinemaTitle {
    private static final Pattern LANGUAGE=Pattern.compile("^(?:VOSE|VOSI|VOS|VO|CAT|VAL)\\s+");
    private static final Pattern SUBTITLES=Pattern.compile("\\s*\\(\\s*(?:(?:sin|con)\\s+subt[ií]tulos|subt[ií]tulos\\s+en\\s+ingl[eé]s)\\s*\\)\\s*$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private CinemaTitle(){}
    static String clean(String title){
        if(title==null)return "";
        String base=LANGUAGE.matcher(title.trim()).replaceFirst("").trim();
        String cleaned=SUBTITLES.matcher(base).replaceFirst("").trim();
        return cleaned.isEmpty()?base:cleaned;
    }
}
