package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherAltarStructure extends SmallNetherStructure {
    public static final MapCodec<NetherAltarStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherAltarStructure::new));
    public NetherAltarStructure(StructureSettings settings,int medium,int big) { super(settings,medium,big,0,11,-3,16); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) { return new NetherAltarPiece(context.structureTemplateManager(),origin,direction); }
    @Override public StructureType<?> type() { return LocationContent.ALTAR.get(); }
}
