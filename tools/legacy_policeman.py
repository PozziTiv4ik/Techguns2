"""ZombiePoliceman attributes, independent armor rolls, source skin and five loot pools."""
import json
import re
import struct
from legacy_models import strip_comments, numeric
from legacy_npcs import LEGACY, RESOURCES, npc_loot


def policeman_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/ZombiePoliceman.java').read_text())
    render=(LEGACY/'java/techguns/client/render/entities/npcs/RenderZombiePoliceman.java').read_text()
    texture=re.search(r'"(textures/entity/[^"]+)"',render)[1]
    entities=(LEGACY/'java/techguns/TGEntities.java').read_text()
    colors=re.search(r'registerModEntityWithEgg\(ZombiePoliceman.class, "ZombiePoliceman",\s*(0x\w+),\s*(0x\w+)\)',entities)
    assert 'extends GenericNPCUndead' in source and 'shouldBurnInDay' not in source
    return {'id':'zombiepoliceman','source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/ZombiePoliceman.java',
            'attributes':{k:numeric(v) for k,v in re.findall(r'getEntityAttribute\(SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([^)]+)\)',source)},
            'intrinsic_armor':numeric(re.search(r'setTGArmorStats\(([^,]+),',source)[1]),
            'weapons':re.findall(r'case \d+:\s*weapon = TGuns\.(\w+);',source),
            'armor':re.findall(r'getNewWithCamo\(TGArmors\.(\w+),\s*camo\)',source),
            'armor_chance':numeric(re.search(r'double chance = ([^;]+);',source)[1]),'armor_inclusive':True,
            'camo':int(re.search(r'int camo=(\d+);',source)[1]),'undead':True,'burns_in_daylight':True,
            'spawner_origin_prevents_sunlight':True,'fire_immune':False,'natural_spawn_entry':False,'faction':'HOSTILE',
            'texture':texture,'texture_size':list(struct.unpack('>II',(LEGACY/'resources/assets/techguns'/texture).read_bytes()[16:24])),
            'egg_colors':[int(colors[i],16) for i in (1,2)]}


def generate_policeman_content():
    files={}; d=policeman_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/zombiepoliceman.json',d)
    data(RESOURCES+'data/techguns/loot_table/entities/zombiepoliceman.json',npc_loot('zombiepoliceman'))
    data(RESOURCES+'assets/techguns/items/zombiepoliceman_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':d['egg_colors'][0]}]}})
    texture='assets/techguns/'+d['texture']; files[RESOURCES+texture]=(LEGACY/'resources'/texture).read_bytes()
    for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
        data(RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json',{'replace':False,'values':['techguns:zombiepoliceman']})
    return files


def policeman_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.zombiepoliceman':source['entity.techguns.ZombiePoliceman.name'].strip(),
            'item.techguns.zombiepoliceman_spawn_egg':'Яйцо призыва зомби-полицейского' if lang=='ru_ru' else 'Zombie Policeman Spawn Egg'}
