"""Original ore metadata, generation rules and the complete furnace recipe table."""
from pathlib import Path
import json
import re
from legacy_items import arguments, parse_stack
from legacy_models import strip_comments

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def ore_data():
    enum = strip_comments((LEGACY / 'java/techguns/blocks/EnumOreType.java').read_text())
    source = strip_comments((LEGACY / 'java/techguns/world/OreGenerator.java').read_text())
    config = strip_comments((LEGACY / 'java/techguns/TGConfig.java').read_text())
    metadata = {}
    for meta, (name, hardness, mining, light) in enumerate(re.findall(r'ORE_(\w+)\(([\d.]+)f,(\d)(?:,(\d))?\)', enum)):
        metadata[name] = {'id': 'ore_'+name.lower(), 'legacy_metadata': meta, 'hardness': float(hardness),
                          'mining_level': int(mining), 'light': int(light or 0)}
    ores = []
    for call in re.findall(r'this\.addOreSpawn\(([^;]+)\);', source):
        values = arguments(call)
        name = re.search(r'EnumOreType\.ORE_(\w+)', values[0])[1]
        size = re.fullmatch(r'(\d+)\+random.nextInt\((\d+)\)', values[7])
        if values[4:7] != ['chunkZ*16', '16', '16'] or size is None: raise ValueError('Ore generation shape changed')
        flag = 'doOreGen'+name.title()
        default = re.search(rf'{flag}\s*=\s*config.getBoolean\("{flag}",\s*WORLDGEN,\s*(true|false)', config)[1]
        ores.append({**metadata[name], 'config': flag, 'enabled_by_default': default == 'true',
                     'vein_size_min': int(size[1]), 'vein_size_max': int(size[1])+int(size[2])-1,
                     'attempts': int(values[8]), 'min_y': int(values[9]), 'max_y_exclusive': int(values[10])})
    if set(metadata) != {o['id'].removeprefix('ore_').upper() for o in ores}: raise ValueError('Missing ore generation rule')
    return ores


def smelting_data():
    source = strip_comments((LEGACY / 'java/techguns/TGMachineRecipes.java').read_text())
    recipes = []
    for call in re.findall(r'GameRegistry\.addSmelting\(([^;]+)\);', source):
        first, result, experience = arguments(call)
        ore = re.fullmatch(r'TGBlocks.TG_ORE.getStackFor\(EnumOreType.ORE_(\w+)\)', first)
        ingredient = {'id': 'techguns:ore_'+ore[1].lower(), 'count': 1} if ore else parse_stack(first)
        if ingredient['count'] != 1: raise ValueError('Counted furnace input needs a distinct implementation')
        recipes.append({'id': ingredient['id'].split(':')[1], 'ingredient': ingredient['id'], 'result': parse_stack(result),
                        'experience': float(experience.removesuffix('f')), 'cookingtime': 200})
    return recipes


