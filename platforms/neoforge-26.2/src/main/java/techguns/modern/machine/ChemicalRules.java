package techguns.modern.machine;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import techguns.core.ChemicalDefaults;
import techguns.modern.TGContent;

public final class ChemicalRules {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.ConfigValue<List<? extends String>> OILS=BUILDER.defineListAllowEmpty("FluidListOil",ChemicalDefaults.OIL,
            () -> "oil",value -> value instanceof String text && !text.isBlank());
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FUELS=BUILDER.defineListAllowEmpty("FluidListFuel",ChemicalDefaults.FUEL,
            () -> "fuel",value -> value instanceof String text && !text.isBlank());
    public static final ModConfigSpec.BooleanValue KEEP_LAVA=BUILDER.define("keepLavaRecipesWhenFuelIsPresent",false);
    private static final ModConfigSpec SPEC=BUILDER.build();
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER,SPEC,"techguns-chemistry-server.toml"); }
    public static boolean groupMatches(String group,Fluid fluid) {
        if (fluid==net.minecraft.world.level.material.Fluids.EMPTY) return false;
        if (fluid instanceof FlowingFluid flowing) fluid=flowing.getSource();
        if (fluid.builtInRegistryHolder().is(TagKey.create(Registries.FLUID,TGContent.id("chemical_"+group)))) return true;
        Identifier id=BuiltInRegistries.FLUID.getKey(fluid);
        return (group.equals("oils") ? OILS : FUELS).get().stream().anyMatch(name -> name.contains(":") ? name.equals(id.toString()) : name.equals(id.getPath()));
    }
    public static boolean groupPresent(String group) { return BuiltInRegistries.FLUID.stream().anyMatch(fluid -> groupMatches(group,fluid)); }
    public static boolean itemTagPresent(String name) { return BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,Identifier.parse(name))).iterator().hasNext(); }
    public static boolean active(String rule) {
        return switch (rule) {
            case "always" -> true;
            case "coal_dust_present" -> itemTagPresent("c:dusts/coal");
            case "coal_dust_absent" -> !itemTagPresent("c:dusts/coal");
            case "biofuel_present" -> itemTagPresent("c:fuels/bio");
            case "oils_present" -> groupPresent("oils");
            case "oils_absent" -> !groupPresent("oils");
            case "fuels_present" -> groupPresent("fuels");
            case "lava_fuel_fallback" -> !groupPresent("fuels") || KEEP_LAVA.get();
            default -> false;
        };
    }
    private ChemicalRules() {}
}
