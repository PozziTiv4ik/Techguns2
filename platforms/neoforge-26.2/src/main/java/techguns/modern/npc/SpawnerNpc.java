package techguns.modern.npc;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import techguns.modern.npc.spawner.*;

/** Persistent ITGSpawnerNPC lifecycle, independent of weapon AI and GenericNPC factions. */
public abstract class SpawnerNpc extends Monster implements SpawnerLinked {
    private final SpawnerLifecycle spawner=new SpawnerLifecycle();
    protected SpawnerNpc(EntityType<? extends SpawnerNpc> type,Level level) { super(type,level); }
    @Override public SpawnerLifecycle spawnerLifecycle() { return spawner; }
    @Override public void tick() { spawner.tick(this); super.tick(); }
    @Override public void die(DamageSource source) { super.die(source); if(dead) spawner.notifyOwner(this,true); }
    @Override public void remove(RemovalReason reason) { spawner.remove(this,reason); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); spawner.save(out); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); spawner.load(this,in); }
}
