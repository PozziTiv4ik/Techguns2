"""Original metal panels, reinforced concrete and free-standing metal ladders."""
import copy
import json
import re
from legacy_items import LEGACY
from legacy_repair import RESOURCES
from legacy_models import strip_comments

FAMILIES = {'metalpanel': 'TGMetalPanelType', 'concrete': 'EnumConcreteType', 'ladder0': 'EnumLadderType'}
RECIPES = ('metalpanel_0', 'metalpanel_0_alt', 'concrete_0', 'ladder0_8', 'ladder0_8_alt')
PENDING_RECIPES = ('metalpanel_4', 'metalpanel_6', 'concrete_1', 'concrete_3')


def building_definitions():
    result = []
    for family, enum in FAMILIES.items():
        source = strip_comments((LEGACY/f'java/techguns/blocks/{enum}.java').read_text(encoding='utf-8'))
        names = re.search(r'implements IStringSerializable\s*\{(.*?);', source, re.S)[1]
        for index, name in enumerate(names.replace('\n', '').split(',')):
            name = name.strip().lower()
            identifier = ('metalpanel_' if family == 'metalpanel' else 'ladder_' if family == 'ladder0' else '') + name
            result.append({'id': identifier, 'family': family, 'index': index,
                           'metadata': index + (8 if family == 'ladder0' else 0),
                           'model': 'ladder_' + name if family == 'ladder0' else name,
                           'hardness': 6 if family == 'ladder0' else 8})
    return result


def building_id(family, metadata):
    index = metadata & 3 if family == 'ladder0' else metadata
    return next(v['id'] for v in building_definitions() if v['family'] == family and v['index'] == index)


def ladder_inventory_model(base):
    """Bake the original NORTH item state's y=180 rotation before display transforms."""
    model = copy.deepcopy(base)
    opposite = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'up', 'down': 'down'}
    for element in model['elements']:
        low, high = element['from'], element['to']
        element['from'] = [16-high[0], low[1], 16-high[2]]
        element['to'] = [16-low[0], high[1], 16-low[2]]
        faces = {}
        for side, face in element['faces'].items():
            if side in ('up', 'down'): face['rotation'] = (face.get('rotation', 0)+180) % 360
            faces[opposite[side]] = face
        element['faces'] = faces
    return model


def generate_building_content():
    files = {}; assets = LEGACY/'resources/assets/techguns'; variants = building_definitions(); textures = set()
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode('utf-8')
    base = json.loads((assets/'models/block/ladder_base.json').read_text(encoding='utf-8'))
    base['parent'] = 'minecraft:block/block'
    data(RESOURCES+'assets/techguns/models/block/ladder_base.json', base)
    inventory = ladder_inventory_model(base)
    data(RESOURCES+'assets/techguns/models/item/ladder_base.json', inventory)
    for v in variants:
        name = v['id']; model = json.loads((assets/f'models/block/{v["model"]}.json').read_text(encoding='utf-8'))
        if model['parent'].startswith('block/'): model['parent'] = 'minecraft:'+model['parent']
        model['textures'] = {k: t.replace(':blocks/', ':block/') for k, t in model['textures'].items()}
        textures.update(t.split(':block/')[1] for t in model['textures'].values())
        data(RESOURCES+f'assets/techguns/models/block/{name}.json', model)
        item = copy.deepcopy(model); item['parent'] = item['parent'].replace('techguns:block/', 'techguns:item/')
        item['textures'] = {k: t.replace('techguns:block/', 'techguns:item/') for k, t in item['textures'].items()}
        data(RESOURCES+f'assets/techguns/models/item/{name}.json', item)
        data(RESOURCES+f'assets/techguns/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': 'techguns:item/'+name}})
        states = {'': {'model': 'techguns:block/'+name}}
        if v['family'] == 'ladder0':
            states = {'facing='+side: {'model': 'techguns:block/'+name, 'y': angle}
                      for side, angle in [('north', 180), ('east', 270), ('south', 0), ('west', 90)]}
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json', {'variants': states})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
             'entries': [{'type': 'minecraft:item', 'name': 'techguns:'+name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for texture in textures:
        for atlas in ('block', 'item'):
            files[RESOURCES+f'assets/techguns/textures/{atlas}/{texture}.png'] = (assets/f'textures/blocks/{texture}.png').read_bytes()
    data(RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': ['techguns:'+v['id'] for v in variants]})
    data(RESOURCES+'data/minecraft/tags/block/climbable.json', {'replace': False, 'values': ['techguns:'+v['id'] for v in variants if v['family'] == 'ladder0']})
    # The legacy wildcard accepted every concrete dye metadata, never concrete powder.
    colors = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
    data(RESOURCES+'data/techguns/tags/item/legacy_concrete.json', {'replace': False, 'values': ['minecraft:'+c+'_concrete' for c in colors]})
    data('content/building-blocks.json', {'source': 'legacy/1.12.2/src/main/java/techguns/TGBlocks.java', 'variants': variants,
         'recipes': list(RECIPES), 'pending_stair_return_recipes': list(PENDING_RECIPES),
         'ladder': {'thickness': 0.125, 'default_facing': 'north', 'support_required': False, 'vertical_precedence': ['below', 'above', 'opposite_player'],
                    'world_metadata_facing': ['south','west','north','east'], 'waterlogging': False},
         'ctm_integration': 'pending; source base pixels retained', 'client_acceptance': 'pending'})
    entries = ',\n'.join(f'        new Variant("{v["id"]}", "{v["family"]}", {v["index"]}, {v["metadata"]}, {v["hardness"]})' for v in variants)
    files['core/src/main/java/techguns/core/BuildingBlocks.java'] = ('''package techguns.core;
import java.util.List;
import java.util.Optional;
/** Generated original building families and metadata order. */
public final class BuildingBlocks {
    public record Variant(String id, String family, int index, int metadata, int hardness) {
        public boolean ladder() { return family.equals("ladder0"); }
        public String camoKey() { return ladder() ? "techguns.ladder0.camoname."+index : "block.techguns."+id; }
    }
    public static final List<Variant> ALL=List.of(
'''+entries+'''
    );
    public static final List<CamoPalette> PALETTES=List.of("metalpanel","concrete","ladder0").stream()
            .map(f->new CamoPalette(f,ALL.stream().filter(v->v.family().equals(f)).map(v->"techguns:"+v.id()).toList())).toList();
    public static Optional<CamoPalette> palette(String item) { return PALETTES.stream().filter(p->p.index(item)>=0).findFirst(); }
    public static Optional<Variant> variant(String item) { return ALL.stream().filter(v->item.equals("techguns:"+v.id())).findFirst(); }
    private BuildingBlocks() {}
}
''').encode('utf-8')
    return files


def building_translations(lang):
    source = dict(line.split('=', 1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    result = {kind+'.techguns.'+v['id']: source[f'tile.techguns.{v["family"]}.{v["metadata"]}.name'] for v in building_definitions() for kind in ('block','item')}
    for i in range(4):
        key = 'techguns.ladder0.camoname.'+str(i); result[key] = source[key]
    return result
