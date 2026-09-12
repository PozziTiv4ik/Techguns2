package techguns.modern.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import techguns.core.RuralZombieRules;

public final class ZombieFarmer extends RuralZombie {
    public ZombieFarmer(EntityType<? extends ZombieFarmer> type,Level level) { super(type,level,RuralZombieRules.Kind.FARMER); }
}
