"""The two original metal stair finishes needed by AircraftCarrier, including return recipes."""
import copy
import json
from legacy_npcs import LEGACY, RESOURCES

VARIANTS = ('stairs_metal', 'stairs_metal_dark')
RECIPES = ('stairs_metal_7', 'stairs_metal_15', 'metalpanel_4', 'metalpanel_6')


def generate_metal_stairs_content():
    files = {}; assets = LEGACY/'resources/assets/techguns'
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode()
    original = json.loads((assets/'blockstates/stairs_metal.json').read_text())['variants']
    for index, name in enumerate(VARIANTS):
        states = {}; models = set()
        for key, value in original.items():
            if key.endswith('type2='+str(bool(index)).lower()):
                v = copy.deepcopy(value); model = v['model'].split(':')[1]; models.add(model)
                v['model'] = 'techguns:block/'+model; states[key.rsplit(',type2=',1)[0]] = v
        assert len(states) == 40
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json', {'variants':states})
        for model in models:
            v = json.loads((assets/f'models/block/{model}.json').read_text())
            v['parent'] = 'minecraft:'+v['parent']; v['textures'] = {k:t.replace(':blocks/',':block/') for k,t in v['textures'].items()}
            data(RESOURCES+f'assets/techguns/models/block/{model}.json',v)
        data(RESOURCES+f'assets/techguns/items/{name}.json', {'model':{'type':'minecraft:model','model':'techguns:block/metal_stairs'+('_2' if index else '')}})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for tag in ('mineable/pickaxe','stairs'):
        data(RESOURCES+f'data/minecraft/tags/block/{tag}.json',{'replace':False,'values':['techguns:'+n for n in VARIANTS]})
    return files


def metal_stairs_translations(lang):
    source = dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {kind+'.techguns.'+name:source[f'tile.techguns.stairs_metal.{7+8*i}.name'] for i,name in enumerate(VARIANTS) for kind in ('block','item')}
