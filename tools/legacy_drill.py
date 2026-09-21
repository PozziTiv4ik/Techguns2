"""Original Ore Drill blocks, heads and weighted resource entries. No replacement production recipes."""
import json
import re
from legacy_models import strip_comments
from legacy_items import arguments, parse_stack
from legacy_npcs import LEGACY, RESOURCES

PARTS=('frame','scaffold','rod','engine','controller')
SOUNDS=tuple('machines.oredrill'+size+'work' for size in ('small','medium','large'))


def drill_heads():
    source=strip_comments((LEGACY/'java/techguns/TGItems.java').read_text())
    heads=[]
    for name,size,stack in re.findall(r'addsharedVariant\("(oredrill[^"]+)", false, TGSlotType.DRILL_(\w+), (\d+), true\)',source):
        assert int(stack)==1
        heads.append({'id':name,'size':('SMALL','MEDIUM','LARGE').index(size),'level':('steel','obsidiansteel','carbon').index(name.split('_')[1])+1})
    assert len(heads)==9
    return heads


def cluster_outputs():
    source=strip_comments((LEGACY/'java/techguns/TGOreClusters.java').read_text())
    source=source.split('public void RecipeInit()',1)[1].split('@Override',1)[0]
    outputs={}
    tags={'oreSilver':'c:ores/silver','oreOsmium':'c:ores/osmium','oreAluminium':'c:ores/aluminum',
          'oreCertusQuartz':'c:ores/certus_quartz','oreChargedCertusQuartz':'c:ores/charged_certus_quartz'}
    for call in re.findall(r'this.addOreToCluster\(([^;]+)\);',source):
        value,cluster,weight=arguments(call); cluster=cluster.split('.')[1].lower()
        if value.startswith('new FluidStack'): continue  # Original conditional oil resolver, one 1000 mB entry, weight 10.
        if value.startswith('"'): entry={'kind':'tag','id':tags[value.strip('"')],'legacy_ore_dictionary':value.strip('"')}
        elif 'TGBlocks.TG_ORE' in value: entry={'kind':'item','id':'techguns:'+re.search(r'EnumOreType\.(\w+)',value)[1].lower()}
        else:
            entry={'kind':'item','id':parse_stack(value)['id'].replace('minecraft:quartz_ore','minecraft:nether_quartz_ore')}
        outputs.setdefault(cluster,[]).append({**entry,'weight':int(weight)})
    outputs['oil']=[{'kind':'oil','id':'','weight':10}]
    return outputs


def drill_cube():
    """Keep source face order, coordinates and UVs; replace its swapped side normals with geometric normals."""
    source=(LEGACY/'java/techguns/client/render/tileentities/OreDrillCube.java').read_text()
    constants={name:float(value) for name,value in re.findall(r'double (\w+) = ([\d.]+)D;',source)}
    lines=['mtllib oredrill_slice.mtl','o original_drill_cube','usemtl slice']; vertices=[]; uv=[]; faces=[]
    for name in ('Top','Bottom','Front','Back','Left','Right'):
        body=source.split('protected void draw'+name,1)[1].split('\n\t}',1)[0]
        points=re.findall(r'buf.pos\(([^)]+)\).tex\(([^)]+)\)',body); assert len(points)==4
        base=len(vertices)+1
        for position,texture in points:
            vertices.append([0.0 if p.strip().startswith('-') else 1.0 for p in position.split(',')])
            uv.append([constants[p.strip()] for p in texture.split(',')])
        faces.append((base,{'Top':(0,1,0),'Bottom':(0,-1,0),'Front':(0,0,-1),'Back':(0,0,1),'Left':(-1,0,0),'Right':(1,0,0)}[name]))
    for v in vertices: lines.append('v '+' '.join(map(str,v)))
    for v in uv: lines.append('vt '+' '.join(map(str,v)))
    for _,normal in faces: lines.append('vn '+' '.join(map(str,normal)))
    for i,(base,_) in enumerate(faces): lines.append('f '+' '.join(f'{n}/{n}/{i+1}' for n in range(base,base+4)))
    return '\n'.join(lines)+'\n','newmtl slice\nmap_Kd #slice\n'


