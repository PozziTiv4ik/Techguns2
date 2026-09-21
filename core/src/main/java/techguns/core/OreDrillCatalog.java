package techguns.core;
import java.util.*;
/** Generated from TGItems and TGOreClusters. Optional tags are resolved when starting an operation. */
public final class OreDrillCatalog {
    public record Head(String id,int size,int level) {}
    public record Output(String kind,String id,int weight) {}
    public static final List<Head> HEADS=List.of(
        new Head("oredrillsmall_steel", 0, 1),
        new Head("oredrillsmall_obsidiansteel", 0, 2),
        new Head("oredrillsmall_carbon", 0, 3),
        new Head("oredrillmedium_steel", 1, 1),
        new Head("oredrillmedium_obsidiansteel", 1, 2),
        new Head("oredrillmedium_carbon", 1, 3),
        new Head("oredrilllarge_steel", 2, 1),
        new Head("oredrilllarge_obsidiansteel", 2, 2),
        new Head("oredrilllarge_carbon", 2, 3)
    );
    public static final Map<String,List<Output>> OUTPUTS=Map.ofEntries(
        Map.entry("coal", List.of(new Output("item", "minecraft:coal_ore", 99), new Output("item", "minecraft:diamond", 1))),
        Map.entry("common_metal", List.of(new Output("item", "minecraft:iron_ore", 45), new Output("item", "techguns:ore_copper", 30), new Output("item", "techguns:ore_tin", 25))),
        Map.entry("rare_metal", List.of(new Output("item", "techguns:ore_lead", 50), new Output("tag", "c:ores/osmium", 50), new Output("tag", "c:ores/aluminum", 50))),
        Map.entry("shiny_metal", List.of(new Output("item", "techguns:ore_titanium", 20), new Output("item", "minecraft:gold_ore", 40), new Output("tag", "c:ores/silver", 40))),
        Map.entry("common_gem", List.of(new Output("item", "minecraft:redstone_ore", 40), new Output("item", "minecraft:lapis_ore", 20), new Output("tag", "c:ores/certus_quartz", 40), new Output("tag", "c:ores/charged_certus_quartz", 5))),
        Map.entry("shiny_gem", List.of(new Output("item", "minecraft:diamond_ore", 40), new Output("item", "minecraft:emerald_ore", 20))),
        Map.entry("uranium", List.of(new Output("item", "techguns:ore_uranium", 50))),
        Map.entry("nether_crystal", List.of(new Output("item", "minecraft:nether_quartz_ore", 50), new Output("item", "minecraft:glowstone", 40), new Output("item", "minecraft:blaze_rod", 10))),
        Map.entry("oil", List.of(new Output("oil", "", 10)))
    );
    private OreDrillCatalog() {}
}
