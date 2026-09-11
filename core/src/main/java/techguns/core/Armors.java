package techguns.core;

import java.util.List;

/** Generated from the original T2_COMBAT material and item declarations. */
public final class Armors {
    public static final List<ArmorSpec> ALL = List.of(
        new ArmorSpec("t2_combat_helmet", ArmorSlot.HEAD, 4.5f, 3.375f, 990, 1.0f, 0.1, 0.0, 0.1, 2, 0.5),
        new ArmorSpec("t2_combat_chestplate", ArmorSlot.CHEST, 5.4f, 4.05f, 990, 1.0f, 0.1, 0.0, 0.25, 4, 0.5),
        new ArmorSpec("t2_combat_leggings", ArmorSlot.LEGS, 4.5f, 3.375f, 990, 1.0f, 0.1, 0.0, 0.15, 3, 0.3333333333333333),
        new ArmorSpec("t2_combat_boots", ArmorSlot.FEET, 3.6f, 2.7f, 990, 1.0f, 0.1, 0.1, 0.1, 2, 0.5)
    );
    public static final List<String> CAMOS = List.of("t2_combat", "t2_combat_wood", "t2_combat_desert", "t2_combat_arctic", "t2_combat_swat", "t2_combat_security");
    public static ArmorSpec forSlot(ArmorSlot slot) { return ALL.stream().filter(a -> a.slot()==slot).findFirst().orElseThrow(); }
    private Armors() {}
}
