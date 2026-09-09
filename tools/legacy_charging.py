"""Original Charging Station recipes, block model and interface resources."""
from pathlib import Path
import json
import re
from legacy_models import strip_comments
from legacy_items import arguments, shared_fields

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def charging_data():
    fields = shared_fields()
    source = strip_comments((LEGACY / 'java/techguns/TGMachineRecipes.java').read_text())
    machine = strip_comments((LEGACY / 'java/techguns/tileentities/ChargingStationTileEnt.java').read_text())
    operation = strip_comments((LEGACY / 'java/techguns/tileentities/operation/MachineOperation.java').read_text())
    rates = {name: int(re.search(r'\b'+name+r'\s*=\s*(\d+)', machine)[1]) for name in ('CHARGERATE', 'ITEMCHARGERATE')}
    # The multiplication is inside MachineOperation, not ChargingStationTileEnt.update().
    assert re.search(r'return powerPerTick\s*\*\s*this.stackMultiplier\s*;', operation)
    assert 'consumePower(this.currentOperation.getPowerPerTick())' in machine
    recipes = []
    for expression in re.findall(r'ChargingStationRecipe.addRecipe\((.*?)\);', source):
        input_expr, output_expr, amount = arguments(expression)
        input_field = re.fullmatch(r'new ItemStackOreDict\(TGItems\.(\w+)\)', input_expr)[1]
        output_field = re.fullmatch(r'TGItems\.(\w+)', output_expr)[1]
        recipes.append({'input': {'ingredient':'techguns:'+fields[input_field], 'count':1},
                        'result': {'id':'techguns:'+fields[output_field], 'count':1}, 'charge_amount':int(amount)})
    return {'source':'legacy/1.12.2/src/main/java/techguns/tileentities/ChargingStationTileEnt.java',
            'energy_capacity': int(re.search(r'super\(3,\s*false,\s*(\d+)\)', machine)[1]),
            'charge_rate':rates['CHARGERATE'], 'item_charge_rate':rates['ITEMCHARGERATE'],
            'batch_power_exponent':1, 'upgrade_limit':7, 'recipes':recipes}


def generate_charging_content():
    files = {}
    def output(path, value): files[RESOURCES+path] = value.encode('utf-8') if isinstance(value,str) else value
    def data(path, value): output(path, json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    catalog = charging_data()
    for recipe in catalog['recipes']:
        data('data/techguns/recipe/charging_station/'+recipe['result']['id'].split(':')[1]+'.json', {'type':'techguns:charging_station', **recipe})
    assets = LEGACY / 'resources/assets/techguns'
    mesh = '\n'.join(line.rstrip() for line in (assets / 'models/block/charging_station_centered.obj').read_text().splitlines())+'\n'
    output('assets/techguns/models/block/charging_station.obj', mesh)
    material = (assets / 'models/block/charging_station.mtl').read_text().replace('techguns:blocks/charging_station', '#body')
    output('assets/techguns/models/block/charging_station.mtl', material)
    for atlas in ('block', 'item'):
        data('assets/techguns/models/'+atlas+'/charging_station.json', {
            'parent':'minecraft:block/block', 'loader':'neoforge:obj', 'model':'techguns:models/block/charging_station.obj',
            'flip_v':True, 'automatic_culling':False,
            'textures':{'body':'techguns:'+atlas+'/charging_station', 'particle':'techguns:'+atlas+'/charging_station'}})
        output('assets/techguns/textures/'+atlas+'/charging_station.png', (assets/'textures/blocks/charging_station.png').read_bytes())
    data('assets/techguns/items/charging_station.json', {'model':{'type':'minecraft:model','model':'techguns:item/charging_station'}})
    data('assets/techguns/blockstates/charging_station.json', {'variants':{
        'facing='+direction:{'model':'techguns:block/charging_station','y':angle}
        for direction,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    data('data/techguns/loot_table/blocks/charging_station.json', {'type':'minecraft:block','pools':[{'rolls':1,
        'entries':[{'type':'minecraft:item','name':'techguns:charging_station'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    data('data/minecraft/tags/block/mineable/pickaxe.json', {'replace':False,'values':['techguns:charging_station']})
    output('assets/techguns/textures/gui/charging_station.png', (assets/'textures/gui/charging_station_gui.png').read_bytes())
    catalog['model'] = {'source':'legacy/1.12.2/src/main/resources/assets/techguns/models/block/charging_station_centered.obj',
                        'parts':len(re.findall(r'^o ',mesh,re.M)), 'faces':len(re.findall(r'^f ',mesh,re.M)), 'flip_v':True}
    catalog['rendering'] = 'Original block OBJ and working item in ground display context; visual acceptance and custom flare particles pending'
    files['content/charging-station.json'] = (json.dumps(catalog,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    return files


def charging_translations(lang):
    ru = lang == 'ru_ru'
    name = 'Зарядная станция' if ru else 'Charging Station'
    values = {'block.techguns.charging_station':name, 'item.techguns.charging_station':name}
    for i,en,russian in [(0,'Idle','Ожидание'), (1,'Charging batteries','Зарядка батарей'), (2,'Charging item','Зарядка предмета'), (3,'Output occupied','Выход занят')]:
        values['gui.techguns.charging.state_'+str(i)] = russian if ru else en
    values['gui.techguns.charging.batch'] = 'Партия: %s' if ru else 'Batch: %s'
    return values
