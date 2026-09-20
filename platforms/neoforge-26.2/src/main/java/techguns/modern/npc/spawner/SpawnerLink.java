package techguns.modern.npc.spawner;

import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import techguns.modern.npc.SpawnerNpc;

/** Origin remains on the NPC for the original daylight exemption; instance IDs prevent adopting an old NPC after replacement. */
public record SpawnerLink(GlobalPos origin,UUID instance) {
    public static final Codec<SpawnerLink> CODEC=RecordCodecBuilder.create(i->i.group(
            GlobalPos.CODEC.fieldOf("origin").forGetter(SpawnerLink::origin),UUIDUtil.CODEC.fieldOf("instance").forGetter(SpawnerLink::instance)).apply(i,SpawnerLink::new));
    public NpcSpawnerBlockEntity loadedOwner(ServerLevel level) {
        if(!origin.dimension().equals(level.dimension()) || !level.hasChunkAt(origin.pos())) return null;
        return level.getBlockEntity(origin.pos()) instanceof NpcSpawnerBlockEntity block && !block.isRemoved() && instance.equals(block.instance()) ? block : null;
    }
    public void relink(SpawnerNpc npc) {
        if(npc.level() instanceof ServerLevel level) {
            var owner=loadedOwner(level);
            if(owner!=null && npc.isAlive()) owner.relink(npc);
        }
    }
    public void removed(SpawnerNpc npc,boolean killed) {
        if(!(npc.level() instanceof ServerLevel level)) return;
        var ownerLevel=level.getServer().getLevel(origin.dimension());
        if(ownerLevel==null) return;
        // A dimension departure releases the original slot, even if the copied NPC dies later.
        boolean death=killed && level.dimension().equals(origin.dimension());
        var owner=loadedOwner(ownerLevel);
        if(owner!=null) owner.finish(npc.getUUID(),death);
        else if(!ownerLevel.hasChunkAt(origin.pos())) SpawnerMailbox.get(ownerLevel).enqueue(this,npc.getUUID(),death);
    }
}
