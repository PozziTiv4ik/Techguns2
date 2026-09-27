"""FactoryHouseSmall's source palette, two-colour foundation, yellow lamps and double chest."""
import copy
import hashlib
import json
import re
from legacy_building import building_id, building_definitions
from legacy_fortifications import lamp_id
from legacy_locations import location_nbt, small_overworld_candidates
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def factory_house_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/factory_building_small').read_bytes().replace(b'\r\n',b'\n')
    rows=raw.decode().splitlines(); original=[list(map(int,s.split(','))) for s in rows[1:] if s]
    assert len(original)==int(rows[0])==463 and len({tuple(c[:3]) for c in original})==462
    # The last HOLE post replaces an earlier AIR cell in the same source pass.
    cells=[c.copy() for c in {tuple(c[:3]):c for c in original}.values()]
    source=strip_comments((LEGACY/'java/techguns/world/structures/FactoryHouseSmall.java').read_text(encoding='utf-8'))
    register=strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text(encoding='utf-8'))
    spawn=strip_comments((LEGACY/'java/techguns/world/TGStructureSpawnRegister.java').read_text(encoding='utf-8'))
    dimensions=list(map(int,re.search(r'new FactoryHouseSmall\(8,0,7,9,5,10\).setXZSize\((\d+), (\d+)\)',spawn).groups()))
    entries=re.findall(r'blockList.add\((.*)\);',source); assert len(entries)==22
    palette=[]; entities={}; faces=['down','up','north','south','west','east']
    for index,entry in enumerate(entries):
        props={}
        if entry.startswith('MBlockRegister.'):
            alias=entry.split('.')[1]; definition=re.search(r'\b'+alias+r'\s*=\s*(.*);',register)[1]
            if alias=='FACTORY_PLATE': assert definition=='new MBlock(Blocks.BRICK_BLOCK,0)'; name='minecraft:bricks'
            elif alias=='FACTORY_PLATE_DOTTED':
                assert definition=='new MBlock(Blocks.HARDENED_CLAY,EnumDyeColor.GRAY.ordinal())'
                # BlockHardenedClay inherits Block.getStateFromMeta: GRAY metadata is ignored.
                name='minecraft:terracotta'
            elif alias=='TECHNICAL_CONCRETE':
                meta=int(re.fullmatch(r'new MBlock\(TGBlocks.CONCRETE,(\d+)\)',definition)[1]); name='techguns:'+building_id('concrete',meta)
            else:
                kind=re.fullmatch(r'new MBlock\(TGBlocks.METAL_PANEL.getDefaultState\(\).withProperty\(TGBlocks.METAL_PANEL.TYPE, TGMetalPanelType.(\w+)\)\)',definition)[1].lower()
                name='techguns:'+next(v['id'] for v in building_definitions() if v['family']=='metalpanel' and v['id']=='metalpanel_'+kind)
        elif entry.startswith('new MBlockChestLoottable'):
            assert entry=='new MBlockChestLoottable(Blocks.CHEST, 5, CHEST_LOOT)'
            name='minecraft:chest'; props={'facing':'east','type':'single','waterlogged':'false'}
            entities[index]={'id':'minecraft:chest','LootTable':'techguns:chests/factory_building'}
        elif entry.startswith('new MBlockTGSpawner'):
            match=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(ZombieMiner.class, (\d+)\)',entry)
            left,active,delay,radius,weight=map(int,match.groups()); name='techguns:tg_spawner'
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':delay,'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombieminer','weight':weight}]}
        elif 'TGBlocks.LAMP_0' in entry:
            facing=re.fullmatch(r'new MBlock\(TGBlocks.LAMP_0, EnumFacing.(\w+).ordinal\(\)\)',entry)[1].lower()
            name='techguns:'+lamp_id(faces.index(facing)); props={'facing':facing}
        elif 'TGBlocks.BUNKER_DOOR' in entry:
            half=re.fullmatch(r'new MBlock\(TGBlocks.BUNKER_DOOR.getDefaultState\(\).withProperty\(TGBlocks.BUNKER_DOOR.FACING, EnumFacing.SOUTH\).withProperty\(TGBlocks.BUNKER_DOOR.HINGE, EnumHingePosition.LEFT\).withProperty\(TGBlocks.BUNKER_DOOR.HALF, EnumDoorHalf.(LOWER|UPPER)\)\)',entry)[1].lower()
            name='techguns:bunkerdoor'; props={'facing':'south','hinge':'left','half':half,'open':'false','powered':'false'}
        elif 'TGBlocks.LADDER_0' in entry:
            assert entry=='new MBlock(TGBlocks.LADDER_0.getDefaultState().withProperty(TGBlocks.LADDER_0.FACING, EnumFacing.SOUTH))'
            name='techguns:'+building_id('ladder0',0); props={'facing':'south'}
        else:
            owner,block,meta=re.fullmatch(r'new MBlock\((TGBlocks|Blocks).(\w+),\s*(\d+)\)',entry).groups(); meta=int(meta)
            if owner=='TGBlocks': assert block=='CONCRETE'; name='techguns:'+building_id('concrete',meta)
            elif block=='FURNACE': assert meta==3; name='minecraft:furnace'; props={'facing':'south','lit':'false'}
            else: assert meta==0; name='minecraft:'+{'AIR':'air','GLASS_PANE':'glass_pane','IRON_BARS':'iron_bars','CRAFTING_TABLE':'crafting_table'}[block]
        palette.append({'Name':name,**({'Properties':props} if props else {})})
    source_palette=copy.deepcopy(palette)
    # East-facing left half connects south, keeping a separate deferred reward in each half.
    palette[6]['Properties']['type']='left'; other=copy.deepcopy(palette[6]); other['Properties']['type']='right'; palette.append(other); entities[22]=copy.deepcopy(entities[6])
    for c in cells:
        if c[:3]==[1,1,4]: assert c[3]==6; c[3]=22
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/FactoryHouseSmall.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[max(c[i] for c in cells)+1 for i in range(3)],'registered_xz_size':dimensions,'pivot':[dimensions[0]//2,0,dimensions[1]//2],
            'source_entries':entries,'source_cells':original,'source_palette':source_palette,'cells':cells,'palette':palette,'block_entities':entities,
            'foundation_cells':93,'foundation_depth':3,'clear_height':7,'worldgen_floor_offset':-1,'surface_swap_xz':False,
            'generation':{'dimension':'minecraft:overworld','small_grid':16,'reserved_medium_grid':32,'reserved_big_grid':64,'height_samples_x':[0,4,8],'height_samples_z':[0,4,8],
                          'maximum_height_spread':3,'weight':10,'tickets':[0,9],'ore_toggle_required':False,'ocean_excluded':True,'candidates':small_overworld_candidates()},
            'loot_table':'techguns:chests/factory_building'}


def generate_factory_house_content():
    files={}; d=factory_house_definition(); name='factory_house_small'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/factory-house-small.json',d); files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_medium_grid':32,'reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':16,'separation':15,'salt':1337262}})
    return files
