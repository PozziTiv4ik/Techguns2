package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class GasStationStructure extends SmallOverworldStructure {
    public static final MapCodec<GasStationStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,GasStationStructure::new));
    public GasStationStructure(StructureSettings settings,int medium,int big) { super(settings,medium,big,3,9,12,3,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) { return new GasStationPiece(context.structureTemplateManager(),origin,turns); }
    @Override public StructureType<?> type() { return LocationContent.GAS.get(); }
}
