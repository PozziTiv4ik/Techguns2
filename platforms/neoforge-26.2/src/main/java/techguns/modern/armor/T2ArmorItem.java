package techguns.modern.armor;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import techguns.core.*;

public final class T2ArmorItem extends Item {
    private final ArmorSpec spec;
    public T2ArmorItem(Properties properties,ArmorSpec spec) { super(properties); this.spec=spec; }
    public ArmorSpec spec() { return spec; }
    public static int camo(ItemStack stack) { return Math.floorMod(stack.getOrDefault(ArmorContent.CAMO.get(),0),Armors.CAMOS.size()); }
    public static void setCamo(ItemStack stack,int camo) {
        if (!(stack.getItem() instanceof T2ArmorItem item) || camo<0 || camo>=Armors.CAMOS.size()) throw new IllegalArgumentException("Invalid armor camouflage");
        stack.set(ArmorContent.CAMO.get(),camo);
        stack.set(DataComponents.EQUIPPABLE,ArmorContent.equippable(EquipmentSlot.valueOf(item.spec.slot().name()),camo));
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel) { var stack=player.getItemInHand(hand); setCamo(stack,(camo(stack)+1)%Armors.CAMOS.size()); }
            return InteractionResult.SUCCESS;
        }
        return super.use(level,player,hand);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,display,lines,flag);
        lines.accept(Component.translatable("tooltip.techguns.armor.camo",Component.translatable("tooltip.techguns.armor.camo."+camo(stack))));
        lines.accept(Component.translatable("tooltip.techguns.armor.defense",spec.physical(),spec.elemental()));
        if (spec.bonusesActive(stack.getDamageValue())) {
            lines.accept(Component.translatable("tooltip.techguns.armor.bonuses",Math.round(spec.knockback()*100)));
            if (spec.jump()>0) lines.accept(Component.translatable("tooltip.techguns.armor.jump"));
        } else lines.accept(Component.translatable("tooltip.techguns.armor.worn"));
    }
}
