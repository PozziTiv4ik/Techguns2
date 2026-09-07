package techguns.core;

import java.util.List;

/** Generated from legacy TGuns.java by tools/generate_weapon_content.py. */
public final class Weapons {
    public static final List<WeaponDefinition> ALL = List.of(
        new WeaponDefinition(new WeaponSpec("handcannon", 1, 12, 30, 8.0f, 5.0f, 10.0, 25.0, 1.0, 25, 0.035), new AmmoSpec("stonebullets", "", "", 0, false), false, 0, 0.0, 0.015, 0.0, 1.0f, "guns.handgunfire", "guns.handgunreload"),
        new WeaponDefinition(new WeaponSpec("sawedoff", 2, 4, 28, 4.0f, 1.5f, 1.0, 4.0, 1.5, 10, 0.01), new AmmoSpec("shotgunrounds", "", "", 0, true), false, 7, 0.2, 0.0, 0.0, 1.0f, "guns.sawedofffire", "guns.sawedoffreload"),
        new WeaponDefinition(new WeaponSpec("revolver", 6, 6, 45, 8.0f, 6.0f, 12.0, 20.0, 2.0, 40, 0.025), new AmmoSpec("pistolrounds", "", "", 0, false), false, 0, 0.0, 0.0, 0.0, 1.0f, "guns.revolverfire", "guns.revolverreload"),
        new WeaponDefinition(new WeaponSpec("goldenrevolver", 6, 8, 45, 14.0f, 6.0f, 12.0, 30.0, 1.75, 40, 0.015), new AmmoSpec("pistolrounds", "", "", 0, false), false, 0, 0.0, 0.0, 0.0, 1.0f, "guns.goldenrevolverfire", "guns.revolverreload"),
        new WeaponDefinition(new WeaponSpec("thompson", 20, 3, 40, 5.0f, 3.0f, 15.0, 24.0, 1.75, 40, 0.05), new AmmoSpec("smgmagazine", "smgmagazineempty", "pistolrounds", 2, false), true, 0, 0.0, 0.0, 0.0, 1.0f, "guns.thompsonfire", "guns.thompsonreload"),
        new WeaponDefinition(new WeaponSpec("boltaction", 6, 25, 50, 16.0f, 10.0f, 40.0, 60.0, 3.5, 90, 0.05), new AmmoSpec("riflerounds", "", "", 0, false), false, 0, 0.0, 0.0, 0.5, 0.35f, "guns.boltactionfire", "guns.boltactionreload"),
        new WeaponDefinition(new WeaponSpec("combatshotgun", 8, 14, 50, 4.0f, 1.5f, 2.0, 5.0, 1.5, 15, 0.01), new AmmoSpec("shotgunrounds", "", "", 0, true), false, 7, 0.15, 0.0, 0.5, 1.0f, "guns.combatshotgunfire", "guns.combatshotgunreload"));
    public static WeaponDefinition definition(String id) {
        return ALL.stream().filter(gun -> gun.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown weapon: " + id));
    }
    public static final WeaponSpec REVOLVER = definition("revolver").stats();
    private Weapons() {}
}
