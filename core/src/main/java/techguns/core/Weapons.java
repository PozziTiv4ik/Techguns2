package techguns.core;

import java.util.List;

/** Generated from legacy TGuns.java by tools/generate_weapon_content.py. */
public final class Weapons {
    public static final List<WeaponDefinition> ALL = List.of(
        new WeaponDefinition(new WeaponSpec("handcannon", 1, 12, 30, 8.0f, 5.0f, 10.0, 25.0, 1.0, 25, 0.035), new AmmoSpec("stonebullets", "", "", 0, false), ProjectileKind.BALLISTIC, false, 0, 0.0, 0.015, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.handgunfire", "guns.handgunreload"),
        new WeaponDefinition(new WeaponSpec("sawedoff", 2, 4, 28, 4.0f, 1.5f, 1.0, 4.0, 1.5, 10, 0.01), new AmmoSpec("shotgunrounds", "", "", 0, true), ProjectileKind.BALLISTIC, false, 7, 0.2, 0.0, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.sawedofffire", "guns.sawedoffreload"),
        new WeaponDefinition(new WeaponSpec("revolver", 6, 6, 45, 8.0f, 6.0f, 12.0, 20.0, 2.0, 40, 0.025), new AmmoSpec("pistolrounds", "", "", 0, false), ProjectileKind.BALLISTIC, false, 0, 0.0, 0.0, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.revolverfire", "guns.revolverreload"),
        new WeaponDefinition(new WeaponSpec("goldenrevolver", 6, 8, 45, 14.0f, 6.0f, 12.0, 30.0, 1.75, 40, 0.015), new AmmoSpec("pistolrounds", "", "", 0, false), ProjectileKind.BALLISTIC, false, 0, 0.0, 0.0, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.goldenrevolverfire", "guns.revolverreload"),
        new WeaponDefinition(new WeaponSpec("thompson", 20, 3, 40, 5.0f, 3.0f, 15.0, 24.0, 1.75, 40, 0.05), new AmmoSpec("smgmagazine", "smgmagazineempty", "pistolrounds", 2, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.thompsonfire", "guns.thompsonreload"),
        new WeaponDefinition(new WeaponSpec("boltaction", 6, 25, 50, 16.0f, 10.0f, 40.0, 60.0, 3.5, 90, 0.05), new AmmoSpec("riflerounds", "", "", 0, false), ProjectileKind.BALLISTIC, false, 0, 0.0, 0.0, 0.5, new AimSpec(0.35f, true, 0.125f, true), "guns.boltactionfire", "guns.boltactionreload"),
        new WeaponDefinition(new WeaponSpec("combatshotgun", 8, 14, 50, 4.0f, 1.5f, 2.0, 5.0, 1.5, 15, 0.01), new AmmoSpec("shotgunrounds", "", "", 0, true), ProjectileKind.BALLISTIC, false, 7, 0.15, 0.0, 0.5, new AimSpec(1.0f, false, 1.0f, false), "guns.combatshotgunfire", "guns.combatshotgunreload"),
        new WeaponDefinition(new WeaponSpec("pistol", 18, 4, 35, 8.0f, 5.0f, 18.0, 25.0, 2.0, 40, 0.025), new AmmoSpec("pistolmagazine", "pistolmagazineempty", "pistolrounds", 3, false), ProjectileKind.BALLISTIC, false, 0, 0.0, 0.0, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.pistolfire", "guns.pistolreload"),
        new WeaponDefinition(new WeaponSpec("ak47", 30, 3, 45, 9.0f, 5.0f, 20.0, 30.0, 2.5, 60, 0.03), new AmmoSpec("assaultriflemagazine", "assaultriflemagazineempty", "riflerounds", 3, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.5, new AimSpec(1.0f, false, 1.0f, false), "guns.ak47fire", "guns.ak47reload"),
        new WeaponDefinition(new WeaponSpec("m4", 30, 3, 45, 8.0f, 6.0f, 25.0, 40.0, 3.0, 60, 0.015), new AmmoSpec("assaultriflemagazine", "assaultriflemagazineempty", "riflerounds", 3, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.5, new AimSpec(0.75f, true, 0.75f, false), "guns.assaultriflefire", "guns.assaultriflereload"),
        new WeaponDefinition(new WeaponSpec("m4_infiltrator", 30, 3, 45, 8.0f, 6.0f, 25.0, 35.0, 2.0, 60, 0.01), new AmmoSpec("assaultriflemagazine", "assaultriflemagazineempty", "riflerounds", 3, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.5, new AimSpec(0.5f, true, 0.5f, true), "guns.silencedm4fire", "guns.assaultriflereload"),
        new WeaponDefinition(new WeaponSpec("lmg", 100, 2, 100, 8.0f, 6.0f, 40.0, 60.0, 3.0, 75, 0.02), new AmmoSpec("lmgmagazine", "lmgmagazineempty", "riflerounds", 8, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.5, new AimSpec(0.75f, true, 0.75f, false), "guns.lmgfire", "guns.lmgreload"),
        new WeaponDefinition(new WeaponSpec("mac10", 32, 2, 40, 5.0f, 3.0f, 15.0, 24.0, 1.75, 40, 0.05), new AmmoSpec("smgmagazine", "smgmagazineempty", "pistolrounds", 2, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.mac10fire", "guns.assaultriflereload"),
        new WeaponDefinition(new WeaponSpec("as50", 10, 10, 80, 32.0f, 24.0f, 40.0, 60.0, 3.5, 90, 0.0625), new AmmoSpec("as50magazine", "as50magazineempty", "sniperrounds", 2, false), ProjectileKind.BALLISTIC, false, 0, 0.0, 0.0, 2.0, new AimSpec(0.35f, true, 0.125f, true), "guns.as50fire", "guns.as50reload"),
        new WeaponDefinition(new WeaponSpec("aug", 30, 3, 45, 8.0f, 7.0f, 30.0, 45.0, 2.0, 60, 0.01), new AmmoSpec("assaultriflemagazine", "assaultriflemagazineempty", "riflerounds", 3, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.5, new AimSpec(0.5f, true, 0.5f, true), "guns.augfire", "guns.augreload"),
        new WeaponDefinition(new WeaponSpec("scar", 20, 4, 45, 12.0f, 10.0f, 35.0, 60.0, 3.0, 75, 0.015), new AmmoSpec("assaultriflemagazine", "assaultriflemagazineempty", "riflerounds", 3, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 1.0, new AimSpec(0.65f, true, 0.5f, true), "guns.scarfire", "guns.scarreload"),
        new WeaponDefinition(new WeaponSpec("vector", 25, 2, 40, 6.0f, 4.0f, 17.0, 25.0, 2.0, 40, 0.05), new AmmoSpec("smgmagazine", "smgmagazineempty", "pistolrounds", 2, false), ProjectileKind.BALLISTIC, true, 0, 0.0, 0.0, 0.5, new AimSpec(0.75f, true, 0.35f, false), "guns.vectorfire", "guns.vectorreload"),
        new WeaponDefinition(new WeaponSpec("lasergun", 45, 5, 45, 12.0f, 12.0f, 90.0, 90.0, 100.0, 7, 0.0), new AmmoSpec("energycell", "energycellempty", "", 0, false), ProjectileKind.LASER, true, 0, 0.0, 0.0, 0.0, new AimSpec(0.75f, true, 0.75f, false), "guns.lasergunfire", "guns.lasergunreload"),
        new WeaponDefinition(new WeaponSpec("laserpistol", 20, 6, 40, 9.0f, 9.0f, 90.0, 90.0, 100.0, 7, 0.025), new AmmoSpec("redstone_battery", "redstone_battery_empty", "", 0, false), ProjectileKind.LASER, true, 0, 0.0, 0.0, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.laserpistolfire", "guns.laserpistolreload"),
        new WeaponDefinition(new WeaponSpec("rocketlauncher", 1, 10, 40, 50.0f, 10.0f, 3.0, 5.0, 1.0, 200, 0.05), new AmmoSpec("rocket", "", "", 0, false), ProjectileKind.ROCKET, false, 0, 0.0, 0.01, 0.0, new AimSpec(1.0f, false, 1.0f, false), "guns.rocketfire", "guns.rocketreload"));
    public static WeaponDefinition definition(String id) {
        return ALL.stream().filter(gun -> gun.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown weapon: " + id));
    }
    public static final WeaponSpec REVOLVER = definition("revolver").stats();
    private Weapons() {}
}
