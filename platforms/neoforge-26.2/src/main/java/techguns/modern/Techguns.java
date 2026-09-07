package techguns.modern;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Techguns.MOD_ID)
public final class Techguns {
    public static final String MOD_ID = "techguns";

    public Techguns(IEventBus modBus) {
        TGContent.COMPONENTS.register(modBus);
        TGContent.ITEMS.register(modBus);
        TGContent.SOUNDS.register(modBus);
        TGContent.ENTITIES.register(modBus);
        TGContent.TABS.register(modBus);
        if (Boolean.getBoolean("techguns.gametest")) {
            techguns.modern.test.WeaponGameTests.FUNCTIONS.register(modBus);
            modBus.addListener(techguns.modern.test.WeaponGameTests::registerTests);
        }
    }
}
