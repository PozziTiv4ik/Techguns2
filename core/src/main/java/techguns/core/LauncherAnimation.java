package techguns.core;

/** ModelBaseBakedGrenadeLauncher: one 60-degree drum turn during GenericGun's five-tick recoil. */
public final class LauncherAnimation {
    public static float drumDegrees(long shotTick,long now,float partial) {
        double elapsed=(double)now-shotTick+Math.clamp(partial,0,1);
        return shotTick<0 || elapsed<0 || elapsed>=5 ? 0 : (float)(60*elapsed/5);
    }
    private LauncherAnimation() {}
}
