"""SmallTrainstation: sparse scan, inclusive damage rolls, source aliases and resource chest."""
import hashlib
import json
import re
from legacy_locations import factory_chest_loot, location_nbt, small_overworld_candidates
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def train_station_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/small_trainstation').read_bytes().replace(b'\r\n',b'\n')
    rows=raw.decode().splitlines(); cells=[list(map(int,s.split(','))) for s in rows[1:] if s]
    assert len(cells)==int(rows[0])==328 and len({tuple(c[:3]) for c in cells})==327
    source_cells=cells
    # The final source post overwrites an earlier AIR record at [7,1,4] in pass zero.
    # Keep that source history in the catalog, and the final state once in native NBT.
    cells=list({tuple(c[:3]):c for c in source_cells}.values())
    source=strip_comments((LEGACY/'java/techguns/world/structures/SmallTrainstation.java').read_text(encoding='utf-8'))
    register=strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text(encoding='utf-8'))
    spawn=strip_comments((LEGACY/'java/techguns/world/TGStructureSpawnRegister.java').read_text(encoding='utf-8'))
    dimensions=list(map(int,re.search(r'new SmallTrainstation\(0, 0, 0, 0, 0, 0\).setXZSize\((\d+), (\d+)\)',spawn).groups()))
    entries=re.findall(r'blockList.add\((.*)\);',source); assert len(entries)==22
    palette=[]; entities={}; weighted=[]
    def state(name,**properties): return {'Name':'minecraft:'+name,**({'Properties':properties} if properties else {})}
    air=state('air')
    for index,entry in enumerate(entries):
        variants=None; weights=None
        if entry.startswith('new MultiMBlock'):
            match=re.fullmatch(r'new MultiMBlock\(new Block\[\]\{Blocks.(RAIL|GLASS_PANE),Blocks.AIR\},new int\[\]\{(\d+),0\}, new int\[\]\{(\d+),(\d+)\}\)',entry)
            block,meta,first,second=match.groups(); assert (block,int(meta)) in (('RAIL',0),('GLASS_PANE',4))
            variants=[state('rail',shape='north_south',waterlogged='false') if block=='RAIL' else state('glass_pane'),air]; weights=[int(first),int(second)]
        elif entry.startswith('MBlockRegister.'):
            alias=entry.split('.')[1]; definition=re.search(r'\b'+alias+r'\s*=\s*(.*);',register)[1]
            if alias=='COBBLESTONE_FLOOR': assert definition=='new MBlock(Blocks.STONE.getDefaultState())'; value=state('stone')
            elif alias=='BRICKS_CRACKED_RND': assert definition=='new MBlock(Blocks.BRICK_BLOCK,0)'; value=state('bricks')
            elif alias=='COBBLESTONE_MOSSY_RND':
                assert re.fullmatch(r'new MultiMBlock\(new Block\[\]\{Blocks.COBBLESTONE,Blocks.MOSSY_COBBLESTONE\},new int\[\]\{0,0\}, new int\[\]\{2,1\}\)',definition)
                variants=[state('cobblestone'),state('mossy_cobblestone')]; weights=[2,1]
            elif alias=='STONEBRICK_CRACKED_RND':
                assert re.fullmatch(r'new MultiMBlock\(new Block\[\] \{Blocks.STONEBRICK, Blocks.STONEBRICK, Blocks.STONEBRICK, Blocks.AIR\}, new int\[\]\{0,1,2,0\}, new int\[\]\{3,3,3,2\}\)',definition)
                variants=[state('stone_bricks'),state('mossy_stone_bricks'),state('cracked_stone_bricks'),air]; weights=[3,3,3,2]
            else:
                facing=re.search(r'BlockHorizontal.FACING, EnumFacing.(EAST|WEST|SOUTH|NORTH)',definition)[1].lower()
                value=state('stone_brick_stairs',facing=facing,half='top' if 'EnumHalf.TOP' in definition else 'bottom',shape='straight',waterlogged='false')
                if 'CRACKED' in alias:
                    assert definition.startswith('new MultiMBlock') and 'new int[] {4,1}' in definition
                    variants=[value,air]; weights=[4,1]
                else: assert definition.startswith('new MBlock(Blocks.STONE_BRICK_STAIRS.getDefaultState()')
        elif entry.startswith('new MBlockChestLoottable'):
            assert entry=='new MBlockChestLoottable(Blocks.CHEST, 2, CHEST_LOOT)'
            value=state('chest',facing='north',type='single',waterlogged='false'); entities[index]={'id':'minecraft:chest','LootTable':'techguns:chests/small_trainstation'}
        elif entry.startswith('new MBlockTGSpawner'):
            match=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(ZombieMiner.class, (\d+)\)',entry)
            left,active,delay,radius,weight=map(int,match.groups()); value={'Name':'techguns:tg_spawner'}
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombieminer','weight':weight}]}
        else:
            block,meta=re.fullmatch(r'new MBlock\(Blocks.(\w+), (\d+)\)',entry).groups(); meta=int(meta)
            if block=='WOODEN_SLAB': assert meta==8; value=state('oak_slab',type='top',waterlogged='false')
            elif block=='LADDER': assert meta==3; value=state('ladder',facing='south',waterlogged='false')
            elif block=='FURNACE': assert meta==4; value=state('furnace',facing='west',lit='false')
            elif block=='WOODEN_PRESSURE_PLATE': assert meta==0; value=state('oak_pressure_plate',powered='false')
            else: assert meta==0; value=state({'GRAVEL':'gravel','AIR':'air','OAK_FENCE':'oak_fence','CRAFTING_TABLE':'crafting_table'}[block])
        if variants is not None:
            value=state('structure_block',mode='data'); marker='techguns:train_'+str(index)
            entities[index]={'id':'minecraft:structure_block','mode':'DATA','metadata':marker,'Variants':variants,'Weights':weights}
            weighted.append({'palette_index':index,'marker':marker,'states':variants,'weights':weights,'roll_bound':sum(weights)+1,'roll_counts':[weights[0]+1,*weights[1:]]})
        palette.append(value)
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/SmallTrainstation.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[max(c[i] for c in cells)+1 for i in range(3)],'registered_xz_size':dimensions,'pivot':[dimensions[0]//2,0,dimensions[1]//2],
            'palette':palette,'cells':cells,'source_cells':source_cells,'block_entities':entities,'weighted_cells':weighted,'source_entries':entries,
            'foundation_cells':sum(c[1]==0 for c in cells),'foundation_depth':1,'clear_height':7,'worldgen_floor_offset':-1,
            'mixture_rng':'Saved per-piece DamageSeed XOR absolute block position, including independent foundation rolls; not legacy world.rand sequence',
            'generation':{'dimension':'minecraft:overworld','small_grid':16,'reserved_medium_grid':32,'reserved_big_grid':64,'height_samples_x':[0,4,8],'height_samples_z':[0,4,8,12],
                          'maximum_height_spread':3,'weight':10,'tickets':[10,19],'ore_toggle_required':False,'ocean_excluded':True,
                          'candidates':small_overworld_candidates()}}


def generate_train_station_content():
    files={}; d=train_station_definition(); name='small_trainstation'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/train-station.json',d); files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/loot_table/chests/{name}.json',factory_chest_loot(name))
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_medium_grid':32,'reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':16,'separation':15,'salt':1337262}})
    return files
