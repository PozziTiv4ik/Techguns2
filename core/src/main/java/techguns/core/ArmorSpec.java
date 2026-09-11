package techguns.core;

/** Source material values are fractional and must not be rounded to the HUD's armor points. */
public record ArmorSpec(String id, ArmorSlot slot, float physical, float elemental, int durability,
                        float toughness, double speed, double jump, double knockback,
                        int repairParts, double repairMetalRatio) {
    public ArmorSpec {
        if (id == null || slot == null || physical < 0 || elemental < 0 || durability < 2 || toughness < 0
                || !Float.isFinite(physical) || !Float.isFinite(elemental) || !Float.isFinite(toughness)
                || !Double.isFinite(speed) || !Double.isFinite(jump) || !Double.isFinite(knockback)
                || speed < 0 || jump < 0 || knockback < 0 || repairParts < 1 || !Double.isFinite(repairMetalRatio)
                || repairMetalRatio < 0 || repairMetalRatio > 1) throw new IllegalArgumentException("Invalid armor specification");
    }
    public float armor(DamageKind kind) {
        return switch(kind) {
            case PHYSICAL, PROJECTILE -> physical;
            case RADIATION, UNRESISTABLE -> 0;
            default -> elemental; // The source constructor includes POISON despite its older comment.
        };
    }
    public double absorption(DamageKind kind, float penetration) {
        if (!Float.isFinite(penetration) || penetration < 0) throw new IllegalArgumentException("Invalid penetration");
        // GenericArmor.getProperties passes raw penetration, unlike the NPC caller's x4 multiplier.
        return Math.clamp(armor(kind) - Math.max(penetration - toughness, 0), 0, 24) / 25.0;
    }
    public boolean bonusesActive(int damage) { return damage < durability - 1; }
    public int displayedArmor(int damage) { return bonusesActive(damage) ? Math.round(physical) : 0; }
    public int specialWearLimit(int damage, int requested) { return Math.clamp(requested, 0, Math.max(0, durability - 1 - damage)); }
    public int[] repairBenchCosts(int damage) {
        int count = (int)Math.ceil(repairParts * Math.clamp(damage, 0, durability - 1) / (double)(durability - 1));
        int metal = (int)Math.ceil(count * repairMetalRatio);
        return new int[]{metal, count - metal};
    }
}
