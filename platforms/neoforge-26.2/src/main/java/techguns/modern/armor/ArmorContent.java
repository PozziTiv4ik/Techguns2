package techguns.modern.armor;

import java.util.*;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import techguns.core.*;
import techguns.modern.TGContent;

public final class ArmorContent {
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<Integer>> CAMO = TGContent.COMPONENTS.registerComponentType("armor_camo",
            builder -> builder.persistent(Codec.intRange(0,5)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final Map<ArmorSlot,DeferredItem<T2ArmorItem>> ITEMS = items();
    private static Map<ArmorSlot,DeferredItem<T2ArmorItem>> items() {
        var items=new EnumMap<ArmorSlot,DeferredItem<T2ArmorItem>>(ArmorSlot.class);
        for(var spec:Armors.ALL) {
            EquipmentSlot slot=EquipmentSlot.valueOf(spec.slot().name());
            items.put(spec.slot(),TGContent.ITEMS.registerItem(spec.id(),props -> new T2ArmorItem(props,spec),props ->
                    props.durability(spec.durability()).repairable(TGContent.MATERIALS.get("ingotobsidiansteel").get())
                            .component(CAMO.get(),0).component(DataComponents.EQUIPPABLE,equippable(slot,0))
                            .attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR_TOUGHNESS,
                                    new AttributeModifier(TGContent.id("armor_toughness."+spec.slot().name().toLowerCase(Locale.ROOT)),spec.toughness(),AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.bySlot(slot)).build())));
        }
        return Collections.unmodifiableMap(items);
    }
    public static Equippable equippable(EquipmentSlot slot,int camo) {
        return Equippable.builder(slot).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID,TGContent.id(Armors.CAMOS.get(camo)))).build();
    }
    public static void register(IEventBus bus) { NeoForge.EVENT_BUS.register(T2ArmorSystem.class); }
    private ArmorContent() {}
}
