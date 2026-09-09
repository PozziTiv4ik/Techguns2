"""Translate every live ChemLab recipe call, including the original conditional integrations."""
from pathlib import Path
import json
import re
from legacy_items import ORE_TAGS, arguments, parse_stack
from legacy_models import strip_comments

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT/'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'
STANDALONE_ITEMS = {'radpills','radaway'}


def chemical_recipes():
    source=strip_comments((LEGACY/'java/techguns/TGMachineRecipes.java').read_text())
    def stack(expression):
        expression={'fuelTank':'TGItems.FUEL_TANK','fuelTankEmpty':'TGItems.FUEL_TANK_EMPTY'}.get(expression,expression)
        match=re.fullmatch(r'new ItemStack\(TGItems.(RAD_PILLS|RAD_AWAY),\s*(\d+)\)',expression)
        if match: return {'id':'techguns:'+{'RAD_PILLS':'radpills','RAD_AWAY':'radaway'}[match[1]],'count':int(match[2])}
        if expression=='new ItemStack(Items.DYE,1,2)': return {'id':'minecraft:green_dye','count':1}
        if expression=='new ItemStack(Blocks.CONCRETE,2,8)': return {'id':'minecraft:light_gray_concrete','count':2}
        return parse_stack(expression.replace('Items.SPECKLED_MELON','Items.GLISTERING_MELON_SLICE'))
    def ingredient(expression,count):
        if expression in ('null','(ItemStack)null','nullStack'):
            if count!=0: raise ValueError('Nonzero amount for empty ChemLab slot')
            return None
        expression={'stackLogWood':'"logWood"','uranium':'"oreUranium"'}.get(expression,expression)
        value='#'+ORE_TAGS[expression.strip('"').upper()][0] if expression.startswith('"') else stack(expression)['id']
        return {'ingredient':value,'count':count}
    recipes=[]
    for index,call in enumerate(re.findall(r'ChemLabRecipes\.addRecipe\(([^;]+)\);',source)):
        first,n1,second,n2,bottle,n3,fluid_in,fluid_out,result,swap,power=arguments(call)
        recipe={'id':f'{index:02d}', 'allow_swap':swap=='true','duration':100,'power_per_tick':int(power),'activation':'always'}
        for key,expression,count in [('first',first,int(n1)),('second',second,int(n2)),('bottle',bottle,int(n3))]:
            value=ingredient(expression,count)
            if value: recipe[key]=value
        if result!='null': recipe['result']=stack(result)
        for key,expression in [('fluid_input',fluid_in),('fluid_output',fluid_out)]:
            if expression=='null': continue
            fluid,amount=arguments(re.fullmatch(r'new FluidStack\((.*)\)',expression)[1])
            if fluid=='f': recipe[key]={'group':'oils' if recipe.get('result',{}).get('id')=='techguns:rawplastic' else 'fuels','amount':int(amount)}
            else:
                identifier={'WATER':'minecraft:water','LAVA':'minecraft:lava','ACID':'techguns:creeper_acid','MILK':'#c:milk'}[fluid.split('.')[1]]
                recipe[key]={'ingredient' if key=='fluid_input' else 'id':identifier,'amount':int(amount)}
        if second=='"dustCoal"': recipe['activation']='coal_dust_present'
        elif recipe.get('result',{}).get('id')=='minecraft:gunpowder': recipe['activation']='coal_dust_absent'
        if recipe.get('result',{}).get('id')=='techguns:rawplastic': recipe['activation']='oils_present' if recipe['fluid_input'].get('group') else 'oils_absent'
        if first=='"itemBioFuel"': recipe['activation']='biofuel_present'
        if recipe.get('fluid_input',{}).get('group')=='fuels': recipe['activation']='fuels_present'
        if recipe.get('result',{}).get('id') in ('techguns:tgx','techguns:fueltank','techguns:rocket_high_velocity') and recipe.get('fluid_input',{}).get('ingredient')=='minecraft:lava':
            recipe['activation']='lava_fuel_fallback'
        result_id=recipe.get('result',recipe.get('fluid_output'))['id'].split(':')[1]
        recipe['id']+='_'+result_id
        recipes.append(recipe)
    return recipes


def fluid_group_defaults():
    source=(LEGACY/'java/techguns/TGConfig.java').read_text()
    return {name:re.findall('"([^"]+)"',re.search(rf'fluidList{name}\s*=.*?new String\[\]\{{([^}}]+)\}}',source)[1]) for name in ('Oil','Fuel')}


def generate_chemical_content():
    files={}
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    for recipe in chemical_recipes():
        data(RESOURCES+'data/techguns/recipe/chem_lab/'+recipe['id']+'.json',{'type':'techguns:chem_lab',**{k:v for k,v in recipe.items() if k!='id'}})
    for tag in ('c:dusts/coal','c:fuels/bio'):
        namespace,path=tag.split(':'); data(RESOURCES+f'data/{namespace}/tags/item/{path}.json',{'replace':False,'values':[]})
    for tag in ('oils','fuels'): data(RESOURCES+f'data/techguns/tags/fluid/chemical_{tag}.json',{'replace':False,'values':[]})
    defaults=fluid_group_defaults()
    data('content/chem-lab.json',{'source':'legacy/1.12.2/src/main/java/techguns/TGMachineRecipes.java','energy_capacity':20000,
         'tank_capacities':[8000,16000],'duration':100,'batch_power_exponent':1,'recipes':chemical_recipes(),
         'fluid_groups':defaults,'pending':['model animation','special ammunition weapon behavior']})
    files['core/src/main/java/techguns/core/ChemicalDefaults.java']=('''package techguns.core;

import java.util.List;

/** Generated original fluid-name lists, including their historical spelling. */
public final class ChemicalDefaults {
'''+''.join('    public static final List<String> '+name.upper()+' = List.of('+', '.join(json.dumps(n) for n in values)+');\n' for name,values in defaults.items())+'''    private ChemicalDefaults() {}
}
''').encode('utf-8')
    return files


def chemical_translations(lang):
    values={'item.techguns.chem_lab':'Chemical Laboratory' if lang=='en_us' else 'Химическая лаборатория',
            'block.techguns.chem_lab':'Chemical Laboratory' if lang=='en_us' else 'Химическая лаборатория',
            'container.techguns.chem_lab':'Chemical Laboratory' if lang=='en_us' else 'Химическая лаборатория',
            'gui.techguns.chem.drain':'Drain: %s' if lang=='en_us' else 'Отбор: %s',
            'gui.techguns.chem.input':'Input' if lang=='en_us' else 'Вход',
            'gui.techguns.chem.output':'Output' if lang=='en_us' else 'Выход',
            'gui.techguns.chem.dump_input':'Empty input tank' if lang=='en_us' else 'Очистить входной бак',
            'gui.techguns.chem.dump_output':'Empty output tank' if lang=='en_us' else 'Очистить выходной бак',
            'gui.techguns.chem.empty':'Empty' if lang=='en_us' else 'Пусто',
            'gui.techguns.chem.amount':'%s / %s mB'}
    return values
