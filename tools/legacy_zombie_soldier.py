"""Original ZombieSoldier resources and a reviewable Overworld spawn-table catalog."""
import json
import re
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES, npc_loot


def overworld_table():
    entities = strip_comments((LEGACY / 'java/techguns/TGEntities.java').read_text())
    config = strip_comments((LEGACY / 'java/techguns/TGConfig.java').read_text())
    entries = []
    for name, setting, danger in re.findall(r'spawnTableOverworld.registerSpawn\(new TGNpcSpawn\((\w+)\.class, TGConfig\.(\w+)\), (\d)\)', entities):
        default = re.search(setting + r'\s*=\s*config.getInt\("([^"]+)", "NPC Spawn", (\d+)', config)
        entries.append({'npc':name, 'config':default[1], 'weight':int(default[2]), 'danger':int(danger), 'implemented':name in ('ZombieSoldier','ZombieFarmer','ZombieMiner','SkeletonSoldier')})
    return sorted(entries, key=lambda entry:entry['danger'])  # Stable: bucket order, then registration order.


def generate_zombie_soldier_content():
    files = {}
    def data(path, value): files[path] = (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/zombiesoldier.json', {
        'source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/ZombieSoldier.java',
        'weapons':['techguns:revolver','techguns:thompson','minecraft:iron_shovel','minecraft:stone_shovel'],
        'health':25, 'speed':.25, 'attack':4, 'follow_range':50, 'intrinsic_armor':5,
        'armor':'T1_COMBAT', 'independent_armor_chance':.5, 'burns_in_daylight':False, 'fire_immune':False,
        'original_egg_colors':[0x757468,0x38b038], 'renderer':'Original 64x64 skin on the modern humanoid mesh; no zombie arm animation'})
    data('content/overworld-npc-spawns.json', {
        'source':['legacy/1.12.2/src/main/java/techguns/TGEntities.java','legacy/1.12.2/src/main/java/techguns/entities/spawn/TGSpawnManager.java'],
        'pool_weight':600, 'group':[1,3], 'distance_thresholds':[500,1000,2500], 'entries':overworld_table(),
        'selection':'nextInt(total); inclusive cumulative >= roll; zero-weight entries excluded',
        'unported_selection':'No replacement entity; original tickets retained',
        'dimension_scope':'minecraft:overworld only; original other-dimension fallback remains pending',
        'biome_mapping':'Legacy BiomeDictionary categories use corresponding NeoForge c: tags; SPARSE becomes is_sparse_vegetation'})
    data(RESOURCES+'data/techguns/loot_table/entities/zombiesoldier.json',npc_loot('zombiesoldier'))
    data(RESOURCES+'assets/techguns/items/zombiesoldier_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':0x757468}]}})
    texture = 'assets/techguns/textures/entity/zombie_soldier.png'
    files[RESOURCES+texture] = (LEGACY/'resources'/texture).read_bytes()
    for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
        data(RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json',{'replace':False,'values':['techguns:zombiesoldier']})
    data(RESOURCES+'data/techguns/neoforge/biome_modifier/overworld_npcs.json',{'type':'techguns:overworld_npcs'})
    return files


def zombie_soldier_translations(lang):
    ru = lang == 'ru_ru'
    return {'entity.techguns.zombiesoldier':'Зомби-солдат' if ru else 'Zombie Soldier',
            'item.techguns.zombiesoldier_spawn_egg':'Яйцо призыва зомби-солдата' if ru else 'Zombie Soldier Spawn Egg'}
