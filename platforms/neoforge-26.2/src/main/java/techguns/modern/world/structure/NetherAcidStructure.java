package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherAcidStructure extends SmallNetherStructure {
    public static final MapCodec<NetherAcidStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherAcidStructure::new));
    public NetherAcidStructure(StructureSettings settings,int medium,int big) { super(settings,medium,big,3,9,-2,16); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) {
        return new NetherAcidPiece(context.structureTemplateManager(),origin,direction,context.random().nextLong());
    }
    @Override public StructureType<?> type() { return LocationContent.ACID.get(); }
}
