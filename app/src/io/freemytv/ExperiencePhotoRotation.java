package io.freemytv;

/** Focus-scoped monotonic schedule: replace one of the three slots every three seconds. */
final class ExperiencePhotoRotation {
    static final long INTERVAL=3000,FADE=650;
    private boolean active;
    private long due;
    private int slot;
    void active(boolean value,long now){if(active!=value){active=value;due=now+INTERVAL;}}
    boolean due(long now){return active && now>=due;}
    long delay(long now){return Math.max(0,due-now);}
    int slot(){return slot;}
    void loaded(long now){due=now+INTERVAL;}
    void replaced(long now){slot=(slot+1)%3;due=now+INTERVAL;}
}
