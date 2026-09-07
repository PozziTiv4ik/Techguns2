"""Source-derived Ammo Press resources. Interactive rendering is verified separately."""
from pathlib import Path
import json
import re
from legacy_models import convert_mesh, strip_comments
from legacy_items import ORE_TAGS, shared_fields, arguments

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def ammo_press_data():
    plans = strip_comments((LEGACY / 'java/techguns/tileentities/operation/AmmoPressBuildPlans.java').read_text())
    amounts = {name: int(amount) for name, amount in re.findall(r'AMMOUNT_(\w+)\s*=\s*(\d+)', plans)}
    machine = strip_comments((LEGACY / 'java/techguns/tileentities/AmmoPressTileEnt.java').read_text())
    duration = int(re.search(r'this.totaltime\s*=\s*(\d+)', machine)[1])
    power = int(re.search(r'POWER_PER_TICK\s*=\s*(\d+)', machine)[1])
    return [{'plan': index, 'metal1': '#techguns:ammo_press/metal1', 'metal2': '#techguns:ammo_press/metal2',
             'powder': '#techguns:ammo_press/powder', 'result': {'id': 'techguns:'+item, 'count': amounts[name]},
             'duration': duration, 'power_per_tick': power}
            for index, (name,item) in enumerate([('PISTOL','pistolrounds'), ('SHOTGUN','shotgunrounds'), ('RIFLE','riflerounds'), ('SNIPER','sniperrounds')])]


def metal_press_data():
    source = strip_comments((LEGACY / 'java/techguns/TGMachineRecipes.java').read_text())
    fields = shared_fields()
    machine = strip_comments((LEGACY / 'java/techguns/tileentities/MetalPressTileEnt.java').read_text())
    duration = int(re.search(r'this.totaltime\s*=\s*(\d+)', machine)[1])
    power = int(re.search(r'POWER_PER_TICK\s*=\s*(\d+)', machine)[1])
    def stack(expression):
        match = re.fullmatch(r'TGItems\.newStack\(TGItems\.(\w+),\s*(\d+)\)', expression)
        if match: return {'id': 'techguns:'+fields[match[1]], 'count': int(match[2])}
        match = re.fullmatch(r'new ItemStack\((Items|Blocks)\.(\w+)(?:,\s*(\d+))?\)', expression)
        if match: return {'id': 'minecraft:'+match[2].lower(), 'count': int(match[3] or 1)}
        raise ValueError(f'Unconverted machine stack: {expression}')
    def ingredient(expression):
        if expression.startswith('"'): return '#'+ORE_TAGS[expression.strip('"').upper()][0]
        value = stack(expression)
        if value['count'] != 1: raise ValueError('Metal Press consumes one of each input')
        return value['id']
    recipes = []
    for call in re.findall(r'MetalPressRecipes\.addRecipe\(([^;]+)\);', source):
        first, second, result, swap = arguments(call)
        if swap not in ('true', 'false'): raise ValueError('Nonliteral machine swap rule')
        recipes.append({'first': ingredient(first), 'second': ingredient(second), 'allow_swap': swap == 'true',
                        'result': stack(result), 'duration': duration, 'power_per_tick': power})
    return recipes


