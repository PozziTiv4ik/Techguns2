"""Original Reaction Chamber recipe table and multiblock assets, without legacy Java compilation."""
from pathlib import Path
import json
import re
from legacy_items import ORE_TAGS, arguments, parse_stack
from legacy_models import strip_comments

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT/'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'
PARTS = ('reactionchamber_housing', 'reactionchamber_glass', 'reactionchamber_controller')


def reaction_recipes():
    source = strip_comments((LEGACY/'java/techguns/TGMachineRecipes.java').read_text())
    result = []
    for call in re.findall(r'ReactionChamberRecipe\.addRecipe\(([^;]+)\);', source):
        # The shared Java argument parser handles parentheses; protect the output array's commas too.
        array = re.search(r'new ItemStack\[\]\{([^}]+)\}', call)
        outputs = [parse_stack(value) for value in arguments(array[1])]
        fields = arguments(call[:array.start()]+'OUTPUTS'+call[array.end():])
        key, item, focus, fluid, _, cycles, completion, intensity, margin, level, consumption, instability, risk, power = fields
        inner = re.fullmatch(r'new ItemStackOreDict\((.*)\)', item)[1]
        if inner.startswith('"'): ingredient = '#'+ORE_TAGS[arguments(inner)[0].strip('"').upper()][0]
        elif 'TGBlocks.TG_ORE' in inner: ingredient = 'techguns:ore_titanium'
        else:
            stack = parse_stack(inner)
            if stack['count'] != 1: raise ValueError('Reaction chamber consumes exactly one item')
            ingredient = stack['id']
        fluid = {'WATER':'minecraft:water', 'LAVA':'minecraft:lava', 'ACID':'techguns:creeper_acid',
                 'LIQUID_REDSTONE':'redstone', 'LIQUID_ENDER':'ender'}[fluid.split('.')[1]]
        result.append({'id':key.strip('"').lower().removeprefix('rc_'), 'input':ingredient,
                       'focus':parse_stack(focus)['id'], 'fluid':fluid, 'results':outputs,
                       'cycles':int(cycles), 'required_completion':int(completion), 'intensity':int(intensity),
                       'margin':int(margin), 'liquid_level':int(level), 'fluid_consumption':int(consumption),
                       'instability':float(instability.removesuffix('f')), 'risk':risk.split('.')[1].lower(), 'energy_per_check':int(power)})
    return result


