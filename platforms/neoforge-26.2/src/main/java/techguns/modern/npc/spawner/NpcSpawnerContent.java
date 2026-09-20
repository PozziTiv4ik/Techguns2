package techguns.modern.npc.spawner;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import techguns.modern.*;

public final class NpcSpawnerContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<NpcSpawnerBlock> BLOCK=BLOCKS.registerBlock("tg_spawner",NpcSpawnerBlock::new,
            p->p.mapColor(MapColor.STONE).strength(-1,0).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> ITEM=TGContent.ITEMS.registerSimpleBlockItem(BLOCK);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NpcSpawnerBlockEntity>> ENTITY=ENTITIES.register("tg_spawner",
            ()->new BlockEntityType<>(NpcSpawnerBlockEntity::new,true,BLOCK.get()));
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); NeoForge.EVENT_BUS.addListener(SpawnerMailbox::cleanLoadedPositions); }
    private NpcSpawnerContent() {}
}
