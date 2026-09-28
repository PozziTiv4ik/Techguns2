"""Original conditional DesertOilCluster scan, its two mixtures, and finite military posts."""
from legacy_structure_grids import grid_placement
import hashlib
import json
import re
from legacy_locations import location_nbt
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def desert_oil_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/desert_oil_cluster').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode().splitlines(); cells=[list(map(int,s.split(','))) for s in lines[1:] if s]
    assert len(cells)==int(lines[0])==579 and len({tuple(c[:3]) for c in cells})==579
    source=strip_comments((LEGACY/'java/techguns/world/structures/DesertOilCluster.java').read_text(encoding='utf-8'))
    entries=re.findall(r'blockList.add\((.*)\);',source)
    assert entries==['new MBlock(Blocks.SANDSTONE,0)','new MBlock(TGBlocks.SANDBAGS,0)','new MBlock(Blocks.AIR,0)',
                     'new MBlockTGSpawner(EnumMonsterSpawnerType.SOLDIER_SPAWN,3,1,200,1).addMobType(ArmySoldier.class, 4).addMobType(Commando.class, 1)',
                     'oilblockSand','new MBlockOreclusterType(types, clusterWeights, 0.5f, ores)','oilblock',
                     'new MBlockOreclusterType(types, clusterWeights, 0, null)','oilblock']
    assert re.search(r'types = \{EnumOreClusterType.OIL\}',source)
    assert re.search(r'clusterWeights = \{10\}',source)
    assert 'new int[] {1,1}' in source and 'TGFluids.OIL_WORLDSPAWN.getBlock().getDefaultState()' in source
    palette=[{'Name':'minecraft:sandstone'},{'Name':'techguns:sandbags'},{'Name':'minecraft:air'},{'Name':'techguns:soldier_spawn'}]
    entities={3:{'id':'techguns:tg_spawner','mobsLeft':3,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':1.,'spawnHeightOffset':0,
                 'mobtypes':[{'id':'techguns:armysoldier','weight':4},{'id':'techguns:commando','weight':1}]}}
    for index,marker in enumerate(('rim','cluster_or_oil','oil','cluster','oil'),start=4):
        palette.append({'Name':'minecraft:structure_block','Properties':{'mode':'data'}})
        entities[index]={'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:desert_oil_'+marker}
    assert [max(c[i] for c in cells)+1 for i in range(3)]==[11,10,11]
    assert all(c[1]>0 for c in cells)  # cleanUpwards only acts on y=0, absent from this scan.
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/DesertOilCluster.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[11,10,11],'pivot':[5,0,5],'palette':palette,'cells':cells,'block_entities':entities,'height_offset':-4,'worldgen_floor_offset':-1,
            'clear_height_parameter':6,'effective_clearing_columns':0,'foundation_depth':0,
            'rim_weights':[1,1],'rim_effective_tickets':[2,1],'cluster_alternative_chance':.5,'cluster_type':'oil',
            'no_fluid_debug_palette':{'rim':'minecraft:magma_block','cluster_alternative':'minecraft:sandstone','oil':'minecraft:lava'},
            'generation':{'dimension':'minecraft:overworld','medium_grid':32,'reserved_big_grid':64,'height_samples':[0,4,8],'maximum_height_spread':3,
                          'weight':15,'ore_toggle_required':True,'world_oil_required':True,'biomes':['sandy','wasteland'],'ocean_excluded':True,
                          'oil_configuration':'FluidListOilWorldspawn (oil, crude_oil), plus optional techguns:worldgen_oils tag',
                          'native_rng':'Per-piece saved fluid ID and 64-bit mixture seed; absolute cell position replaces legacy shared world.rand'}}


def generate_desert_oil_content():
    files={}; d=desert_oil_definition(); name='desert_oil_cluster'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/desert-oil-cluster.json',d); files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+'data/techguns/tags/fluid/worldgen_oils.json',{'replace':False,'values':[]})
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none'})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],'placement':grid_placement('medium')})
    return files
