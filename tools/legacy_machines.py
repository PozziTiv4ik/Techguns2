"""Source-derived Ammo Press resources. Interactive rendering is verified separately."""
from pathlib import Path
import json
import re
from legacy_models import convert_mesh, strip_comments

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


def generate_machine_content():
    files = {}
    def output(path, value): files[RESOURCES + path] = value.encode('utf-8') if isinstance(value, str) else value
    def data(path, value): output(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    for plan in ammo_press_data():
        data('data/techguns/recipe/ammo_press/'+plan['result']['id'].split(':')[1]+'.json', {'type': 'techguns:ammo_press', **plan})
    data('data/techguns/tags/item/ammo_press/metal1.json', {'values': ['#c:ingots/lead', '#c:ingots/steel']})
    data('data/techguns/tags/item/ammo_press/metal2.json', {'values': ['#c:ingots/copper', '#c:ingots/iron',
        {'id': '#c:ingots/tin', 'required': False}, {'id': '#c:ingots/bronze', 'required': False}]})
    data('data/techguns/tags/item/ammo_press/powder.json', {'values': ['#c:gunpowders']})
    source = (LEGACY / 'java/techguns/client/models/machines/ModelAmmoPress.java').read_text()
    # RenderMachine: translate(.5,1.5,.5), then Rz(180)*Ry(180). This proper rotation
    # flips Y and Z, so winding remains the original ModelBox winding.
    model, obj, material = convert_mesh(source, 'ModelAmmoPress', 'ammo_press', 'techguns:block/ammo_press', '-z',
        coordinate_transform=lambda p: [.5+p[0]/16, 1.5-p[1]/16, .5-p[2]/16], reverse_winding=False,
        model_folder='block', skip_parts=('MetalPiece','bullet1','bullet2','bullet3'))
    model.pop('display')
    model['parent'] = 'minecraft:block/block'
    output('assets/techguns/models/block/ammo_press.obj', obj)
    output('assets/techguns/models/block/ammo_press.mtl', material)
    data('assets/techguns/models/block/ammo_press.json', model)
    item_model = {**model, 'textures': {'gun': 'techguns:item/ammo_press', 'particle': 'techguns:item/ammo_press'}}
    data('assets/techguns/models/item/ammo_press.json', item_model)
    data('assets/techguns/items/ammo_press.json', {'model': {'type': 'minecraft:model', 'model': 'techguns:item/ammo_press'}})
    texture = (LEGACY / 'resources/assets/techguns/textures/blocks/ammopress.png').read_bytes()
    for atlas in ('block', 'item'): output(f'assets/techguns/textures/{atlas}/ammo_press.png', texture)
    output('assets/techguns/textures/gui/ammo_press.png', (LEGACY / 'resources/assets/techguns/textures/gui/ammo_press_gui.png').read_bytes())
    data('assets/techguns/blockstates/ammo_press.json', {'variants': {
        'facing='+direction: {'model': 'techguns:block/ammo_press', 'y': angle}
        for direction, angle in [('north',0), ('east',90), ('south',180), ('west',270)]}})
    data('data/techguns/loot_table/blocks/ammo_press.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': 'techguns:ammo_press'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    data('data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': ['techguns:ammo_press']})
    files['content/ammo-press.json'] = (json.dumps({'source': 'legacy/1.12.2/src/main/java/techguns/tileentities/AmmoPressTileEnt.java',
        'energy_capacity': 20000, 'input_counts': [1,2,1], 'upgrade_limit': 7, 'plans': ammo_press_data(),
        'rendering': 'original geometry at rest; animation pending'}, ensure_ascii=False, indent=2)+'\n').encode()
    return files


def machine_translations(lang):
    if lang == 'ru_ru':
        name, power, redstone, security = 'Пресс для патронов', '%s / 20000 FE', 'Редстоун: %s', 'Доступ: %s'
        values = {'ignore':'без сигнала', 'high':'высокий', 'low':'низкий', 'public':'всем', 'private':'владельцу'}
    else:
        name, power, redstone, security = 'Ammo Press', '%s / 20000 FE', 'Redstone: %s', 'Access: %s'
        values = {'ignore':'ignored', 'high':'high', 'low':'low', 'public':'public', 'private':'owner'}
    return {'block.techguns.ammo_press': name, 'item.techguns.ammo_press': name,
            'gui.techguns.machine.energy': power, 'gui.techguns.machine.redstone': redstone,
            'gui.techguns.machine.security': security, **{'gui.techguns.machine.'+key: value for key,value in values.items()}}
