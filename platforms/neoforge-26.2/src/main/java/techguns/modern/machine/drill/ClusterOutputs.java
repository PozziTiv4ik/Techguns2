package techguns.modern.machine.drill;

import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.FluidStack;
import techguns.core.OreDrillCatalog;
import techguns.modern.machine.ChemicalRules;

/** Resolve optional resources before weighting, as the original OreDictionary registration did. */
public final class ClusterOutputs {
    public record Output(ItemStack item,FluidStack fluid,int weight) {}
    public static List<Output> entries(String cluster) {
        var out=new ArrayList<Output>();
        for(var entry:OreDrillCatalog.OUTPUTS.get(cluster)) {
            ItemStack item=ItemStack.EMPTY; FluidStack fluid=FluidStack.EMPTY;
            switch(entry.kind()) {
                case "item" -> item=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.id())));
                case "tag" -> { var candidates=BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,Identifier.parse(entry.id()))).iterator(); if(candidates.hasNext()) item=new ItemStack(candidates.next()); }
                case "oil" -> { var oil=oil(); if(oil!=Fluids.EMPTY) fluid=new FluidStack(oil,1000); }
                default -> throw new IllegalStateException(entry.kind());
            }
            if(item.isEmpty() && fluid.isEmpty()) continue;
            boolean duplicate=false;
            for(var existing:out) if(!item.isEmpty() && ItemStack.isSameItemSameComponents(existing.item(),item) || !fluid.isEmpty() && FluidStack.isSameFluidSameComponents(existing.fluid(),fluid)) { duplicate=true; break; }
            if(!duplicate) out.add(new Output(item,fluid,entry.weight()));
        }
        return List.copyOf(out);
    }
    public static Output select(List<Output> entries,int roll) {
        if(roll<0) throw new IllegalArgumentException("Negative weighted roll");
        for(var e:entries) { roll-=e.weight(); if(roll<0) return new Output(e.item().copy(),e.fluid().copy(),e.weight()); }
        throw new IllegalArgumentException("Weighted roll outside total");
    }
    private static Fluid oil() {
        var oils=new ArrayList<Fluid>();
        for(var name:ChemicalRules.OILS.get()) BuiltInRegistries.FLUID.stream().filter(f->source(f) && ChemicalRules.groupMatches("oils",f))
                .filter(f->name.contains(":")?BuiltInRegistries.FLUID.getKey(f).toString().equals(name):BuiltInRegistries.FLUID.getKey(f).getPath().equals(name)).forEach(f->{ if(!oils.contains(f)) oils.add(f); });
        BuiltInRegistries.FLUID.stream().filter(f->source(f) && ChemicalRules.groupMatches("oils",f)).forEach(f->{ if(!oils.contains(f)) oils.add(f); });
        return oils.stream().filter(f->!f.defaultFluidState().createLegacyBlock().is(Blocks.AIR)).findFirst().orElse(oils.isEmpty()?Fluids.EMPTY:oils.getFirst());
    }
    private static boolean source(Fluid f) { return f!=Fluids.EMPTY && (!(f instanceof FlowingFluid flow) || flow.getSource()==f); }
    private ClusterOutputs() {}
}
