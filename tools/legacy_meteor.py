"""The 2265-cell OreClusterMeteorBasis scan and its native 26.2 template."""
import hashlib
import json
import re
from collections import Counter
from legacy_building import building_id
from legacy_locations import location_nbt
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES

TYPES=('coal','common_metal','common_gem','rare_metal','shiny_metal','shiny_gem','uranium','nether_crystal')


def meteor_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/orecluster_meteorbase').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode('utf-8').splitlines(); cells=[list(map(int,line.split(','))) for line in lines[1:] if line]
    assert len(cells)==int(lines[0])==2265 and len({tuple(c[:3]) for c in cells})==2265
    source=strip_comments((LEGACY/'java/techguns/world/structures/OreClusterMeteorBasis.java').read_text(encoding='utf-8'))
    entries=re.findall(r'blockList.add\((.*)\);',source); assert len(entries)==21
    fixed=['new MBlock(Blocks.STONE, 0)','new MBlock(Blocks.GRAVEL, 0)','new MBlock(TGBlocks.SANDBAGS, 0)',
           'new MBlock(Blocks.IRON_BARS, 0)','new MBlock(Blocks.AIR, 0)']
    assert entries[:5]==fixed
    assert entries[5]=='new MBlockTGSpawner(EnumMonsterSpawnerType.HOLE,2,1,200,1).addMobType(ArmySoldier.class, 4).addMobType(Commando.class, 1)'
    assert entries[6]=='new MBlock(TGBlocks.METAL_PANEL, TGMetalPanelType.PANEL_LARGE_BORDER.ordinal())'
    assert entries[7]=='new MultiMBlock(new Block[] {Blocks.MAGMA, Blocks.STONE}, new int[]{0,0}, new int[]{1,1})'
    assert entries[8]=='new MBlock(TGBlocks.CONCRETE, EnumConcreteType.CONCRETE_BROWN_LIGHT_SCAFF.ordinal())'
    assert entries[9:15]==['new MBlock(TGBlocks.LAMP_0, 6)','new MBlock(Blocks.GLASS_PANE, 15)',
                          'new MBlock(TGBlocks.METAL_PANEL, 7)','new MBlock(TGBlocks.BUNKER_DOOR, 1)',
                          'new MBlock(TGBlocks.BUNKER_DOOR, 8)','new MBlock(TGBlocks.LAMP_0, 7)']
    assert entries[15:] == ['new MBlock(Blocks.MAGMA, 0)',
                             'new MBlockOreclusterType(types, clusterWeights, 0.5f, ores)',
                             'new MBlock(TGBlocks.LADDER_0, 12)',
                             'new MBlockOreclusterType(types, clusterWeights, 0, null)',
                             'new MBlock(TGBlocks.LADDER_0, 0)','new MBlock(Blocks.AIR, 0)']
    weights=[int(x) for x in re.search(r'clusterWeights = \{([^}]+)',source)[1].split(',')]
    assert weights==[5]*7+[15]
    assert tuple(re.findall(r'EnumOreClusterType\.(\w+)',re.search(r'types = \{([^}]+)',source)[1]))==tuple(t.upper() for t in TYPES)
    palette=[{'Name':'minecraft:'+n} for n in ('stone','gravel')]+[{'Name':'techguns:sandbags'},
            {'Name':'minecraft:iron_bars'},{'Name':'minecraft:air'},
            {'Name':'techguns:tg_spawner'},
            {'Name':'techguns:'+building_id('metalpanel',4)},
            {'Name':'minecraft:structure_block','Properties':{'mode':'data'}},
            {'Name':'techguns:'+building_id('concrete',5)},
            {'Name':'techguns:lamp_white','Properties':{'facing':'down'}},
            {'Name':'minecraft:glass_pane'},
            {'Name':'techguns:'+building_id('metalpanel',7)},
            {'Name':'techguns:bunkerdoor','Properties':{'facing':'south','half':'lower','hinge':'left','open':'false','powered':'false'}},
            {'Name':'techguns:bunkerdoor','Properties':{'facing':'south','half':'upper','hinge':'left','open':'false','powered':'false'}},
            {'Name':'techguns:lamp_white','Properties':{'facing':'up'}},
            {'Name':'minecraft:magma_block'},
            {'Name':'minecraft:structure_block','Properties':{'mode':'data'}},
            {'Name':'techguns:'+building_id('ladder0',12),'Properties':{'facing':'east'}},
            {'Name':'minecraft:structure_block','Properties':{'mode':'data'}},
            {'Name':'techguns:'+building_id('ladder0',0),'Properties':{'facing':'south'}},
            {'Name':'minecraft:air'}]
    entities={5:{'id':'techguns:tg_spawner','mobsLeft':2,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,
                 'mobtypes':[{'id':'techguns:armysoldier','weight':4},{'id':'techguns:commando','weight':1}]}}
    for index,name in ((7,'meteor_magma_or_stone'),(16,'meteor_cluster_ore'),(18,'meteor_cluster')):
        entities[index]={'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:'+name}
    counts=Counter(c[3] for c in cells); assert sum(counts.values())==2265 and counts[5]==5
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/OreClusterMeteorBasis.java',
            'scan_sha256':hashlib.sha256(raw).hexdigest(),'size':[17,12,17],'pivot':[8,0,8],
            'surface_offset':-5,'clear_above_surface':30,'clear_bottom_columns':sum(c[1]==0 for c in cells),
            'foundation_depth':0,'palette':palette,'block_entities':entities,'cells':cells,
            'cluster_types':list(TYPES),'cluster_weights':weights,'cluster_roll_bound':sum(weights)+1,
            'mixtures':{'magma_or_stone':[1,1],'cluster_or_ore_chance':0.5,
                        'ores':[['minecraft:coal_ore'],['minecraft:iron_ore','techguns:ore_copper','techguns:ore_tin'],
                                ['minecraft:redstone_ore','minecraft:lapis_ore'],['techguns:ore_lead'],
                                ['minecraft:gold_ore','techguns:ore_titanium'],
                                ['minecraft:diamond_ore','minecraft:emerald_ore','minecraft:stone'],
                                ['techguns:ore_uranium','minecraft:stone'],
                                ['minecraft:netherrack','minecraft:nether_quartz_ore','minecraft:glowstone']],
                        'ore_weights':[[1],[1,1,1],[1,1],[1],[1,1],[2,1,3],[1,1],[2,1,1]]},
            'generation':{'dimension':'minecraft:overworld','medium_grid':32,'reserved_big_grid':64,
                          'height_samples':[0,4,8,12,16],'maximum_height_spread':3,
                          'ordinary_land_total':35,'sandy_wasteland_total':55,'sandy_wasteland_with_block_oil_total':70,
                          'meteor_weight':5,'ocean_excluded':True,'unported_candidates_retain_weight':True,
                          'native_rng':'26.2 chunk seed; saved type and per-position mixture seed'}}


def generate_meteor_content():
    files={}; d=meteor_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    data('content/orecluster-meteor-basis.json',d)
    files[RESOURCES+'data/techguns/structure/orecluster_meteor_basis.nbt']=location_nbt(d)
    data(RESOURCES+'data/techguns/tags/worldgen/biome/has_orecluster_meteor_basis.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+'data/techguns/worldgen/structure/orecluster_meteor_basis.json',
         {'type':'techguns:orecluster_meteor_basis','biomes':'#techguns:has_orecluster_meteor_basis','step':'top_layer_modification',
          'spawn_overrides':{},'terrain_adaptation':'none','reserved_big_grid':64})
    data(RESOURCES+'data/techguns/worldgen/structure_set/orecluster_meteor_basis.json',
         {'structures':[{'structure':'techguns:orecluster_meteor_basis','weight':1}],
          'placement':{'type':'minecraft:random_spread','spacing':32,'separation':31,'salt':1337262}})
    return files
