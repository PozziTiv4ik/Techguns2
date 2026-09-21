package techguns.modern.test;

import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.world.OreClusterContent;
import techguns.modern.world.structure.*;
import techguns.modern.machine.drill.*;

final class OreDrillWorldGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        if(Boolean.getBoolean("techguns.worldgenTest")) r.register("structure_drill_survival_chain_natural_chunks",()->OreDrillWorldGameTests::natural);
    }
    private static void natural(GameTestHelper h) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=(NetherClusterStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherClusterPiece.TEMPLATE); var g=l.getChunkSource().getGenerator(); ChunkPos chosen=null;
        // A separate area avoids modifying locations examined by the template conservation tests.
        for(int n=257;n<=512;n++) { var c=new ChunkPos(16,16*n); var context=new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); if(s.findGenerationPoint(context).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Native cluster found in new region"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Saved native cluster start");
        var p=(NetherClusterPiece)start.getPieces().getFirst(); var box=p.getBoundingBox(); for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z);
        var target=p.templatePosition().offset(1,1,1); h.assertTrue(l.getBlockState(target).is(OreClusterContent.netherCrystal()),"Actual generated crystal used as target");
        var origin=target.west(2); l.setBlock(origin,OreDrillContent.BLOCKS.get("controller").get().defaultBlockState(),3); l.setBlock(origin.east(),OreDrillContent.BLOCKS.get("rod").get().defaultBlockState(),3);
        var d=(OreDrillBlockEntity)l.getBlockEntity(origin); var player=FakePlayerFactory.getMinecraft(l); player.setPos(Vec3.atCenterOf(origin).add(0,2,0));
        h.assertTrue(d.form(player),"Two-block drill forms against generated cluster"); d.setItem(0,TGContent.MATERIALS.get("oredrillsmall_obsidiansteel").toStack());
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(d.energy().insert(288000,tx),288000,"Real cycle energy budget"); tx.commit(); }
        for(int i=0;i<6001;i++) OreDrillBlockEntity.tick(l,origin,d.getBlockState(),d);
        var result=d.getItem(2); h.assertTrue(result.getCount()==1 && (result.is(Items.NETHER_QUARTZ_ORE) || result.is(Items.GLOWSTONE) || result.is(Items.BLAZE_ROD)),"Native location yields an original Nether resource");
        h.assertValueEqual(d.energy().getAmountAsInt(),0,"All paid energy used once"); h.assertTrue(l.getBlockState(target).is(OreClusterContent.netherCrystal()),"Generated source remains infinite");
        var output=result.getItem(); l.setBlock(origin.below(),Blocks.HOPPER.defaultBlockState(),3); var hopper=(HopperBlockEntity)l.getBlockEntity(origin.below());
        h.runAfterDelay(12,()->{
            // Remote chunks need not be entity-ticking; drive the native hopper tick explicitly.
            for(int i=0;i<12;i++) HopperBlockEntity.pushItemsTick(l,origin.below(),l.getBlockState(origin.below()),hopper);
            h.assertTrue(d.getItem(2).isEmpty() && java.util.stream.IntStream.range(0,5).anyMatch(i->hopper.getItem(i).is(output)),"Native hopper completes exploration-to-resource chain");
            com.mojang.logging.LogUtils.getLogger().info("Techguns native Ore Drill chain: origin={}, target={}, output={}",origin,target,output); h.succeed();
        });
    }
    private OreDrillWorldGameTests() {}
}
