package techguns.modern.npc.spawner;

import java.util.*;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.SpawnerRules;
import techguns.modern.TGContent;
import techguns.modern.npc.ArmedNpc;

/** Source finite-death spawner, with persistent reservations and unloaded-owner delivery. No FE or GUI. */
public final class NpcSpawnerBlockEntity extends BlockEntity implements IOwnedSpawner {
    public record Entry(Identifier id,int weight) {
        public static final Codec<Entry> CODEC=RecordCodecBuilder.create(i->i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),Codec.intRange(1,10000).fieldOf("weight").forGetter(Entry::weight)).apply(i,Entry::new));
        public Entry { if(id==null || weight<1 || weight>10000) throw new IllegalArgumentException("Invalid NPC entry"); }
    }
    private UUID instance=UUID.randomUUID();
    private final net.minecraft.util.RandomSource random=net.minecraft.util.RandomSource.create();
    private int remaining=SpawnerRules.DEFAULT_REMAINING, maximum=SpawnerRules.DEFAULT_ACTIVE, interval=SpawnerRules.DEFAULT_DELAY, delay=SpawnerRules.DEFAULT_DELAY, height;
    private double range=SpawnerRules.DEFAULT_RANGE;
    private List<Entry> entries=List.of();
    private ItemStack weapon=ItemStack.EMPTY;
    private final Set<UUID> active=new HashSet<>();
    public NpcSpawnerBlockEntity(BlockPos pos,BlockState state) { super(NpcSpawnerContent.ENTITY.get(),pos,state); }
    public UUID instance() { return instance; }
    public int remaining() { return remaining; }
    public int activeCount() { return active.size(); }
    public Set<UUID> activeIds() { return Set.copyOf(active); }
    public int delay() { return delay; }
    public int maximum() { return maximum; }
    public int interval() { return interval; }
    public int heightOffset() { return height; }
    public double range() { return range; }
    public List<Entry> entries() { return entries; }
    public ItemStack weaponOverride() { return weapon.copy(); }
    public SpawnerLink link() { return new SpawnerLink(GlobalPos.of(level.dimension(),worldPosition),instance); }
    @Override public Either<BlockEntity,Entity> getOwner() { return Either.left(this); }
    public void defaultHole() {
        // Placing a copied block item copies configuration, not live NPC ownership.
        instance=UUID.randomUUID(); active.clear();
        if(entries.isEmpty()) { entries=List.of(new Entry(TGContent.id("zombiesoldier"),1)); setChanged(); }
        setChanged();
    }
    public void configure(int remaining,int maximum,int interval,double range,int height,List<Entry> entries,ItemStack weapon) {
        if(remaining<0 || remaining>10000 || maximum<1 || maximum>128 || interval<1 || interval>72000
                || !Double.isFinite(range) || range<0 || range>64 || Math.abs((long)height)>1024 || entries.size()>256)
            throw new IllegalArgumentException("Spawner configuration outside supported bounds");
        this.remaining=remaining; this.maximum=maximum; this.interval=interval; delay=interval; this.range=range; this.height=height;
        this.entries=List.copyOf(entries); this.weapon=weapon.copy(); setChanged();
    }
    public void relink(ArmedNpc npc) {
        if(level instanceof ServerLevel && !isRemoved() && npc.level()==level && npc.isAlive() && link().equals(npc.spawnerLink()) && active.add(npc.getUUID())) {
            npc.setHomeTo(worldPosition,SpawnerRules.HOME_RADIUS); setChanged();
        }
    }
    public void finish(UUID npc,boolean killed) {
        if(active.remove(npc)) { if(killed) remaining=Math.max(0,remaining-1); setChanged(); }
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,NpcSpawnerBlockEntity block) {
        if(level instanceof ServerLevel server && !block.isRemoved()) block.tick(server);
    }
    private void tick(ServerLevel server) {
        for(var notice:SpawnerMailbox.get(server).drain(link())) finish(notice.npc(),notice.killed());
        if(remaining<=0) { server.setBlock(worldPosition,Blocks.AIR.defaultBlockState(),3); return; }
        if(delay>0) { delay--; setChanged(); }
        if(delay>0) return;
        if(!SpawnerRules.hasRoom(active.size(),maximum,remaining) || entries.isEmpty() || server.getDifficulty()==Difficulty.PEACEFUL) { resetDelay(); return; }
        var weights=entries.stream().map(Entry::weight).toList(); int total=weights.stream().mapToInt(Integer::intValue).sum();
        var entry=entries.get(SpawnerRules.choose(weights,random.nextInt(total)));
        double x=worldPosition.getX()+SpawnerRules.offset(random.nextDouble(),random.nextDouble(),range);
        double y=worldPosition.getY()+1+height;
        double z=worldPosition.getZ()+SpawnerRules.offset(random.nextDouble(),random.nextDouble(),range);
        var at=BlockPos.containing(x,y,z);
        // The original ignores daylight, ground, collision and nearby-player checks. Preserve that,
        // but do not load a neighboring chunk merely to make a configured spawn attempt.
        if(!server.hasChunkAt(at) || !server.isInsideBuildHeight(at) || !BuiltInRegistries.ENTITY_TYPE.containsKey(entry.id())) { resetDelay(); return; }
        Entity entity=BuiltInRegistries.ENTITY_TYPE.getValue(entry.id()).create(server,EntitySpawnReason.SPAWNER);
        if(!(entity instanceof ArmedNpc npc)) { resetDelay(); return; }
        npc.setPos(x,y,z);
        var event=EventHooks.finalizeMobSpawnSpawner(npc,server,server.getCurrentDifficultyAt(at),EntitySpawnReason.SPAWNER,null,this,true);
        if(event.isCanceled() || event.isSpawnCancelled()) return; // Source special-spawn veto retries on the next tick.
        npc.setHomeTo(worldPosition,SpawnerRules.HOME_RADIUS);
        if(!weapon.isEmpty()) npc.setItemSlot(EquipmentSlot.MAINHAND,weapon.copy());
        npc.bindSpawner(link());
        if(!server.addFreshEntity(npc) || !npc.isAlive()) { npc.discard(); return; }
        active.add(npc.getUUID()); delay=interval; setChanged();
        server.levelEvent(2004,worldPosition,0); npc.spawnAnim();
    }
    private void resetDelay() { delay=interval; setChanged(); }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("instance",UUIDUtil.CODEC,instance); out.store("active",UUIDUtil.CODEC.listOf(),active.stream().sorted().toList());
        out.putInt("mobsLeft",remaining); out.putInt("maxActive",maximum); out.putInt("spawnDelay",interval); out.putInt("delay",delay);
        out.putDouble("spawnRange",range); out.putInt("spawnHeightOffset",height); out.store("mobtypes",Entry.CODEC.listOf(),entries);
        if(!weapon.isEmpty()) out.store("weapon",ItemStack.CODEC,weapon);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        instance=in.read("instance",UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        remaining=Math.clamp(in.getIntOr("mobsLeft",SpawnerRules.DEFAULT_REMAINING),0,10000);
        maximum=Math.clamp(in.getIntOr("maxActive",SpawnerRules.DEFAULT_ACTIVE),1,128);
        int savedInterval=in.getIntOr("spawnDelay",SpawnerRules.DEFAULT_DELAY); interval=savedInterval<1?SpawnerRules.DEFAULT_DELAY:Math.min(savedInterval,72000);
        delay=Math.clamp(in.getIntOr("delay",interval),0,72000); height=Math.clamp(in.getIntOr("spawnHeightOffset",0),-1024,1024);
        double savedRange=in.getDoubleOr("spawnRange",SpawnerRules.DEFAULT_RANGE); range=Double.isFinite(savedRange)?Math.clamp(savedRange,0,64):SpawnerRules.DEFAULT_RANGE;
        entries=in.read("mobtypes",Entry.CODEC.listOf(0,256)).orElse(List.of()); weapon=in.read("weapon",ItemStack.CODEC).orElse(ItemStack.EMPTY);
        active.clear(); active.addAll(in.read("active",UUIDUtil.CODEC.listOf(0,10000)).orElse(List.of()));
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) {
        if(level instanceof ServerLevel server) { var mailbox=server.getDataStorage().get(SpawnerMailbox.TYPE); if(mailbox!=null) mailbox.forget(pos); }
        super.preRemoveSideEffects(pos,state);
    }
}
