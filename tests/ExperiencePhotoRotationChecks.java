package io.freemytv;

/** Boundary timing and focus loss must not advance several photos or catch up off-screen. */
public final class ExperiencePhotoRotationChecks {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    public static void main(String[] args){
        ExperiencePhotoRotation rotation=new ExperiencePhotoRotation();
        check(!rotation.due(100000),"No rotation before focus");
        rotation.active(true,1000);
        check(!rotation.due(3999),"Keep all photos for the first three seconds");
        check(rotation.due(4000),"One change due at three seconds");
        check(rotation.slot()==0,"First card changes first");
        rotation.replaced(4000);
        check(rotation.slot()==1 && !rotation.due(6999),"Only one slot advances per change");
        rotation.active(true,5000);
        check(rotation.due(7000),"Moving within the board does not restart the clock");
        rotation.replaced(7000);check(rotation.slot()==2,"Second change advances to third card");
        rotation.replaced(10000);check(rotation.slot()==0,"Rotate through the three cards");
        rotation.active(false,11000);
        check(!rotation.due(100000),"Never rotate while away or in a dialog");
        rotation.active(true,100000);
        check(!rotation.due(102999) && rotation.due(103000),"Re-entry starts a fresh interval without catch-up");
        rotation.replaced(110000);
        check(!rotation.due(112999) && rotation.due(113000),"Slow downloads do not cause a burst of changes");
        check(rotation.delay(120000)==0,"Late prefetched photos can start without negative delays");
        rotation.active(false,120000);rotation.active(false,121000);rotation.active(true,122000);
        check(rotation.delay(122000)==3000,"Pause/resume keeps a full focus interval");
        rotation.loaded(130000);
        check(!rotation.due(132999) && rotation.due(133000),"Initial loading leaves the first photos visible for three seconds");
        rotation.active(false,134000);rotation.loaded(140000);
        check(!rotation.due(150000),"Finishing a background load does not resume an inactive board");
        System.out.println("ExperiencePhotoRotationChecks: "+checks+" passed");
    }
}
