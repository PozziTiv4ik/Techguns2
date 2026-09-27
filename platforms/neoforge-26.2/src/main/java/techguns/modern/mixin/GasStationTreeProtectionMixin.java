package techguns.modern.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import techguns.modern.world.structure.GasStationStructure;
import techguns.modern.world.structure.MilitaryCampStructure;
import techguns.modern.world.structure.CastleStructure;
import techguns.modern.world.structure.CastlePiece;
import techguns.modern.world.structure.MilitaryCampPiece;

/** Neighbouring foliage respects the station and the military camp's cleared surface. */
@Mixin(TreeFeature.class)
public abstract class GasStationTreeProtectionMixin {
    @Inject(method="validTreePos",at=@At("RETURN"),cancellable=true)
    private static void techguns$preserveStation(LevelSimulatedReader level,BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
        // Only initial decoration: saplings and player-triggered tree growth remain available.
        if(!ci.getReturnValueZ() || !(level instanceof WorldGenRegion region)) return;
        var manager=region.getLevel().structureManager().forWorldGenRegion(region);
        for(var start:manager.startsForStructure(SectionPos.of(pos).chunk(),s->s instanceof GasStationStructure || s instanceof MilitaryCampStructure || s instanceof CastleStructure))
            if(start.getPieces().stream().anyMatch(p->p instanceof CastlePiece castle?castle.protectsDecoration(pos):p instanceof MilitaryCampPiece camp?camp.protectsDecoration(pos,region):p.getBoundingBox().isInside(pos))) { ci.setReturnValue(false); return; }
    }
}
