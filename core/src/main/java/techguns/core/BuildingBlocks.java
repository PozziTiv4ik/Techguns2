package techguns.core;
import java.util.List;
import java.util.Optional;
/** Generated original building families and metadata order. */
public final class BuildingBlocks {
    public record Variant(String id, String family, int index, int metadata, int hardness) {
        public boolean ladder() { return family.equals("ladder0"); }
        public String camoKey() { return ladder() ? "techguns.ladder0.camoname."+index : "block.techguns."+id; }
    }
    public static final List<Variant> ALL=List.of(
        new Variant("metalpanel_container_red", "metalpanel", 0, 0, 8),
        new Variant("metalpanel_container_green", "metalpanel", 1, 1, 8),
        new Variant("metalpanel_container_blue", "metalpanel", 2, 2, 8),
        new Variant("metalpanel_container_orange", "metalpanel", 3, 3, 8),
        new Variant("metalpanel_panel_large_border", "metalpanel", 4, 4, 8),
        new Variant("metalpanel_steelframe_blue", "metalpanel", 5, 5, 8),
        new Variant("metalpanel_steelframe_dark", "metalpanel", 6, 6, 8),
        new Variant("metalpanel_steelframe_scaffold", "metalpanel", 7, 7, 8),
        new Variant("concrete_brown", "concrete", 0, 0, 8),
        new Variant("concrete_brown_light", "concrete", 1, 1, 8),
        new Variant("concrete_grey", "concrete", 2, 2, 8),
        new Variant("concrete_grey_dark", "concrete", 3, 3, 8),
        new Variant("concrete_brown_pipes", "concrete", 4, 4, 8),
        new Variant("concrete_brown_light_scaff", "concrete", 5, 5, 8),
        new Variant("ladder_metal", "ladder0", 0, 8, 6),
        new Variant("ladder_shiny", "ladder0", 1, 9, 6),
        new Variant("ladder_rusty", "ladder0", 2, 10, 6),
        new Variant("ladder_carbon", "ladder0", 3, 11, 6)
    );
    public static final List<CamoPalette> PALETTES=List.of("metalpanel","concrete","ladder0").stream()
            .map(f->new CamoPalette(f,ALL.stream().filter(v->v.family().equals(f)).map(v->"techguns:"+v.id()).toList())).toList();
    public static Optional<CamoPalette> palette(String item) { return PALETTES.stream().filter(p->p.index(item)>=0).findFirst(); }
    public static Optional<Variant> variant(String item) { return ALL.stream().filter(v->item.equals("techguns:"+v.id())).findFirst(); }
    private BuildingBlocks() {}
}
