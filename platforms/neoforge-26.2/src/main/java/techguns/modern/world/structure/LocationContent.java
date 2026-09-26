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
    public static final DeferredHolder<StructureType<?>,StructureType<NetherAcidStructure>> ACID=TYPES.register("nether_acid_hole",()->()->NetherAcidStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> ACID_PIECE=PIECES.register("nether_acid_hole",()->(context,tag)->new NetherAcidPiece(context.structureTemplateManager(),tag));
    public static final DeferredHolder<StructureType<?>,StructureType<NetherSoulStructure>> SOUL=TYPES.register("nether_soul_platform",()->()->NetherSoulStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> SOUL_PIECE=PIECES.register("nether_soul_platform",()->(context,tag)->new NetherSoulPiece(context.structureTemplateManager(),tag));
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); }
    public static final DeferredHolder<StructureType<?>,StructureType<NetherClusterStructure>> CLUSTER=TYPES.register("nether_ore_cluster_small",()->()->NetherClusterStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> CLUSTER_PIECE=PIECES.register("nether_ore_cluster_small",()->(context,tag)->new NetherClusterPiece(context.structureTemplateManager(),tag));
    public static final DeferredHolder<StructureType<?>,StructureType<OreSpikeStructure>> SPIKE=TYPES.register("orecluster_spike",()->()->OreSpikeStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> SPIKE_PIECE=PIECES.register("orecluster_spike",()->(context,tag)->new OreSpikePiece(context.structureTemplateManager(),tag));
    public static final DeferredHolder<StructureType<?>,StructureType<MeteorStructure>> METEOR=TYPES.register("orecluster_meteor_basis",()->()->MeteorStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> METEOR_PIECE=PIECES.register("orecluster_meteor_basis",()->(context,tag)->new MeteorPiece(context.structureTemplateManager(),tag));
    public static final DeferredHolder<StructureType<?>,StructureType<NetherGhastStructure>> GHAST=TYPES.register("nether_ghast_spawner",()->()->NetherGhastStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> GHAST_PIECE=PIECES.register("nether_ghast_spawner",()->(context,tag)->new NetherGhastPiece(context.structureTemplateManager(),tag));
    private LocationContent() {}
    public static final DeferredHolder<StructureType<?>,StructureType<NetherMediumAltarStructure>> MEDIUM_ALTAR=TYPES.register("nether_altar_medium",()->()->NetherMediumAltarStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> MEDIUM_ALTAR_PIECE=PIECES.register("nether_altar_medium",()->(context,tag)->new NetherMediumAltarPiece(context.structureTemplateManager(),tag));
    public static final DeferredHolder<StructureType<?>,StructureType<NetherCastleStructure>> NETHER_CASTLE=TYPES.register("nether_ore_cluster_castle",()->()->NetherCastleStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> NETHER_CASTLE_PIECE=PIECES.register("nether_ore_cluster_castle",()->(context,tag)->new NetherCastlePiece(context.structureTemplateManager(),tag));
    public static final DeferredHolder<StructureType<?>,StructureType<BugNestStructure>> BUGNEST=TYPES.register("alienbug_nest",()->()->BugNestStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> BUGNEST_PIECE=PIECES.register("alienbug_nest",()->(context,tag)->new BugNestPiece(tag));
}
