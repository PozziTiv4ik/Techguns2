package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class NetherLootStructure extends SmallNetherStructure {
    public static final MapCodec<NetherLootStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherLootStructure::new));
    public NetherLootStructure(StructureSettings settings,int medium,int big) { super(settings,medium,big,2,6,0,16); }
    @Override protected TemplateStructurePiece createPiece(StructureTemplateManager manager,BlockPos origin,int direction) { return new NetherLootPiece(manager,origin,direction); }
    @Override public StructureType<?> type() { return LocationContent.LOOT.get(); }
}
