"""Select and translate the original workbench graph required by the current arsenal.

Materials retain their original recipes. A missing machine recipe remains a missing
production step; it is never replaced by cheaper vanilla ingredients.
"""
from pathlib import Path
import json
import re
from legacy_items import ORE_TAGS, shared_items, ammo_slot_items
from legacy_machines import metal_press_data, blast_furnace_data

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
STANDARD = {'minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'forge:ore_shaped', 'forge:ore_shapeless'}


def inputs(recipe):
    return list(recipe.get('key', {}).values()) + recipe.get('ingredients', [])


def convert_recipe(legacy, shared, weapons):
    def item(value):
        identifier = value['item']
        if identifier == 'techguns:basicmachine':
            return 'techguns:' + {0: 'ammo_press', 1: 'metal_press'}[value.get('data', 0)]
        if identifier == 'techguns:simplemachine' and value.get('data') == 11: return 'techguns:blast_furnace'
        if identifier == 'minecraft:stonebrick' and value.get('data', 0) == 0: return 'minecraft:stone_bricks'
        if identifier == 'techguns:itemshared': return 'techguns:' + shared[value['data']]
        if identifier.startswith('#'): raise ValueError('A tag cannot be a recipe result')
        if value.get('data', 0) not in (0, 32767) and identifier.removeprefix('techguns:') not in weapons:
            raise ValueError(f'Unconverted metadata: {value}')
        return identifier

    def ingredient(value):
        identifier = value['item']
        if identifier.startswith('#'):
            name = identifier[1:]
            if name == 'HARDENEDGLASSORGLASS':
                return {'neoforge:ingredient_type': 'techguns:tag_fallback', 'preferred': ORE_TAGS[name][0], 'fallback': 'c:glass_blocks'}
            return '#' + ORE_TAGS[name][0]
        if identifier.removeprefix('techguns:') in weapons and value.get('data', 0) not in (0, 32767):
            raise ValueError('Weapon-upgrade metadata requires a behavior-specific conversion')
        return item(value)

    kind = legacy['type'].replace('forge:ore_', 'minecraft:crafting_')
    if kind not in STANDARD | {'techguns:copy_nbt'}: raise ValueError(f'Unsupported recipe: {kind}')
    result = {'id': item(legacy['result']), 'count': legacy['result'].get('count', 1)}
    gun = weapons.get(result['id'].removeprefix('techguns:'))
    if gun:
        rounds = gun['capacity'] - legacy['result'].get('data', 0)
        if not 0 <= rounds <= gun['capacity']: raise ValueError(f'Invalid gun recipe damage: {legacy}')
        result['components'] = {'techguns:rounds': rounds}
    recipe = {'type': 'techguns:copy_gun' if kind == 'techguns:copy_nbt' else kind,
              'category': 'misc', 'result': result}
    if 'pattern' in legacy:
        recipe['pattern'] = legacy['pattern']
        recipe['key'] = {key: ingredient(value) for key, value in legacy['key'].items()}
    else:
        recipe['ingredients'] = [ingredient(value) for value in legacy['ingredients']]
    return recipe


def plan_crafting(weapon_list):
    weapons = {gun['id']: gun for gun in weapon_list}
    shared = shared_items()
    by_name = {name: meta for meta, name in shared.items()}
    source_recipes = {path.stem: json.loads(path.read_text()) for path in sorted(
        (LEGACY / 'resources/assets/techguns/recipes').glob('*.json'), key=lambda p: p.name) if not path.name.startswith('_')}
    ammo = {gun['ammo'][field] for gun in weapon_list for field in ('item', 'empty_item', 'loose_item') if gun['ammo'][field]}
    selected = {name: recipe for name, recipe in source_recipes.items()
                if name in weapons or name.endswith('_alt') and name[:-4] in weapons}
    selected['basicmachine_0_ammo_press'] = source_recipes['basicmachine_0_ammo_press']
    for name in ('basicmachine_1_metal_press', 'basicmachine_1_metal_press_alt'): selected[name] = source_recipes[name]
    selected['simplemachine_11_blast_furnace'] = source_recipes['simplemachine_11_blast_furnace']
    # These two original recipes overlap after GenericGun.onCreated resets damage to zero.
    # Keep that migration question explicit until upgrade state has its own verified rule.
    pending = {name: selected.pop(name) for name in ('m4_infiltrator', 'm4_infiltrator_alt') if name in selected}
    wanted = {by_name[name] for name in ammo | {'stonebarrel', 'woodstock', 'machinestackupgrade'}}
    used_tags = set()
    for recipe in metal_press_data() + blast_furnace_data():
        for identifier in (recipe['first'], recipe['second'], recipe['result']['id']):
            if identifier.startswith('techguns:'): wanted.add(by_name[identifier.split(':')[1]])
            elif identifier.startswith('#'):
                key = next(key for key, (tag, _) in ORE_TAGS.items() if tag == identifier[1:])
                used_tags.add(key)
                if ORE_TAGS[key][1]: wanted.add(by_name[ORE_TAGS[key][1]])
    while True:
        for recipe in selected.values():
            for value in [recipe['result']] + inputs(recipe):
                identifier = value['item']
                if identifier == 'techguns:itemshared': wanted.add(value['data'])
                elif identifier.startswith('#'):
                    key = identifier[1:]
                    used_tags.add(key)
                    own = ORE_TAGS[key][1]
                    if own: wanted.add(by_name[own])
        additional = {name: recipe for name, recipe in source_recipes.items()
                      if name not in selected and recipe['result'].get('item') == 'techguns:itemshared'
                      and recipe['result']['data'] in wanted and recipe['type'] in STANDARD}
        if not additional: break
        selected.update(additional)

    recipes, sources = {}, []
    for name, recipe in sorted(selected.items()):
        identifier = name
        if name == 'basicmachine_0_ammo_press': identifier = 'ammo_press'
        if name.startswith('basicmachine_1_metal_press'): identifier = name.removeprefix('basicmachine_1_')
        if name == 'simplemachine_11_blast_furnace': identifier = 'blast_furnace'
        if name.startswith('itemshared_'): identifier = re.sub(r'^itemshared_\d+_', '', name)
        if identifier in recipes: raise ValueError(f'Colliding recipe ID: {identifier}')
        recipes[identifier] = convert_recipe(recipe, shared, weapons)
        sources.append({'id': 'techguns:' + identifier,
                        'source': 'legacy/1.12.2/src/main/resources/assets/techguns/recipes/' + name + '.json'})
    names = {shared[meta] for meta in wanted}
    extra_ammo = names & ammo_slot_items() - ammo
    materials = sorted(names - ammo - extra_ammo)
    tags = {ORE_TAGS[key][0]: ['techguns:' + ORE_TAGS[key][1]] for key in sorted(used_tags) if ORE_TAGS[key][1]}
    # Record direct machine-produced materials/ammunition separately from metal packing loops.
    workbench_outputs = {r['result']['id'].removeprefix('techguns:') for r in recipes.values()}
    catalog = {'shared_metadata': {str(meta): 'techguns:' + shared[meta] for meta in sorted(wanted)},
               'block_metadata': {'techguns:basicmachine@0': 'techguns:ammo_press', 'techguns:basicmachine@1': 'techguns:metal_press',
                                  'techguns:simplemachine@11': 'techguns:blast_furnace'},
               'recipes': sources, 'tags': tags,
               'requires_non_workbench_production': sorted(names - workbench_outputs),
               'metal_packing_requires_feedstock': sorted(names & {'ingotlead', 'ingotsteel'}),
               'pending_upgrade_recipes': [name + '.json' for name in pending]}
    return {'recipes': recipes, 'materials': materials, 'extra_ammo': sorted(extra_ammo), 'tags': tags, 'catalog': catalog}
