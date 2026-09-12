package techguns.modern.machine.grinder;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.GrinderRules;
import techguns.modern.*;
import techguns.modern.machine.TGMachineConfig;
import techguns.modern.machine.workbench.OwnedWorkbenchBlockEntity;

public final class GrinderBlockEntity extends OwnedWorkbenchBlockEntity {
    public static final int INPUT = 0, UPGRADE = 1, OUTPUT_START = 2, OUTPUT_END = 11, METRICS = 7;
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(GrinderRules.CAPACITY) {
        @Override protected void onEnergyChanged(int previous) { setChanged(); }
    };
    private ItemStack reserved = ItemStack.EMPTY;
    private List<ItemStack> pending = List.of();
    private int progress, batch = 1, redstone;
    private boolean dirty = true, forceSync, sentRunning;
    private ItemStack clientItem = ItemStack.EMPTY;
    private int clientProgress, clientDuration;
    private long clientTime;
    private boolean clientRunning;
    public final ContainerData metrics = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> energy.getAmountAsInt(); case 1 -> progress; case 2 -> working() ? GrinderRules.DURATION : 0;
                case 3 -> !working() ? 0 : progress >= GrinderRules.DURATION ? 3 : canAdvance() ? 1 : 2;
                case 4 -> redstone; case 5 -> batch; case 6 -> working() && progress < GrinderRules.DURATION && !TGMachineConfig.MACHINES_NEED_NO_POWER.get() ? GrinderRules.power(batch) : 0;
                default -> 0;
            };
        }
        @Override public void set(int index,int value) {}
        @Override public int getCount() { return METRICS; }
    };
    public GrinderBlockEntity(BlockPos pos,BlockState state) { super(GrinderContent.ENTITY.get(),pos,state,OUTPUT_END); }
    @Override public int getMaxStackSize() { return 64; } // Original ItemStackHandlerPlus limit; modern containers default to 99.
    public SimpleEnergyHandler energy() { return energy; }
    public boolean working() { return !reserved.isEmpty(); }
    public static boolean isUpgrade(ItemStack stack) { return stack.is(TGContent.MATERIALS.get("machinestackupgrade").get()); }
    public boolean acceptsRecipe(ItemStack stack) { return level instanceof ServerLevel server && recipe(server,stack).isPresent(); }
    private Optional<GrinderRecipe> recipe(ServerLevel server,ItemStack stack) {
        return server.getServer().getRecipeManager().getRecipeFor(GrinderContent.RECIPE.get(),new SingleRecipeInput(stack),server).map(r -> r.value());
    }
    @Override protected GrinderMenu makeMenu(int id,Inventory inventory) { return new GrinderMenu(id,inventory,this,metrics); }
    @Override protected boolean performAction(Player player,int button) {
        if (button != 1) return false;
        redstone = (redstone + 1) % 3; forceSync = true; publishChange(player); return true;
    }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return super.canPlaceItem(slot,stack) && (slot == INPUT || slot == UPGRADE && isUpgrade(stack)); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return super.canTakeItemThroughFace(slot,stack,side) && slot >= OUTPUT_START && slot < OUTPUT_END; }
    @Override public void setChanged() { super.setChanged(); dirty = true; }
    private boolean enabled() { boolean signal = level != null && level.hasNeighborSignal(worldPosition); return redstone == 0 || redstone == 1 && signal || redstone == 2 && !signal; }
    private boolean canAdvance() { return enabled() && working() && progress < GrinderRules.DURATION && (TGMachineConfig.MACHINES_NEED_NO_POWER.get() || energy.getAmountAsInt() >= GrinderRules.power(batch)); }
    private Optional<List<ItemStack>> planOutputs(List<ItemStack> results) {
        List<ItemStack> planned = new ArrayList<>();
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) planned.add(getItem(slot).copy());
        for (var result : results) {
            int remaining = result.getCount();
            for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < planned.size() && remaining > 0; slot++) {
                var current = planned.get(slot);
                if (pass == 0 ? current.isEmpty() || !ItemStack.isSameItemSameComponents(current,result) : !current.isEmpty()) continue;
                int amount = Math.min(remaining,Math.min(getMaxStackSize(),result.getMaxStackSize()) - current.getCount());
                if (amount > 0) { planned.set(slot,result.copyWithCount(current.getCount() + amount)); remaining -= amount; }
            }
            if (remaining > 0) return Optional.empty();
        }
        return Optional.of(planned);
    }
    private void start(ServerLevel server) {
        dirty = false;
        var match = recipe(server,getItem(INPUT)); if (match.isEmpty()) return;
        var recipe = match.get(); var input = getItem(INPUT);
        int selected = Math.min(input.getCount(),isUpgrade(getItem(UPGRADE)) ? Math.min(8,1 + getItem(UPGRADE).getCount()) : 1);
        // Choose space by maximum possible outputs, independently of random rolls.
        while (selected > 0 && planOutputs(recipe.results(input,selected,true,() -> 0)).isEmpty()) selected--;
        if (selected == 0) return;
        pending = List.copyOf(recipe.results(input,selected,false,server.getRandom()::nextDouble));
        reserved = removeItem(INPUT,selected); batch = selected; progress = 0; forceSync = true; setChanged();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,GrinderBlockEntity machine) {
        if (!(level instanceof ServerLevel server)) return;
        if (machine.enabled()) {
            if (!machine.working()) {
                if (machine.dirty || level.getGameTime() % 20 == 0) machine.start(server);
            } else {
                if (machine.canAdvance()) {
                    if (!TGMachineConfig.MACHINES_NEED_NO_POWER.get()) try (var tx = Transaction.openRoot()) {
                        if (machine.energy.extract(GrinderRules.power(machine.batch),tx) != GrinderRules.power(machine.batch)) return;
                        tx.commit();
                    }
                    machine.progress++; machine.effects(server); machine.setChanged();
                }
                if (machine.progress >= GrinderRules.DURATION) {
                    var plan = machine.planOutputs(machine.pending);
                    if (plan.isPresent()) {
                        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) machine.setItem(slot,plan.get().get(slot - OUTPUT_START));
                        machine.reserved = ItemStack.EMPTY; machine.pending = List.of(); machine.progress = 0; machine.batch = 1; machine.forceSync = true;
                        machine.start(server); machine.setChanged();
                    }
                }
            }
        }
        boolean running = machine.canAdvance();
        if (machine.forceSync || running != machine.sentRunning || machine.working() && machine.progress % 5 == 0 && running) {
            machine.sentRunning = running; machine.forceSync = false; server.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);
        }
    }
    private void effects(ServerLevel server) {
        if (progress == 1 || progress == 21 || progress == 59) server.playSound(null,worldPosition,progress == 1 ? GrinderContent.START.get() : GrinderContent.WORK.get(),SoundSource.BLOCKS,1,1);
        if (progress % 2 == 0) {
            var particle = reserved.getItem() instanceof GunItem ? TGContent.MATERIALS.get("platesteel").toStack() : reserved.copyWithCount(1);
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(particle)),worldPosition.getX()+.5,worldPosition.getY()+.6,worldPosition.getZ()+.5,1,.05,.05,.05,.1);
        }
    }
    public ItemStack displayItem() { return level instanceof ServerLevel ? reserved.copyWithCount(1) : clientItem.copy(); }
    public float displayProgress(float partial) {
        if (level instanceof ServerLevel) return working() ? progress / (float)GrinderRules.DURATION : 0;
        double elapsed = clientRunning && level != null ? Math.clamp(level.getGameTime() - clientTime + partial,0,5) : 0;
        return clientDuration == 0 ? 0 : (float)Math.clamp((clientProgress + elapsed) / clientDuration,0,1);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);
        output.store("display_item",ItemStack.OPTIONAL_CODEC,displayItem()); output.putInt("progress",progress);
        output.putInt("duration",working() ? GrinderRules.DURATION : 0); output.putBoolean("running",canAdvance()); return output.buildResult();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) {
        clientItem = input.read("display_item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        clientProgress = Math.clamp(input.getIntOr("progress",0),0,GrinderRules.DURATION);
        clientDuration = Math.clamp(input.getIntOr("duration",0),0,GrinderRules.DURATION); clientRunning = input.getBooleanOr("running",false);
        clientTime = level == null ? 0 : level.getGameTime();
    }
    @Override public void onDataPacket(Connection connection,ValueInput input) { handleUpdateTag(input); }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); output.putInt("energy",energy.getAmountAsInt()); output.putInt("redstone",redstone);
        if (working()) { output.store("reserved",ItemStack.OPTIONAL_CODEC,reserved); output.store("pending",ItemStack.OPTIONAL_CODEC.listOf(),pending); output.putInt("progress",progress); output.putInt("batch",batch); }
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); energy.set(Math.clamp(input.getIntOr("energy",0),0,GrinderRules.CAPACITY)); redstone = Math.clamp(input.getIntOr("redstone",0),0,2);
        reserved = input.read("reserved",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY); pending = input.read("pending",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        progress = working() ? Math.clamp(input.getIntOr("progress",0),0,GrinderRules.DURATION) : 0;
        batch = working() ? Math.clamp(input.getIntOr("batch",1),1,8) : 1; dirty = forceSync = true;
        if (!working()) pending = List.of();
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) {
        if (level instanceof ServerLevel && working()) {
            // Paid but blocked jobs return their already-rolled outputs; unfinished jobs return the untouched input.
            var drops = progress >= GrinderRules.DURATION ? pending : List.of(reserved);
            for (var stack : drops) Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),stack.copy());
            reserved = ItemStack.EMPTY; pending = List.of();
        }
        super.preRemoveSideEffects(pos,state);
    }
}
