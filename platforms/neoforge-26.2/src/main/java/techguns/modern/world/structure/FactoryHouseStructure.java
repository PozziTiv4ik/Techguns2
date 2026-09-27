package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class FactoryHouseStructure extends SmallOverworldStructure {
    public static final MapCodec<FactoryHouseStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,FactoryHouseStructure::new));
    public FactoryHouseStructure(StructureSettings settings,int medium,int big) { super(settings,medium,big,0,11,10,3,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) { return new FactoryHousePiece(context.structureTemplateManager(),origin,turns); }
    @Override public StructureType<?> type() { return LocationContent.FACTORY.get(); }
}
