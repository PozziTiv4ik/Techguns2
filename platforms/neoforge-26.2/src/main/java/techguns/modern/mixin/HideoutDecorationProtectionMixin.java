package techguns.modern.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import techguns.modern.world.structure.SurvivorHideoutStructure;

/** A later neighbouring chunk must not grow a vanilla shrub inside the scanned hideout. */
@Mixin(SimpleBlockFeature.class)
public abstract class HideoutDecorationProtectionMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void techguns$preserveHideout(FeaturePlaceContext<SimpleBlockConfiguration> context,CallbackInfoReturnable<Boolean> ci) {
        // Restrict this to initial terrain decoration: player placement and bonemeal stay available.
        if(!(context.level() instanceof WorldGenRegion region)) return;
        var manager=region.getLevel().structureManager().forWorldGenRegion(region); var pos=context.origin();
        for(var start:manager.startsForStructure(SectionPos.of(pos).chunk(),s->s instanceof SurvivorHideoutStructure))
            if(start.getPieces().stream().anyMatch(p->p.getBoundingBox().isInside(pos))) { ci.setReturnValue(false); return; }
    }
}
