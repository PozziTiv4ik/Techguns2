"""Original NetherOreClusterCastle scan, palette and rifle overrides, without changing legacy."""
import hashlib
import json
import re
from legacy_models import strip_comments
from legacy_locations import location_nbt, metal_definitions
from legacy_npcs import LEGACY, RESOURCES


def nether_castle_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/nether_orecluster_castle').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode().splitlines(); cells=[list(map(int,line.split(','))) for line in lines[1:] if line]
    assert len(cells)==int(lines[0]) and len({tuple(c[:3]) for c in cells})==len(cells)
    source=strip_comments((LEGACY/'java/techguns/world/structures/NetherOreClusterCastle.java').read_text(encoding='utf-8'))
    register=strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text(encoding='utf-8'))
    assert re.search(r'NETHERBRICKS_C13\s*=\s*new MBlock\(Blocks.NETHER_BRICK,0\)',register)
    palette=[]; entities={}
    for index,entry in enumerate(re.findall(r'blockList.add\((.*)\);',source)):
        metal=re.fullmatch(r'new MBlock\(TGBlocks.NETHER_METAL, (\d+)\)',entry)
        spawn=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(ZombiePigmanSoldier.class, (\d+)\).setWeaponOverride\(new ItemStack\(TGuns.(\w+)\)\)',entry)
        if metal: state={'Name':'techguns:'+metal_definitions()[int(metal[1])]['id']}
        elif entry=='MBlockRegister.NETHERBRICKS_C13': state={'Name':'minecraft:nether_bricks'}
        elif entry=='MBlockRegister.AIR': state={'Name':'minecraft:air'}
        elif entry=='new MBlock(Blocks.NETHER_BRICK_FENCE, 0)': state={'Name':'minecraft:nether_brick_fence'}
        elif spawn:
            left,maximum,interval,radius,weight=map(int,spawn.groups()[:5]); state={'Name':'techguns:tg_spawner'}
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':maximum,'spawnDelay':interval,'delay':200,
                             'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombiepigmansoldier','weight':weight}],
                             'weapon':{'id':'techguns:'+spawn[6],'count':1}}
        elif entry=='new MultiMBlock(new Block[] {TGBlocks.ORE_CLUSTER, Blocks.AIR}, new int[] {7,0}, new int[] {40,60})':
            state={'Name':'minecraft:structure_block','Properties':{'mode':'data'}}
            entities[index]={'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:castle_cluster_or_air'}
        elif entry=='new MBlock(TGBlocks.ORE_CLUSTER, 7)': state={'Name':'techguns:ore_cluster_nether_crystal'}
        else: raise ValueError('Unmapped NetherOreClusterCastle palette entry: '+entry)
        palette.append(state)
    assert len(palette)==9 and all(0<=c[3]<len(palette) for c in cells)
    declared=list(map(int,re.search(r'super\((\d+),(\d+),(\d+),',source).groups()))
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/NetherOreClusterCastle.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[max(c[i] for c in cells)+1 for i in range(3)],'declared_size':declared,'pivot':[5,0,5],
            'height_offset':int(re.search(r'int hoffset = (-?\d+);',source)[1]),'worldgen_floor_offset':-1,
            'foundation_cells':sum(c[1]==0 for c in cells),'foundation_depth':16,'foundation_stop_after_solids':2,
            'palette':palette,'block_entities':entities,'cells':cells,'mixture_weights':[40,60],'mixture_effective_tickets':[41,60],
            'generation':{'dimension':'minecraft:the_nether','medium_grid':32,'reserved_big_grid':64,'min_y':20,'max_y':100,
                          'clearance':10,'corner_height_spread':10,'ore_toggle_required':True,
                          'candidates':[{'id':name,'weight':weight,'implemented':done} for name,weight,done in
                                        [('nether_altar_medium',10,True),('nether_ghast_spawner',10,True),('nether_ore_cluster_castle',1000,True)]],
                          'native_rng':'Native structure seed; per-piece 64-bit mixture seed plus absolute position replaces legacy world.rand'}}


def generate_nether_castle_content():
    files={}; d=nether_castle_definition(); name='nether_ore_cluster_castle'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/nether-ore-cluster-castle.json',d)
    files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_nether']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,
         'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],
         'placement':{'type':'minecraft:random_spread','spacing':32,'separation':31,'salt':1337262}})
    return files
