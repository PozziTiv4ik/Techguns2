"""Original Overworld ore spike scan, palette, two encounters and slimy block assets."""
import copy
import hashlib
import json
import re
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES
from legacy_locations import location_nbt

def spike_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/orecluster_spike').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode().splitlines(); cells=[list(map(int,s.split(','))) for s in lines[1:] if s]
    assert len(cells)==int(lines[0]) and len({tuple(c[:3]) for c in cells})==len(cells)
    source=strip_comments((LEGACY/'java/techguns/world/structures/OreClusterSpike.java').read_text(encoding='utf-8'))
    palette=[]; entities={}
    markers={'new MultiMBlock(new Block[] {Blocks.STONE, Blocks.AIR}, new int[] {0,0}, new int[] {1,1})':'spike_stone_air',
             'new MBlockOreClusterTypeOre(ores, oreWeights, new MBlock(Blocks.STONE,0),0.5f)':'spike_stone_ore',
             'new MBlockOreclusterType(types, clusterWeights, 0.5f, ores)':'spike_cluster_ore',
             'new MBlockOreclusterType(types, clusterWeights, 0, null)':'spike_cluster'}
    for index,entry in enumerate(re.findall(r'blockList.add\((.*)\);',source)):
        vanilla=re.fullmatch(r'new MBlock\(Blocks.(AIR|STONE),\s*0\)',entry)
        ladder=re.fullmatch(r'new MBlock\(TGBlocks.SLIMY_LADDER, (\d+)\)',entry)
        spawn=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(AlienBug.class, (\d+)\)',entry)
        if vanilla: state={'Name':'minecraft:'+vanilla[1].lower()}
        elif ladder: state={'Name':'techguns:slimyladder','Properties':{'facing':{2:'north',3:'south',4:'west',5:'east'}[int(ladder[1])],'waterlogged':'false'}}
        elif entry=='new MBlock(TGBlocks.SLIMY_BLOCK, 0)': state={'Name':'techguns:bugnest_eggs'}
        elif spawn:
            left,active,delay,radius,weight=map(int,spawn.groups()); state={'Name':'techguns:tg_spawner'}
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:alienbug','weight':weight}]}
        elif entry in markers:
            state={'Name':'minecraft:structure_block','Properties':{'mode':'data'}}
            entities[index]={'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:'+markers[entry]}
        else: raise ValueError('Unmapped OreClusterSpike palette entry: '+entry)
        palette.append(state)
    types=re.findall(r'EnumOreClusterType\.(\w+)',re.search(r'types = \{([^}]+)',source)[1])
    weights=list(map(int,re.search(r'clusterWeights = \{([^}]+)',source)[1].split(',')))
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/OreClusterSpike.java','scan_sha256':hashlib.sha256(raw).hexdigest(),
            'size':[max(c[i] for c in cells)+1 for i in range(3)],'pivot':[4,0,4],'palette':palette,'block_entities':entities,'cells':cells,
            'cluster_types':[t.lower() for t in types],'cluster_weights':weights,'cluster_roll_bound':sum(weights)+1,
            'clear_bottom_columns':sum(c[1]==0 for c in cells),'clear_above':[1,6],'foundation_depth':0,
            'generation':{'dimension':'minecraft:overworld','medium_grid':32,'reserved_big_grid':64,'height_samples':[0,4,8],'maximum_height_spread':3,
                          'ordinary_land_total':35,'sandy_wasteland_total':55,'sandy_wasteland_with_block_oil_total':70,'spike_weight':10,
                          'unported_candidates_retain_weight':True,'ocean_excluded':True,'native_rng':'26.2 chunk seed; saved type and per-position mixture seed',
                          'native_surface':'Nine noise terrain columns; reject liquid above solid floor. Modern negative heights supported.'}}

def generate_spike_content():
    files={}; assets=LEGACY/'resources/assets/techguns'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    d=spike_definition(); data('content/orecluster-spike.json',d)
    files[RESOURCES+'data/techguns/structure/orecluster_spike.nbt']=location_nbt(d)
    data(RESOURCES+'data/techguns/tags/worldgen/biome/has_orecluster_spike.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+'data/techguns/worldgen/structure/orecluster_spike.json',{'type':'techguns:orecluster_spike','biomes':'#techguns:has_orecluster_spike','step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_big_grid':64})
    data(RESOURCES+'data/techguns/worldgen/structure_set/orecluster_spike.json',{'structures':[{'structure':'techguns:orecluster_spike','weight':1}],'placement':{'type':'minecraft:random_spread','spacing':32,'separation':31,'salt':1337262}})
    for name,texture in [('bugnest_eggs','bugnest_eggs'),('slimyladder','bugnestslimy')]:
        model=json.loads((assets/f'models/block/{name}.json').read_text(encoding='utf-8')); model.pop('groups',None)
        if model.get('parent','').startswith('block/'): model['parent']='minecraft:'+model['parent']
        model['textures']={k:v.replace(':blocks/',':block/') for k,v in model['textures'].items()}
        for element in model.get('elements',[]): element.pop('type',None)
        data(RESOURCES+f'assets/techguns/models/block/{name}.json',model)
        item=copy.deepcopy(model); item['textures']={k:v.replace(':block/',':item/') for k,v in item['textures'].items()}
        if name=='slimyladder':
            # Source ItemBlock uses metadata 3: SOUTH, whose blockstate applies x=90, y=180.
            for element in item['elements']: element['rotation']={'origin':[8,8,8],'x':-90,'y':-180,'z':0}
        data(RESOURCES+f'assets/techguns/models/item/{name}.json',item)
        data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+name}})
        if name=='bugnest_eggs': states={'variants':{'':{'model':'techguns:block/'+name}}}
        else:
            states=json.loads((assets/'blockstates/slimyladder.json').read_text(encoding='utf-8'))
            for variant in states['variants'].values(): variant['model']='techguns:block/slimyladder'
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json',states)
        data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        for atlas in ('block','item'):
            files[RESOURCES+f'assets/techguns/textures/{atlas}/{texture}.png']=(assets/f'textures/blocks/{texture}.png').read_bytes()
            meta=assets/f'textures/blocks/{texture}.png.mcmeta'
            if meta.exists(): files[RESOURCES+f'assets/techguns/textures/{atlas}/{texture}.png.mcmeta']=meta.read_bytes()
    data(RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['techguns:bugnest_eggs']})
    data(RESOURCES+'data/minecraft/tags/block/climbable.json',{'replace':False,'values':['techguns:slimyladder']})
    return files

def spike_translations(lang):
    names=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {kind+'.techguns.'+name:names['tile.techguns.'+original+'.name'] for name,original in [('bugnest_eggs','slimy.0'),('slimyladder','slimyladder.3')] for kind in ('block','item')}