def generate_drill_content():
    files={}; assets=LEGACY/'resources/assets/techguns'; textures=set()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    for path in sorted((assets/'models/block').glob('oredrill*.json')):
        model=json.loads(path.read_text()); model['parent']='minecraft:'+model['parent']
        model['textures']={k:v.replace(':blocks/',':block/') for k,v in model['textures'].items()}
        textures.update(v.split(':block/')[1] for v in model['textures'].values())
        data(RESOURCES+f'assets/techguns/models/block/{path.name}',model)
        if path.stem in ['oredrill_'+p for p in PARTS]:
            data(RESOURCES+f'assets/techguns/models/item/{path.name}',{**model,'textures':{k:v.replace(':block/',':item/') for k,v in model['textures'].items()}})
    for texture in textures:
        for atlas in ('block','item'): files[RESOURCES+f'assets/techguns/textures/{atlas}/{texture}.png']=(assets/f'textures/blocks/{texture}.png').read_bytes()
    for part in PARTS:
        name='oredrill_'+part
        data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+name}})
        variants={}
        for facing in ('north','south','east','west'):
            for formed in (False,True):
                for cap in (False,True):
                    suffix='_h' if formed and part=='scaffold' and cap else '_c' if formed and part!='controller' else ''
                    variants[f'facing={facing},formed={str(formed).lower()},end_cap={str(cap).lower()}']={'model':'techguns:block/'+name+suffix}
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json',{'variants':variants})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:'+name}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    data(RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['techguns:oredrill_'+p for p in PARTS]})
    files[RESOURCES+'assets/techguns/textures/gui/ore_drill_gui.png']=(assets/'textures/gui/ore_drill_gui.png').read_bytes()
    obj,mtl=drill_cube(); files[RESOURCES+'assets/techguns/models/item/oredrill_slice.obj']=obj.encode(); files[RESOURCES+'assets/techguns/models/item/oredrill_slice.mtl']=mtl.encode()
    for material,texture in [('steel','drillhead'),('obsidiansteel','drillhead_obsidian'),('carbon','drillhead_carbon')]:
        files[RESOURCES+f'assets/techguns/textures/item/{texture}.png']=(assets/f'textures/blocks/{texture}.png').read_bytes()
        data(RESOURCES+f'assets/techguns/models/item/oredrill_slice_{material}.json',{'parent':'minecraft:block/block','loader':'neoforge:obj',
             'model':'techguns:models/item/oredrill_slice.obj','automatic_culling':False,'flip_v':False,'emissive_ambient':False,
             'textures':{'slice':'techguns:item/'+texture,'particle':'techguns:item/'+texture}})
        data(RESOURCES+f'assets/techguns/items/oredrill_slice_{material}.json',{'model':{'type':'minecraft:model','model':'techguns:item/oredrill_slice_'+material}})
    heads=drill_heads(); outputs=cluster_outputs()
    data('content/ore-drill.json',{'source':'legacy/1.12.2/src/main/java/techguns/tileentities/OreDrillTileEntMaster.java',
         'energy_capacity':500000,'input_tank':16000,'output_tank':32000,'inventory_size':11,'output_slots':list(range(2,11)),
         'max_length':16,'max_engine_radius':3,'heads':heads,'cluster_outputs':outputs,
         'source_item_overflow':'eject at controller','source_fluid_overflow':'excess discarded',
         'compatibility_changes':['unloaded chunks never forced','zero-cost ticks do not make fuel buffer negative','one-rod-per-connected-cluster enforced for formed rods too'],
         'pending':['interactive visual acceptance','real third-party oil/ore integrations','CraftTweaker/JEI integration']})
    head_java=',\n'.join(f'        new Head("{h["id"]}", {h["size"]}, {h["level"]})' for h in heads)
    output_java=',\n'.join('        Map.entry("'+key+'", List.of('+', '.join('new Output("'+v['kind']+'", "'+v['id']+'", '+str(v['weight'])+')' for v in values)+'))' for key,values in outputs.items())
    files['core/src/main/java/techguns/core/OreDrillCatalog.java']=('''package techguns.core;
import java.util.*;
/** Generated from TGItems and TGOreClusters. Optional tags are resolved when starting an operation. */
public final class OreDrillCatalog {
    public record Head(String id,int size,int level) {}
    public record Output(String kind,String id,int weight) {}
    public static final List<Head> HEADS=List.of(
'''+head_java+'''
    );
    public static final Map<String,List<Output>> OUTPUTS=Map.ofEntries(
'''+output_java+'''
    );
    private OreDrillCatalog() {}
}
''').encode()
    return files


def drill_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    result={kind+'.techguns.oredrill_'+part:source[f'tile.techguns.oredrill.{i}.name'] for i,part in enumerate(PARTS) for kind in ('block','item')}
    ru=lang=='ru_ru'
    result.update({'container.techguns.ore_drill':'Рудный бур' if ru else 'Ore Drill',
        'gui.techguns.drill.unformed':'Проверьте стержни, двигатели, каркас и рудный кластер' if ru else 'Check rods, engines, frame and ore cluster',
        'gui.techguns.drill.rod_placement':'Начните стержни от кластера; к нему уже не должен быть подключён бур' if ru else 'Start rods at a cluster that has no connected drill',
        'gui.techguns.drill.controller_placement':'Рядом уже есть контроллер бура' if ru else 'Another drill controller is adjacent',
        'gui.techguns.drill.rate':'%s ресурсов/час · %s FE/тик' if ru else '%s resources/hour · %s FE/tick',
        'gui.techguns.drill.size':'Длина: %s · радиус: %s' if ru else 'Length: %s · radius: %s',
        'gui.techguns.drill.head':'Головка: %s · уровень: %s' if ru else 'Head: %s · level: %s',
        'gui.techguns.drill.fuel':'Запас топлива: %s / %s' if ru else 'Fuel buffer: %s / %s'})
    for material in ('steel','obsidiansteel','carbon'):
        result['item.techguns.oredrill_slice_'+material]=source['item.techguns.oredrillsmall_'+material+'.name']
    return result
