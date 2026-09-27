package techguns.core;

/** Registered AIHelicopterAttack: visibility gates the clock, including its negative rest. */
public final class HelicopterAttack {
    public enum Action { NONE, BULLET, ROCKET }
    private int timer;
    public int timer() { return timer; }
    public void start() { timer=0; }
    public void restore(int value) { timer=Math.clamp(value,-30,35); }
    public boolean attacking() { return timer>10; }
    public Action tick(double distanceSquared,boolean visible) {
        if(distanceSquared<4096 && visible) {
            ++timer;
            if(timer>=14 && timer<24 && timer%2==0) return Action.BULLET;
            if(timer==35) return Action.ROCKET;
            if(timer>35) timer=-30;
        } else if(timer>0) --timer;
        return Action.NONE;
    }
    public static float armor(DamageKind kind) {
        return switch(kind) {
            case ENERGY,LIGHTNING->10;
            case FIRE,ICE,PHYSICAL,PROJECTILE,POISON,RADIATION->20;
            default->0;
        };
    }
    private static float progress(float death) { return death/100; }
    public static float deathOffset(float death) { float p=progress(death); return -4*p*p; }
    public static float deathTurn(float death) { float p=progress(death); return 1440*p*p; } // Both source yaw rotations apply.
}
