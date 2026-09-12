"""Grinder recipes and original models; unported inputs remain catalogued rather than registered."""
import json
import re
from pathlib import Path
from legacy_items import LEGACY, parse_stack
from legacy_models import strip_comments, numeric
from legacy_armors import ARMOR_SETS

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def args(text):
    parts=[]; start=0; depth=0; quoted=False
    for i,char in enumerate(text):
        if char=='"': quoted=not quoted
        if not quoted:
            if char in '([{': depth+=1
            elif char in ')]}': depth-=1
            elif char==',' and depth==0: parts.append(text[start:i].strip()); start=i+1
    return parts+[text[start:].strip()]


def grinder_data():
    source=strip_comments((LEGACY/'java/techguns/TGMachineRecipes.java').read_text())
    selected=set(json.loads((ROOT/'content/weapon-ports.json').read_text()))
    arrays={name:value for name,value in re.findall(r'ItemStack\[\]\s+(\w+)\s*=\s*(\{[^;]+\});',source)}
    helpers={}
    for name,parameters,body in re.findall(r'private static ItemStack\[\] (\w+)\(([^)]*)\)\s*\{(.*?)return ret;',source,re.S):
        helpers[name]=([p.strip().split()[-1] for p in parameters.split(',')],re.findall(r'ret\[i\+\+\]\s*=\s*([^;]+);',body))
    def stack(expression):
        match=re.fullmatch(r'TGItems.newStack\(GOLD_OR_ELECTRUM,\s*(\d+)\)',expression)
        if match: return {'result':{'id':'minecraft:gold_ingot','count':int(match[1])},'preferred_tag':'c:ingots/electrum'}
        result=parse_stack(expression)
        if result['id']=='minecraft:log': result['id']='minecraft:oak_log'
        return {'result':result}
    def outputs(expression):
        expression=expression.strip()
        if expression in arrays: return outputs(arrays[expression])
        if '{' in expression:
            return [value for entry in args(expression[expression.index('{')+1:expression.rindex('}')]) for value in outputs(entry)]
        match=re.fullmatch(r'(\w+)\(([^)]*)\)',expression)
        if match and match[1] in helpers:
            params,entries=helpers[match[1]]; values=dict(zip(params,args(match[2])))
            result=[]
            for entry in entries:
                for parameter,value in values.items(): entry=re.sub(r'\b'+parameter+r'\b',value,entry)
                parsed=stack(entry)
                if parsed['result']['count']>0: result.append(parsed)
            return result
        return [stack(expression)]
    recipes=[]; skipped=[]
    for kind,call in re.findall(r'GrinderRecipes\.(addRecipeChance|addRecipe)\(([^;]+)\);',source):
        values=args(call); expression=values.pop(0)
        if expression.startswith('TGuns.'):
            identifier=expression.split('.')[1]
            if identifier not in selected: skipped.append(identifier); continue
            input_id='techguns:'+identifier
        else: input_id=parse_stack(expression)['id']; identifier=input_id.split(':')[1]
        chances=None
        if kind=='addRecipeChance':
            formula=values.pop(); tokens=args(formula[formula.index('{')+1:formula.rindex('}')])
            chances=[]
            for token in tokens:
                numbers=token.split('/'); chances.append(numeric(numbers[0])/(numeric(numbers[1]) if len(numbers)>1 else 1))
        results=[output for value in values for output in outputs(value)]
        if chances is not None:
            if len(chances)!=len(results): raise ValueError('Grinder chance/output mismatch')
            for result,factor in zip(results,chances): result['factor']=factor
        recipes.append({'id':identifier,'input':input_id,'outputs':results,'random':chances is not None})
    for armor_set in ARMOR_SETS:
        for part in ('helmet','chestplate','leggings','boots'):
            recipes.append({'id':armor_set+'_'+part,'input':'techguns:'+armor_set+'_'+part,'outputs':[],'armor':True})
    machine=strip_comments((LEGACY/'java/techguns/tileentities/GrinderTileEnt.java').read_text())
    operation=strip_comments((LEGACY/'java/techguns/tileentities/operation/MachineOperation.java').read_text())
    return {'source':'legacy/1.12.2/src/main/java/techguns/TGMachineRecipes.java','recipes':recipes,
        'unported_weapon_inputs':skipped,'ported_weapons_without_source_recipe':sorted(selected-{r['id'] for r in recipes}),
        'duration':int(re.search(r'\btime\s*=\s*(\d+)',operation)[1]),'power_per_tick':int(re.search(r'POWER_PER_TICK\s*=\s*(\d+)',machine)[1]),
        'energy_capacity':int(re.search(r'super\(11,\s*false,\s*(\d+)\)',machine)[1]),'input_slot':0,'upgrade_slot':1,'output_slots':list(range(2,11))}


def generate_grinder_content():
    catalog=grinder_data(); files={}; assets=LEGACY/'resources/assets/techguns'
    def data(path,value): files[RESOURCES+path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    for recipe in catalog['recipes']:
        data('data/techguns/recipe/grinder/'+recipe['id']+'.json',{'type':'techguns:grinder',**{k:v for k,v in recipe.items() if k!='id'}})
    for atlas,model_source in [('block','models/block/grinder.json'),('item','models/item/simplemachine2_grinder_inv.json')]:
        model=json.loads((assets/model_source).read_text()); model['parent']='minecraft:block/block'; model.pop('groups',None)
        model['textures']={key:value.replace(':blocks/',':'+atlas+'/') for key,value in model['textures'].items()}
        data(f'assets/techguns/models/{atlas}/grinder.json',model)
        for value in set(model['textures'].values()):
            name=value.split('/')[-1]; files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png']=(assets/f'textures/blocks/{name}.png').read_bytes()
    data('assets/techguns/items/grinder.json',{'model':{'type':'minecraft:model','model':'techguns:item/grinder'}})
    data('assets/techguns/models/item/grinder_roll.json',{'parent':'minecraft:block/block','textures':{'roll':'techguns:item/grinder_roll','particle':'techguns:item/grinder_roll'},'elements':[
        {'from':[2,6.5,6.5],'to':[14,9.5,9.5],'faces':{face:{'texture':'#roll','uv':[14,5,2,2] if face=='north' else [2,2,14,5]} for face in ('up','south','down','north')}}]})
    data('assets/techguns/items/grinder_roll.json',{'model':{'type':'minecraft:model','model':'techguns:item/grinder_roll'}})
    data('assets/techguns/blockstates/grinder.json',{'variants':{'facing='+direction:{'model':'techguns:block/grinder','y':angle} for direction,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    data('data/techguns/loot_table/blocks/grinder.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:grinder'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    data('data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['techguns:grinder']})
    files[RESOURCES+'assets/techguns/textures/gui/grinder.png']=(assets/'textures/gui/grinder_gui.png').read_bytes()
    catalog['model_parts']={'block':19,'item':21,'animated_rollers':2}
    files['content/grinder.json']=(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n').encode()
    return files


def grinder_translations(lang):
    ru=lang=='ru_ru'; name='Измельчитель' if ru else 'Grinder'
    result={'block.techguns.grinder':name,'item.techguns.grinder':name,'item.techguns.grinder_roll':'Вал измельчителя' if ru else 'Grinder roller',
        'gui.techguns.grinder.batch':'Партия: %s' if ru else 'Batch: %s'}
    for key,en,russian in [('idle','Idle','Ожидание'),('running','Recycling','Переработка'),('blocked','Output occupied','Выход занят'),('paused','Paused','Пауза')]:
        result['gui.techguns.grinder.'+key]=russian if ru else en
    return result
