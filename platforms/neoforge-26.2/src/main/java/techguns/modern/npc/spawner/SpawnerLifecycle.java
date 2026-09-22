package techguns.modern.npc.spawner;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.storage.*;

/** Persistent reservation lifecycle, independent of the NPC's vanilla superclass. */
public final class SpawnerLifecycle {
    private SpawnerLink link;
    private boolean notified;
    public SpawnerLink link() { return link; }
    public void bind(SpawnerLink value) { link=value; notified=false; }
    public void notifyOwner(Mob npc,boolean killed) {
        if(link!=null && !notified && !npc.level().isClientSide()) { link.removed(npc,killed); notified=true; }
    }
    public void tick(Mob npc) {
        if(link!=null && !notified && !npc.level().isClientSide()) {
            if(!npc.level().dimension().equals(link.origin().dimension())) { notifyOwner(npc,false); npc.clearHome(); }
            else if(npc.tickCount%20==0) link.relink(npc);
        }
    }
    public void remove(Mob npc,RemovalReason reason) {
        if(reason==RemovalReason.KILLED || reason==RemovalReason.DISCARDED || reason==RemovalReason.CHANGED_DIMENSION)
            notifyOwner(npc,reason==RemovalReason.KILLED);
    }
    public void save(ValueOutput out) {
        if(link!=null) { out.store("tg_spawner",SpawnerLink.CODEC,link); out.putBoolean("tg_spawner_released",notified); }
    }
    public void load(Mob npc,ValueInput in) {
        link=in.read("tg_spawner",SpawnerLink.CODEC).orElse(null); notified=in.getBooleanOr("tg_spawner_released",false);
        // Dimension transfer copies NBT before the old entity receives CHANGED_DIMENSION.
        if(link!=null && !npc.level().dimension().equals(link.origin().dimension())) { notified=true; npc.clearHome(); }
    }
}
