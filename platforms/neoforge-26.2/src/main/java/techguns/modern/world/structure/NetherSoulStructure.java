package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherSoulStructure extends SmallNetherStructure {
    public static final MapCodec<NetherSoulStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherSoulStructure::new));
    // Source registration uses 11x11 despite the class/scan's 13x13 dimensions. Preserve its centre and shifts.
    public NetherSoulStructure(StructureSettings settings,int medium,int big) { super(settings,medium,big,1,11,-3,16); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) {
        return new NetherSoulPiece(context.structureTemplateManager(),origin,direction);
    }
    @Override public StructureType<?> type() { return LocationContent.SOUL.get(); }
}
