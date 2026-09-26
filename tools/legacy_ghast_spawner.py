"""NetherGhastSpawner scan with the original vanilla spawner and deferred chest."""
import copy
import hashlib
import json
import re
from legacy_models import strip_comments
from legacy_locations import location_nbt, metal_definitions
from legacy_nether_castle import nether_castle_definition
from legacy_npcs import LEGACY, RESOURCES


def ghast_spawner_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/nether_ghast_spawner').read_bytes().replace(b'\r\n',b'\n')
    rows=raw.decode().splitlines(); cells=[list(map(int,line.split(','))) for line in rows[1:] if line]
    assert len(cells)==int(rows[0]) and len({tuple(c[:3]) for c in cells})==len(cells)
    source=strip_comments((LEGACY/'java/techguns/world/structures/NetherGhastSpawner.java').read_text())
    register=strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text())
    palette=[]; entities={}
    for index,entry in enumerate(re.findall(r'blockList.add\((.*)\);',source)):
        rock=re.fullmatch(r'MBlockRegister.(NETHERRACK_\w+)',entry)
        metal=re.fullmatch(r'new MBlock\(TGBlocks.NETHER_METAL, (\d+)\)',entry)
        vanilla=re.fullmatch(r'new MBlock\(Blocks.(\w+), (\d+)\)',entry)
        if rock:
            assert re.search(rock[1]+r'\s*=\s*new MBlock\(Blocks.NETHERRACK,\s*0\)',register)
            state={'Name':'minecraft:netherrack'}
        elif entry=='MBlockRegister.AIR': state={'Name':'minecraft:air'}
        elif metal: state={'Name':'techguns:'+metal_definitions()[int(metal[1])]['id']}
        elif vanilla:
            name,meta=vanilla[1],int(vanilla[2]); state={'Name':'minecraft:'+{'GLOWSTONE':'glowstone','IRON_BARS':'iron_bars','SKULL':'skeleton_skull','SOUL_SAND':'soul_sand'}[name]}
            if name=='SKULL':
                assert meta==1; state['Properties']={'rotation':'0','powered':'false'}
            else: assert meta==0
        elif entry=='new MBlockVanillaSpawner(Ghastling.class)':
            state={'Name':'minecraft:spawner'}
            # Leave vanilla timers/range/nearby cap at their defaults, as setEntityId did in 1.12.2.
            entities[index]={'id':'minecraft:mob_spawner','SpawnData':{'entity':{'id':'techguns:ghastling'}}}
        elif entry=='new MBlockChestLoottable(Blocks.CHEST, 3, CHEST_LOOT)':
            state={'Name':'minecraft:chest','Properties':{'facing':'south','type':'single','waterlogged':'false'}}
            table=re.search(r'CHEST_LOOT = new ResourceLocation\(Techguns.MODID,"([^"]+)"\)',source)[1]
            entities[index]={'id':'minecraft:chest','LootTable':'techguns:'+table}
        else: raise ValueError('Unmapped NetherGhastSpawner palette entry: '+entry)
        palette.append(state)
    assert len(palette)==13 and all(0<=c[3]<len(palette) for c in cells)
    declared=list(map(int,re.search(r'super\((\d+),(\d+),(\d+),',source).groups()))
    generation=copy.deepcopy(nether_castle_definition()['generation']); generation['ore_toggle_required']=False
    generation['native_rng']='Shared 26.2 medium candidate and rotation; native deferred chest seed'
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/NetherGhastSpawner.java',
            'scan_sha256':hashlib.sha256(raw).hexdigest(),'size':[max(c[i] for c in cells)+1 for i in range(3)],
            'declared_size':declared,'pivot':[5,0,5],
            'height_offset':int(re.search(r'int hoffset = (-?\d+);',source)[1]),'worldgen_floor_offset':-1,
            'foundation_cells':sum(c[1]==0 for c in cells),'foundation_depth':16,'foundation_stop_after_solids':2,
            'palette':palette,'block_entities':entities,'cells':cells,'generation':generation}


def generate_ghast_spawner_content():
    files={}; d=ghast_spawner_definition(); name='nether_ghast_spawner'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/nether-ghast-spawner.json',d)
    files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_nether']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,
         'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],
         'placement':{'type':'minecraft:random_spread','spacing':32,'separation':31,'salt':1337262}})
    return files
