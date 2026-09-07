package techguns.core;

/** Port of DamageSystem.getDamageAfterAbsorb_TGFormula and its caller's penetration x4. */
public final class ArmorMath {
    public static float afterArmor(float damage, float armor, float toughness, float penetrationRating) {
        if (!Float.isFinite(damage) || !Float.isFinite(armor) || !Float.isFinite(toughness)
                || !Float.isFinite(penetrationRating) || damage < 0 || armor < 0 || toughness < 0 || penetrationRating < 0)
            throw new IllegalArgumentException("Armor calculation requires finite nonnegative inputs");
        float penetration = Math.max(penetrationRating * 4 - toughness, 0);
        double effectiveArmor = Math.clamp(armor - penetration, 0, 24);
        return (float) (damage * (1 - effectiveArmor / 25));
    }

    public static float defaultArmor(DamageKind type, float armor, boolean fireImmune) {
        return switch (type) {
            case PHYSICAL, PROJECTILE -> armor;
            case EXPLOSION, ENERGY, ICE, LIGHTNING, DARK -> armor * .5f;
            case FIRE -> armor * (fireImmune ? 2 : .5f);
            case POISON, RADIATION, UNRESISTABLE -> 0;
        };
    }
    private ArmorMath() {}
}
