package techguns.modern.npc.spawner;

import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import techguns.modern.TGContent;

/** Persist removal notifications without force-loading the owner's chunk. Normal unload is never a removal notification. */
public final class SpawnerMailbox extends SavedData {
    public record Notice(SpawnerLink owner,UUID npc,boolean killed) {
        public static final Codec<Notice> CODEC=RecordCodecBuilder.create(i->i.group(
                SpawnerLink.CODEC.fieldOf("owner").forGetter(Notice::owner),UUIDUtil.CODEC.fieldOf("npc").forGetter(Notice::npc),
                Codec.BOOL.fieldOf("killed").forGetter(Notice::killed)).apply(i,Notice::new));
    }
    public static final Codec<SpawnerMailbox> CODEC=Notice.CODEC.listOf().fieldOf("notices").xmap(SpawnerMailbox::new,m->List.copyOf(m.notices)).codec();
    public static final SavedDataType<SpawnerMailbox> TYPE=new SavedDataType<>(TGContent.id("spawner_notices"),SpawnerMailbox::new,CODEC);
    private final List<Notice> notices=new ArrayList<>();
    public SpawnerMailbox() {}
    private SpawnerMailbox(List<Notice> notices) { this.notices.addAll(notices); }
    public static SpawnerMailbox get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public int size() { return notices.size(); }
    public void enqueue(SpawnerLink owner,UUID npc,boolean killed) {
        for(int i=0;i<notices.size();i++) {
            var old=notices.get(i);
            if(old.owner().equals(owner) && old.npc().equals(npc)) {
                if(killed && !old.killed()) { notices.set(i,new Notice(owner,npc,true)); setDirty(); }
                return;
            }
        }
        notices.add(new Notice(owner,npc,killed)); setDirty();
    }
    public List<Notice> drain(SpawnerLink owner) {
        var result=new ArrayList<Notice>();
        for(var it=notices.iterator();it.hasNext();) {
            var notice=it.next();
            if(notice.owner().origin().equals(owner.origin())) {
                if(notice.owner().equals(owner)) result.add(notice);
                it.remove(); setDirty(); // A replacement at this position must not inherit the old instance's deaths.
            }
        }
        return result;
    }
    public void forget(BlockPos pos) { if(notices.removeIf(n->n.owner().origin().pos().equals(pos))) setDirty(); }
    public static void cleanLoadedPositions(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%200!=0) return;
        var data=level.getDataStorage().get(TYPE); if(data==null) return;
        if(data.notices.removeIf(n->level.hasChunkAt(n.owner().origin().pos()) && n.owner().loadedOwner(level)==null)) data.setDirty();
    }
}
