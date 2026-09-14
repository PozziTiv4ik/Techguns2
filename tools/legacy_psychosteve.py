"""PsychoSteve's fixed Chainsaw, miner suit, source skin and fueled loot."""
import json
import re
import struct
from legacy_npcs import LEGACY, RESOURCES, npc_loot
from legacy_models import strip_comments, numeric
from legacy_zombie_soldier import overworld_table


def psycho_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/PsychoSteve.java').read_text())
    render=(LEGACY/'java/techguns/client/render/entities/npcs/RenderPsychoSteve.java').read_text()
    texture=re.search(r'"(textures/entity/[^"]+)"',render)[1]
    pixels=(LEGACY/'resources/assets/techguns'/texture).read_bytes()
    entities=(LEGACY/'java/techguns/TGEntities.java').read_text()
    colors=re.search(r'registerModEntityWithEgg\(PsychoSteve.class, "PsychoSteve",\s*(0x\w+),\s*(0x\w+)\)',entities)
    spawn=next(e for e in overworld_table() if e['npc']=='PsychoSteve')
    return {'id':'psychosteve','source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/PsychoSteve.java',
            'attributes':{k:numeric(v) for k,v in re.findall(r'getEntityAttribute\(SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([^)]+)\)',source)},
            'intrinsic_armor':numeric(re.search(r'setTGArmorStats\(([^,]+),',source)[1]),
            'declared_base_experience':int(re.search(r'experienceValue\s*=\s*(\d+)',source)[1]),
            'weapon':re.search(r'new ItemStack\(TGuns\.(\w+)\)',source)[1],
            'armor':'T1_MINER','always_equipped':['HEAD','CHEST','LEGS','FEET'],'shared_random_camo':True,
            'undead':False,'burns_in_daylight':False,'fire_immune':False,'danger_level':spawn['danger'],'spawn_weight':spawn['weight'],
            'loot_id':'entities/psychosteve','texture':texture,'texture_size':list(struct.unpack('>II',pixels[16:24])),
            'model':'ModelGenericNPC; shared modern humanoid renderer with source skin',
            'sounds':{'ambient':'minecraft:entity.villager.ambient','hurt':'minecraft:entity.villager.hurt','death':'minecraft:entity.villager.death','step':'minecraft:entity.zombie.step'},
            'egg_colors':[int(colors[i],16) for i in (1,2)]}


def generate_psycho_content():
    files={}; definition=psycho_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/psychosteve.json',definition)
    loot=npc_loot('psychosteve')
    # GenericGun.getCurrentAmmo initializes fresh, metadata-zero loot with a full magazine.
    source=strip_comments((LEGACY/'java/techguns/TGuns.java').read_text())
    capacity=int(re.search(r'chainsaw\s*=\s*new Chainsaw\([^,]+,[^,]+,[^,]+,[^,]+,\s*(\d+)',source)[1])
    for pool in loot['pools']:
        for entry in pool['entries']:
            # Apply before set_count: set_components validates maximum stack size, while
            # the final LootTable consumer splits temporary count-two tool stacks.
            if entry['name']=='techguns:chainsaw': entry['functions'].insert(0,{'function':'minecraft:set_components','components':{'techguns:rounds':capacity,'techguns:mining_head':0}})
    data(RESOURCES+'data/techguns/loot_table/entities/psychosteve.json',loot)
    data(RESOURCES+'assets/techguns/items/psychosteve_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':definition['egg_colors'][0]}]}})
    texture='assets/techguns/'+definition['texture']; files[RESOURCES+texture]=(LEGACY/'resources'/texture).read_bytes()
    return files


def psycho_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.psychosteve':source['entity.techguns.PsychoSteve.name'],
            'item.techguns.psychosteve_spawn_egg':'Яйцо призыва Стива-психа' if lang=='ru_ru' else 'Psycho Steve Spawn Egg'}
