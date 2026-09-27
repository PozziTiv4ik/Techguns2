package techguns.modern.world;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import techguns.core.MilitaryCrates;
import techguns.modern.*;

public final class MilitaryCrateContent {
    private static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String, DeferredBlock<MilitaryCrateBlock>> BLOCKS = create();
    private static Map<String, DeferredBlock<MilitaryCrateBlock>> create() {
        var blocks = new LinkedHashMap<String, DeferredBlock<MilitaryCrateBlock>>();
        for (var v : MilitaryCrates.ALL) {
            // GenericBlockMetaEnum's short constructor supplies STONE sound even with Material.WOOD.
            var block = REGISTRY.registerBlock(v.id(), p -> new MilitaryCrateBlock(v.metadata(), p), p -> p.mapColor(MapColor.WOOD)
                    .strength(4,4).sound(SoundType.STONE).instrument(NoteBlockInstrument.BASS).ignitedByLava().noOcclusion()
                    .isRedstoneConductor((s,l,pos) -> false)
                    .overrideLootTable(Optional.of(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("blocks/"+v.id()+"_self")))));
            TGContent.ITEMS.registerSimpleBlockItem(block); blocks.put(v.id(), block);
        }
        return Collections.unmodifiableMap(blocks);
    }
    public static MilitaryCrateBlock fromMetadata(int metadata) { return BLOCKS.get(MilitaryCrates.byMetadata(metadata).id()).get(); }
    public static void register(IEventBus bus) { REGISTRY.register(bus); NeoForge.EVENT_BUS.register(MilitaryCrateDrops.class); }
    private MilitaryCrateContent() {}
}
