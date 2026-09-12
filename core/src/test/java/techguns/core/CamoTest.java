package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CamoTest {
    @Test void originalFamiliesAreDisjointAndBedsAreExcluded() {
        assertEquals(Set.of("wool","concrete","concrete_powder","terracotta","stained_glass","stained_glass_pane","banner","carpet"),
                new HashSet<>(CamoPalettes.ALL.stream().map(CamoPalette::id).toList()));
        assertEquals(128, CamoPalettes.ALL.stream().flatMap(p -> p.items().stream()).distinct().count());
        assertTrue(CamoPalettes.forItem("minecraft:white_bed").isEmpty());
        assertTrue(CamoPalettes.forItem("minecraft:terracotta").isEmpty());
    }
    @Test void blockColorsUseMetadataOrder() {
        var wool = CamoPalettes.forItem("minecraft:white_wool").orElseThrow();
        assertEquals("minecraft:orange_wool", wool.next("minecraft:white_wool", false));
        assertEquals("minecraft:black_wool", wool.next("minecraft:white_wool", true));
        assertEquals("minecraft:light_gray_wool", wool.next("minecraft:gray_wool", false));
    }
    @Test void bannersUseReversedDyeOrder() {
        var banner = CamoPalettes.forItem("minecraft:black_banner").orElseThrow();
        assertEquals(0, banner.index("minecraft:black_banner"));
        assertEquals("minecraft:red_banner", banner.next("minecraft:black_banner", false));
        assertEquals("minecraft:white_banner", banner.next("minecraft:black_banner", true));
        assertEquals("minecraft:black_banner", banner.next("minecraft:white_banner", false));
    }
    @Test void everyPaletteCyclesInBothDirectionsWithoutLoss() {
        for (var palette : CamoPalettes.ALL) for (var item : palette.items()) {
            String forward = item, back = item;
            for (int i = 0; i < 16; i++) { forward = palette.next(forward, false); back = palette.next(back, true); }
            assertEquals(item, forward); assertEquals(item, back);
            assertEquals(item, palette.next(palette.next(item, true), false));
        }
        assertEquals(0, CamoPalette.cycle(5,6,false)); assertEquals(5, CamoPalette.cycle(0,6,true));
    }
    @Test void invalidPaletteAndIndicesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CamoPalette("bad", List.of("same", "same")));
        assertThrows(IllegalArgumentException.class, () -> CamoPalette.cycle(-1, 6, true));
        assertThrows(IllegalArgumentException.class, () -> CamoPalette.cycle(6, 6, false));
        assertThrows(IllegalArgumentException.class, () -> CamoPalette.cycle(0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> CamoPalettes.ALL.getFirst().next("minecraft:stone", false));
    }
}
