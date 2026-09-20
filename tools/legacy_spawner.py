"""Original HOLE spawner geometry and source behavior catalog; ArmySoldier preset remains pending."""
import json
import re
from legacy_npcs import LEGACY, RESOURCES
from legacy_models import strip_comments, numeric


def spawner_definition():
    source=strip_comments((LEGACY/'java/techguns/tileentities/TGSpawnerTileEnt.java').read_text())
    defaults={name:numeric(value) for name,value in re.findall(r'protected (?:int|double) (\w+)\s*=\s*([^;]+);',source)}
    return {'source':'legacy/1.12.2/src/main/java/techguns/tileentities/TGSpawnerTileEnt.java','defaults':defaults,
            'variants':[{'id':'hole','legacy_metadata':0,'npc':'techguns:zombiesoldier','implemented':True},
                        {'id':'soldier_spawn','legacy_metadata':1,'npc':'techguns:armysoldier','implemented':False,'requires':['ArmySoldier','T2 beret']}],
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
    model=json.loads((LEGACY/'resources/assets/techguns/models/block/hole.json').read_text())
    model['parent']='minecraft:block/block'; model.pop('groups',None)
    model['textures']={key:value.replace(':blocks/',':block/') for key,value in model['textures'].items()}
    data(RESOURCES+'assets/techguns/models/block/tg_spawner.json',model)
    data(RESOURCES+'assets/techguns/models/item/tg_spawner.json',{'parent':'techguns:block/tg_spawner'})
    data(RESOURCES+'assets/techguns/items/tg_spawner.json',{'model':{'type':'minecraft:model','model':'techguns:item/tg_spawner'}})
    data(RESOURCES+'assets/techguns/blockstates/tg_spawner.json',{'variants':{'':{'model':'techguns:block/tg_spawner'}}})
    data(RESOURCES+'data/techguns/loot_table/blocks/tg_spawner.json',{'type':'minecraft:block','pools':[]})
    files[RESOURCES+'assets/techguns/textures/block/hole.png']=(LEGACY/'resources/assets/techguns/textures/blocks/hole.png').read_bytes()
    return files


def spawner_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {key:source['tile.techguns.tg_spawner.0.name'] for key in ('block.techguns.tg_spawner','item.techguns.tg_spawner')}
