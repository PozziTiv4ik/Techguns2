"""Reproducible port of the active MilitaryCamp geometry, with native planning adapters.

The original files remain reference-only. Generated classes use modern BlockState
and a bounded, in-memory CampWorld; they never write into neighbouring chunks.
"""
import hashlib
import json
import re
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES

JAVA = 'platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/camp/'
PARTS = ('Tent', 'Containers', 'CampProps', 'Bunker', 'Barracks', 'Helipad', 'Tanks', 'WatchTowerSmall', 'EmptyPlane')


def source(name):
    return strip_comments((LEGACY / f'java/techguns/world/structures/{name}.java').read_text(encoding='utf-8'))


def method(text, signature):
    start = text.index(signature)
    brace = text.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


def adapt(text):
    text = re.sub(r'^(?:package|import) [^;]+;\s*', '', text, flags=re.M)
    text = text.replace('TGBlocks.CAMONET_TOP.getDefaultState().withProperty(TGBlocks.CAMONET_TOP.TYPE, camo)', 'CampPalette.canopy(camo)')
    text = text.replace('TGBlocks.LAMP_0.getDefaultState().withProperty(TGBlocks.LAMP_0.LAMP_TYPE, EnumLampType.YELLOW_LANTERN)', 'CampPalette.lantern()')
    text = text.replace('world.getBlockState(p).getBlock() == TGBlocks.LAMP_0', 'CampPalette.isLamp(world.getBlockState(p))')
    text = re.sub(r'new MBlock\((TGBlocks|Blocks)\.(\w+),\s*(\d+)\)',
                  lambda m: f'new MBlock(CampPalette.legacy("{m[1]}", "{m[2]}", {m[3]}))', text)
    text = re.sub(r'TGBlocks\.(\w+)\.getDefaultState\(\)', lambda m: f'CampPalette.legacy("TGBlocks", "{m[1]}", 0)', text)
    text = re.sub(r'Blocks\.(BED|LADDER)\.getStateFromMeta\(([^)]+)\)', lambda m: f'CampPalette.legacy("Blocks", "{m[1]}", {m[2]})', text)
    text = text.replace('Blocks.OAK_FENCE_GATE.getStateFromMeta((direction+1) % 4)', 'CampPalette.legacy("Blocks", "OAK_FENCE_GATE", (direction+1) % 4)')
    text = text.replace('Blocks.PLANKS', 'Blocks.OAK_PLANKS').replace('Blocks.SNOW_LAYER', 'Blocks.SNOW')
    text = text.replace('new ResourceLocation(Techguns.MODID,', 'TGContent.id(')
    text = re.sub(r'TileEntity tile = world.getTileEntity\(p\);\s*if\(tile !=null && tile instanceof TileEntityChest\)\s*\{\s*\(\(TileEntityChest\)tile\).setLootTable\(CHEST_LOOT, world.rand.nextLong\(\)\);\s*\}',
                  'world.loot(p, CHEST_LOOT, world.rand.nextLong());', text)
    for old, new in {'WorldgenStructure':'CampPart', 'MilitaryCamp':'CampLayout', 'World':'CampWorld', 'IBlockState':'BlockState',
                     'ResourceLocation':'Identifier', 'EnumFacing':'Direction', 'EnumCamoNetType':'Camo',
                     'BlockUtils':'CampTerrain', 'MBlockRegister':'CampPalette', 'BlockRotator':'CampPalette',
                     'ItemTGDoor2x1':'CampPalette', 'ItemDoor':'CampPalette'}.items():
        text = re.sub(r'\b'+old+r'\b', new, text)
    text = re.sub(r'\s*this.lootTier=EnumLootType.TIER1;', '', text)
    text = text.replace('.getDefaultState()', '.defaultBlockState()').replace('.setPos(', '.set(')
    text = text.replace('.getOpposite()', '.getOpposite()')
    text = text.replace('TGBlocks.BUNKER_DOOR', 'FortificationContent.DOOR.get()')
    text = re.sub(r'\n[ \t]*\n(?:[ \t]*\n)+', '\n\n', text)
    text = '\n'.join(line.rstrip().expandtabs(4) for line in text.strip().splitlines())+'\n'
    assert not re.search(r'\b(?:TGBlocks\.|TileEntity\b|EnumLootType\b|net\.minecraft\.world\.World\b)', text), text[:100]
    return text


