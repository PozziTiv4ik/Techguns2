package techguns.core;
import java.util.List;
/** Generated source metadata and TGConfig defaults; enum constructor values were unused in 1.12.2. */
public final class OreClusters {
    public record Variant(String id, String type, int metadata, int miningLevel, double oreMultiplier, double powerMultiplier) {}
    public static final List<Variant> ALL=List.of(
        new Variant("ore_cluster_coal", "coal", 0, 0, 10.0, 0.1),
        new Variant("ore_cluster_common_metal", "common_metal", 1, 0, 5.0, 0.2),
        new Variant("ore_cluster_rare_metal", "rare_metal", 2, 1, 2.5, 0.4),
        new Variant("ore_cluster_shiny_metal", "shiny_metal", 3, 2, 1.0, 1.0),
        new Variant("ore_cluster_uranium", "uranium", 4, 3, 0.5, 1.0),
        new Variant("ore_cluster_common_gem", "common_gem", 5, 1, 5.0, 0.2),
        new Variant("ore_cluster_shiny_gem", "shiny_gem", 6, 3, 0.2, 1.0),
        new Variant("ore_cluster_nether_crystal", "nether_crystal", 7, 2, 4.0, 0.5),
        new Variant("ore_cluster_oil", "oil", 8, 2, 4.0, 1.0)
    );
    private OreClusters() {}
}
