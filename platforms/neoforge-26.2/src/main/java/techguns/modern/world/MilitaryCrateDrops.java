package techguns.modern.world;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import techguns.modern.TGContent;

/** TGEventHandler.MilitaryCrateDrops: replace player harvest at HIGH; later protections still own the result. */
public final class MilitaryCrateDrops {
    public static LootParams params(ServerLevel level, BlockPos pos, Player player, int fortune) {
        return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.THIS_ENTITY, player).withLuck(fortune).create(LootContextParamSets.CHEST);
    }
    public static List<ItemStack> rewards(MilitaryCrateBlock block, ServerLevel level, BlockPos pos, Player player, int fortune, RandomSource random) {
        return level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id(block.variant().loot())))
                .getRandomItems(params(level,pos,player,fortune), random);
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof MilitaryCrateBlock block) || !(event.getBreaker() instanceof Player player)) return;
        var level = event.getLevel(); var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SILK_TOUCH),event.getTool()) > 0) return;
        // Original spawnAsEntity applied these guards AFTER HarvestDropsEvent. NeoForge captures drops before its event.
        if (!level.getGameRules().get(GameRules.BLOCK_DROPS) || level.restoringBlockSnapshots) return;
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.FORTUNE),event.getTool());
        var stacks = rewards(block,level,event.getPos(),player,fortune,level.getRandom());
        event.getDrops().clear();
        for (var stack : stacks) {
            if (stack.isEmpty()) continue;
            var pos = Vec3.atCenterOf(event.getPos()).add((level.getRandom().nextDouble()-.5)*.5,(level.getRandom().nextDouble()-.5)*.5-.125,(level.getRandom().nextDouble()-.5)*.5);
            var item = new ItemEntity(level,pos.x,pos.y,pos.z,stack); item.setDefaultPickUpDelay();
            event.getDrops().add(item); // CommonHooks spawns these only if all later handlers accept the event.
        }
    }
    private MilitaryCrateDrops() {}
}
