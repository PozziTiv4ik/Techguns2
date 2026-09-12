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
    public static final Map<ArmorSlot,DeferredItem<TGArmorItem>> ITEMS = items(Armors.T2_COMBAT);
    public static final Map<ArmorSlot,DeferredItem<TGArmorItem>> HAZMAT = items(Armors.HAZMAT);
    public static final Map<ArmorSlot,DeferredItem<TGArmorItem>> T1_COMBAT = items(Armors.T1_COMBAT);
    public static final Map<ArmorSlot,DeferredItem<TGArmorItem>> T1_MINER = items(Armors.T1_MINER);
    private static Map<ArmorSlot,DeferredItem<TGArmorItem>> items(List<ArmorSpec> specifications) {
        var items=new EnumMap<ArmorSlot,DeferredItem<TGArmorItem>>(ArmorSlot.class);
        for(var spec:specifications) {
            EquipmentSlot slot=EquipmentSlot.valueOf(spec.slot().name());
            items.put(spec.slot(),TGContent.ITEMS.registerItem(spec.id(),props -> new TGArmorItem(props,spec),props -> {
                var modifiers=ItemAttributeModifiers.builder();
                if(spec.toughness()>0) modifiers.add(Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(TGContent.id("armor_toughness."+spec.slot().name().toLowerCase(Locale.ROOT)),spec.toughness(),AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.bySlot(slot));
                // GenericArmor's radiation attribute has no wear guard and applies to any wearer.
                if(spec.radiationResistance()>0) modifiers.add(techguns.modern.radiation.RadiationSystem.RESISTANCE,
                        new AttributeModifier(TGContent.id("armor_radiation."+spec.slot().name().toLowerCase(Locale.ROOT)),spec.radiationResistance(),AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.bySlot(slot));
                return props.durability(spec.durability()).repairable(TGArmorItem.material(spec.repairMetal().isEmpty()?spec.repairCloth():spec.repairMetal()))
                        .component(CAMO.get(),0).component(DataComponents.EQUIPPABLE,equippable(spec,0)).attributes(modifiers.build());
            }));
        }
        return Collections.unmodifiableMap(items);
    }
    public static Equippable equippable(ArmorSpec spec,int camo) {
        return Equippable.builder(EquipmentSlot.valueOf(spec.slot().name())).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID,TGContent.id(spec.camos().get(camo)))).build();
    }
    public static void register(IEventBus bus) { NeoForge.EVENT_BUS.register(TGArmorSystem.class); }
    private ArmorContent() {}
}
