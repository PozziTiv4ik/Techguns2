"""Reuse the original radiation icons without modifying their pixels."""
from pathlib import Path
import json
import struct

ROOT=Path(__file__).resolve().parents[1]
LEGACY=ROOT/'legacy/1.12.2/src/main'
RESOURCES='platforms/neoforge-26.2/src/main/resources/'


def generate_radiation_content():
    files={}
    def data(path,value): files[RESOURCES+path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    texture=(LEGACY/'resources/assets/techguns/textures/gui/tgplayerinventory.png').read_bytes()
    width,height=struct.unpack('>II',texture[16:24])
    files[RESOURCES+'assets/techguns/textures/gui/radiation_icons_source.png']=texture
    data('assets/minecraft/atlases/gui.json',{'sources':[{'type':'minecraft:unstitch','resource':'techguns:gui/radiation_icons_source',
        'divisor_x':width,'divisor_y':height,'regions':[{'sprite':'techguns:mob_effect/'+name,'x':16*index,'y':168,'width':16,'height':16}
            for index,name in enumerate(('radiation','radresistance','radregeneration'))]}]})
    for name in ('radiation','radiation_poisoning'):
        data('data/techguns/damage_type/'+name+'.json',{'message_id':'techguns.'+name,'scaling':'never','exhaustion':0})
    for tag in ('bypasses_armor','bypasses_cooldown','no_knockback'):
        data(f'data/minecraft/tags/damage_type/{tag}.json',{'replace':False,'values':['techguns:radiation','techguns:radiation_poisoning']})
    for tag in ('bypasses_effects','bypasses_enchantments'):
        data(f'data/minecraft/tags/damage_type/{tag}.json',{'replace':False,'values':['techguns:radiation_poisoning']})
    return files


def radiation_translations(lang):
    ru=lang=='ru_ru'
    return {'effect.techguns.radiation':'Облучение' if ru else 'Radiation Exposure',
            'effect.techguns.radregeneration':'Выведение радиации' if ru else 'Radiation Regeneration',
            'effect.techguns.radresistance':'Защита от радиации' if ru else 'Radiation Resistance',
            'attribute.techguns.radiation_resistance':'Сопротивление радиации' if ru else 'Radiation Resistance',
            'death.attack.techguns.radiation':'%1$s погиб от облучения' if ru else '%1$s died from radiation exposure',
            'death.attack.techguns.radiation_poisoning':'%1$s погиб от лучевой болезни' if ru else '%1$s died from radiation poisoning',
            'death.attack.techguns.radiation.player':'%1$s погиб от облучения, спасаясь от %2$s' if ru else '%1$s died from radiation whilst escaping %2$s',
            'death.attack.techguns.radiation_poisoning.player':'%1$s погиб от лучевой болезни, спасаясь от %2$s' if ru else '%1$s died from radiation poisoning whilst escaping %2$s'}
