"""Sandbags, lamps and the bunker door from the original building catalog."""
import copy
import json
from legacy_items import LEGACY
from legacy_repair import RESOURCES

LAMPS = [('lamp_yellow',0,'yellow'), ('lamp_white',6,'white'),
         ('lantern_yellow',12,'yellow_lantern'), ('lantern_white',13,'white_lantern')]
RECIPES = ('sandbags','item_bunkerdoor','lamp0_0','lamp0_0_alt','lamp0_6','lamp0_12','lamp0_12_alt','lamp0_13')
SOUNDS = ('blocks.metaldooropen',)
DOOR_PARENTS = {'door_bottom':'door_bottom_left','door_bottom_rh':'door_bottom_right',
                'door_top':'door_top_left','door_top_rh':'door_top_right'}


def lamp_id(metadata):
    return LAMPS[0 if metadata < 6 else 1 if metadata < 12 else metadata-10][0]


def generate_fortification_content():
    files = {}; assets = LEGACY/'resources/assets/techguns'; visited = set()
    def data(path, value): files[path] = (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    def model(source, target, atlas):
        if (source,target,atlas) in visited: return
        visited.add((source,target,atlas))
        value = json.loads((assets/f'models/{source}.json').read_text(encoding='utf-8'))
        parent = value.get('parent')
        if parent and parent.startswith('techguns:'):
            src = parent.split(':')[1]; dest = src.split('/')[-1]
            model(src,dest,atlas); value['parent'] = f'techguns:{atlas}/{dest}'
        elif parent:
            name = parent.removeprefix('minecraft:')
            if name.startswith('block/door_'): name = 'block/'+DOOR_PARENTS[name[6:]]
            value['parent'] = 'minecraft:'+name
        for key, texture in value.get('textures',{}).items():
            if texture.startswith('techguns:'):
                src = texture.split(':')[1]; name = src.split('/')[-1]
                value['textures'][key] = f'techguns:{atlas}/{name}'
                files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png'] = (assets/f'textures/{src}.png').read_bytes()
        data(RESOURCES+f'assets/techguns/models/{atlas}/{target}.json',value)
    def states(source, target, lamp_type=None):
        value = json.loads((assets/f'blockstates/{source}.json').read_text(encoding='utf-8'))
        if lamp_type is not None:
            value['multipart'] = [p for p in value['multipart'] if p.get('when',{}).get('lamp_type') == lamp_type]
            for p in value['multipart']:
                p['when'].pop('lamp_type')
                if not p['when']: p.pop('when')
        entries = [p['apply'] for p in value['multipart']] if 'multipart' in value else value['variants'].values()
        for entry in entries:
            name = entry['model'].split(':')[1]; model('block/'+name,name,'block'); entry['model'] = 'techguns:block/'+name
        data(RESOURCES+f'assets/techguns/blockstates/{target}.json',value)
    def item(source, name):
        model('item/'+source,name,'item')
        data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+name}})
    def loot(block, item, lower=False):
        conditions = [{'condition':'minecraft:survives_explosion'}]
        if lower: conditions.append({'condition':'minecraft:block_state_property','block':'techguns:'+block,'properties':{'half':'lower'}})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{block}.json',{'type':'minecraft:block','pools':[{'rolls':1,'conditions':conditions,'entries':[{'type':'minecraft:item','name':'techguns:'+item}]}]})
    states('sandbags','sandbags'); item('sandbags_inventory','sandbags'); loot('sandbags','sandbags')
    states('bunkerdoor','bunkerdoor'); item('item_bunkerdoor','item_bunkerdoor'); loot('bunkerdoor','item_bunkerdoor',True)
    for name, meta, source_type in LAMPS:
        states('lamp0',name,source_type); item('lamp_inventory_'+source_type,name); loot(name,name)
    data(RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['techguns:bunkerdoor']+['techguns:'+n for n,_,_ in LAMPS]})
    data(RESOURCES+'data/minecraft/tags/block/doors.json',{'replace':False,'values':['techguns:bunkerdoor']})
    data('content/fortifications.json',{'source':'legacy/1.12.2/src/main/java/techguns/TGBlocks.java',
         'sandbags':{'hardness':6,'blast_resistance':9,'collision':'center and cardinal arms; no corner collision','fence_connection':'fence extends to sandbags; sandbags do not extend to fence'},
         'lamps':[{'id':n,'metadata':m,'source_type':t} for n,m,t in LAMPS],
         'lamp_rules':{'hardness':4,'blast_resistance':4,'light':15,'camo_bench':False,'color_change':'original shapeless recipes','support':'selected face, vertical checks support top even on ceiling',
                       'lantern_attachment_save':'modern FACING persists; legacy metadata 12/13 lost attachment direction'},
         'door':{'hardness':8,'blast_resistance':8,'manual_sound':SOUNDS[0],'manual_pitch':1,'manual_volume':1,'paired_toggle':'same block and same previous open state','piston':'block','placement_face':'up'},
         'recipes':list(RECIPES),'pending':['Visual acceptance','Other-mod block attachment compatibility','OreClusterMeteorBasis placement']})
    return files


def fortification_translations(lang):
    source = dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    names = {n:source[f'tile.techguns.lamp0.{m}.name'] for n,m,_ in LAMPS}
    names['sandbags'] = source['tile.techguns.sandbags.name']; names['item_bunkerdoor'] = source['item.techguns.item_bunkerdoor.name']
    result = {kind+'.techguns.'+n:label for n,label in names.items() for kind in ('block','item')}
    result['block.techguns.bunkerdoor'] = names['item_bunkerdoor']
    return result
