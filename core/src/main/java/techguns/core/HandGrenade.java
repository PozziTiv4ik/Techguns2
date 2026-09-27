package techguns.core;

/** Generated from TGuns/GenericGrenade and both projectile constructors. */
public enum HandGrenade {
    STIELGRANATE("stielgranate", 10.0f, 5.0f, 3.0, 5.0, 3, 16, 72000, 30, 200, 0.75, 0.1),
    FRAGGRENADE("fraggrenade", 12.0f, 6.0f, 3.5, 7.0, 2, 16, 72000, 30, 200, 0.75, 0.1);
    private final String id;
    public final float damage, minimumDamage;
    public final double innerRadius, outerRadius, speed, spread;
    public final int bounces, stackSize, useDuration, chargeTicks, lifetime;
    HandGrenade(String id, float damage, float minimumDamage, double innerRadius, double outerRadius,
                int bounces, int stackSize, int useDuration, int chargeTicks, int lifetime, double speed, double spread) {
        this.id=id; this.damage=damage; this.minimumDamage=minimumDamage; this.innerRadius=innerRadius;
        this.outerRadius=outerRadius; this.bounces=bounces; this.stackSize=stackSize; this.useDuration=useDuration;
        this.chargeTicks=chargeTicks; this.lifetime=lifetime; this.speed=speed; this.spread=spread;
    }
    public String id() { return id; }
    public static HandGrenade fromId(String id) {
        for (var grenade : values()) if (grenade.id.equals(id)) return grenade;
        throw new IllegalArgumentException("Unknown hand grenade: " + id);
    }
    public float gravity(int heldTicks) {
        // Source divides by zero on an immediate release. Use its shortest finite, one-tick throw.
        return .015f / ((float)Math.clamp(heldTicks, 1, chargeTicks) / chargeTicks);
    }
    public float directDamage(double distance) {
        if (distance <= innerRadius) return damage;
        if (distance > outerRadius) return minimumDamage;
        return minimumDamage + (damage-minimumDamage) * (1-(float)((distance-innerRadius)/(outerRadius-innerRadius)));
    }
    public float blastDamage(double distance) {
        return (float)ExplosionMath.band(distance, innerRadius, outerRadius, damage, minimumDamage);
    }
}
