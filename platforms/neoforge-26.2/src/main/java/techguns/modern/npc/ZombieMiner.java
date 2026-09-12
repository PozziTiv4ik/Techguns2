package techguns.modern.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import techguns.core.RuralZombieRules;

public final class ZombieMiner extends RuralZombie {
    public ZombieMiner(EntityType<? extends ZombieMiner> type,Level level) { super(type,level,RuralZombieRules.Kind.MINER); }
}
