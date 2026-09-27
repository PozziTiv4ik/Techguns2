"""Nine original BlockMilitaryCrate variants and six unabridged break-reward tables."""
import copy
import json
import re
from legacy_items import LEGACY, shared_items
from legacy_models import strip_comments
from legacy_repair import RESOURCES


def crate_definition():
    enum = strip_comments((LEGACY/'java/techguns/blocks/EnumMilitaryCrateType.java').read_text(encoding='utf-8'))
    names = [n.strip().lower() for n in re.search(r'implements IStringSerializable\s*\{(.*?);', enum, re.S)[1].split(',')]
    source = strip_comments((LEGACY/'java/techguns/TGBlocks.java').read_text(encoding='utf-8'))
    registration = re.search(r'MILITARY_CRATE\s*=.*?;', source, re.S)[0]
    assert 'Material.WOOD' in registration and 'setHardness(4.0f)' in registration
    variants = [{'id':'military_crate_'+n, 'metadata':i, 'type':n,
                 'loot':'military_crate_'+('generic' if n.startswith('generic_') else 'explosives' if n=='explosive' else n)} for i,n in enumerate(names)]
    return {'source':'legacy/1.12.2/src/main/java/techguns/blocks/BlockMilitaryCrate.java', 'variants':variants,
            'hardness':4, 'explosion_resistance':4, 'material':'wood', 'sound':'stone', 'note_instrument':'bass',
            'bounds':[.03125,0,.03125,.96875,1,.96875], 'support':'CENTER_BIG on UP/DOWN, UNDEFINED on sides',
            'recipes':[], 'container':False, 'rotation':'identity; source has no facing property',
            'loot_trigger':'server player harvest without Silk Touch; Forge HIGH event; Fortune is luck',
            'other_destruction':'ordinary variant drop, subject to normal drop/explosion rules',
            'pending':['MilitaryCamp placement', 'GPU acceptance']}


def crate_id(metadata):
    variants = crate_definition()['variants']
    if not 0 <= metadata < len(variants): raise ValueError('Invalid military crate metadata')
    return variants[metadata]['id']


def crate_loot(weapons):
    guns = {g['id']:g for g in weapons}; shared = shared_items(); tables = {}
    for path in sorted((LEGACY/'resources/assets/techguns/loot_tables/blocks').glob('military_crate_*.json')):
        pools = []
        for pool in json.loads(path.read_text(encoding='utf-8'))['pools']:
            result = {'name':pool['name'], 'rolls':pool['rolls'], 'entries':[]}
            if 'bonus_rolls' in pool: result['bonus_rolls'] = {'type':'minecraft:uniform', **pool['bonus_rolls']}
            for e in pool['entries']:
                assert e['type']=='item'
                name = e['name']; functions = []
                for f in e.get('functions', []):
                    if f['function']=='set_data':
                        assert name=='techguns:itemshared'; name = 'techguns:'+shared[f['data']]
                    elif f['function']=='set_count': functions.append({'function':'minecraft:set_count','count':{'type':'minecraft:uniform',**f['count']}})
                    else: raise ValueError('Unconverted crate function: '+f['function'])
                if name.removeprefix('techguns:') in guns:
                    # GenericGun.onCreated/getCurrentAmmo lazily turns metadata 0 into a full magazine.
                    functions.append({'function':'minecraft:set_components','components':{'techguns:rounds':guns[name.split(':')[1]]['capacity']}})
                entry = {'type':'minecraft:item', 'name':name, 'weight':e['weight']}
                if functions: entry['functions'] = functions
                if 'quality' in e: entry['quality'] = e['quality']
                result['entries'].append(entry)
            pools.append(result)
        tables[path.stem] = {'type':'minecraft:chest', 'pools':pools}
    if len(tables)!=6: raise ValueError('Review changed source crate tables')
    return tables


def generate_crate_content(weapons):
    files = {}; definition = crate_definition(); assets = LEGACY/'resources/assets/techguns'; tables = crate_loot(weapons)
    def data(path, value): files[path] = (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    data('content/military-crates.json', {**definition,'loot':tables})
    states = json.loads((assets/'blockstates/military_crate.json').read_text(encoding='utf-8'))
    geometry = json.loads((assets/'models/block/military_crate.json').read_text(encoding='utf-8'))
    for v in definition['variants']:
        name = v['id']; source_textures = states['variants']['type'][v['type']]['textures']
        for atlas in ('block','item'):
            textures = {}
            for slot, texture in source_textures.items():
                if texture.startswith('minecraft:blocks/planks_'): textures[slot] = 'minecraft:block/'+texture.split('planks_')[1]+'_planks'
                else:
                    assert texture.startswith('techguns:blocks/')
                    leaf = texture.split('/')[-1]; textures[slot] = 'techguns:'+atlas+'/'+leaf
                    files[RESOURCES+f'assets/techguns/textures/{atlas}/{leaf}.png'] = (assets/f'textures/blocks/{leaf}.png').read_bytes()
            data(RESOURCES+f'assets/techguns/models/{atlas}/{name}.json', {**copy.deepcopy(geometry), 'parent':'minecraft:block/block', 'textures':textures})
        data(RESOURCES+f'assets/techguns/items/{name}.json', {'model':{'type':'minecraft:model','model':'techguns:item/'+name}})
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json', {'variants':{'':{'model':'techguns:block/'+name}}})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{name}_self.json', {'type':'minecraft:block','pools':[{'rolls':1,
             'entries':[{'type':'minecraft:item','name':'techguns:'+name}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for name, table in tables.items(): data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json', table)
    data(RESOURCES+'data/minecraft/tags/block/mineable/axe.json', {'replace':False,'values':['techguns:'+v['id'] for v in definition['variants']]})
    entries = ',\n'.join(f'        new Variant("{v["id"]}",{v["metadata"]},"blocks/{v["loot"]}")' for v in definition['variants'])
    files['core/src/main/java/techguns/core/MilitaryCrates.java'] = ('''package techguns.core;

import java.util.List;

/** Generated original metadata order and the six break-reward table names. */
public final class MilitaryCrates {
    public record Variant(String id, int metadata, String loot) {}
    public static final List<Variant> ALL = List.of(
'''+entries+'''
    );
    public static Variant byMetadata(int metadata) {
        if (metadata < 0 || metadata >= ALL.size()) throw new IllegalArgumentException("Invalid military crate metadata: " + metadata);
        return ALL.get(metadata);
    }
    private MilitaryCrates() {}
}
''').encode('utf-8')
    return files


def crate_translations(lang):
    source = dict(l.split('=',1) for l in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8-sig').splitlines() if '=' in l)
    return {kind+'.techguns.'+v['id']:source[f'tile.techguns.military_crate.{v["metadata"]}.name'] for v in crate_definition()['variants'] for kind in ('block','item')}
