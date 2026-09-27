"""SmallMine: exact sparse scan, biome cover, shared cluster type and inclusive ore mixtures."""
import hashlib
import json
import re
from legacy_locations import location_nbt, small_overworld_candidates
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def small_mine_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/small_mine').read_bytes().replace(b'\r\n',b'\n')
    rows=raw.decode().splitlines(); cells=[list(map(int,s.split(','))) for s in rows[1:] if s]
    assert len(cells)==int(rows[0])==972 and len({tuple(c[:3]) for c in cells})==972
    source=strip_comments((LEGACY/'java/techguns/world/structures/SmallMine.java').read_text(encoding='utf-8'))
    spawn=strip_comments((LEGACY/'java/techguns/world/TGStructureSpawnRegister.java').read_text(encoding='utf-8'))
    dimensions=list(map(int,re.search(r'new SmallMine\(\).setXZSize\((\d+), (\d+)\)',spawn).groups()))
    types=[v.lower() for v in re.findall(r'EnumOreClusterType\.(\w+)',re.search(r'types = \{([^}]+)',source)[1])]
    weights=list(map(int,re.search(r'clusterWeights = \{([^}]+)',source)[1].split(',')))
    assert types==['coal','common_metal','common_gem','rare_metal','shiny_metal','shiny_gem','uranium'] and weights==[10,10,10,5,2,2,2]
    ore_source=re.search(r'static MBlock\[\] ores = \{(.*?)\n\s*\};',source,re.S)[1]
    ore_entries=[line.strip().rstrip(',') for line in ore_source.splitlines() if line.strip()]
    ores=[]; ore_weights=[]
    def ore_state(owner,block,meta):
        if owner=='TGBlocks':
            assert block=='TG_ORE'; return 'techguns:'+re.fullmatch(r'EnumOreType\.(ORE_\w+)\.ordinal\(\)',meta)[1].lower()
        assert meta=='0'; return 'minecraft:'+block.lower()
    for entry in ore_entries:
        if entry.startswith('new MultiMBlock'):
            blocks,metas,w=re.fullmatch(r'new MultiMBlock\(new Block\[\] \{([^}]+)\}, new int\[\] \{([^}]+)\}, new int\[\] \{([^}]+)\}\)',entry).groups()
            blocks=[v.strip().split('.') for v in blocks.split(',')]; metas=[v.strip() for v in metas.split(',')]
            ores.append([ore_state(*b,m) for b,m in zip(blocks,metas)]); ore_weights.append(list(map(int,w.split(','))))
        else:
            owner,block,meta=re.fullmatch(r'new MBlock\((Blocks|TGBlocks)\.(\w+),\s*(.*?)\)',entry).groups()
            ores.append([ore_state(owner,block,meta)]); ore_weights.append([1])
    assert len(ores)==7
    entries=re.findall(r'blockList.add\((.*)\);',source); assert len(entries)==17
    palette=[]; entities={}
    def state(name,**props): return {'Name':'minecraft:'+name,**({'Properties':props} if props else {})}
    markers={3:'cover',5:'patch',13:'stone_ore',15:'cluster_ore',16:'cluster'}
    for i,entry in enumerate(entries):
        if i in markers:
            expected={3:'new MBlockBiomeColorType(new Block[] {Blocks.GRASS, Blocks.SNOW, Blocks.SAND, Blocks.NETHERRACK }, new int[] {0,0,0,0})',
                      5:'new MultiMMBlock(new MBlock[] {new MBlockBiomeColorType(new Block[] {Blocks.GRASS, Blocks.GRASS, Blocks.SAND, Blocks.NETHERRACK }, new int[] {0,0,0,0}), new MBlock(Blocks.AIR,0)}, new int[] {4,1})',
                      13:'new MBlockOreClusterTypeOre(ores, oreWeights, new MBlock(Blocks.STONE,0),0.75f)',
                      15:'new MBlockOreclusterType(types, clusterWeights, 0.5f, ores)',16:'new MBlockOreclusterType(types, clusterWeights, 0, null)'}
            assert entry==expected[i]; value=state('structure_block',mode='data')
            entities[i]={'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:mine_'+markers[i]}
        elif entry.startswith('new MBlockTGSpawner'):
            left,active,delay,radius,weight=map(int,re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(ZombieMiner.class, (\d+)\)',entry).groups())
            value={'Name':'techguns:tg_spawner'}; entities[i]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':delay,'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombieminer','weight':weight}]}
        else:
            block,meta=re.fullmatch(r'new MBlock\(Blocks.(\w+), (\d+)\)',entry).groups(); meta=int(meta)
            if block=='LOG': value=state('oak_log',axis={0:'y',4:'x',8:'z'}[meta])
            elif block=='RAIL': value=state('rail',shape={1:'east_west',3:'ascending_west'}[meta],waterlogged='false')
            elif block=='TORCH': value=state('wall_torch',facing={2:'west',3:'south',4:'north'}[meta])
            elif block=='STONE_STAIRS': assert meta==1; value=state('cobblestone_stairs',facing='west',half='bottom',shape='straight',waterlogged='false')
            else: assert meta==0 and block in ('AIR','STONE'); value=state(block.lower())
        palette.append(value)
    assert 'placeFoundation' not in source
    assert 'posY-5' in source and 'direction, 0, 4)' in source
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/SmallMine.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[max(c[i] for c in cells)+1 for i in range(3)],'registered_xz_size':dimensions,'pivot':[dimensions[0]//2,0,dimensions[1]//2],
            'cells':cells,'palette':palette,'block_entities':entities,'source_entries':entries,'ore_source_entries':ore_entries,
            'surface_offset':-5,'worldgen_floor_offset':-1,'clear_height':4,'clear_bottom_cells':[c for c in cells if c[1]==0],'foundation_depth':0,
            'cluster_types':types,'cluster_weights':weights,'cluster_roll_bound':sum(weights)+1,'cluster_roll_counts':[weights[0]+1,*weights[1:]],
            'mixtures':{'cover':['minecraft:grass_block','minecraft:snow_block','minecraft:sand','minecraft:netherrack'],
                        'patch_cover':['minecraft:grass_block','minecraft:grass_block','minecraft:sand','minecraft:netherrack'],'patch_weights':[4,1],'patch_roll_counts':[5,1],
                        'stone_chance':0.75,'cluster_or_ore_chance':0.5,'ores':ores,'ore_weights':ore_weights,
                        'ore_roll_counts':[[w[0]+1,*w[1:]] if len(w)>1 else [1] for w in ore_weights]},
            'mixture_rng':'Saved type, biome cover and per-piece MixtureSeed XOR absolute block position; not legacy world.rand sequence',
            'generation':{'dimension':'minecraft:overworld','small_grid':16,'reserved_medium_grid':32,'reserved_big_grid':64,'height_samples_x':[0,4,8,12,16],'height_samples_z':[0,4,8],
                          'maximum_height_spread':3,'weight':10,'tickets':[20,29],'ore_toggle_required':False,'ocean_excluded':True,'candidates':small_overworld_candidates()}}


def generate_small_mine_content():
    files={}; d=small_mine_definition(); name='small_mine'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/small-mine.json',d); files[RESOURCES+f'data/techguns/structure/{name}.nbt']=location_nbt(d)
    data(RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+f'data/techguns/worldgen/structure/{name}.json',{'type':'techguns:'+name,'biomes':'#techguns:has_'+name,'step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_medium_grid':32,'reserved_big_grid':64})
    data(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json',{'structures':[{'structure':'techguns:'+name,'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':16,'separation':15,'salt':1337262}})
    return files
