"""PoliceStation's scan, paired door/chest states, finite posts and four original loot pools."""
import copy
import hashlib
import json
import re
from legacy_building import building_id
from legacy_items import shared_items, arguments
from legacy_locations import location_nbt
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def police_loot():
    source=json.loads((LEGACY/'resources/assets/techguns/loot_tables/chests/policestation.json').read_text(encoding='utf-8'))
    guns=strip_comments((LEGACY/'java/techguns/TGuns.java').read_text(encoding='utf-8'))
    pools=[]
    for pool in source['pools']:
        entries=[]
        for e in pool['entries']:
            name=e['name']; functions=[]
            for f in e.get('functions',[]):
                if f['function']=='set_data':
                    assert name=='techguns:itemshared'; name='techguns:'+shared_items()[f['data']]
                elif f['function']=='set_count': functions.append({'function':'minecraft:set_count','count':{'type':'minecraft:uniform',**f['count']}})
                else: raise ValueError(f)
            entry={'type':'minecraft:item','name':name,'weight':e['weight']}
            if pool['name']=='gun':
                # GenericGun initializes a metadata-zero loot stack with a full magazine on first use.
                constructor=re.search(r'\b'+name.split(':')[1]+r'\s*=\s*new GenericGun\(([^;]+)',guns)[1]
                functions.append({'function':'minecraft:set_components','components':{'techguns:rounds':int(arguments(constructor)[4])}})
            if functions: entry['functions']=functions
            entries.append(entry)
        pools.append({'rolls':{'type':'minecraft:uniform',**pool['rolls']},'entries':entries})
    return {'type':'minecraft:chest','pools':pools}


def police_station_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/policestation').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode().splitlines(); cells=[list(map(int,s.split(','))) for s in lines[1:] if s]
    assert len(cells)==int(lines[0])==1212 and len({tuple(c[:3]) for c in cells})==1212
    source=strip_comments((LEGACY/'java/techguns/world/structures/PoliceStation.java').read_text(encoding='utf-8'))
    entries=re.findall(r'blockList.add\((.*)\);',source); assert len(entries)==27
    aliases={'IRON_BLOCK_SMALL_INGOTS':('IRON_BLOCK',0,'iron_block'),'IRON_PANE_MODERN_FENCE':('IRON_BARS',0,'iron_bars'),
             'BLUE_CONCRETE_SMALL_BRICKS':('CONCRETE',11,'blue_concrete'),'OAK_PLANKS_1':('PLANKS',0,'oak_planks'),'COBBLESTONE_7':('STONE',6,'polished_andesite')}
    register=strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text(encoding='utf-8'))
    palette=[]; entities={}
    for index,entry in enumerate(entries):
        state=None
        if entry.startswith('MBlockRegister.'):
            alias=entry.split('.')[1]
            if alias=='AIR': state={'Name':'minecraft:air'}
            else:
                block,meta,name=aliases[alias]; assert re.search(alias+r'\s*=\s*new MBlock\(Blocks.'+block+r',\s*'+str(meta)+r'\)',register)
                state={'Name':'minecraft:'+name}
        elif entry.startswith('new MBlockTGSpawner'):
            match=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.SOLDIER_SPAWN,(\d+),(\d+),(\d+),(\d+)\).addMobType\(ZombiePoliceman.class, (\d+)\)',entry)
            left,active,delay,radius,weight=map(int,match.groups()); state={'Name':'techguns:soldier_spawn'}
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombiepoliceman','weight':weight}]}
        elif entry.startswith('new MBlockChestLoottable'):
            meta=int(re.fullmatch(r'new MBlockChestLoottable\(Blocks.CHEST, (\d+), CHEST_LOOT\)',entry)[1])
            state={'Name':'minecraft:chest','Properties':{'facing':{2:'north',3:'south'}[meta],'type':'single','waterlogged':'false'}}
            entities[index]={'id':'minecraft:chest','LootTable':'techguns:chests/policestation'}
        else:
            owner,name,meta=re.fullmatch(r'new MBlock\((TGBlocks|Blocks).(\w+), (\d+)\)',entry).groups(); meta=int(meta)
            if owner=='TGBlocks':
                if name=='CONCRETE': state={'Name':'techguns:'+building_id('concrete',meta)}
                elif name=='SANDBAGS': assert meta==0; state={'Name':'techguns:sandbags'}
                elif name=='LAMP_0': assert meta in (7,11); state={'Name':'techguns:lamp_white','Properties':{'facing':['down','up','north','south','west','east'][meta-6]}}
                elif name=='LADDER_0': state={'Name':'techguns:'+building_id('ladder0',meta),'Properties':{'facing':['south','west','north','east'][meta>>2]}}
                elif name=='BUNKER_DOOR':
                    # Legacy upper metadata stores hinge only; both modern halves need facing and hinge.
                    assert meta in (2,9,1,8)
                    state={'Name':'techguns:bunkerdoor','Properties':{'facing':'west' if meta in (2,9) else 'south','half':'upper' if meta>=8 else 'lower','hinge':'right' if meta in (2,9) else 'left','open':'false','powered':'false'}}
                else: raise ValueError(entry)
            elif name=='RAIL': state={'Name':'minecraft:rail','Properties':{'shape':{6:'south_east',7:'south_west',8:'north_west',9:'north_east'}[meta],'waterlogged':'false'}}
            elif name=='STONE_SLAB': assert meta==8; state={'Name':'minecraft:smooth_stone_slab','Properties':{'type':'top','waterlogged':'false'}}
            elif name=='OAK_FENCE_GATE': assert meta==0; state={'Name':'minecraft:oak_fence_gate','Properties':{'facing':'south','open':'false','powered':'false','in_wall':'false'}}
            else: assert meta==0; state={'Name':'minecraft:'+{'STONEBRICK':'stone_bricks','GLASS_PANE':'glass_pane','CRAFTING_TABLE':'crafting_table'}[name]}
        palette.append(state)
    source_palette=copy.deepcopy(palette); source_cells=copy.deepcopy(cells)
    # The two north-facing neighbours form a native double chest. Both retain their own loot NBT/seed.
    palette[12]['Properties']['type']='left'; right=copy.deepcopy(palette[12]); right['Properties']['type']='right'; palette.append(right); entities[27]=copy.deepcopy(entities[12])
    for c in cells:
        if c[:3]==[6,2,7]: assert c[3]==12; c[3]=27
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/PoliceStation.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[13,8,13],'pivot':[6,0,6],'source_palette':source_palette,'source_cells':source_cells,'palette':palette,'cells':cells,'block_entities':entities,
            'foundation_cells':169,'foundation_depth':3,'clear_height':7,'worldgen_floor_offset':-1,
            'generation':{'dimension':'minecraft:overworld','medium_grid':32,'reserved_big_grid':64,'height_samples':[0,4,8,12],'maximum_height_spread':3,
                          'weight':10,'ore_toggle_required':False,'ocean_excluded':True,'unported_candidates_retain_weight':True}}


def generate_police_station_content():
    files={}; d=police_station_definition(); name='policestation'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/police-station.json',d); files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+'data/techguns/loot_table/chests/policestation.json',police_loot())
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':32,'separation':31,'salt':1337262}})
    return files
