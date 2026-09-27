package techguns.modern.mixin;

import java.util.function.Function;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import techguns.modern.world.structure.CastlePiece;
import techguns.modern.world.structure.CastleStructure;

/** Neighbouring ore/gravel/stone blobs must not replace authored dungeon walls. */
@Mixin(OreFeature.class)
public abstract class CastleOreProtectionMixin {
    @WrapOperation(method="doPlace",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/feature/OreFeature;canPlaceOre(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration$TargetBlockState;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Z"))
    private boolean techguns$preserveDungeon(BlockState state,Function<BlockPos,BlockState> getter,RandomSource random,
            OreConfiguration config,OreConfiguration.TargetBlockState target,BlockPos.MutableBlockPos pos,
            Operation<Boolean> original,@Local(argsOnly=true) WorldGenLevel level) {
        // Evaluate the native rule first, preserving its RNG consumption and ordinary feature calls.
        if(!original.call(state,getter,random,config,target,pos)) return false;
        if(!(level instanceof WorldGenRegion region)) return true;
        var manager=region.getLevel().structureManager().forWorldGenRegion(region);
        for(var start:manager.startsForStructure(SectionPos.of(pos).chunk(),s->s instanceof CastleStructure))
            if(start.getPieces().stream().anyMatch(p->p instanceof CastlePiece castle&&castle.protectsDecoration(pos))) return false;
        return true;
    }
}