def generate_machine_content():
    files = {}
    def output(path, value): files[RESOURCES + path] = value.encode('utf-8') if isinstance(value, str) else value
    def data(path, value): output(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    for plan in ammo_press_data():
        data('data/techguns/recipe/ammo_press/'+plan['result']['id'].split(':')[1]+'.json', {'type': 'techguns:ammo_press', **plan})
    for recipe in metal_press_data():
        data('data/techguns/recipe/metal_press/'+recipe['result']['id'].split(':')[1]+'.json', {'type': 'techguns:metal_press', **recipe})
    metal_inputs = sorted({r[key] for r in metal_press_data() for key in ('first', 'second')})
    data('data/techguns/tags/item/metal_press/inputs.json', {'values': metal_inputs})
    data('data/techguns/tags/item/ammo_press/metal1.json', {'values': ['#c:ingots/lead', '#c:ingots/steel']})
    data('data/techguns/tags/item/ammo_press/metal2.json', {'values': ['#c:ingots/copper', '#c:ingots/iron',
        {'id': '#c:ingots/tin', 'required': False}, {'id': '#c:ingots/bronze', 'required': False}]})
    data('data/techguns/tags/item/ammo_press/powder.json', {'values': ['#c:gunpowders']})
    machines = [('ammo_press', 'ModelAmmoPress', 'ammopress', ('MetalPiece','bullet1','bullet2','bullet3')),
                ('metal_press', 'ModelMetalPress', 'metalpress', ('MetalPiece',))]
    for identifier, class_name, texture_name, skip in machines:
        source = (LEGACY / f'java/techguns/client/models/machines/{class_name}.java').read_text()
        # RenderMachine: translate(.5,1.5,.5), then Rz(180)*Ry(180); winding is preserved.
        model, obj, material = convert_mesh(source, class_name, identifier, f'techguns:block/{identifier}', '-z',
            coordinate_transform=lambda p: [.5+p[0]/16, 1.5-p[1]/16, .5-p[2]/16], reverse_winding=False,
            model_folder='block', skip_parts=skip)
        model.pop('display')
        model['parent'] = 'minecraft:block/block'
        output(f'assets/techguns/models/block/{identifier}.obj', obj)
        output(f'assets/techguns/models/block/{identifier}.mtl', material)
        data(f'assets/techguns/models/block/{identifier}.json', model)
        item_model = {**model, 'textures': {'gun': f'techguns:item/{identifier}', 'particle': f'techguns:item/{identifier}'}}
        data(f'assets/techguns/models/item/{identifier}.json', item_model)
        data(f'assets/techguns/items/{identifier}.json', {'model': {'type': 'minecraft:model', 'model': f'techguns:item/{identifier}'}})
        texture = (LEGACY / f'resources/assets/techguns/textures/blocks/{texture_name}.png').read_bytes()
        for atlas in ('block', 'item'): output(f'assets/techguns/textures/{atlas}/{identifier}.png', texture)
        output(f'assets/techguns/textures/gui/{identifier}.png', (LEGACY / f'resources/assets/techguns/textures/gui/{identifier}_gui.png').read_bytes())
        data(f'assets/techguns/blockstates/{identifier}.json', {'variants': {
            'facing='+direction: {'model': f'techguns:block/{identifier}', 'y': angle}
            for direction, angle in [('north',0), ('east',90), ('south',180), ('west',270)]}})
        data(f'data/techguns/loot_table/blocks/{identifier}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': f'techguns:{identifier}'}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    data('data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': ['techguns:'+m[0] for m in machines]})
    files['content/ammo-press.json'] = (json.dumps({'source': 'legacy/1.12.2/src/main/java/techguns/tileentities/AmmoPressTileEnt.java',
        'energy_capacity': 20000, 'input_counts': [1,2,1], 'upgrade_limit': 7, 'plans': ammo_press_data(),
        'rendering': 'original geometry at rest; animation pending'}, ensure_ascii=False, indent=2)+'\n').encode()
    files['content/metal-press.json'] = (json.dumps({'source': 'legacy/1.12.2/src/main/java/techguns/TGMachineRecipes.java',
        'energy_capacity': 20000, 'input_counts': [1,1], 'upgrade_limit': 7, 'batch_power_exponent': 2,
        'recipes': metal_press_data(), 'rendering': 'original geometry at rest; animation pending'}, ensure_ascii=False, indent=2)+'\n').encode()
    return files


def machine_translations(lang):
    if lang == 'ru_ru':
        name, power, redstone, security = 'Пресс для патронов', '%s / 20000 FE', 'Редстоун: %s', 'Доступ: %s'
        values = {'ignore':'без сигнала', 'high':'высокий', 'low':'низкий', 'public':'всем', 'private':'владельцу'}
    else:
        name, power, redstone, security = 'Ammo Press', '%s / 20000 FE', 'Redstone: %s', 'Access: %s'
        values = {'ignore':'ignored', 'high':'high', 'low':'low', 'public':'public', 'private':'owner'}
    metal = 'Металлический пресс' if lang == 'ru_ru' else 'Metal Press'
    return {'block.techguns.ammo_press': name, 'item.techguns.ammo_press': name,
            'block.techguns.metal_press': metal, 'item.techguns.metal_press': metal,
            'gui.techguns.machine.autosplit': 'Авторазделение' if lang == 'ru_ru' else 'Auto split',
            'gui.techguns.machine.enabled': 'Включено' if lang == 'ru_ru' else 'Enabled',
            'gui.techguns.machine.disabled': 'Выключено' if lang == 'ru_ru' else 'Disabled',
            'gui.techguns.machine.energy_rate': 'За рабочий такт: %s FE' if lang == 'ru_ru' else 'Per working tick: %s FE',
            'gui.techguns.machine.energy': power, 'gui.techguns.machine.redstone': redstone,
            'gui.techguns.machine.security': security, **{'gui.techguns.machine.'+key: value for key,value in values.items()}}
