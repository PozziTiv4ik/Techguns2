"""First wearable armor set and ZombiePigmanSoldier assets, extracted from original sources."""
import json
import re
from legacy_models import strip_comments, numeric
from legacy_items import arguments
from legacy_npcs import LEGACY, RESOURCES, npc_loot


def armor_definitions():
    source = strip_comments((LEGACY / 'java/techguns/TGArmors.java').read_text())
    args = arguments(re.search(r'T2_COMBAT\s*=\s*new TGArmorMaterial\(([^;]+)\);', source)[1])
    base, defense, toughness = int(args[1]), numeric(args[3]), numeric(args[5])
    textures = re.findall(r'"([^"]+)"', re.search(r'String\[\] t2_combat_textures\s*=\s*\{([^}]+)\}', source)[1])
    material = strip_comments((LEGACY / 'java/techguns/items/armors/TGArmorMaterial.java').read_text())
    factors = {slot: numeric(re.search(r'factor' + name + r'\s*=\s*([^;]+);', material)[1]) for slot,name in [('HEAD','Head'),('CHEST','Chest'),('LEGS','Legs'),('FEET','Boots')]}
    elemental = numeric(re.search(r'float f\s*=\s*([^;]+);', material)[1])
    result = []
    for slot, part in [('HEAD','helmet'),('CHEST','chestplate'),('LEGS','leggings'),('FEET','boots')]:
        statement = re.search(r't2_combat_' + part.capitalize() + r'\s*=\s*new GenericArmorMultiCamo\(([^;]+);', source)[1]
        speed, jump = map(numeric, re.search(r'\.setSpeedBoni\(([^)]+)\)', statement)[1].split(','))
        repair = arguments(re.search(r'\.setRepairMats\(([^)]+)\)', statement)[1])
        ratio = repair[2].replace('f','').split('/')
        result.append({'id':'t2_combat_'+part,'slot':slot, 'physical':round(defense*factors[slot],6),
            'elemental':round(defense*elemental*factors[slot],6), 'durability':round(.25*55*base),
            'toughness':toughness,'speed':speed,'jump':jump,
            'knockback':numeric(re.search(r'\.setKnockbackResistance\(([^)]+)\)',statement)[1]),
            'enchantability':int(args[2]), 'repair_parts':int(repair[3]), 'repair_metal_ratio':float(ratio[0]) / (float(ratio[1]) if len(ratio)>1 else 1),
            'repair_metal':'ingotobsidiansteel','repair_cloth':'heavycloth','textures':textures})
    return result


