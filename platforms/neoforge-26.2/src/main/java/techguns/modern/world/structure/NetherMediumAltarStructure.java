package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherMediumAltarStructure extends MediumNetherStructure {
    public static final MapCodec<NetherMediumAltarStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherMediumAltarStructure::new));
    public NetherMediumAltarStructure(StructureSettings settings,int big) { super(settings,big,0,16,-2,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) {
        return new NetherMediumAltarPiece(context.structureTemplateManager(),origin,turns);
    }
    @Override public StructureType<?> type() { return LocationContent.MEDIUM_ALTAR.get(); }
}
