package techguns.core;
import java.util.List;
/** Generated original Nether Metal enum order, independent of vanilla dye palettes. */
public final class NetherMetal {
    public record Variant(String id, int metadata, int light) {}
    public static final List<Variant> ALL=List.of(
        new Variant("nethermetal_panel", 0, 0),
        new Variant("nethermetal_grate1", 1, 0),
        new Variant("nethermetal_grate2", 2, 0),
        new Variant("nethermetal_grey_dark", 3, 0),
        new Variant("nethermetal_grey", 4, 0),
        new Variant("nethermetal_grey_tiles", 5, 0),
        new Variant("nethermetal_border_red", 6, 0),
        new Variant("nethermetal_plate_black", 7, 0),
        new Variant("nethermetal_plate_red", 8, 0),
        new Variant("nethermetal_border_lava", 9, 15)
    );
    public static final CamoPalette PALETTE=new CamoPalette("nethermetal",ALL.stream().map(v->"techguns:"+v.id()).toList());
    private NetherMetal() {}
}
