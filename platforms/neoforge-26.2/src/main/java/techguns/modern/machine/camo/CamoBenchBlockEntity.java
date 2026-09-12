package techguns.modern.machine.camo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.machine.workbench.OwnedWorkbenchBlockEntity;

public final class CamoBenchBlockEntity extends OwnedWorkbenchBlockEntity {
    public CamoBenchBlockEntity(BlockPos pos, BlockState state) { super(CamoBenchContent.ENTITY.get(), pos, state, 1); }
    @Override protected CamoBenchMenu makeMenu(int id, Inventory inventory) { return new CamoBenchMenu(id, inventory, this); }
    @Override protected boolean performAction(Player player, int button) {
        if (button < 1 || button > 10) return false;
        var menu = (CamoBenchMenu)player.containerMenu;
        int target = (button - 1) / 2;
        var changed = CamoCycling.change(menu.target(target), button % 2 == 0);
        if (changed.isEmpty()) return false;
        if (target == 0) setItem(0, changed.get()); else player.setItemSlot(CamoBenchMenu.ARMOR.get(target - 1), changed.get());
        publishChange(player); return true;
    }
}
