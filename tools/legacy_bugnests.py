"""AlienBugNest source inventory and original hardened-sand assets (no prebuilt room template)."""
import hashlib
import json
import re
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def bugnest_definition():
    path=LEGACY/'java/techguns/world/structures/AlienBugNest.java'
    raw=path.read_bytes().replace(b'\r\n',b'\n'); source=strip_comments(raw.decode())
    palettes=re.findall(r'new int\[\]\s*\{(16,1,2|12,1)\}',source)
    assert palettes==['16,1,2','12,1']
    encounter=re.search(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE, (\d+), (\d+), (\d+), (\d+)\).addMobType\(AlienBug.class,\s*(\d+)\)',source)
    assert encounter
    left,active,interval,radius,weight=map(int,encounter.groups())
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/AlienBugNest.java','source_sha256':hashlib.sha256(raw).hexdigest(),
            'main_radius':5,'primary_branches':5,'extra_room_chance':0.5,'room_radius_range':[3,5],
            'width_depth_range':[16,31],'height_argument':0,'surface_offset':-2,'main_drop_range':[10,16],
            'sphere_weights':[16,1,2],'sphere_effective_tickets':[17,1,2],'tunnel_weights':[12,1],'tunnel_effective_tickets':[13,1],
            'spawner':{'mobsLeft':left,'maxActive':active,'spawnDelay':interval,'delay':200,'spawnRange':radius,'mobtypes':[{'id':'techguns:alienbug','weight':weight}]},
            'generation':{'dimension':'minecraft:overworld','biomes':['c:is_sandy','c:is_wasteland'],'medium_grid':32,'reserved_big_grid':64,
                          'weight':20,'four_surface_corners':[0,4],'maximum_corner_delta_exclusive':256,'ore_toggle_independent':True,
                          'native_difference':'Saved layout and decoration seeds replace global world RNG; complete voxel plan and attachments are persisted from noise terrain before clipping.',
                          'source_quirks':['Inclusive palette weights','Absolute-coordinate truncation toward zero','Half-open infinite-line cylinder bounds','Entrance slime box uses self-cross-product distance zero']}}


def generate_bugnest_content():
    files={}; assets=LEGACY/'resources/assets/techguns'
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/alienbug-nest.json',bugnest_definition())
    name='bugnest_sand'
    for atlas in ('block','item'):
        data(RESOURCES+f'assets/techguns/models/{atlas}/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'techguns:{atlas}/{name}'}})
        files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png']=(assets/f'textures/blocks/{name}.png').read_bytes()
    data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':f'techguns:item/{name}'}})
    data(RESOURCES+f'assets/techguns/blockstates/{name}.json',{'variants':{'':{'model':f'techguns:block/{name}'}}})
    data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'techguns:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    data(RESOURCES+'data/minecraft/tags/block/mineable/shovel.json',{'replace':False,'values':['techguns:bugnest_sand']})
    data(RESOURCES+'data/techguns/tags/worldgen/biome/has_alienbug_nest.json',{'replace':False,'values':['#minecraft:is_overworld']})
    data(RESOURCES+'data/techguns/worldgen/structure/alienbug_nest.json',{'type':'techguns:alienbug_nest','biomes':'#techguns:has_alienbug_nest','step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none','reserved_big_grid':64})
    data(RESOURCES+'data/techguns/worldgen/structure_set/alienbug_nest.json',{'structures':[{'structure':'techguns:alienbug_nest','weight':1}],'placement':{'type':'minecraft:random_spread','spacing':32,'separation':31,'salt':1337262}})
    return files


def bugnest_translations(lang):
    names=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {kind+'.techguns.bugnest_sand':names['tile.techguns.sand_hard.0.name'] for kind in ('block','item')}
