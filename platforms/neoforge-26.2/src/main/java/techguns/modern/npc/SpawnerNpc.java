package techguns.modern.npc;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** Persistent ITGSpawnerNPC lifecycle, independent of weapon AI and GenericNPC factions. */
public abstract class SpawnerNpc extends Monster {
    private techguns.modern.npc.spawner.SpawnerLink spawnerLink;
    private boolean spawnerNotified;
    protected SpawnerNpc(EntityType<? extends SpawnerNpc> type,Level level) { super(type,level); }
    public techguns.modern.npc.spawner.SpawnerLink spawnerLink() { return spawnerLink; }
    public boolean hasSpawnerOrigin() { return spawnerLink!=null; }
    public void bindSpawner(techguns.modern.npc.spawner.SpawnerLink link) { spawnerLink=link; spawnerNotified=false; }
    private void notifySpawner(boolean killed) {
        if(spawnerLink!=null && !spawnerNotified && !level().isClientSide()) { spawnerLink.removed(this,killed); spawnerNotified=true; }
    }
    @Override public void tick() {
        if(spawnerLink!=null && !spawnerNotified && !level().isClientSide()) {
            if(!level().dimension().equals(spawnerLink.origin().dimension())) { notifySpawner(false); clearHome(); }
            else if(tickCount%20==0) spawnerLink.relink(this);
        }
        super.tick();
    }
    @Override public void die(DamageSource source) { super.die(source); if(dead) notifySpawner(true); }
    @Override public void remove(RemovalReason reason) {
        if(reason==RemovalReason.KILLED || reason==RemovalReason.DISCARDED || reason==RemovalReason.CHANGED_DIMENSION) notifySpawner(reason==RemovalReason.KILLED);
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if(spawnerLink!=null) { out.store("tg_spawner",techguns.modern.npc.spawner.SpawnerLink.CODEC,spawnerLink); out.putBoolean("tg_spawner_released",spawnerNotified); }
    }
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        spawnerLink=in.read("tg_spawner",techguns.modern.npc.spawner.SpawnerLink.CODEC).orElse(null); spawnerNotified=in.getBooleanOr("tg_spawner_released",false);
        // Dimension transfer copies NBT before the old entity receives CHANGED_DIMENSION.
        // Keep the origin marker, but the copied NPC must never re-adopt the old reservation.
        if(spawnerLink!=null && !level().dimension().equals(spawnerLink.origin().dimension())) { spawnerNotified=true; clearHome(); }
    }
}
