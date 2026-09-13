"""Living Bandit: original equipment, six loot pools and unmodified humanoid skin."""
import json
import re
import struct
from legacy_npcs import LEGACY, RESOURCES, npc_loot
from legacy_models import strip_comments, numeric


def bandit_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/Bandit.java').read_text())
    render=(LEGACY/'java/techguns/client/render/entities/npcs/RenderBandit.java').read_text()
    texture=re.search(r'"(textures/entity/[^"]+)"',render)[1]
    pixels=(LEGACY/'resources/assets/techguns'/texture).read_bytes()
    entities=(LEGACY/'java/techguns/TGEntities.java').read_text()
    colors=re.search(r'registerModEntityWithEgg\(Bandit.class, "Bandit",\s*(0x\w+),\s*(0x\w+)\)',entities)
    source_loot=re.search(r'new ResourceLocation\(Techguns.MODID, "([^"]+)"\)',source)[1]
    return {'id':'bandit','source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/Bandit.java',
            'attributes':{k:numeric(v) for k,v in re.findall(r'getEntityAttribute\(SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([^)]+)\)',source)},
            'intrinsic_armor':numeric(re.search(r'setTGArmorStats\(([^,]+),',source)[1]),
            'weapons':re.findall(r'weapon = TGuns\.(\w+);',source),'weapon_roll_bound':int(re.search(r'r.nextInt\((\d+)\)',source)[1]),
            'armor':'T1_SCOUT','always_equipped':['CHEST','LEGS','FEET'],'helmet_chance':.5,'equipment_camo':0,
            'undead':False,'burns_in_daylight':False,'fire_immune':False,'danger_level':2,'spawn_weight':50,
            'source_loot_id':source_loot,'loot_id':source_loot.lower(),
            'texture':texture,'texture_size':list(struct.unpack('>II',pixels[16:24])),
            'model':'ModelGenericNPC; shared modern humanoid renderer with source skin',
            'sounds':{'ambient':'minecraft:entity.villager.ambient','hurt':'minecraft:entity.villager.hurt','death':'minecraft:entity.villager.death','step':'Native block step sound'},
            'egg_colors':[int(colors[i],16) for i in (1,2)]}


def generate_bandit_content():
    files={}; definition=bandit_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/bandit.json',definition)
    data(RESOURCES+'data/techguns/loot_table/entities/bandit.json',npc_loot('bandit'))
    data(RESOURCES+'assets/techguns/items/bandit_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':definition['egg_colors'][0]}]}})
    texture='assets/techguns/'+definition['texture']; files[RESOURCES+texture]=(LEGACY/'resources'/texture).read_bytes()
    return files


def bandit_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.bandit':source['entity.techguns.Bandit.name'],
            'item.techguns.bandit_spawn_egg':'Яйцо призыва бандита' if lang=='ru_ru' else 'Bandit Spawn Egg'}
