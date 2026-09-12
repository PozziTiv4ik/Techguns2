package techguns.modern;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Techguns.MOD_ID)
public final class Techguns {
    public static final String MOD_ID = "techguns";

    public Techguns(IEventBus modBus, ModContainer container) {
        if (Boolean.getBoolean("techguns.chemistryTest")) net.neoforged.neoforge.common.NeoForgeMod.enableMilkFluid();
        TGContent.COMPONENTS.register(modBus);
        TGContent.ITEMS.register(modBus);
        TGContent.SOUNDS.register(modBus);
        TGContent.ENTITIES.register(modBus);
        TGContent.TABS.register(modBus);
        techguns.modern.crafting.TGCrafting.register(modBus);
        techguns.modern.machine.TGMachineContent.register(modBus);
        techguns.modern.machine.reaction.ReactionContent.register(modBus);
        techguns.modern.machine.fabricator.FabricatorContent.register(modBus);
        techguns.modern.machine.charging.ChargingStationContent.register(modBus);
        techguns.modern.machine.repair.RepairBenchContent.register(modBus);
        techguns.modern.machine.camo.CamoBenchContent.register(modBus);
        techguns.modern.radiation.RadiationSystem.register(modBus,container);
        SafeMode.register(modBus, container);
        techguns.modern.npc.NpcContent.register(modBus, container);
        techguns.modern.armor.ArmorContent.register(modBus);
        techguns.modern.machine.TGMachineConfig.register(container);
        techguns.modern.world.TGOreContent.register(modBus);
        techguns.modern.world.TGOreConfig.register(container);
        techguns.modern.fluid.TGFluids.register(modBus);
        techguns.modern.machine.ChemicalRules.register(container);
        modBus.addListener(techguns.modern.network.GunNetwork::register);
        modBus.addListener(techguns.modern.network.MachineTanksPayload::register);
        NeoForge.EVENT_BUS.register(ReloadSessions.class);
        NeoForge.EVENT_BUS.register(AimSessions.class);
        NeoForge.EVENT_BUS.addListener(ArmorDamage::onIncoming);
        NeoForge.EVENT_BUS.addListener(RocketDamage::knockback);
        if (Boolean.getBoolean("techguns.gametest")) {
            techguns.modern.test.ChargingTestItems.register(modBus);
            techguns.modern.test.WeaponGameTests.FUNCTIONS.register(modBus);
            modBus.addListener(techguns.modern.test.WeaponGameTests::registerTests);
        }
    }
}
