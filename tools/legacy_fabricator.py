"""Source-derived Fabricator recipes, slot categories and the original multiblock geometry."""
from pathlib import Path
import json
import re
from legacy_items import ORE_TAGS, arguments, parse_stack
from legacy_models import strip_comments, convert_mesh

ROOT=Path(__file__).resolve().parents[1]
LEGACY=ROOT/'legacy/1.12.2/src/main'
RESOURCES='platforms/neoforge-26.2/src/main/resources/'
PARTS=('fabricator_housing','fabricator_glass','fabricator_controller')


def ore_ingredient(expression):
    match=re.fullmatch(r'new ItemStackOreDict\((.*)\)',expression)
    if not match: raise ValueError('Unexpected Fabricator ingredient: '+expression)
    inner=arguments(match[1])[0]
    # OreDictionary.itemMatches ignores this wrapper's stackSize. Consumption is a separate argument.
    return '#'+ORE_TAGS[inner.strip('"').upper()][0] if inner.startswith('"') else parse_stack(inner)['id']


def ingredients_and_slots():
    source=strip_comments((LEGACY/'java/techguns/tileentities/operation/FabricatorRecipe.java').read_text())
    aliases={name:ore_ingredient(value) for name,value in re.findall(r'ItemStackOreDict\s+(\w+)\s*=\s*(new ItemStackOreDict\([^;]+\));',source)}
    slots={key:[aliases[name] for name in re.findall(rf'items_{field}slot\.add\((\w+)\)',source)] for key,field in [('wire','wire'),('powder','powder'),('plate','plate')]}
    return aliases,slots


def fabricator_recipes():
    source=strip_comments((LEGACY/'java/techguns/TGMachineRecipes.java').read_text())
    tile=strip_comments((LEGACY/'java/techguns/tileentities/FabricatorTileEntMaster.java').read_text())
    aliases,_=ingredients_and_slots(); recipes=[]
    duration=int(re.search(r'this.totaltime\s*=\s*(\d+)',tile)[1]); power=int(re.search(r'powerPerTick\s*=\s*(\d+)',tile)[1])
    for call in re.findall(r'FabricatorRecipe\.addRecipe\(([^;]+)\);',source):
        a,n1,b,n2,c,n3,d,n4,out,count=arguments(call)
        recipe={'input':{'ingredient':ore_ingredient(a),'count':int(n1)}}
        for key,value,n in [('wire',b,n2),('powder',c,n3),('plate',d,n4)]:
            recipe[key]={'ingredient':aliases[value.removeprefix('FabricatorRecipe.')],'count':int(n)}
        result=parse_stack(out); result['count']=int(count)
        recipes.append({'id':result['id'].split(':')[1],**recipe,'result':result,'duration':duration,'power_per_tick':power})
    return recipes


def generate_fabricator_content():
    files={}
    def output(path,value): files[RESOURCES+path]=value.encode('utf-8') if isinstance(value,str) else value
    def data(path,value): output(path,json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    for recipe in fabricator_recipes():
        data('data/techguns/recipe/fabricator/'+recipe['id']+'.json',{'type':'techguns:fabricator',**{k:v for k,v in recipe.items() if k!='id'}})
    _,slots=ingredients_and_slots()
    for key,values in slots.items(): data(f'data/techguns/tags/item/fabricator/{key}.json',{'replace':False,'values':values})
    assets=LEGACY/'resources/assets/techguns'
    for part in PARTS:
        model=json.loads((assets/f'models/block/{part}.json').read_text()); model['parent']='minecraft:'+model['parent']
        model['textures']={k:v.replace('techguns:blocks/','techguns:block/') for k,v in model['textures'].items()}
        data(f'assets/techguns/models/block/{part}.json',model)
        data(f'assets/techguns/models/item/{part}.json',{'parent':'techguns:block/'+part})
        data(f'assets/techguns/items/{part}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+part}})
        output(f'assets/techguns/textures/block/{part}.png',(assets/f'textures/blocks/{part}.png').read_bytes())
        variants={}
        for facing,rotation in [('south',0),('west',90),('north',180),('east',270)]:
            variants[f'facing={facing},formed=false']={'model':'techguns:block/'+part}
            variants[f'facing={facing},formed=true']={'model':'techguns:block/'+('fabricator' if part.endswith('controller') else 'fabricator_empty'),'y':rotation}
        data(f'assets/techguns/blockstates/{part}.json',{'variants':variants})
        data(f'data/techguns/loot_table/blocks/{part}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:'+part}],
            'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    data('assets/techguns/models/block/fabricator_empty.json',{'parent':'minecraft:block/block','textures':{'particle':'techguns:block/fabricator_housing'}})
    source=(LEGACY/'java/techguns/client/models/machines/ModelFabricator.java').read_text()
    model,obj,material=convert_mesh(source,'ModelFabricator','fabricator','techguns:block/fabricator','-z',
        coordinate_transform=lambda p:[.5+p[0]/16,1.5-p[1]/16,.5-p[2]/16],reverse_winding=False,model_folder='block',skip_parts=('WorkingLaser1',))
    model.pop('display'); model['parent']='minecraft:block/block'
    data('assets/techguns/models/block/fabricator.json',model); output('assets/techguns/models/block/fabricator.obj',obj); output('assets/techguns/models/block/fabricator.mtl',material)
    output('assets/techguns/textures/block/fabricator.png',(assets/'textures/blocks/fabricator.png').read_bytes())
    output('assets/techguns/textures/gui/fabricator.png',(assets/'textures/gui/fabricator_gui.png').read_bytes())
    for slot in ('wires','powder','plate'):
        output(f'assets/techguns/textures/gui/emptyslot_{slot}.png',(assets/f'textures/gui/emptyslots/emptyslot_{slot}.png').read_bytes())
    data('data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['techguns:'+part for part in PARTS]})
    files['content/fabricator.json']=(json.dumps({'source':'legacy/1.12.2/src/main/java/techguns/TGMachineRecipes.java','structure':{'size':[2,2,2],'housing':3,'glass':4,'controller':1},
        'energy_capacity':100000,'duration':100,'power_per_tick':80,'batch_power_exponent':1,'upgrade_limit':7,'slot_categories':slots,'recipes':fabricator_recipes(),
        'pending':['moving tools and working item rendering','interactive visual acceptance','original mob sources of first cybernetic parts','advanced equipment using the manufactured components']},ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    return files


def fabricator_translations(lang):
    ru=lang=='ru_ru'; names=('Корпус фабрикатора','Стекло фабрикатора','Контроллер фабрикатора') if ru else ('Fabricator Housing','Fabricator Glass','Fabricator Controller')
    values={f'{kind}.techguns.{part}':name for part,name in zip(PARTS,names) for kind in ('item','block')}
    values.update({'container.techguns.fabricator':'Фабрикатор' if ru else 'Fabricator',
        'gui.techguns.fabricator.unformed':'Соберите фабрикатор 2×2×2 и нажмите на контроллер спереди' if ru else 'Build the 2×2×2 fabricator and use the front of its controller',
        'gui.techguns.fabricator.ready':'Конструкция собрана' if ru else 'Structure complete',
        'gui.techguns.fabricator.incomplete':'Конструкция недоступна' if ru else 'Structure unavailable',
        'gui.techguns.fabricator.wire':'Провода / платы' if ru else 'Wires / circuits',
        'gui.techguns.fabricator.powder':'Порошок / механические детали' if ru else 'Powder / mechanical parts',
        'gui.techguns.fabricator.plate':'Пластины / пластик' if ru else 'Plates / plastic'})
    return values
