"""Original camouflage nets: enum order, shape table and Forge multipart models."""
import copy
import json
import re
from fractions import Fraction
from legacy_items import LEGACY
from legacy_models import strip_comments
from legacy_repair import RESOURCES

FAMILIES = ('camonet', 'camonet_top')
RECIPES = ('camonet_0', 'camonet_top_0')
ASSETS = LEGACY / 'resources/assets/techguns'


def net_definitions():
    source = strip_comments((LEGACY / 'java/techguns/blocks/EnumCamoNetType.java').read_text(encoding='utf-8'))
    names = re.search(r'implements IStringSerializable\s*\{(.*?);', source, re.S)[1]
    colors = [name.strip().lower() for name in names.split(',')]
    if colors != ['wood', 'desert', 'snow']: raise ValueError('Review changed camouflage enum')
    return [{'id': family + '_' + color, 'family': family, 'color': color, 'metadata': index}
            for family in FAMILIES for index, color in enumerate(colors)]


def net_id(family, metadata):
    return next(v['id'] for v in net_definitions() if v['family'] == family and v['metadata'] == metadata)


def canopy_boxes():
    source = strip_comments((LEGACY / 'java/techguns/blocks/BlockTGCamoNetTop.java').read_text(encoding='utf-8'))
    height = re.search(r'float height\s*=\s*([^;]+);', source)[1]
    table = re.search(r'bounding_boxes\s*=\s*\{(.*?)\};', source, re.S)[1]
    boxes = []
    for entry in re.findall(r'new AxisAlignedBB\(([^)]+)\)', table):
        coords = [Fraction(value.strip().replace('height', height).replace('d', '').replace('f', '')) * 16
                  for value in entry.split(',')]
        if len(coords) != 6 or any(v.denominator != 1 for v in coords): raise ValueError('Review changed canopy coordinates')
        boxes.append([int(v) for v in coords])
    if len(boxes) != 16: raise ValueError('Expected all sixteen canopy shapes')
    return boxes