HEADER = '''// Generated from the attributed 1.12.2 source by tools/legacy_military_camp.py.
// Edit the converter or native adapters; legacy is never part of the modern source set.
package techguns.modern.world.structure.camp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.TGContent;
import techguns.modern.world.FortificationContent;
import techguns.modern.world.structure.camp.CampPart.BiomeColorType;
import techguns.modern.world.structure.camp.CampPalette.MBlock;
import techguns.modern.world.structure.camp.CampPalette.Camo;

'''


def generated_geometry():
    files = {}
    for name in PARTS:
        text = source(name)
        variant = 'type' if name in ('Tent', 'Bunker') else 'variant' if name == 'CampProps' else 'towerSize' if name == 'WatchTowerSmall' else None
        text = text.rstrip()[:-1] + f'\n    @Override public String key() {{ return "{name}"'+(' + ":" + '+variant if variant else '')+'; }\n}\n'
        files[JAVA+name+'.java'] = (HEADER+adapt(text)).encode()
    text = source('MilitaryCamp')
    original = method(text, 'private void initCampFlagEntity(')
    text = text.replace(original, '''private void initCampFlagEntity(World world) {
        if (flagSegment == null) return;
        BlockUtils.flattenArea(world, flagSegment.x, flagSegment.z, flagSegment.sizeX, flagSegment.sizeZ, 0);
        int x = flagSegment.x + flagSegment.sizeX/2;
        int z = flagSegment.z + flagSegment.sizeZ/2;
        int y = getGroundY(world,x,z)+1;
        world.post(new BlockPos(x,y,z), "attackhelicopter", 1, Math.min(y+64,world.getActualHeight())-y);
        for (int i=0; i<spawnPositions.size(); i+=3)
            world.post(new BlockPos(this.posX+spawnPositions.get(i), this.posY+spawnPositions.get(i+1), this.posZ+spawnPositions.get(i+2)), "armysoldier", 3, 0);
    }''')
    text = text.replace('structure.setBlocks(world, x, y, z, sizeX, height, sizeZ, this.direction, colorType, rnd);',
                        'world.component(structure.key(), x, y, z, sizeX, height, sizeZ, this.direction);\n                    structure.setBlocks(world, x, y, z, sizeX, height, sizeZ, this.direction, colorType, rnd);')
    text = text.replace('System.out.println("WTF!");', '')
    text = text.rstrip()[:-1]+'''
    private record Vec2(double x, double y) {
        static Vec2 substract(Vec2 a, Vec2 b) { return new Vec2(b.x-a.x,b.y-a.y); }
        double lenSquared() { return x*x+y*y; }
    }
    public static List<CampPart> parts(String group) {
        return List.copyOf(switch(group) { case "inside" -> structures; case "border" -> borderStructures; case "corner" -> cornerStructures; default -> throw new IllegalArgumentException(group); });
    }
}
'''
    files[JAVA+'CampLayout.java'] = (HEADER+adapt(text)).encode()
    terrain = strip_comments((LEGACY/'java/techguns/util/BlockUtils.java').read_text())
    gaussian = re.search(r'public static final float\[\]\[\] FILTER_GAUSSIAN_5x5 =.*?};', terrain, re.S)[0]
    methods = '\n'.join(method(terrain, signature) for signature in ('public static void flattenArea(', 'public static void apply2DHeightmapFilter(', 'public static void fillBlocks(', 'public static void fillBlocksHollow('))
    native = '''
    public static BiomeColorType getBiomeType(CampWorld world,int x,int z) { return world.color(); }
    public static int getHeightValueNoTrees(CampWorld world,int x,int z) { return world.solidHeight(x,z); }
    public static void removeJunkInArea(CampWorld world,int x,int z,int sx,int sz) {
        for(int dx=0;dx<sx;dx++) for(int dz=0;dz<sz;dz++) {
            world.clearColumn(x+dx,z+dz);
            for(int y=world.topAll(x+dx,z+dz); y>0; y--) {
                var p=new BlockPos(x+dx,y,z+dz);
                if(ground(world.getBlockState(p))) break;
                world.setBlockToAir(p);
            }
        }
    }
    private static void adjustHeightAtPos(CampWorld world,int x,int z,int y,MutableBlockPos p,int newY) {
        if(newY>y) world.raiseGround(x,y,z,newY);
        else for(int i=y;i>newY;i--) world.setBlockToAir(p.set(x,i,z));
    }
    public static boolean ground(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(net.minecraft.tags.BlockTags.DIRT)
            || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.TERRACOTTA)
            || s.is(Blocks.CLAY) || s.is(Blocks.GRAVEL) || s.is(Blocks.BEDROCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
            || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.STONE_BRICKS)
            || techguns.core.BuildingBlocks.ALL.stream().filter(v->v.family().equals("concrete")).anyMatch(v->s.is(techguns.modern.world.BuildingContent.BLOCKS.get(v.id()).get()));
    }
'''
    files[JAVA+'CampTerrain.java'] = (HEADER+adapt('public final class CampTerrain {\n'+gaussian+'\n'+methods.replace('new Integer(y)','Integer.valueOf(y)')+'\n'+native+'\n}')).encode()
    return files


