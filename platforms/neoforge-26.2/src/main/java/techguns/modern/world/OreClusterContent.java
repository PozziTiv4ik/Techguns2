package techguns.modern.world;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.core.OreClusters;
import techguns.modern.*;

/** Infinite drill targets, not mineable ores. The original block has neither drops nor a ticking entity. */
public final class OreClusterContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String,DeferredBlock<Block>> BLOCKS=create();
    private static Map<String,DeferredBlock<Block>> create() {
        var blocks=new LinkedHashMap<String,DeferredBlock<Block>>();
        for(var v:OreClusters.ALL) {
            // 1.12 setResistance multiplied by 3, getExplosionResistance divided by 5.
            var block=REGISTRY.registerBlock(v.id(),Block::new,p->p.mapColor(MapColor.STONE).strength(-1,3600000).noLootTable());
            TGContent.ITEMS.registerItem(v.id(),p->new ClusterItem(block.get(),p.useBlockDescriptionPrefix(),v.id()));
            blocks.put(v.id(),block);
        }
        return Collections.unmodifiableMap(blocks);
    }
    public static Block netherCrystal() { return BLOCKS.get("ore_cluster_nether_crystal").get(); }
    public static final class ClusterItem extends BlockItem {
        private final String cluster;
        private ClusterItem(Block block,Properties properties,String cluster) { super(block,properties); this.cluster=cluster; }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
            super.appendHoverText(stack,context,display,lines,flag);
            var values=OreClusterConfig.VALUES.get(cluster);
            lines.accept(Component.translatable("techguns.orecluster.mininglevel").append(": "+values.miningLevel().get()));
            lines.accept(Component.translatable("techguns.orecluster.powermult").append(": x"+String.format(Locale.ROOT,"%.1f",values.power().get())));
            lines.accept(Component.translatable("techguns.orecluster.amountmult").append(": x"+String.format(Locale.ROOT,"%.1f",values.ores().get())));
        }
    }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private OreClusterContent() {}
}
