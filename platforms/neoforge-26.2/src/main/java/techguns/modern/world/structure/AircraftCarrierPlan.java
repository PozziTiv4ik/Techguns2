package techguns.modern.world.structure;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.AircraftCarrierRules;
import techguns.modern.world.MilitaryCrateContent;

/** Both original placement passes are evaluated once, before any chunk writes or serialization. */
public record AircraftCarrierPlan(BlockPos origin,int turns,long seed,Map<BlockPos,BlockState> cells,Map<BlockPos,Long> loot) {
    public static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    public record Cell(int x,int y,int z,BlockState state,String kind) {}
    private static final class Scan {
        static final List<Cell> CELLS=load();
        private static List<Cell> load() {
            try(var stream=AircraftCarrierPlan.class.getResourceAsStream("/data/techguns/aircraft_carrier/scan.json")) {
                if(stream==null) throw new IOException("Missing AircraftCarrier scan");
                var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                var palette=root.getAsJsonArray("palette"); var states=new ArrayList<BlockState>();var kinds=new ArrayList<String>();
                for(var value:palette) { var p=value.getAsJsonObject();states.add(BlockState.CODEC.parse(JsonOps.INSTANCE,p.get("state")).getOrThrow());kinds.add(p.get("kind").getAsString()); }
                var cells=new ArrayList<Cell>();
                for(var value:root.getAsJsonArray("cells")) {var a=value.getAsJsonArray();int i=a.get(3).getAsInt();cells.add(new Cell(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),states.get(i),kinds.get(i)));}
                if(cells.size()!=3967) throw new IOException("Incomplete AircraftCarrier scan");
                return List.copyOf(cells);
            } catch(IOException e) {throw new UncheckedIOException(e);}
        }
    }
    public AircraftCarrierPlan {
        origin=origin.immutable();if(turns<0||turns>3) throw new IllegalArgumentException("Carrier rotation");
        cells=Collections.unmodifiableMap(new LinkedHashMap<>(cells));loot=Map.copyOf(loot);
    }
    public static List<Cell> scan() { return Scan.CELLS; }
    public BlockPos position(int x,int y,int z) { return position(origin,x,y,z,turns); }
    private static BlockPos position(BlockPos origin,int x,int y,int z,int turns) {
        int[] p=AircraftCarrierRules.rotated(x,z,turns);return origin.offset(p[0],y,p[1]);
    }
    public static AircraftCarrierPlan create(BlockPos origin,int turns,long seed) {
        var random=new Random(seed);var cells=new LinkedHashMap<BlockPos,BlockState>();var loot=new HashMap<BlockPos,Long>();
        for(var cell:scan()) {
            var p=position(origin,cell.x(),cell.y(),cell.z(),turns);var state=cell.state().rotate(ROTATIONS.get(turns));
            if(cell.kind().startsWith("supply")) {
                boolean chance=cell.kind().equals("supply_chance");int meta=AircraftCarrierRules.supply(random.nextInt(chance?19:10),chance);
                if(meta>=0) state=MilitaryCrateContent.fromMetadata(meta).defaultBlockState();
            }
            cells.put(p,state);loot.remove(p);
            if(cell.kind().equals("chest")) loot.put(p,random.nextLong());
        }
        return new AircraftCarrierPlan(origin,turns,seed,cells,loot);
    }
}
