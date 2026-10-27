package io.freemytv;

/** Main-thread turn identity: duplicate/stale recognition results must never execute a second command. */
final class AssistTurn {
    enum Stage { IDLE, LISTENING, SENDING, REPLY }
    private long generation;
    private Stage stage=Stage.IDLE;
    long begin(){stage=Stage.LISTENING;return ++generation;}
    void cancel(){generation++;stage=Stage.IDLE;}
    boolean active(long turn){return generation==turn;}
    boolean submit(long turn){if(!active(turn)||stage!=Stage.LISTENING)return false;stage=Stage.SENDING;return true;}
    boolean finish(long turn){if(!active(turn)||stage!=Stage.SENDING)return false;stage=Stage.REPLY;return true;}
    boolean recognitionFailed(long turn){if(!active(turn)||stage!=Stage.LISTENING)return false;stage=Stage.IDLE;return true;}
    Stage stage(){return stage;}
}
