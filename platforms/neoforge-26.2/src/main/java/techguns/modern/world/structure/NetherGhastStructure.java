package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherGhastStructure extends MediumNetherStructure {
    public static final MapCodec<NetherGhastStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherGhastStructure::new));
    public NetherGhastStructure(StructureSettings settings,int big) { super(settings,big,1,10,-3,13); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) {
        return new NetherGhastPiece(context.structureTemplateManager(),origin,turns);
    }
    @Override public StructureType<?> type() { return LocationContent.GHAST.get(); }
}
