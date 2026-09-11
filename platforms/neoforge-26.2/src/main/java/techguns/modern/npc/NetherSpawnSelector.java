package techguns.modern.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** TGDummySpawn equivalent: replaced on the server before it enters the world. */
public final class NetherSpawnSelector extends Monster {
    public NetherSpawnSelector(EntityType<? extends NetherSpawnSelector> type, Level level) { super(type, level); }
    @Override protected void registerGoals() {}
}
