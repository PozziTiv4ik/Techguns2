"""Source resources for the two original danger-zero NPCs."""
import json
import re
from legacy_npcs import LEGACY, RESOURCES, npc_loot
from legacy_models import strip_comments

NAMES = ('ZombieFarmer','ZombieMiner')


def rural_definitions():
    result=[]
    for name in NAMES:
        source=strip_comments((LEGACY/f'java/techguns/entities/npcs/{name}.java').read_text())
        attributes={key:float(value.rstrip('DdFf')) for key,value in re.findall(r'getEntityAttribute\(SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([^)]+)\)',source)}
        weapons=['minecraft:'+value.lower() for value in re.findall(r'weapon = Items\.(\w+);',source)]+['techguns:handcannon']
        render=(LEGACY/f'java/techguns/client/render/entities/npcs/Render{name}.java').read_text()
        texture=re.search(r'"(textures/entity/[^\"]+)"',render)[1]
        result.append({'id':name.lower(),'class':name,'attributes':attributes,'weapons':weapons,
            'weapon_roll_bound':int(re.search(r'r.nextInt\((\d+)\)',source)[1]),'texture':texture,
            'armor':'T1_MINER','shared_equipment_camo_count':4,'always_equipped':'CHEST' if name=='ZombieFarmer' else 'HEAD',
            'never_equipped':['HEAD'] if name=='ZombieFarmer' else [],'other_armor_chance':.5,
            'intrinsic_armor':0,'danger_level':0,'spawn_weight':200,'burns_in_daylight':True,
            'helmet_prevents_sun_ignition':False,'custom_techguns_spawner_exemption':'Pending with the custom spawner/link system',
            'source':f'legacy/1.12.2/src/main/java/techguns/entities/npcs/{name}.java'})
    return result


def generate_rural_content():
    files={}
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    for npc in rural_definitions():
        identifier=npc['id']; data(f'content/{identifier}.json',npc)
        data(RESOURCES+f'data/techguns/loot_table/entities/{identifier}.json',npc_loot(identifier))
        data(RESOURCES+f'assets/techguns/items/{identifier}_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':0x757468}]}})
    for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
        data(RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json',{'replace':False,'values':['techguns:'+name.lower() for name in NAMES]})
    # The original RenderZombieFarmer/RenderZombieMiner both use ZombieSoldier's already copied skin.
    return files


def rural_translations(lang):
    result={}; ru=lang=='ru_ru'
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    for identifier,en,russian in [('zombiefarmer','Zombie Farmer','Зомби-фермер'),('zombieminer','Zombie Miner','Зомби-шахтёр')]:
        result['entity.techguns.'+identifier]=source['entity.techguns.'+('ZombieFarmer' if identifier=='zombiefarmer' else 'ZombieMiner')+'.name']
        result['item.techguns.'+identifier+'_spawn_egg']=('Яйцо призыва '+('зомби-фермера' if identifier=='zombiefarmer' else 'зомби-шахтёра')) if ru else en+' Spawn Egg'
    return result
