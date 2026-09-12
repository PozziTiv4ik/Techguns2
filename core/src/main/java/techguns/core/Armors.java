package techguns.core;

import java.util.List;

/** Generated from the original material and item declarations of the supported armor sets. */
public final class Armors {
    public static final List<ArmorSpec> ALL = List.of(
        new ArmorSpec("t2_combat_helmet", ArmorSlot.HEAD, 4.5f, 3.375f, 990, 1.0f, 0.1, 0.0, 0.1, 2, 0.5,
                3.375f, 3.375f, 3.375f, 0.0f, 0.0, 0.0, 0.0, "ingotobsidiansteel", "heavycloth", List.of("t2_combat", "t2_combat_wood", "t2_combat_desert", "t2_combat_arctic", "t2_combat_swat", "t2_combat_security"), "t2_combat"),
        new ArmorSpec("t2_combat_chestplate", ArmorSlot.CHEST, 5.4f, 4.05f, 990, 1.0f, 0.1, 0.0, 0.25, 4, 0.5,
                4.05f, 4.05f, 4.05f, 0.0f, 0.0, 0.0, 0.0, "ingotobsidiansteel", "heavycloth", List.of("t2_combat", "t2_combat_wood", "t2_combat_desert", "t2_combat_arctic", "t2_combat_swat", "t2_combat_security"), "t2_combat"),
        new ArmorSpec("t2_combat_leggings", ArmorSlot.LEGS, 4.5f, 3.375f, 990, 1.0f, 0.1, 0.0, 0.15, 3, 0.3333333333333333,
                3.375f, 3.375f, 3.375f, 0.0f, 0.0, 0.0, 0.0, "ingotobsidiansteel", "heavycloth", List.of("t2_combat", "t2_combat_wood", "t2_combat_desert", "t2_combat_arctic", "t2_combat_swat", "t2_combat_security"), "t2_combat"),
        new ArmorSpec("t2_combat_boots", ArmorSlot.FEET, 3.6f, 2.7f, 990, 1.0f, 0.1, 0.1, 0.1, 2, 0.5,
                2.7f, 2.7f, 2.7f, 0.0f, 0.0, 0.0, 0.0, "ingotobsidiansteel", "heavycloth", List.of("t2_combat", "t2_combat_wood", "t2_combat_desert", "t2_combat_arctic", "t2_combat_swat", "t2_combat_security"), "t2_combat"),
        new ArmorSpec("hazmat_helmet", ArmorSlot.HEAD, 2.5f, 4.0f, 1100, 0.0f, 0.0, 0.0, 0.0, 2, 0.0,
                2.5f, 5.0f, 1.875f, 5.0f, 1.0, 0.0, 0.0, "", "protectivefiber", List.of("hazmatsuit", "hazmatsuit_grey", "hazmatsuit_orange", "hazmatsuit_blue"), "hazmat"),
        new ArmorSpec("hazmat_chestplate", ArmorSlot.CHEST, 3.0f, 4.8f, 1100, 0.0f, 0.0, 0.0, 0.0, 4, 0.0,
                3.0f, 6.0f, 2.25f, 6.0f, 1.0, 0.0, 0.0, "", "protectivefiber", List.of("hazmatsuit", "hazmatsuit_grey", "hazmatsuit_orange", "hazmatsuit_blue"), "hazmat"),
        new ArmorSpec("hazmat_leggings", ArmorSlot.LEGS, 2.5f, 4.0f, 1100, 0.0f, 0.0, 0.0, 0.0, 3, 0.0,
                2.5f, 5.0f, 1.875f, 5.0f, 1.0, 0.0, 0.0, "", "protectivefiber", List.of("hazmatsuit", "hazmatsuit_grey", "hazmatsuit_orange", "hazmatsuit_blue"), "hazmat"),
        new ArmorSpec("hazmat_boots", ArmorSlot.FEET, 2.0f, 3.2f, 1100, 0.0f, 0.0, 0.0, 0.0, 2, 0.0,
                2.0f, 4.0f, 1.5f, 4.0f, 1.0, 0.1, 0.5, "", "protectivefiber", List.of("hazmatsuit", "hazmatsuit_grey", "hazmatsuit_orange", "hazmatsuit_blue"), "hazmat")
    );
    public static final List<ArmorSpec> T2_COMBAT = ALL.stream().filter(a -> a.set().equals("t2_combat")).toList();
    public static final List<ArmorSpec> HAZMAT = ALL.stream().filter(a -> a.set().equals("hazmat")).toList();
    public static final List<String> CAMOS = T2_COMBAT.getFirst().camos();
    public static ArmorSpec forSlot(ArmorSlot slot) { return T2_COMBAT.stream().filter(a -> a.slot()==slot).findFirst().orElseThrow(); }
    public static ArmorSpec forSlot(String set, ArmorSlot slot) { return ALL.stream().filter(a -> a.set().equals(set) && a.slot()==slot).findFirst().orElseThrow(); }
    private Armors() {}
}
