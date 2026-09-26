package techguns.modern.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.BasaltColumnsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;

/** Preserve the same structure-block exclusions during both basalt surface and upward searches. */
@Mixin(BasaltColumnsFeature.class)
public abstract class NetherBasaltProtectionMixin {
    @Unique private static final TagKey<Block> TECHGUNS_STRUCTURE_BLOCKS=TagKey.create(Registries.BLOCK,Identifier.parse("techguns:nether_structure_blocks"));
    @Redirect(method={"canPlaceAt","findAir"},at=@At(value="INVOKE",target="Lcom/google/common/collect/ImmutableList;contains(Ljava/lang/Object;)Z"))
    private static boolean techguns$excludeStructureBlocks(ImmutableList<Block> excluded,Object candidate) {
        return excluded.contains(candidate) || candidate instanceof Block block && block.defaultBlockState().is(TECHGUNS_STRUCTURE_BLOCKS);
    }
}
