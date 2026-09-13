package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import techguns.core.UndeadRules;

/** Source daylight behavior; the exemption for linked Techguns spawners awaits that system's port. */
public abstract class BurningUndeadNpc extends ArmedNpc {
    protected BurningUndeadNpc(EntityType<? extends BurningUndeadNpc> type,Level level) { super(type,level); }
    @Override public void aiStep() {
        if(!level().isClientSide() && isAlive() && level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN,position())) {
            float brightness=getLightLevelDependentMagicValue();
            // GenericNPCUndead ignites the body regardless of headgear, without vanilla helmet wear.
            if(brightness>.5f && UndeadRules.sunIgnites(brightness,random.nextFloat())
                    && level().canSeeSky(BlockPos.containing(getX(),getEyeY(),getZ()))) igniteForSeconds(8);
        }
        super.aiStep();
    }
}
