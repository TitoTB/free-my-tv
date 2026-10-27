package io.freemytv;

/** Confirmation is a changed device state, never just an HTTP 200 or an unavailable sample. */
final class HomeControlPending {
    final int index,position;
    final HomeControlState before;
    final long deadline;
    HomeControlPending(int index,int position,HomeControlState before,long now) {
        this.index=index; this.position=position; this.before=before;
        deadline=now+(index==2?90_000:20_000);
    }
    boolean confirmed(HomeControlState next) {
        if(!next.available) return false;
        return index==0?!before.value.equals(next.value):index==1?before.on!=next.on:next.position==position;
    }
    HomeControlState visible(HomeControlState previous,HomeControlState next) {
        return next.available?next:previous;
    }
    boolean expired(long now) { return now>=deadline; }
}