def generate_ore_content():
    files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode('utf-8')
    def resource(path, value): data(RESOURCES+path, value)
    ores = ore_data()
    all_ids = ['techguns:'+o['id'] for o in ores]
    tags = {}
    def tag(kind, name, values): tags[f'data/{name.split(":")[0]}/tags/{kind}/{name.split(":")[1]}.json'] = values
    for ore in ores:
        identifier = ore['id']
        source = json.loads((LEGACY / f'resources/assets/techguns/models/block/{identifier}.json').read_text())
        for atlas in ('block', 'item'):
            resource(f'assets/techguns/models/{atlas}/{identifier}.json', {**source, 'parent': 'minecraft:block/cube_all',
                     'textures': {'all': f'techguns:{atlas}/{identifier}'}})
            files[RESOURCES+f'assets/techguns/textures/{atlas}/{identifier}.png'] = (LEGACY / f'resources/assets/techguns/textures/blocks/{identifier}.png').read_bytes()
        resource(f'assets/techguns/items/{identifier}.json', {'model': {'type': 'minecraft:model', 'model': f'techguns:item/{identifier}'}})
        resource(f'assets/techguns/blockstates/{identifier}.json', {'variants': {'': {'model': f'techguns:block/{identifier}'}}})
        resource(f'data/techguns/loot_table/blocks/{identifier}.json', {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'techguns:'+identifier}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        # Original titanium block is ilmenite; oreTitanium refers to the processed shared item.
        ore_tags = ['ores/ilmenite', 'ores/illmenite', 'ores/titanium_iron'] if identifier == 'ore_titanium' else ['ores/'+identifier.removeprefix('ore_')]
        for kind in ('block','item'):
            for name in ore_tags: tag(kind, 'c:'+name, ['techguns:'+identifier])
    tag('block', 'c:ores', all_ids)
    tag('item', 'c:ores', all_ids + ['techguns:oretitanium'])
    tag('item', 'c:ores/titanium', ['techguns:oretitanium'])
    for level, tool in ((1,'stone'),(2,'iron'),(3,'diamond')):
        tag('block', 'minecraft:needs_'+tool+'_tool', ['techguns:'+o['id'] for o in ores if o['mining_level'] == level])
    tag('block', 'minecraft:mineable/pickaxe', all_ids)
    for path, values in tags.items(): resource(path, {'replace': False, 'values': values})
    resource('data/techguns/worldgen/configured_feature/ores.json', {'type': 'techguns:ores', 'config': {}})
    # Exactly one call per chunk: the feature chooses one size per ore, then runs its attempts in source order.
    resource('data/techguns/worldgen/placed_feature/ores.json', {'feature': 'techguns:ores', 'placement': []})
    resource('data/techguns/neoforge/biome_modifier/ores.json', {'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld',
             'features': 'techguns:ores', 'step': 'underground_ores'})
    for recipe in smelting_data():
        resource('data/techguns/recipe/smelting/'+recipe['id']+'.json', {'type': 'minecraft:smelting', 'category': 'misc',
                 **{key:value for key,value in recipe.items() if key != 'id'}})
    data('content/ores.json', {'source': 'legacy/1.12.2/src/main/java/techguns/world/OreGenerator.java',
         'dimension': 'minecraft:overworld', 'size_selection': 'once_per_ore_per_chunk', 'ores': ores,
         'block_metadata': {f'techguns:basicore@{o["legacy_metadata"]}': 'techguns:'+o['id'] for o in ores},
         'vein_geometry': 'Minecraft 26.2 OreFeature; original attempt counts, absolute origin heights and size ranges',
         'replacement_tag': 'minecraft:stone_ore_replaceables'})
    data('content/smelting.json', {'source': 'legacy/1.12.2/src/main/java/techguns/TGMachineRecipes.java', 'recipes': smelting_data(),
         'pending_ore_processing': {'techguns:ore_titanium': 'Reaction Chamber RC_TITANIUM with acid and heat-ray focus',
                                    'techguns:ore_uranium': 'Chemical Laboratory with acid produces yellowcake'}})
    definitions = ',\n'.join('            new OreDefinition('+', '.join([
        json.dumps(o['id']), json.dumps(o['config']), str(o['enabled_by_default']).lower(), str(o['legacy_metadata']),
        str(o['hardness'])+'f', str(o['mining_level']), str(o['light']), str(o['vein_size_min']), str(o['vein_size_max']),
        str(o['attempts']), str(o['min_y']), str(o['max_y_exclusive'])])+')' for o in ores)
    files['core/src/main/java/techguns/core/Ores.java'] = ('''package techguns.core;

import java.util.List;

/** Generated from the original ore enum, configuration defaults and OreGenerator. */
public final class Ores {
    public static final List<OreDefinition> ALL = List.of(
'''+definitions+''');
    private Ores() {}
}
''').encode('utf-8')
    return files


def ore_translations(lang):
    lines = (LEGACY / f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines()
    old = dict(line.split('=',1) for line in lines if '=' in line and not line.startswith('#'))
    return {f'{kind}.techguns.{ore["id"]}': old[f'tile.techguns.basicore.{ore["legacy_metadata"]}.name']
            for ore in ore_data() for kind in ('block','item')}
