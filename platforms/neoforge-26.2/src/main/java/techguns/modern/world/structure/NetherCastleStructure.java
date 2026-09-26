package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

/** Conditional castle ticket after the medium altar and ghast-spawner tickets. */
public final class NetherCastleStructure extends MediumNetherStructure {
    public static final MapCodec<NetherCastleStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherCastleStructure::new));
    public NetherCastleStructure(StructureSettings settings,int big) { super(settings,big,2,11,-2,8); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) {
        return new NetherCastlePiece(context.structureTemplateManager(),origin,turns,context.random().nextLong());
    }
    @Override public StructureType<?> type() { return LocationContent.NETHER_CASTLE.get(); }
}
