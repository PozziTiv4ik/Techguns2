package techguns.core;

/** The registered AIFireballAttack from Ghastling.java, including its retained cooldown on restart. */
public final class GhastlingAttack {
    public enum Action { NONE, MELEE, SHOT }
    private int step,time;
    private boolean attacking;
    public int step() { return step; }
    public int time() { return time; }
    public boolean attacking() { return attacking; }
    public void start() { step=0; }
    public void stop() { attacking=false; }
    public Action tick(double distanceSquared,double followDistance) {
        time--;
        if(distanceSquared<4) {
            if(time<=0) { time=20; return Action.MELEE; }
        } else if(distanceSquared<followDistance*followDistance && time<=0) {
            step++;
            if(step==1) { time=30; attacking=true; }
            else if(step<=4) time=6;
            else { time=50; step=0; attacking=false; }
            if(step>1) return Action.SHOT;
        }
        return Action.NONE;
    }
}
