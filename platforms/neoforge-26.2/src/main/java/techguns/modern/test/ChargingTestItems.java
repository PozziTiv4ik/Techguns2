package techguns.modern.test;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import techguns.modern.Techguns;

/** Registered only by the headless GameTest bootstrap, never in ordinary games. */
public final class ChargingTestItems {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType("test_charger_energy",
            b -> b.persistent(Codec.intRange(0, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT));
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Techguns.MOD_ID);
    public static final DeferredItem<Item> DEVICE = device("test_charger_device", 1), STACK = device("test_charger_stack", 64),
            LIMITED = device("test_charger_limited", 1), TRANSFORM = device("test_charger_transform", 1), REFUSER = device("test_charger_refuser", 1);
    private static DeferredItem<Item> device(String id, int count) { return ITEMS.registerItem(id, Item::new, p -> p.stacksTo(count).component(ENERGY.get(), 0)); }
    public static void register(IEventBus bus) { COMPONENTS.register(bus); ITEMS.register(bus); bus.addListener(ChargingTestItems::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ENERGY.get(), 5000), DEVICE.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ENERGY.get(), Integer.MAX_VALUE), STACK.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ENERGY.get(), 5000, 137, 0), LIMITED.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ENERGY.get(), 5000, 0, 0), REFUSER.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ENERGY.get(), 1000) {
            @Override protected ItemResource update(ItemResource item, int amount) {
                return amount < 1000 ? super.update(item, amount) : ItemResource.of(Items.DIAMOND, item.getComponentsPatch()).with(ENERGY.get(), amount);
            }
        }, TRANSFORM.get());
    }
    private ChargingTestItems() {}
}
