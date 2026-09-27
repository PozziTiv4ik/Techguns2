package techguns.core;

/** Generated from AttackHelicopter's two constructors; these are NPC profiles, not obtainable guns. */
public final class HelicopterWeapons {
    public static final WeaponDefinition BULLET=profile("attackhelicopter_bullet",ProjectileKind.BALLISTIC);
    public static final WeaponDefinition ROCKET=profile("attackhelicopter_rocket",ProjectileKind.ROCKET);
    private static WeaponDefinition profile(String id,ProjectileKind kind) {
        return new WeaponDefinition(new WeaponSpec(id,1,1,1,12.0f,8.0f,30,40,1.0,100,0.05),
                new AmmoSpec("riflerounds","","",0,true),kind,false,0,0,0,0.25,new AimSpec(1,false,1,true),"","");
    }
    public static WeaponDefinition resolve(String id) {
        if(id.equals(BULLET.id())) return BULLET;
        if(id.equals(ROCKET.id())) return ROCKET;
        return Weapons.definition(id);
    }
    private HelicopterWeapons() {}
}
