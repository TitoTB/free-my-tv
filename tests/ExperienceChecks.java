package io.freemytv;

/** Checklist persistence and remote navigation, including partial pages. */
public final class ExperienceChecks {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        ExperienceData data=new ExperienceData();
        check(!data.add(" \n\t"),"Reject blank experiences");
        check(data.add("  Viajar\n en tren  "),"Add experience");
        check(data.items.get(0).text.equals("Viajar en tren"),"Normalize keyboard whitespace");
        check(!data.items.get(0).done,"New entries start pending");
        check(data.toggle(0) && data.items.get(0).done,"OK marks entry");
        ExperienceData restored=ExperienceData.decode(data.encode());
        check(restored.items.size()==1 && restored.items.get(0).done,"Completion survives restart");
        check(restored.items.get(0).id.equals(data.items.get(0).id),"Stable IDs survive restart");
        check(restored.toggle(0) && !restored.items.get(0).done,"Second OK unmarks entry");
        check(!data.toggle(-1) && !data.toggle(10),"Invalid indices safe");
        check(ExperienceData.decode("broken").items.isEmpty(),"Corrupt data safe");
        check(ExperienceData.decode("[{\"id\":\"a\",\"text\":\"One\"},{\"id\":\"a\",\"text\":\"Duplicate\"},null,{\"text\":\"No ID\"}]").items.size()==1,"Reject invalid and duplicate records");
        check(restored.remove(restored.items.get(0).id) && restored.items.isEmpty(),"Delete by ID");
        check(!restored.remove("missing"),"Delete missing record safe");
        StringBuilder longText=new StringBuilder();for(int i=0;i<160;i++)longText.appendCodePoint(0x1F680);
        String limited=ExperienceData.clean(longText.toString());
        check(limited.codePointCount(0,limited.length())==140,"Unicode truncation retains codepoints");
        for(int size=1;size<=35;size++)for(int selected=0;selected<size;selected++){
            for(int dx:new int[]{-1,1}){
                int next=ExperienceData.navigate(selected,dx,0,size);
                check(next>=0 && next<size,"Horizontal navigation stays in bounds");
                if(Math.abs(next-selected)==ExperienceData.ROWS)check(ExperienceData.navigate(next,-dx,0,size)==selected,"Horizontal navigation reversible across full columns");
                else if(next!=selected)check(next==size-1,"Partial next column clamps to its last experience");
            }
            for(int dy:new int[]{-1,1}){
                int next=ExperienceData.navigate(selected,0,dy,size);
                check(next>=-2 && next<size,"Vertical navigation stays in bounds");
                if(next>=0)check(ExperienceData.pageStart(next)==ExperienceData.pageStart(selected),"Vertical navigation stays on page");
            }
        }
        check(ExperienceData.navigate(5,1,0,11)==10,"Reach partial next page");
        check(ExperienceData.navigate(10,-1,0,11)==5,"Return from partial page");
        check(ExperienceData.navigate(9,1,0,11)==10,"Reach a partial next page from the bottom row too");
        check(ExperienceData.navigate(0,0,-1,8)==-1,"Top row reaches settings");
        check(ExperienceData.navigate(4,0,1,10)==-2,"Bottom row reaches drawer");
        check(ExperienceData.navigate(0,1,0,0)==-1,"Empty board safe");
        for(int i=0;i<ExperienceData.MAX;i++)check(restored.add("Item "+i),"Add within capacity");
        check(!restored.add("Overflow"),"Bounded private storage");
        check(ExperienceData.decode(restored.encode()).items.size()==ExperienceData.MAX,"Large checklist round-trip");
        ExperienceData defaults=ExperienceData.defaults();
        check(defaults.items.isEmpty(),"Community boards contain no bundled personal wishes");
        check(!new ExperienceData().replaceLegacyExamples(),"An intentionally empty board stays empty");
        ExperienceData custom=new ExperienceData();custom.add("Mi lista");
        check(!custom.replaceLegacyExamples() && custom.items.size()==1,"Custom lists are not reset");
        custom.toggle(0);String retainedId=custom.items.get(0).id;
        check(!custom.replaceLegacyExamples() && custom.items.get(0).done && custom.items.get(0).id.equals(retainedId),"Preserve user content, completion and identity");
        System.out.println("ExperienceChecks: "+checks+" passed");
    }
}
