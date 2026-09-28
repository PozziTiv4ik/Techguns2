"""SurvivorHideout scan, shared panel roll, biome canopy, finite posts and unabridged loot."""
from legacy_structure_grids import grid_placement
import copy
import hashlib
import json
import re
from legacy_building import building_id
from legacy_items import shared_items
from legacy_locations import location_nbt
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def survivor_loot():
    source=json.loads((LEGACY/'resources/assets/techguns/loot_tables/chests/survivor_hideout.json').read_text(encoding='utf-8'))
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
            if functions: entry['functions']=functions
            entries.append(entry)
        pools.append({'rolls':{'type':'minecraft:uniform',**pool['rolls']},'entries':entries})
    return {'type':'minecraft:chest','pools':pools}


def survivor_hideout_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/survivor_hideout').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode().splitlines(); cells=[list(map(int,s.split(','))) for s in lines[1:] if s]
    assert len(cells)==int(lines[0])==1277 and len({tuple(c[:3]) for c in cells})==1277
    source=strip_comments((LEGACY/'java/techguns/world/structures/SurvivorHideout.java').read_text(encoding='utf-8'))
    register=strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text(encoding='utf-8'))
    entries=re.findall(r'blockList.add\((.*)\);',source); assert len(entries)==26
    palette=[]; entities={}
    for index,entry in enumerate(entries):
        state=None
        if entry.startswith('MBlockRegister.'):
            alias=entry.split('.')[1]
            if alias=='AIR': state={'Name':'minecraft:air'}
            elif alias=='CAMO_NET_TOP': state={'Name':'techguns:camonet_top_wood'}
            else:
                assert alias in ('SURIVIVOR_HIDEOUT_IRON_BOTTOM','SURIVIVOR_HIDEOUT_IRON_TOP')
                assert re.search(alias+r'\s*=\s*new MBlock\(Blocks.IRON_BLOCK,\s*0\)',register)
                state={'Name':'minecraft:iron_block'}
        elif entry.startswith('new MBlockTGSpawner'):
            match=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(Bandit.class, (\d+)\)(?:.setWeaponOverride\(new ItemStack\(TGuns.(\w+)\)\))?',entry)
            left,active,delay,radius,weight=map(int,match.groups()[:5]); state={'Name':'techguns:tg_spawner'}
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:bandit','weight':weight}]}
            if match[6]: entities[index]['weapon']={'id':'techguns:'+match[6],'count':1}
        elif entry.startswith('new MBlockChestLoottable'):
            meta=int(re.fullmatch(r'new MBlockChestLoottable\(Blocks.CHEST, (\d+), CHEST_LOOT\)',entry)[1])
            state={'Name':'minecraft:chest','Properties':{'facing':{2:'north',3:'south',4:'west'}[meta],'type':'single','waterlogged':'false'}}
            entities[index]={'id':'minecraft:chest','LootTable':'techguns:chests/survivor_hideout'}
        elif entry.startswith('new MultiMMBlockIndexRoll'):
            assert re.findall(r'new MBlock\(TGBlocks.METAL_PANEL, (\d+)\)',entry)==['0','1','2','3']
            state={'Name':'techguns:'+building_id('metalpanel',0)}
        elif entry=='new MBlock(TGBlocks.LAMP_0, 12)':
            # Lantern metadata stores no face. All four original cells have a solid support below.
            state={'Name':'techguns:lantern_yellow','Properties':{'facing':'down'}}
        else:
            name,meta=re.fullmatch(r'new MBlock\("([\w:]+)", (\d+)\)',entry).groups(); meta=int(meta)
            simple={('minecraft:dirt',1):'minecraft:coarse_dirt',('minecraft:fence',0):'minecraft:oak_fence',
                    ('techguns:sandbags',0):'techguns:sandbags',('minecraft:stonebrick',0):'minecraft:stone_bricks',
                    ('minecraft:planks',0):'minecraft:oak_planks',('minecraft:planks',1):'minecraft:spruce_planks',
                    ('minecraft:crafting_table',0):'minecraft:crafting_table'}
            if (name,meta) in simple: state={'Name':simple[name,meta]}
            elif name=='minecraft:oak_stairs':
                assert meta==0; state={'Name':'minecraft:oak_stairs','Properties':{'facing':'east','half':'bottom','shape':'straight','waterlogged':'false'}}
            elif name=='minecraft:stone_slab':
                assert meta==3; state={'Name':'minecraft:cobblestone_slab','Properties':{'type':'bottom','waterlogged':'false'}}
            elif name=='minecraft:bed':
                assert meta in (2,10); state={'Name':'minecraft:red_bed','Properties':{'facing':'north','part':'head' if meta==10 else 'foot','occupied':'false'}}
            elif name=='minecraft:torch':
                assert meta==1; state={'Name':'minecraft:wall_torch','Properties':{'facing':'east'}}
            elif name=='minecraft:furnace':
                assert meta==3; state={'Name':'minecraft:furnace','Properties':{'facing':'south','lit':'false'}}
            elif name=='techguns:ladder0':
                assert meta==0; state={'Name':'techguns:ladder_metal','Properties':{'facing':'south'}}
            elif name=='techguns:simplemachine':
                assert meta==9; state={'Name':'techguns:repair_bench','Properties':{'facing':'north'}}
            else: raise ValueError(entry)
        palette.append(state)
    source_palette=copy.deepcopy(palette); source_cells=copy.deepcopy(cells)
    # Explicit paired states survive separate native chunk placement; every tile retains its own reward.
    for pos,base,kind in [([1,2,17],7,'left'),([2,2,17],7,'right'),([9,3,5],11,'right'),([9,3,6],11,'left')]:
        state=copy.deepcopy(palette[base]); state['Properties']['type']=kind; target=len(palette); palette.append(state); entities[target]=copy.deepcopy(entities[base])
        cell=next(c for c in cells if c[:3]==pos); assert cell[3]==base; cell[3]=target
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/SurvivorHideout.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[11,11,19],'pivot':[5,0,9],'source_palette':source_palette,'source_cells':source_cells,'palette':palette,'cells':cells,'block_entities':entities,
            'foundation_cells':149,'foundation_depth':3,'clear_height':7,'worldgen_floor_offset':-1,
            'panel_variants':[building_id('metalpanel',i) for i in range(4)],'panel_roll':'One nextInt(4) per piece; persisted for all 201 cells',
            'canopy_variants':['camonet_top_wood','camonet_top_desert','camonet_top_snow'],
            'canopy_selection':'COLD or SNOWY first; SANDY, BEACH or MESA second; otherwise woodland (including savanna)',
            'generation':{'dimension':'minecraft:overworld','medium_grid':32,'reserved_big_grid':64,'sample_step':4,'height_sample_count':15,'maximum_height_spread':3,
                          'weight':10,'ore_toggle_required':False,'ocean_excluded':True,'unported_candidates_retain_weight':True,
                          'native_rng':'Native structure seed; saved panel and canopy replace legacy per-placement state'}}


def generate_survivor_hideout_content():
    files={}; d=survivor_hideout_definition(); name='survivor_hideout'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/survivor-hideout.json',d); files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/loot_table/chests/{name}.json',survivor_loot())
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none'})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],'placement':grid_placement('medium')})
    return files
