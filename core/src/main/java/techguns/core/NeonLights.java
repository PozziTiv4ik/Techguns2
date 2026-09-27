package techguns.core;

import java.util.List;
import java.util.Optional;

/** Generated original Neonlights enum order; rotated styles are textures, not directional states. */
public final class NeonLights {
    public record Variant(String id,int metadata) {}
    public static final List<Variant> ALL=List.of(
        new Variant("neontubes2", 0),
        new Variant("neontubes2_rotated", 1),
        new Variant("neontubes4", 2),
        new Variant("neontubes4_rotated", 3),
        new Variant("neonsquare_white", 4)
    );
    public static final CamoPalette PALETTE=new CamoPalette("neonlights",ALL.stream().map(v->"techguns:"+v.id()).toList());
    public static Optional<CamoPalette> palette(String item) { return PALETTE.index(item)>=0?Optional.of(PALETTE):Optional.empty(); }
    public static Variant byMetadata(int metadata) {
        if(metadata<0 || metadata>=ALL.size()) throw new IllegalArgumentException("Invalid neonlights metadata: "+metadata);
        return ALL.get(metadata);
    }
    private NeonLights() {}
}