def generate_camonet_content():
    files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    def read(path): return json.loads((ASSETS / path).read_text(encoding='utf-8'))
    def model(source, target, textures, atlas):
        value = read(f'models/block/{source}.json')
        value['textures'] = {**value.get('textures', {}), **textures}
        # 26.2 derives render layers from sprite alpha; the old render_type hint is obsolete.
        if 'parent' in value:
            if value['parent'] != 'block/block': raise ValueError('Review unexpected net model parent')
            value['parent'] = 'minecraft:block/block'
        for key, texture in value['textures'].items():
            if texture.startswith('techguns:blocks/'):
                name = texture.split('/')[-1]
                value['textures'][key] = f'techguns:{atlas}/{name}'
                files[RESOURCES + f'assets/techguns/textures/{atlas}/{name}.png'] = (ASSETS / f'textures/blocks/{name}.png').read_bytes()
            elif texture.startswith('blocks/planks_'):
                value['textures'][key] = 'minecraft:block/' + texture.removeprefix('blocks/planks_') + '_planks'
            elif not texture.startswith('#'): raise ValueError('Review unexpected net texture: ' + texture)
        data(RESOURCES + f'assets/techguns/models/{atlas}/{target}.json', value)

    definitions = net_definitions()
    for variant in definitions:
        name, family, color = (variant[key] for key in ('id', 'family', 'color'))
        source = read(f'blockstates/{family}.json')['variants']
        textures = source['type'][color]['textures']
        multipart = []
        for connection, entry in source['connection'].items():
            letters = '' if connection == 'none' else connection
            when = {side: str(side[0] in letters).lower() for side in ('north', 'east', 'south', 'west')}
            parts = entry['submodel']
            parts = [{'model': parts}] if isinstance(parts, str) else list(parts.values())
            for part in parts:
                part = copy.deepcopy(part)
                original = part['model'].removeprefix('techguns:')
                target = original + '_' + color
                model(original, target, textures, 'block')
                part['model'] = 'techguns:block/' + target
                if 'y' in part: part['y'] %= 360
                # A list inside apply means random alternatives, not simultaneous submodels.
                multipart.append({'when': when, 'apply': part})
        data(RESOURCES + f'assets/techguns/blockstates/{name}.json', {'multipart': multipart})
        inventory = read(f'blockstates/{family}_inventory.json')
        model(inventory['defaults']['model'].removeprefix('techguns:'), name,
              inventory['variants']['type'][color]['textures'], 'item')
        data(RESOURCES + f'assets/techguns/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': 'techguns:item/' + name}})
        data(RESOURCES + f'data/techguns/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
             'entries': [{'type': 'minecraft:item', 'name': 'techguns:' + name}]}]})

    # Forge 1.12 registers new ItemStack(Blocks.DIRT), metadata 0, not wildcard dirt.
    data(RESOURCES + 'data/techguns/tags/item/legacy_dirt.json', {'replace': False, 'values': ['minecraft:dirt']})
    boxes = canopy_boxes()
    data('content/camouflage-nets.json', {
        'source': 'legacy/1.12.2/src/main/java/techguns/blocks/', 'variants': definitions,
        'hardness': 2, 'blast_resistance': 2, 'sound': 'wool', 'legacy_render_layer': 'cutout',
        'modern_rendering': 'material and sprite alpha; original PNG bytes retained',
        'connection_bits': {'north': 8, 'east': 4, 'south': 2, 'west': 1},
        'connections': 'same family, any camouflage; no attachment requirement',
        'vertical_collision': '2/16 center pole plus separate cardinal arms; outline is enclosing box',
        'canopy_boxes_sixteenths': boxes,
        'pane_connection': 'glass panes and iron bars extend to vertical nets; nets do not extend back',
        'recipes': list(RECIPES), 'dirt_ingredient': 'techguns:legacy_dirt (minecraft:dirt only by default)',
        'minecraft_reference': {'version': '1.12.2', 'client_sha1': '0f275bc1547d01fa5f56ba34bdc87d981ee12daf',
                                'forge': '1.12.2-14.23.5.2807', 'details': 'Block collision/strength, BlockPane face shape, OreDictionary dirt'},
        'pending': ['Client visual acceptance', 'SurvivorHideout placement and incendiary magazine/firing dependencies', 'Optional Chisel integration']})
    variants_java = ',\n'.join(f'        new Variant("{v["id"]}", "{v["family"]}", {v["metadata"]})' for v in definitions)
    boxes_java = ',\n'.join('        new Box(' + ', '.join(map(str, box)) + ')' for box in boxes)
    files['core/src/main/java/techguns/core/CamouflageNets.java'] = ('''package techguns.core;

import java.util.List;
import java.util.Optional;

/** Generated original enum order and BlockTGCamoNetTop bounds, in sixteenths. */
public final class CamouflageNets {
    public record Variant(String id, String family, int metadata) {
        public boolean canopy() { return family.equals("camonet_top"); }
    }
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {}
    public static final List<Variant> ALL = List.of(
''' + variants_java + '''
    );
    public static final List<Box> CANOPY_BOXES = List.of(
''' + boxes_java + '''
    );
    public static final List<CamoPalette> PALETTES = List.of("camonet", "camonet_top").stream()
            .map(f -> new CamoPalette(f, ALL.stream().filter(v -> v.family().equals(f)).map(v -> "techguns:" + v.id()).toList())).toList();
    public static Optional<CamoPalette> palette(String item) { return PALETTES.stream().filter(p -> p.index(item) >= 0).findFirst(); }
    private CamouflageNets() {}
}
''').encode('utf-8')
    return files


def camonet_translations(lang):
    source = dict(line.split('=', 1) for line in (ASSETS / f'lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {kind + '.techguns.' + v['id']: source[f'tile.techguns.{v["family"]}.{v["metadata"]}.name']
            for v in net_definitions() for kind in ('block', 'item')}
