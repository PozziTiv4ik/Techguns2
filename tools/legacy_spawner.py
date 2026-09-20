"""Original HOLE and SOLDIER_SPAWN geometry and shared source behavior catalog."""
import json
import re
from legacy_npcs import LEGACY, RESOURCES
from legacy_models import strip_comments, numeric


def spawner_definition():
    source=strip_comments((LEGACY/'java/techguns/tileentities/TGSpawnerTileEnt.java').read_text())
    defaults={name:numeric(value) for name,value in re.findall(r'protected (?:int|double) (\w+)\s*=\s*([^;]+);',source)}
    return {'source':'legacy/1.12.2/src/main/java/techguns/tileentities/TGSpawnerTileEnt.java','defaults':defaults,
            'variants':[{'id':'hole','legacy_metadata':0,'npc':'techguns:zombiesoldier','implemented':True},
                        {'id':'soldier_spawn','legacy_metadata':1,'npc':'techguns:armysoldier','implemented':True}],
            'home_radius':10,'outline':[2,0,2,14,2,14],'collision':False,'survival_breaking':False,'loot':[],
            'quota':'Only registered deaths decrement mobsLeft; despawn frees a slot; active limit min(maxActive,mobsLeft)',
            'selection':'WeightedRandom uses strict cumulative > roll; unported types retain their weight and skip their attempt',
            'checks':{'peaceful':True,'nearby_player':False,'daylight':False,'supporting_ground':False,'collision':False},
            'modern_persistence':'Instance UUID + NPC reservations + per-dimension SavedData notification mailbox; no forced chunk loading',
            'origin_daylight_exemption':'Persistent origin marker, including after the original block is removed, as in TGSpawnerNPCData.hasSpawner'}


def generate_spawner_content():
    files={}
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/npc-spawner.json',spawner_definition())
    for identifier,legacy_model in [('tg_spawner','hole'),('soldier_spawn','soldier_spawn')]:
        model=json.loads((LEGACY/f'resources/assets/techguns/models/block/{legacy_model}.json').read_text())
        model['parent']='minecraft:block/block'; model.pop('groups',None)
        model['textures']={key:value.replace(':blocks/',':block/') for key,value in model['textures'].items()}
        data(RESOURCES+f'assets/techguns/models/block/{identifier}.json',model)
        data(RESOURCES+f'assets/techguns/models/item/{identifier}.json',{'parent':'techguns:block/'+identifier})
        data(RESOURCES+f'assets/techguns/items/{identifier}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+identifier}})
        data(RESOURCES+f'assets/techguns/blockstates/{identifier}.json',{'variants':{'':{'model':'techguns:block/'+identifier}}})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{identifier}.json',{'type':'minecraft:block','pools':[]})
        files[RESOURCES+f'assets/techguns/textures/block/{legacy_model}.png']=(LEGACY/f'resources/assets/techguns/textures/blocks/{legacy_model}.png').read_bytes()
    return files


def spawner_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {kind+'.techguns.'+identifier:source[f'tile.techguns.tg_spawner.{meta}.name'] for meta,identifier in [(0,'tg_spawner'),(1,'soldier_spawn')] for kind in ('block','item')}
