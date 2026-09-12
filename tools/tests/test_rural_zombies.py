import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_rural_zombies import rural_definitions, generate_rural_content, rural_translations
from legacy_zombie_soldier import overworld_table
from legacy_npcs import LEGACY, RESOURCES
from generate_weapon_content import generate


class RuralZombiePortTests(unittest.TestCase):
    def test_stats_and_weapon_switches_are_read_from_source(self):
        farmer,miner=rural_definitions()
        self.assertEqual(farmer['weapons'],['minecraft:wooden_hoe','minecraft:iron_hoe','minecraft:stone_hoe','techguns:handcannon'])
        self.assertEqual(miner['weapons'],['minecraft:stone_pickaxe','minecraft:iron_pickaxe','techguns:handcannon'])
        for npc,health,attack,bound in [(farmer,18,3,4),(miner,20,4,3)]:
            self.assertEqual(npc['attributes'],{'MOVEMENT_SPEED':.2,'MAX_HEALTH':health,'ATTACK_DAMAGE':attack,'FOLLOW_RANGE':50})
            self.assertEqual(npc['weapon_roll_bound'],bound)
        self.assertIn('case 3:',(LEGACY/'java/techguns/entities/npcs/ZombieMiner.java').read_text())

    def test_source_equipment_and_shared_skin(self):
        farmer,miner=rural_definitions()
        self.assertEqual((farmer['always_equipped'],farmer['never_equipped']),('CHEST',['HEAD']))
        self.assertEqual((miner['always_equipped'],miner['never_equipped']),('HEAD',[]))
        files=generate()
        for npc in (farmer,miner):
            self.assertEqual(npc['texture'],'textures/entity/zombie_soldier.png')
            self.assertEqual(files[RESOURCES+'assets/techguns/'+npc['texture']],(LEGACY/'resources/assets/techguns'/npc['texture']).read_bytes())
            self.assertEqual((npc['armor'],npc['shared_equipment_camo_count']),('T1_MINER',4))

    def test_all_twelve_live_loot_pools_keep_counts_and_looting(self):
        files=generate_rural_content()
        expected={'zombiefarmer':['techguns:heavycloth','minecraft:wheat_seeds','minecraft:gunpowder','minecraft:wheat','minecraft:leather','minecraft:bread','minecraft:apple'],
                  'zombieminer':['techguns:heavycloth','minecraft:coal','minecraft:gunpowder','minecraft:redstone','minecraft:iron_ingot']}
        for name,items in expected.items():
            entries=[p['entries'][0] for p in json.loads(files[RESOURCES+f'data/techguns/loot_table/entities/{name}.json'])['pools']]
            self.assertEqual([e['name'] for e in entries],items)
            self.assertEqual([e['functions'][0]['count']['max'] for e in entries],[2,2,2,5,2,3,4] if name=='zombiefarmer' else [2,2,2,5,1])
            for e in entries:
                self.assertEqual(e['conditions'][0]['unenchanted_chance'],.2)
                self.assertEqual(e['conditions'][0]['enchanted_chance']['per_level_above_first'],.05)
                self.assertEqual(e['functions'][1]['function'],'minecraft:enchanted_count_increase')

    def test_daylight_does_not_use_vanilla_helmet_protection(self):
        files=generate_rural_content()
        self.assertNotIn(RESOURCES+'data/minecraft/tags/entity_type/burn_in_daylight.json',files)
        source=(LEGACY/'java/techguns/entities/npcs/GenericNPCUndead.java').read_text()
        self.assertIn('this.setFire(8)',source); self.assertNotIn('getItemStackFromSlot(',source)
        for npc in rural_definitions(): self.assertFalse(npc['helmet_prevents_sun_ignition'])

    def test_five_undead_types_merge_and_three_overworld_entries_are_available(self):
        files=generate()
        for tag in ('undead','sensitive_to_smite','ignores_poison_and_regen','inverted_healing_and_harm'):
            values=json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']
            self.assertEqual(set(values),{'techguns:cyberdemon','techguns:zombiepigmansoldier','techguns:zombiesoldier','techguns:zombiefarmer','techguns:zombieminer'})
        table=overworld_table()
        self.assertEqual([e['npc'] for e in table if e['implemented']],['ZombieFarmer','ZombieMiner','ZombieSoldier'])
        self.assertEqual([e['weight'] for e in table],[200,200,100,100,3,50])

    def test_original_entity_names_and_both_eggs(self):
        for lang in ('en_us','ru_ru'):
            text=rural_translations(lang)
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            for name in ('ZombieFarmer','ZombieMiner'):
                self.assertEqual(text['entity.techguns.'+name.lower()],source['entity.techguns.'+name+'.name'])
                self.assertIn('item.techguns.'+name.lower()+'_spawn_egg',text)


if __name__=='__main__': unittest.main()
