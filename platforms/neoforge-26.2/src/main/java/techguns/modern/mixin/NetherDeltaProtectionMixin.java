package techguns.modern.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.DeltaFeature;
import net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend DeltaFeature's vanilla fortress/spawner exclusions to the port's structure blocks. */
@Mixin(DeltaFeature.class)
public abstract class NetherDeltaProtectionMixin {
    @Unique private static final TagKey<Block> TECHGUNS_STRUCTURE_BLOCKS=TagKey.create(Registries.BLOCK,Identifier.parse("techguns:nether_structure_blocks"));
    @Inject(method="isClear",at=@At("HEAD"),cancellable=true)
    private static void techguns$preserveNetherStructures(LevelAccessor level,BlockPos pos,DeltaFeatureConfiguration config,CallbackInfoReturnable<Boolean> ci) {
        if(level.getBlockState(pos).is(TECHGUNS_STRUCTURE_BLOCKS)) ci.setReturnValue(false);
    }
}