def camp_definition():
    raw = source('MilitaryCamp')
    groups = {name: re.findall(r'\b'+field+r'\.add\((.+)\);', raw) for name,field in [('inside','structures'),('border','borderStructures'),('corner','cornerStructures')]}
    assert [len(v) for v in groups.values()] == [13,7,2]
    hashes = {}
    for name in ('MilitaryCamp','MilitaryBaseStructure','WorldgenStructure','MBlockRegister')+PARTS:
        data = (LEGACY/f'java/techguns/world/structures/{name}.java').read_bytes().replace(b'\r\n',b'\n')
        hashes[name] = hashlib.sha256(data).hexdigest()
    return {'sources':hashes,'components':groups,'dimensions':{'x':[32,79],'z':[32,79],'y':8,'retry_shrink':16,'retry_minimum':32},
            'terrain':{'sample_step':8,'maximum_height_difference':5,'flatten_difference':4,'filter':'sequential Gaussian 5x5'},
            'selection':{'grid':64,'land':{'MilitaryBaseStructure':1,'CastleStructure':1},'water':{'AircraftCarrier':1}},
            'encounters':{'soldier':{'quota':3,'active':1,'interval':200,'range':0},'helicopter':{'quota':1,'active':1,'interval':200,'range':0,'height':64}},
            'native_adaptation':'A complete terrain/geometry plan and independent decoration RNG are saved before chunk clipping; no global world RNG or neighbouring-chunk writes.',
            'source_quirks':['Tent camoMeta is unused; all tents and watchtower roofs remain woodland','Lower median index and in-place Gaussian filtering','Unused CampProps chest roll still consumes RNG','Flag tile is commented out; helicopter and road soldier posts are active']}


def nested_loot(name):
    original = json.loads((LEGACY/f'resources/assets/techguns/loot_tables/chests/{name}.json').read_text())
    pools=[]
    for pool in original['pools']:
        pools.append({'rolls':{'type':'minecraft:uniform',**pool['rolls']},'entries':[
            {'type':'minecraft:loot_table','value':entry['name'],'weight':entry['weight']} for entry in pool['entries']]})
    return {'type':'minecraft:chest','pools':pools}


def generate_camp_content():
    files=generated_geometry()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/military-camp.json',camp_definition())
    for name in ('militarybase_bunker','militarybase_barracks'):
        data(RESOURCES+f'data/techguns/loot_table/chests/{name}.json',nested_loot(name))
    data(RESOURCES+'data/techguns/tags/worldgen/biome/has_military_camp.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+'data/techguns/worldgen/structure/military_camp.json',{'type':'techguns:military_camp','biomes':'#techguns:has_military_camp','step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none'})
    data(RESOURCES+'data/techguns/worldgen/structure_set/military_camp.json',{'structures':[{'structure':'techguns:military_camp','weight':1}],'placement':{'type':'minecraft:random_spread','spacing':64,'separation':63,'salt':1337262}})
    return files
