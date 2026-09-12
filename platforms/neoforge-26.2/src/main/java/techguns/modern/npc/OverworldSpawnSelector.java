package techguns.modern.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** Server-only TGDummySpawn marker; the Overworld handler removes it before world insertion. */
public final class OverworldSpawnSelector extends Monster {
    public OverworldSpawnSelector(EntityType<? extends OverworldSpawnSelector> type, Level level) { super(type,level); }
    @Override protected void registerGoals() {}
}
