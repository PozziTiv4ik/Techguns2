"""GasStation's original scan, three double chests, finite zombies and fuel loot."""
import copy
import hashlib
import json
import re
from legacy_building import building_id
from legacy_items import shared_items
from legacy_locations import location_nbt
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def gas_station_loot():
    source=json.loads((LEGACY/'resources/assets/techguns/loot_tables/chests/gasstation.json').read_text(encoding='utf-8'))
    def number(value): return {'type':'minecraft:uniform',**value} if isinstance(value,dict) else value
    pools=[]
    for pool in source['pools']:
        entries=[]
        for entry in pool['entries']:
            name=entry['name']; functions=[]
            for f in entry.get('functions',[]):
                if f['function']=='set_data':
                    assert name=='techguns:itemshared'; name='techguns:'+shared_items()[f['data']]
                elif f['function']=='set_count': functions.append({'function':'minecraft:set_count','count':number(f['count'])})
                else: raise ValueError(f)
            e={'type':'minecraft:item','name':name,'weight':entry['weight']}
            if functions: e['functions']=functions
            entries.append(e)
        p={'rolls':number(pool['rolls']),'entries':entries}
        if 'bonus_rolls' in pool: p['bonus_rolls']=number(pool['bonus_rolls'])
        pools.append(p)
    return {'type':'minecraft:chest','pools':pools}


def gas_station_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/gasstation').read_bytes().replace(b'\r\n',b'\n')
    rows=raw.decode().splitlines(); cells=[list(map(int,s.split(','))) for s in rows[1:] if s]
    assert len(cells)==int(rows[0])==719 and len({tuple(c[:3]) for c in cells})==719
    source=strip_comments((LEGACY/'java/techguns/world/structures/GasStation.java').read_text(encoding='utf-8'))
    register=strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text(encoding='utf-8'))
    entries=re.findall(r'blockList.add\((.*)\);',source); assert len(entries)==24
    palette=[]; entities={}
    for index,entry in enumerate(entries):
        properties={}
        if entry.startswith('MBlockRegister.'):
            alias=entry.split('.')[1]
            if alias=='AIR': name='minecraft:air'  # The item frame was commented out in the source.
            elif alias=='COBBLESTONE_7':
                assert re.search(alias+r'\s*=\s*new MBlock\(Blocks.STONE,6\)',register); name='minecraft:polished_andesite'
            elif alias=='GAS_STATION_CONSOLE':
                assert re.search(alias+r'\s*=\s*new MBlock\(Blocks.QUARTZ_STAIRS,1\)',register)
                name='minecraft:quartz_stairs'; properties={'facing':'west','half':'bottom','shape':'straight','waterlogged':'false'}
            else: raise ValueError(entry)
        elif entry.startswith('new MBlockTGSpawner'):
            match=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\)(.*)',entry)
            left,active,delay,radius=map(int,match.groups()[:4]); name='techguns:tg_spawner'
            species=re.findall(r'.addMobType\((\w+).class, (\d+)\)',match[5])
            assert len(species)==3
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,'spawnRange':float(radius),'spawnHeightOffset':0,
                             'mobtypes':[{'id':'techguns:'+mob.lower(),'weight':int(weight)} for mob,weight in species]}
        elif entry.startswith('new MBlockChestLoottable'):
            assert entry=='new MBlockChestLoottable(Blocks.CHEST, 4, CHEST_LOOT)'
            name='minecraft:chest'; properties={'facing':'west','type':'single','waterlogged':'false'}
            entities[index]={'id':'minecraft:chest','LootTable':'techguns:chests/gasstation'}
        else:
            owner,block,meta=re.fullmatch(r'new MBlock\("(\w+):(\w+)", (\d+)\)',entry).groups(); meta=int(meta)
            if owner=='techguns':
                if block=='concrete': name='techguns:'+building_id(block,meta)
                elif block=='lamp0':
                    assert meta in (7,11); name='techguns:lamp_white'; properties={'facing':['down','up','north','south','west','east'][meta-6]}
                else: raise ValueError(entry)
            elif block=='lever':
                assert meta==3; name='minecraft:lever'; properties={'face':'wall','facing':'south','powered':'false'}
            elif block=='wooden_door':
                assert meta in (2,8); name='minecraft:oak_door'; properties={'facing':'west','half':'lower' if meta==2 else 'upper','hinge':'left','open':'false','powered':'false'}
            elif block in ('stone_slab','double_stone_slab','wooden_slab'):
                assert meta in (0,8); name='minecraft:oak_slab' if block=='wooden_slab' else 'minecraft:smooth_stone_slab'
                properties={'type':'double' if block=='double_stone_slab' else 'top' if meta&8 else 'bottom','waterlogged':'false'}
            elif block=='trapdoor':
                assert meta in (10,14); name='minecraft:oak_trapdoor'; properties={'facing':'west','half':'top','open':'true' if meta&4 else 'false','powered':'false','waterlogged':'false'}
            elif block=='stone': assert meta==6; name='minecraft:polished_andesite'
            else:
                assert meta==0; name='minecraft:'+{'quartz_block':'quartz_block','stonebrick':'stone_bricks','brick_block':'bricks','glass_pane':'glass_pane','planks':'oak_planks'}[block]
        state={'Name':name}
        if properties: state['Properties']=properties
        palette.append(state)
    source_palette=copy.deepcopy(palette); source_cells=copy.deepcopy(cells)
    # West-facing left halves connect north. Keep a separate deferred loot tile in every half.
    palette[23]['Properties']['type']='left'; other=copy.deepcopy(palette[23]); other['Properties']['type']='right'; palette.append(other); entities[24]=copy.deepcopy(entities[23])
    for cell in cells:
        if cell[3]==23 and cell[2]==6: cell[3]=24
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/GasStation.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[9,7,12],'pivot':[4,0,6],'source_palette':source_palette,'source_cells':source_cells,'palette':palette,'cells':cells,'block_entities':entities,
            'foundation_cells':108,'foundation_depth':3,'clear_height':7,'worldgen_floor_offset':-1,
            'generation':{'dimension':'minecraft:overworld','small_grid':16,'reserved_medium_grid':32,'reserved_big_grid':64,'height_samples_x':[0,4,8],'height_samples_z':[0,4,8,12],
                          'maximum_height_spread':3,'weight':10,'ore_toggle_required':False,'ocean_excluded':True,
                          'candidates':[{'id':name,'weight':10,'implemented':name in ('small_trainstation','gasstation')} for name in ('factory_house_small','small_trainstation','small_mine','gasstation')]}}


def generate_gas_station_content():
    files={}; d=gas_station_definition(); name='gasstation'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/gas-station.json',d); files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/loot_table/chests/{name}.json',gas_station_loot())
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_medium_grid':32,'reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':16,'separation':15,'salt':1337262}})
    return files
