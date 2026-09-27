"""Original launcher/body/drum/40mm OBJ geometry and modern item definitions."""
from pathlib import Path
from decimal import Decimal
import json
from legacy_models import display_transforms

ROOT=Path(__file__).resolve().parents[1]
LEGACY=ROOT/'legacy/1.12.2/src/main'
RESOURCES='platforms/neoforge-26.2/src/main/resources/'


def launcher_base_model():
    display=display_transforms('+x')
    display['gui']['scale']=[.9]*3
    for hand in ('left','right'):display[f'thirdperson_{hand}hand']['scale']=[.7]*3
    return {'loader':'neoforge:obj','model':'techguns:models/item/grenadelauncher.obj',
            'automatic_culling':False,'flip_v':False,'emissive_ambient':False,
            'textures':{'skin':'techguns:item/grenadelauncher','particle':'techguns:item/grenadelauncher'},'display':display}


def generate_launcher_content():
    files={};catalog=[]
    def data(path,value):files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    for original,name,flying in [('grenadelauncher','grenadelauncher',False),('grenadelauncher_1','grenadelauncher_drum',False),('grenade40mm','grenade40mm',True)]:
        source=LEGACY/f'resources/assets/techguns/models/item/{original}.obj'
        raw=source.read_text().splitlines();lines=[]
        for line in raw:
            if line.startswith('v '):line='v '+' '.join(str(Decimal('.5')+Decimal(v)/(1 if flying else 16)) for v in line.split()[1:])
            if line.startswith('mtllib '):line=f'mtllib {name}.mtl'
            lines.append(line.rstrip())
        files[RESOURCES+f'assets/techguns/models/item/{name}.obj']=('\n'.join(lines)+'\n').encode()
        files[RESOURCES+f'assets/techguns/models/item/{name}.mtl']=b'newmtl tex\nKd 1.00 1.00 1.00\nmap_Kd #skin\n'
        texture='grenade40mm' if flying else 'grenadelauncher'
        model={**launcher_base_model(),'model':f'techguns:models/item/{name}.obj','display':{},'textures':{'skin':'techguns:item/'+texture,'particle':'techguns:item/'+texture}}
        identifier='grenadelauncher_body' if name=='grenadelauncher' else name
        data(RESOURCES+f'assets/techguns/models/item/{identifier}.json',model)
        if flying:data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+name}})
        catalog.append({'source':source.relative_to(ROOT).as_posix(),'model':identifier,'vertices':sum(l.startswith('v ') for l in raw),
                        'faces':sum(l.startswith('f ') for l in raw),'uvs':sum(l.startswith('vt ') for l in raw),'scale':1 if flying else .0625})
    files[RESOURCES+'assets/techguns/textures/item/grenade40mm.png']=(LEGACY/'resources/assets/techguns/textures/entity/launchergrenade.png').read_bytes()
    data('content/grenade-launcher-models.json',{'parts':catalog,'drum_pivot':[0,-2,0],'drum_degrees':60,'recoil_ticks':5,'projectile_scale':.5,'projectile_tumbles':False})
    return files


def launcher_translations(lang):
    name='40-мм граната' if lang=='ru_ru' else '40mm grenade'
    return {'item.techguns.grenade40mm':name,'entity.techguns.grenade40mm':name}
