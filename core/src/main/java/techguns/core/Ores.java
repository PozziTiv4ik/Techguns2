package techguns.core;

import java.util.List;

/** Generated from the original ore enum, configuration defaults and OreGenerator. */
public final class Ores {
    public static final List<OreDefinition> ALL = List.of(
            new OreDefinition("ore_copper", "doOreGenCopper", true, 0, 4.0f, 1, 0, 5, 7, 12, 5, 80),
            new OreDefinition("ore_tin", "doOreGenTin", true, 1, 4.0f, 1, 0, 4, 6, 10, 5, 60),
            new OreDefinition("ore_lead", "doOreGenLead", true, 2, 6.0f, 2, 0, 4, 5, 8, 5, 50),
            new OreDefinition("ore_uranium", "doOreGenUranium", true, 4, 7.0f, 2, 4, 4, 5, 4, 4, 24),
            new OreDefinition("ore_titanium", "doOreGenTitanium", true, 3, 8.0f, 3, 0, 4, 7, 5, 4, 32));
    private Ores() {}
}
