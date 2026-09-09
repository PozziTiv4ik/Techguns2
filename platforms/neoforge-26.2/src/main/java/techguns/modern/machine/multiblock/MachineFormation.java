package techguns.modern.machine.multiblock;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Formation, ownership and unloaded-chunk rules shared by modern multiblock machines. */
public final class MachineFormation {
    public static final BooleanProperty FORMED=BooleanProperty.create("formed");
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public record Part(BlockPos pos,Block block,int connector) {}
    private final BlockEntity owner;
    private final Function<Direction,List<Part>> footprint;
    private UUID generation;
    private boolean unforming;
    public MachineFormation(BlockEntity owner,Function<Direction,List<Part>> footprint) { this.owner=owner; this.footprint=footprint; }
    public boolean formed() { return generation!=null && owner.getBlockState().getValue(FORMED); }
    public Direction orientation() { return owner.getBlockState().getValue(FACING); }
    public boolean linked(UUID id) { return id!=null && id.equals(generation) && !owner.isRemoved() && formed(); }
    public boolean portsAvailable(UUID id) { return linked(id) && status()==1; }

    /** The controller checks menu/owner permissions before requesting formation. */
    public boolean form(Direction orientation,Player player) {
        if(!(owner.getLevel() instanceof ServerLevel level) || owner.isRemoved() || orientation.getAxis().isVertical() || formed()) return false;
        var parts=footprint.apply(orientation);
        for(var part:parts) {
            if(!level.hasChunkAt(part.pos()) || !level.mayInteract(player,part.pos())) return false;
            var state=level.getBlockState(part.pos());
            if(!state.is(part.block()) || state.getValue(FORMED)) return false;
            var entity=level.getBlockEntity(part.pos());
            if(part.pos().equals(owner.getBlockPos()) ? entity!=owner : !(entity instanceof LinkedMachinePartBlockEntity)) return false;
        }
        generation=UUID.randomUUID();
        for(var part:parts) {
            if(level.getBlockEntity(part.pos()) instanceof LinkedMachinePartBlockEntity slave) slave.link(owner.getBlockPos(),generation,part.connector());
            level.setBlock(part.pos(),level.getBlockState(part.pos()).setValue(FORMED,true).setValue(FACING,orientation),3);
            level.invalidateCapabilities(part.pos());
        }
        owner.setChanged(); return true;
    }
    /** 0 means required chunks are unavailable; -1 means a broken structure; 1 means a complete structure. */
    public int status() {
        var level=owner.getLevel();
        if(level==null || owner.isRemoved() || !formed()) return -1;
        var parts=footprint.apply(orientation());
        for(var part:parts) if(!level.hasChunkAt(part.pos())) return 0;
        for(var part:parts) {
            var state=level.getBlockState(part.pos());
            if(!state.is(part.block()) || !state.getValue(FORMED) || state.getValue(FACING)!=orientation()) return -1;
            var entity=level.getBlockEntity(part.pos());
            if(part.pos().equals(owner.getBlockPos())) { if(entity!=owner) return -1; }
            else if(!(entity instanceof LinkedMachinePartBlockEntity slave) || !slave.linkedTo(owner.getBlockPos(),generation) || slave.connector()!=part.connector()) return -1;
        }
        return 1;
    }
    public void unform() {
        var level=owner.getLevel(); if(unforming || level==null || generation==null && !owner.getBlockState().getValue(FORMED)) return;
        unforming=true;
        try {
            UUID old=generation; generation=null;
            for(var part:footprint.apply(orientation())) {
                if(!level.hasChunkAt(part.pos())) continue;
                if(level.getBlockEntity(part.pos()) instanceof LinkedMachinePartBlockEntity slave && slave.linkedTo(owner.getBlockPos(),old)) slave.unlink();
            }
            if(level.hasChunkAt(owner.getBlockPos()) && level.getBlockEntity(owner.getBlockPos())==owner && level.getBlockState(owner.getBlockPos()).is(owner.getBlockState().getBlock()))
                level.setBlock(owner.getBlockPos(),level.getBlockState(owner.getBlockPos()).setValue(FORMED,false),3);
            level.invalidateCapabilities(owner.getBlockPos()); owner.setChanged();
        } finally { unforming=false; }
    }
    public void save(ValueOutput out) { if(generation!=null) out.putString("formation",generation.toString()); }
    public void load(ValueInput in) { generation=uuid(in,"formation"); }
    public static UUID uuid(ValueInput in,String key) { try { return UUID.fromString(in.getStringOr(key,"")); } catch(IllegalArgumentException ignored) { return null; } }
}
