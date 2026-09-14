"""Chainsaw blade variants and tool compatibility, retaining original geometry and textures."""
import json
from legacy_items import LEGACY
from legacy_models import convert_mesh

RES = 'platforms/neoforge-26.2/src/main/resources/'


def chainsaw_item_model():
    def model(name): return {'type':'minecraft:model', 'model':'techguns:item/'+name}
    return {'type':'minecraft:condition', 'property':'techguns:mining_head', 'level':2,
            'on_true':model('chainsaw_carbon'), 'on_false':{
                'type':'minecraft:condition', 'property':'techguns:mining_head', 'level':1,
                'on_true':model('chainsaw_obsidian'), 'on_false':model('chainsaw')}}


def generate_chainsaw_content():
    files = {}
    def data(path,value): files[RES+path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    source=(LEGACY/'java/techguns/client/models/guns/ModelChainsaw.java').read_text()
    for name,color in [('obsidian','0.9 0.55 1'),('carbon','0.4 0.4 0.4')]:
        identifier='chainsaw_'+name
        model,obj,material=convert_mesh(source,'ModelChainsaw',identifier,'techguns:item/chainsaw','+x',skip_parts=('blade2',), repeat_texture=True)
        obj=obj.replace('o blade1\nusemtl gun','o blade1\nusemtl blade')
        material+='newmtl blade\nKa 0 0 0\nKd '+color+'\nmap_Kd #gun\n'
        data('assets/techguns/models/item/'+identifier+'.json',model)
        files[RES+'assets/techguns/models/item/'+identifier+'.obj']=obj.encode()
        files[RES+'assets/techguns/models/item/'+identifier+'.mtl']=material.encode()
    data('data/techguns/damage_type/chainsaw.json',{'message_id':'techguns.chainsaw','scaling':'when_caused_by_living_non_player','exhaustion':0.1})
    for name,tier in [('default','diamond'),('obsidian','netherite'),('carbon','netherite')]:
        data('data/techguns/tags/block/incorrect_for_chainsaw_'+name+'.json',
             {'replace':False,'values':['#minecraft:incorrect_for_'+tier+'_tool']})
    return files


def chainsaw_translations(lang):
    return {'tooltip.techguns.chainsaw.controls':'LMB: chop / melee. RMB: chain attack. R: refuel.' if lang=='en_us' else 'ЛКМ: рубка / удар. ПКМ: атака цепью. R: заправка.',
            'tooltip.techguns.chainsaw.head':'Blade level: %s. Mining speed: %s' if lang=='en_us' else 'Уровень лезвий: %s. Скорость рубки: %s',
            'death.attack.techguns.chainsaw':'%1$s was cut apart' if lang=='en_us' else '%1$s был распилен',
            'death.attack.techguns.chainsaw.player':'%1$s was cut apart by %2$s' if lang=='en_us' else '%1$s был распилен игроком %2$s'}
