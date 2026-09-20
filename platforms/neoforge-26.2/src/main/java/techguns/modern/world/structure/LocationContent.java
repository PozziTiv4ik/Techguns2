package techguns.modern.world.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.modern.Techguns;

public final class LocationContent {
    private static final DeferredRegister<StructureType<?>> TYPES=DeferredRegister.create(Registries.STRUCTURE_TYPE,Techguns.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES=DeferredRegister.create(Registries.STRUCTURE_PIECE,Techguns.MOD_ID);
    public static final DeferredHolder<StructureType<?>,StructureType<NetherAltarStructure>> ALTAR=TYPES.register("nether_altar_small",()->()->NetherAltarStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> ALTAR_PIECE=PIECES.register("nether_altar_small",()->(context,tag)->new NetherAltarPiece(context.structureTemplateManager(),tag));
    public static final DeferredHolder<StructureType<?>,StructureType<NetherLootStructure>> LOOT=TYPES.register("nether_loot_01",()->()->NetherLootStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> LOOT_PIECE=PIECES.register("nether_loot_01",()->(context,tag)->new NetherLootPiece(context.structureTemplateManager(),tag));
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); }
    private LocationContent() {}
}
