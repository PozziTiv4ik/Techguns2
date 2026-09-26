"""Original NetherAltarMedium scan, rotated stairs and four finite CyberDemon encounters."""
import copy
import hashlib
import json
import re
from legacy_models import strip_comments
from legacy_locations import location_nbt, metal_definitions
from legacy_nether_castle import nether_castle_definition
from legacy_clusters import cluster_definitions
from legacy_npcs import LEGACY, RESOURCES


def medium_altar_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/nether_altar_medium').read_bytes().replace(b'\r\n',b'\n')
    rows=raw.decode().splitlines(); cells=[list(map(int,line.split(','))) for line in rows[1:] if line]
    assert len(cells)==int(rows[0]) and len({tuple(c[:3]) for c in cells})==len(cells)
    source=strip_comments((LEGACY/'java/techguns/world/structures/NetherAltarMedium.java').read_text(encoding='utf-8'))
    palette=[]; entities={}; metals=metal_definitions()
    for index,entry in enumerate(re.findall(r'blockList.add\((.*)\);',source)):
        metal=re.fullmatch(r'new MBlock\(TGBlocks.NETHER_METAL, (\d+)\)',entry)
        vanilla=re.fullmatch(r'new MBlock\(Blocks.(\w+), (\d+)\)',entry)
        spawn=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(CyberDemon.class, (\d+)\)',entry)
        if metal: state={'Name':'techguns:'+metals[int(metal[1])]['id']}
        elif entry=='MBlockRegister.AIR': state={'Name':'minecraft:air'}
        elif vanilla:
            name,meta=vanilla[1],int(vanilla[2]); state={'Name':'minecraft:'+{'NETHER_BRICK_FENCE':'nether_brick_fence','NETHER_BRICK':'nether_bricks','NETHER_BRICK_STAIRS':'nether_brick_stairs'}[name]}
            if name=='NETHER_BRICK_STAIRS': state['Properties']={'facing':['east','west','south','north'][meta&3],'half':'top' if meta&4 else 'bottom','shape':'straight','waterlogged':'false'}
            else: assert meta==0
        elif spawn:
            left,active,delay,radius,weight=map(int,spawn.groups()); state={'Name':'techguns:tg_spawner'}
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:cyberdemon','weight':weight}]}
        else: raise ValueError('Unmapped NetherAltarMedium palette entry: '+entry)
        palette.append(state)
    assert len(palette)==15 and all(0<=c[3]<len(palette) for c in cells)
    declared=list(map(int,re.search(r'super\((\d+),(\d+),(\d+),',source).groups()))
    generation=copy.deepcopy(nether_castle_definition()['generation']); generation['ore_toggle_required']=False
    generation['native_rng']='Shared 26.2 medium candidate and rotation; source placement has no random block palette'
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/NetherAltarMedium.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[max(c[i] for c in cells)+1 for i in range(3)],'declared_size':declared,'pivot':[8,0,8],
            'height_offset':int(re.search(r'int hoffset = (-?\d+);',source)[1]),'worldgen_floor_offset':-1,
            'foundation_cells':sum(c[1]==0 for c in cells),'foundation_depth':16,'foundation_stop_after_solids':2,
            'palette':palette,'block_entities':entities,'cells':cells,'generation':generation}


def generate_medium_altar_content():
    files={}; d=medium_altar_definition(); name='nether_altar_medium'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/nether-altar-medium.json',d)
    files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_nether']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,
         'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],
         'placement':{'type':'minecraft:random_spread','spacing':32,'separation':31,'salt':1337262}})
    # DeltaFeature and BasaltColumnsFeature have hardcoded vanilla fortress/spawner lists.
    # Targeted mixins extend them when a neighbouring chunk decorates later.
    data(RESOURCES+'data/techguns/tags/block/nether_structure_blocks.json',{'replace':False,
         'values':['techguns:'+m['id'] for m in metal_definitions()]+['techguns:tg_spawner']+['techguns:'+c['id'] for c in cluster_definitions()]})
    return files
