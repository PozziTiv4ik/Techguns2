package techguns.modern.machine.repair;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.armor.*;

public final class RepairBenchBlockEntity extends techguns.modern.machine.workbench.OwnedWorkbenchBlockEntity {
    public static final int MATERIAL_SLOTS = 9, REPAIR_SLOT = 9, SIZE = 10;
    public RepairBenchBlockEntity(BlockPos pos, BlockState state) { super(RepairBenchContent.ENTITY.get(), pos, state, SIZE); }
    @Override protected RepairBenchMenu makeMenu(int id, Inventory inventory) { return new RepairBenchMenu(id, inventory, this); }
    public static boolean supports(ItemStack stack) { return stack.getItem() instanceof TGArmorItem; }
    public static List<ItemStack> costs(ItemStack stack) {
        if (!(stack.getItem() instanceof TGArmorItem armor) || stack.getDamageValue() <= 0 || stack.getCount() != 1) return List.of();
        int[] amounts = armor.spec().repairBenchCosts(stack.getDamageValue());
        var result = new ArrayList<ItemStack>();
        if (amounts[0] > 0) result.add(armor.repairMaterial(true,amounts[0]));
        if (amounts[1] > 0) result.add(armor.repairMaterial(false,amounts[1]));
        return List.copyOf(result);
    }
    public static int available(Container materialInventory, ItemStack wanted) {
        int count = 0;
        for (int slot = 0; slot < MATERIAL_SLOTS; slot++) if (materialInventory.getItem(slot).is(wanted.getItem())) count += materialInventory.getItem(slot).getCount();
        return count;
    }
    @Override protected boolean performAction(Player player, int button) {
        var menu = (RepairBenchMenu)player.containerMenu;
        if (button < 1 || button > 6) return false;
        ItemStack target = menu.target(button);
        List<ItemStack> costs = costs(target);
        if (costs.isEmpty()) return false;
        // A missing later material rolls back earlier extractions. Only the bench's material slots contribute.
        try (Transaction transaction = Transaction.openRoot()) {
            for (var cost : costs) {
                int remaining = cost.getCount();
                for (int slot = 0; slot < MATERIAL_SLOTS && remaining > 0; slot++) {
                    var stack = getItem(slot);
                    if (stack.is(cost.getItem())) remaining -= automation().extract(slot, ItemResource.of(stack), remaining, transaction);
                }
                if (remaining != 0) return false;
            }
            transaction.commit();
        }
        target.setDamageValue(0);
        TGArmorSystem.refresh(player); publishChange(player);
        return true;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack) && (slot < MATERIAL_SLOTS || supports(stack));
    }
}
