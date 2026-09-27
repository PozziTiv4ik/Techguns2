package techguns.modern;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import techguns.core.*;

/** The launcher uses the same source GrenadeProjectile with constant gravity and two bounces. */
public final class Grenade40mmProjectile extends GrenadeProjectile {
    private WeaponDefinition weapon=Weapons.definition("grenadelauncher");
    public Grenade40mmProjectile(EntityType<? extends Grenade40mmProjectile> type,Level level) {
        super(type,level);configure(weapon);
    }
    public void configure(WeaponDefinition weapon) {
        if(weapon.projectile()!=ProjectileKind.GRENADE_40MM) throw new IllegalArgumentException("Expected 40mm grenade weapon");
        this.weapon=weapon;flight((float)weapon.gravity(),2);
    }
    public WeaponDefinition weapon() { return weapon; }
    @Override protected int lifetime() { return weapon.stats().projectileLifetime(); }
    @Override protected int initialBounces() { return 2; }
    @Override protected float minimumGravity() { return (float)weapon.gravity(); }
    @Override protected float maximumGravity() { return (float)weapon.gravity(); }
    @Override public double outerRadius() { return weapon.stats().dropEnd(); }
    @Override public float directDamage(double distance) { return weapon.stats().damageAt(distance); }
    @Override public float blastDamage(double distance) {
        var s=weapon.stats();return (float)ExplosionMath.band(distance,s.dropStart(),s.dropEnd(),s.damage(),s.minimumDamage());
    }
    public void shootLegacy(LivingEntity source,double spread) {
        int side=source instanceof Mob || source.getMainArm()==HumanoidArm.RIGHT?-1:1;
        super.shootLegacy(source,spread,side,weapon.stats().projectileSpeed());
    }
    @Override protected void saveProfile(ValueOutput output) { output.putString("weapon",weapon.id()); }
    @Override protected void loadProfile(ValueInput input) { configure(Weapons.definition(input.getStringOr("weapon","grenadelauncher"))); }
}
