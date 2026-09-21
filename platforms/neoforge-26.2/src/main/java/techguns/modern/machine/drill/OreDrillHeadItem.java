package techguns.modern.machine.drill;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import techguns.core.OreDrillCatalog;
public final class OreDrillHeadItem extends Item {
    public final OreDrillCatalog.Head head;
    public OreDrillHeadItem(Properties p,String id) { super(p.stacksTo(1)); head=OreDrillCatalog.HEADS.stream().filter(h->h.id().equals(id)).findFirst().orElseThrow(); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,display,lines,flag);
        lines.accept(Component.translatable("gui.techguns.drill.head",new String[]{"S","M","L"}[head.size()],head.level()));
    }
}
