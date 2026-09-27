package techguns.core;

import java.util.List;

/** Generated original metadata order and the six break-reward table names. */
public final class MilitaryCrates {
    public record Variant(String id, int metadata, String loot) {}
    public static final List<Variant> ALL = List.of(
        new Variant("military_crate_ammo",0,"blocks/military_crate_ammo"),
        new Variant("military_crate_gun",1,"blocks/military_crate_gun"),
        new Variant("military_crate_armor",2,"blocks/military_crate_armor"),
        new Variant("military_crate_medical",3,"blocks/military_crate_medical"),
        new Variant("military_crate_explosive",4,"blocks/military_crate_explosives"),
        new Variant("military_crate_generic_oak",5,"blocks/military_crate_generic"),
        new Variant("military_crate_generic_jungle",6,"blocks/military_crate_generic"),
        new Variant("military_crate_generic_birch",7,"blocks/military_crate_generic"),
        new Variant("military_crate_generic_spruce",8,"blocks/military_crate_generic")
    );
    public static Variant byMetadata(int metadata) {
        if (metadata < 0 || metadata >= ALL.size()) throw new IllegalArgumentException("Invalid military crate metadata: " + metadata);
        return ALL.get(metadata);
    }
    private MilitaryCrates() {}
}
