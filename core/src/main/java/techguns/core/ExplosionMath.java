package techguns.core;

/** TGExplosion and EntityRadiation deliberately share the original rising outer band. */
public final class ExplosionMath {
    public static double band(double distance, double innerRadius, double outerRadius, double inner, double outer) {
        if (distance <= innerRadius) return inner;
        if (distance > outerRadius) return 0;
        return outer + (distance - innerRadius) / (outerRadius - innerRadius) * (inner - outer);
    }
    public static int falloutStrength(int original, int age, int duration) {
        return age * 2 < duration ? original : Math.max(0, (int) Math.round(original * (1 - (age - duration * .5) / (duration * .5))));
    }
    public static boolean rocketVisible(int rounds, int reloadRemaining, int reloadDuration) {
        return reloadRemaining == 0 && rounds > 0 || reloadRemaining > 0 && reloadRemaining * 2 < reloadDuration;
    }
    private ExplosionMath() {}
}
