package techguns.core;

import java.util.List;
import java.util.Optional;

/** Generated original enum order and BlockTGCamoNetTop bounds, in sixteenths. */
public final class CamouflageNets {
    public record Variant(String id, String family, int metadata) {
        public boolean canopy() { return family.equals("camonet_top"); }
    }
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {}
    public static final List<Variant> ALL = List.of(
        new Variant("camonet_wood", "camonet", 0),
        new Variant("camonet_desert", "camonet", 1),
        new Variant("camonet_snow", "camonet", 2),
        new Variant("camonet_top_wood", "camonet_top", 0),
        new Variant("camonet_top_desert", "camonet_top", 1),
        new Variant("camonet_top_snow", "camonet_top", 2)
    );
    public static final List<Box> CANOPY_BOXES = List.of(
        new Box(4, 0, 4, 12, 1, 12),
        new Box(0, 0, 4, 9, 1, 12),
        new Box(4, 0, 7, 12, 1, 16),
        new Box(0, 0, 7, 9, 1, 16),
        new Box(7, 0, 4, 16, 1, 12),
        new Box(0, 0, 4, 16, 1, 12),
        new Box(7, 0, 7, 16, 1, 16),
        new Box(0, 0, 7, 16, 1, 16),
        new Box(4, 0, 0, 12, 1, 9),
        new Box(0, 0, 0, 9, 1, 9),
        new Box(4, 0, 0, 12, 1, 16),
        new Box(0, 0, 0, 9, 1, 16),
        new Box(7, 0, 0, 16, 1, 9),
        new Box(0, 0, 0, 16, 1, 9),
        new Box(7, 0, 0, 16, 1, 16),
        new Box(0, 0, 0, 16, 1, 16)
    );
    public static final List<CamoPalette> PALETTES = List.of("camonet", "camonet_top").stream()
            .map(f -> new CamoPalette(f, ALL.stream().filter(v -> v.family().equals(f)).map(v -> "techguns:" + v.id()).toList())).toList();
    public static Optional<CamoPalette> palette(String item) { return PALETTES.stream().filter(p -> p.index(item) >= 0).findFirst(); }
    private CamouflageNets() {}
}
