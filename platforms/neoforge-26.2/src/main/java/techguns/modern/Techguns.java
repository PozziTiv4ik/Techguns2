package techguns.modern;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Techguns.MOD_ID)
public final class Techguns {
    public static final String MOD_ID = "techguns";

    public Techguns(IEventBus modBus) {
        TGContent.COMPONENTS.register(modBus);
        TGContent.ITEMS.register(modBus);
        TGContent.SOUNDS.register(modBus);
        TGContent.ENTITIES.register(modBus);
        TGContent.TABS.register(modBus);
        techguns.modern.crafting.TGCrafting.register(modBus);
        modBus.addListener(techguns.modern.network.GunNetwork::register);
        NeoForge.EVENT_BUS.register(ReloadSessions.class);
        NeoForge.EVENT_BUS.register(AimSessions.class);
        NeoForge.EVENT_BUS.addListener(ArmorDamage::onIncoming);
        if (Boolean.getBoolean("techguns.gametest")) {
            techguns.modern.test.WeaponGameTests.FUNCTIONS.register(modBus);
            modBus.addListener(techguns.modern.test.WeaponGameTests::registerTests);
        }
    }
}
