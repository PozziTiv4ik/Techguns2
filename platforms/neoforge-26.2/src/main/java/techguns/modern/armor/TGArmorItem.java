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

public final class TGArmorItem extends Item {
    private final ArmorSpec spec;
    public TGArmorItem(Properties properties,ArmorSpec spec) { super(properties); this.spec=spec; }
    public ArmorSpec spec() { return spec; }
    public static int camo(ItemStack stack) { return stack.getItem() instanceof TGArmorItem item ? Math.floorMod(stack.getOrDefault(ArmorContent.CAMO.get(),0),item.spec.camos().size()) : 0; }
    public static Component camoName(ItemStack stack) { return stack.getItem() instanceof TGArmorItem item && item.spec.canChangeCamo() ? Component.translatable(item.spec.camoTranslationKey(camo(stack))) : Component.empty(); }
    public static Item material(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id.contains(":") ? id : "techguns:" + id));
    }
    public ItemStack repairMaterial(boolean metal, int count) {
        String id = metal ? spec.repairMetal() : spec.repairCloth();
        return id.isEmpty() || count <= 0 ? ItemStack.EMPTY : new ItemStack(material(id), count);
    }
    public static void setCamo(ItemStack stack,int camo) {
        if (!(stack.getItem() instanceof TGArmorItem item) || !item.spec.canChangeCamo() || camo<0 || camo>=item.spec.camos().size()) throw new IllegalArgumentException("Invalid armor camouflage");
        stack.set(ArmorContent.CAMO.get(),camo);
        stack.set(DataComponents.EQUIPPABLE,ArmorContent.equippable(item.spec,camo));
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if (spec.canChangeCamo() && player.isShiftKeyDown()) {
            if (level instanceof ServerLevel) { var stack=player.getItemInHand(hand); setCamo(stack,(camo(stack)+1)%spec.camos().size()); }
            return InteractionResult.SUCCESS;
        }
        return super.use(level,player,hand);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,display,lines,flag);
        if (spec.canChangeCamo()) lines.accept(Component.translatable("tooltip.techguns.armor.camo",camoName(stack)));
        lines.accept(Component.translatable("tooltip.techguns.armor.defense",spec.physical(),spec.elemental()));
        lines.accept(Component.translatable("tooltip.techguns.armor.typed_defense",spec.explosion(),spec.poison(),spec.dark(),spec.radiation()));
        if (spec.radiationResistance()>0) lines.accept(Component.translatable("tooltip.techguns.armor.radiation_resistance",spec.radiationResistance()));
        if (spec.bonusesActive(stack.getDamageValue())) {
            if (spec.speed()>0) lines.accept(Component.translatable("tooltip.techguns.armor.speed",percentage(spec.speed()),percentage(spec.speed()*2)));
            if (spec.mining()>0) lines.accept(Component.translatable("tooltip.techguns.armor.mining",Math.round(spec.mining()*100)));
            if (spec.knockback()>0) lines.accept(Component.translatable("tooltip.techguns.armor.knockback",Math.round(spec.knockback()*100)));
            if (spec.jump()>0) lines.accept(Component.translatable("tooltip.techguns.armor.jump",spec.jump()));
            if (spec.fallReduction()>0 || spec.freeFallHeight()>0) lines.accept(Component.translatable("tooltip.techguns.armor.fall",spec.freeFallHeight(),Math.round(spec.fallReduction()*100)));
        } else lines.accept(Component.translatable("tooltip.techguns.armor.worn"));
    }
    private static Number percentage(double bonus) {
        double value=bonus*100;
        if(value==Math.rint(value)) return (long)value;
        return value;
    }
}
