package techguns.core;

/** GenericNPCUndead's original daylight ignition probability. */
public final class UndeadRules {
    public static boolean sunIgnites(float brightness,float roll) {
        return brightness>.5f && roll*30f<(brightness-.4f)*2f;
    }
    private UndeadRules() {}
}
