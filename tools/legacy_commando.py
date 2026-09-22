"""Commando equipment, skin and loot taken from the original NPC and renderer."""
import json
import re
import struct
from legacy_models import strip_comments, numeric
from legacy_npcs import LEGACY, RESOURCES, npc_loot

def commando_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/Commando.java').read_text(encoding='utf-8'))
    render=(LEGACY/'java/techguns/client/render/entities/npcs/RenderCommando.java').read_text(encoding='utf-8')
    texture=re.search(r'"(textures/entity/[^"]+)"',render)[1]
    entities=(LEGACY/'java/techguns/TGEntities.java').read_text(encoding='utf-8')
    colors=re.search(r'registerModEntityWithEgg\(Commando.class, "Commando",\s*(0x\w+),\s*(0x\w+)\)',entities)
    return {'id':'commando','source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/Commando.java',
            'attributes':{k:numeric(v) for k,v in re.findall(r'getEntityAttribute\(SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([^)]+)\)',source)},
            'intrinsic_armor':numeric(re.search(r'setTGArmorStats\(([^,]+),',source)[1]),
            'weapon':re.search(r'Item weapon = TGuns\.(\w+)',source)[1],
            'armor':re.findall(r'new ItemStack\(TGArmors\.(\w+)\)',source),'armor_chance':1,
            'undead':False,'fire_immune':False,'natural_spawn_entry':False,'faction':'HOSTILE',
            'loot_id':'entities/commando','texture':texture,'texture_size':list(struct.unpack('>II',(LEGACY/'resources/assets/techguns'/texture).read_bytes()[16:24])),
            'model':'Original shared ModelGenericNPC humanoid and armor layers',
            'egg_colors':[int(colors[i],16) for i in (1,2)]}

def generate_commando_content():
    files={}; definition=commando_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/commando.json',definition)
    data(RESOURCES+'data/techguns/loot_table/entities/commando.json',npc_loot('commando'))
    data(RESOURCES+'assets/techguns/items/commando_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':definition['egg_colors'][0]}]}})
    texture='assets/techguns/'+definition['texture']; files[RESOURCES+texture]=(LEGACY/'resources'/texture).read_bytes()
    return files

def commando_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.commando':source['entity.techguns.Commando.name'].strip(),
            'item.techguns.commando_spawn_egg':'Яйцо призыва спецназовца' if lang=='ru_ru' else 'Commando Spawn Egg'}
