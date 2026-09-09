package techguns.modern.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import techguns.modern.TGContent;

public final class TGLiquidBlock extends LiquidBlock {
    public static final ResourceKey<DamageType> ACID_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("acid"));
    private final boolean acid;
    public TGLiquidBlock(FlowingFluid fluid,Properties properties,boolean acid) { super(fluid,properties); this.acid=acid; }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity,
                                          InsideBlockEffectApplier effects,boolean precise) {
        if (acid && level instanceof ServerLevel server && entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
            // The source applies two poison-type damage with ordinary hurt immunity and no knockback.
            living.hurtServer(server,level.damageSources().source(ACID_DAMAGE),2);
        }
    }
}