def generate_reaction_content():
    files = {}
    def output(path, value): files[RESOURCES+path] = value.encode('utf-8') if isinstance(value, str) else value
    def data(path, value): output(path, json.dumps(value, ensure_ascii=False, indent=2)+'\n')
    for recipe in reaction_recipes():
        data('data/techguns/recipe/reaction_chamber/'+recipe['id']+'.json', {'type':'techguns:reaction_chamber', **{k:v for k,v in recipe.items() if k!='id'}})
    assets = LEGACY/'resources/assets/techguns'
    for part in PARTS:
        model = json.loads((assets/f'models/block/{part}.json').read_text())
        model['parent'] = 'minecraft:'+model['parent']
        model['textures'] = {k:v.replace('techguns:blocks/', 'techguns:block/') for k,v in model['textures'].items()}
        data(f'assets/techguns/models/block/{part}.json', model)
        data(f'assets/techguns/models/item/{part}.json', {'parent':f'techguns:block/{part}'})
        data(f'assets/techguns/items/{part}.json', {'model':{'type':'minecraft:model','model':f'techguns:item/{part}'}})
        output(f'assets/techguns/textures/block/{part}.png', (assets/f'textures/blocks/{part}.png').read_bytes())
        variants = {}
        for facing, rotation in [('south',0), ('west',90), ('north',180), ('east',270)]:
            variants[f'facing={facing},formed=false'] = {'model':f'techguns:block/{part}'}
            variants[f'facing={facing},formed=true'] = {'model':'techguns:block/'+('reactionchamber' if part.endswith('controller') else 'reactionchamber_empty'), 'y':rotation}
        data(f'assets/techguns/blockstates/{part}.json', {'variants':variants})
        data(f'data/techguns/loot_table/blocks/{part}.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:'+part}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    data('assets/techguns/models/block/reactionchamber_empty.json', {'parent':'minecraft:block/block','textures':{'particle':'techguns:block/reactionchamber_housing'}})
    data('assets/techguns/models/block/reactionchamber.json', {'loader':'neoforge:obj','model':'techguns:models/block/reactionchamber.obj',
         'flip_v':True,'automatic_culling':False,'textures':{'particle':'techguns:block/reactionchamber_housing'}})
    output('assets/techguns/models/block/reactionchamber.obj', '\n'.join(line.rstrip() for line in (assets/'models/block/reactionchamber.obj').read_text().splitlines())+'\n')
    output('assets/techguns/models/block/reactionchamber.mtl', (assets/'models/block/reactionchamber.mtl').read_text().replace('techguns:blocks/','techguns:block/'))
    for texture in ('reactionchamber','reactionchamberglass'):
        output(f'assets/techguns/textures/block/{texture}.png', (assets/f'textures/blocks/{texture}.png').read_bytes())
    output('assets/techguns/textures/gui/reaction_chamber_gui.png', (assets/'textures/gui/reaction_chamber_gui.png').read_bytes())
    for tag in ('minecraft:mineable/pickaxe',):
        ns, path = tag.split(':'); data(f'data/{ns}/tags/block/{path}.json', {'replace':False,'values':['techguns:'+p for p in PARTS]})
    for group in ('redstone','ender'):
        data(f'data/techguns/tags/fluid/reaction_{group}.json', {'replace':False,'values':[]})
    files['content/reaction-chamber.json'] = (json.dumps({'source':'legacy/1.12.2/src/main/java/techguns/TGMachineRecipes.java',
        'structure':{'size':[3,4,3],'housing':17,'glass':18,'controller':1}, 'energy_capacity':1000000,'tank_capacity':10000,
        'check_interval':60,'recipes':reaction_recipes(), 'pending':['working item, liquid and beam rendering','interactive visual acceptance',
        'survival supply of cybernetic parts from Fabricator or other original sources','UV emitter has no acquisition recipe in this source snapshot']},ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    return files


def reaction_translations(lang):
    ru = lang == 'ru_ru'
    names = ('Корпус реакционной камеры','Стекло реакционной камеры','Контроллер реакционной камеры') if ru else ('Reaction Chamber Housing','Reaction Chamber Glass','Reaction Chamber Controller')
    values = {f'{kind}.techguns.{part}':name for part,name in zip(PARTS,names) for kind in ('item','block')}
    values.update({'container.techguns.reaction_chamber':'Реакционная камера' if ru else 'Reaction Chamber',
                   'gui.techguns.reaction.unformed':'Соберите камеру 3×3×4 и нажмите на контроллер спереди' if ru else 'Build the 3×3×4 chamber and use the front of its controller',
                   'gui.techguns.reaction.intensity':'Интенсивность: %s / нужна %s' if ru else 'Intensity: %s / required %s',
                   'gui.techguns.reaction.level':'Уровень: %s мБ' if ru else 'Target: %s mB',
                   'gui.techguns.reaction.progress':'Проверки: %s / %s' if ru else 'Successful checks: %s / %s',
                   'gui.techguns.reaction.power':'%s FE за проверку (раз в 60 тиков)' if ru else '%s FE per check (every 60 ticks)',
                   'gui.techguns.reaction.risk':'Риск: %s' if ru else 'Risk: %s'})
    for risk,en,rus in [('break_item','Input lost','Потеря сырья'),('explosion_low','Glass rupture','Разрыв стекла'),('explosion_medium','Explosion','Взрыв')]:
        values['gui.techguns.reaction.risk.'+risk] = rus if ru else en
    return values