def generate_armor_content():
    files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    armor = armor_definitions()
    data('content/t2-combat-armor.json', {'source':'legacy/1.12.2/src/main/java/techguns/TGArmors.java','items':armor})
    definitions=[]
    for item in armor:
        identifier=item['id']; name='assets/techguns/'
        data(RESOURCES + name + f'items/{identifier}.json', {'model':{'type':'minecraft:model','model':'techguns:item/'+identifier}})
        data(RESOURCES + name + f'models/item/{identifier}.json', {'parent':'minecraft:item/generated','textures':{'layer0':'techguns:item/'+identifier}})
        files[RESOURCES + name + f'textures/item/{identifier}.png']=(LEGACY / 'resources' / name / f'textures/items/{identifier}.png').read_bytes()
        definitions.append(f'        new ArmorSpec("{identifier}", ArmorSlot.{item["slot"]}, {item["physical"]}f, {item["elemental"]}f, {item["durability"]}, {item["toughness"]}f, {item["speed"]}, {item["jump"]}, {item["knockback"]}, {item["repair_parts"]}, {item["repair_metal_ratio"]})')
    files['core/src/main/java/techguns/core/Armors.java']=('''package techguns.core;

import java.util.List;

/** Generated from the original T2_COMBAT material and item declarations. */
public final class Armors {
    public static final List<ArmorSpec> ALL = List.of(
''' + ',\n'.join(definitions) + '\n    );\n    public static final List<String> CAMOS = List.of(' + ', '.join('"'+name+'"' for name in armor[0]['textures']) + ''');
    public static ArmorSpec forSlot(ArmorSlot slot) { return ALL.stream().filter(a -> a.slot()==slot).findFirst().orElseThrow(); }
    private Armors() {}
}
''').encode()
    for texture in armor[0]['textures']:
        data(RESOURCES + f'assets/techguns/equipment/{texture}.json', {'layers':{'humanoid':[{'texture':'techguns:'+texture}], 'humanoid_leggings':[{'texture':'techguns:'+texture}]}})
        for number, layer in [(1,'humanoid'),(2,'humanoid_leggings')]:
            files[RESOURCES + f'assets/techguns/textures/entity/equipment/{layer}/{texture}.png']=(LEGACY / f'resources/assets/techguns/textures/models/armor/{texture}_layer_{number}.png').read_bytes()
    for tag in ['enchantable/armor','enchantable/durability','head_armor','chest_armor','leg_armor','foot_armor']:
        slots={'head_armor':'HEAD','chest_armor':'CHEST','leg_armor':'LEGS','foot_armor':'FEET'}
        data(RESOURCES + f'data/minecraft/tags/item/{tag}.json', {'replace':False,'values':['techguns:'+a['id'] for a in armor if tag not in slots or a['slot']==slots[tag]]})
    for tag, slot in [('enchantable/head_armor','HEAD'),('enchantable/chest_armor','CHEST'),('enchantable/leg_armor','LEGS'),('enchantable/foot_armor','FEET')]:
        data(RESOURCES + f'data/minecraft/tags/item/{tag}.json', {'replace':False,'values':['techguns:'+a['id'] for a in armor if a['slot']==slot]})
    data('content/zombiepigmansoldier.json', {'source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/ZombiePigmanSoldier.java',
        'weapons':['thompson']*3+['revolver']*2+['ak47']*2+['pistol']*2, 'armor':'T2_COMBAT', 'camo':3,
        'helmet_chance':1, 'other_armor_chances':.5, 'spawn_weight':100, 'danger_level':0,
        'renderer':'modern Minecraft zombified piglin geometry/skin with source humanoid gun poses', 'original_egg_colors':[0xff1111,0x770000]})
    data(RESOURCES + 'data/techguns/loot_table/entities/zombiepigmansoldier.json',npc_loot('zombiepigmansoldier'))
    data(RESOURCES + 'assets/techguns/items/zombiepigmansoldier_spawn_egg.json', {'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':0xff1111}]}})
    for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
        data(RESOURCES + f'data/minecraft/tags/entity_type/{tag}.json', {'replace':False,'values':['techguns:zombiepigmansoldier']})
    return files


def armor_translations(lang):
    ru=lang=='ru_ru'
    names={'helmet':'Шлем','chestplate':'Нагрудник','leggings':'Поножи','boots':'Ботинки'}
    result={'item.techguns.t2_combat_'+part: name+' боевой брони T2' if ru else 'T2 Combat '+part.title() for part,name in names.items()}
    result.update({'entity.techguns.zombiepigmansoldier':'Зомби-свиночеловек-солдат' if ru else 'Zombie Pigman Soldier',
        'item.techguns.zombiepigmansoldier_spawn_egg':'Яйцо призыва зомби-свиночеловека-солдата' if ru else 'Zombie Pigman Soldier Spawn Egg',
        'tooltip.techguns.armor.camo':'Камуфляж: %s (Shift + ПКМ для смены)' if ru else 'Camouflage: %s (sneak + use to change)',
        'tooltip.techguns.armor.defense':'Физический / пули: %s; стихии / яд: %s' if ru else 'Physical / projectile: %s; elemental / poison: %s',
        'tooltip.techguns.armor.bonuses':'Скорость: +10%% (+20%% при спринте); сопротивление отбрасыванию: +%s%%' if ru else 'Speed: +10%% (+20%% sprinting); knockback resistance: +%s%%',
        'tooltip.techguns.armor.jump':'Прыжок: +0,1 к вертикальной скорости' if ru else 'Jump: +0.1 vertical velocity',
        'tooltip.techguns.armor.worn':'Бонусы отключены из-за износа' if ru else 'Bonuses disabled by wear'})
    for i,(en,russian) in enumerate(zip(['Default','Woodland','Desert','Arctic','SWAT','Security'],['Обычный','Лесной','Пустынный','Арктический','SWAT','Охрана'])):
        result['tooltip.techguns.armor.camo.'+str(i)]=russian if ru else en
    return result
