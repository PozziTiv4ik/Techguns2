import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_zombie_soldier import generate_zombie_soldier_content, overworld_table
from legacy_npcs import LEGACY, RESOURCES
from generate_weapon_content import generate


class ZombieSoldierPortTests(unittest.TestCase):
    def test_original_skin_is_preserved_byte_for_byte(self):
        path='assets/techguns/textures/entity/zombie_soldier.png'
        self.assertEqual(generate_zombie_soldier_content()[RESOURCES+path],(LEGACY/'resources'/path).read_bytes())

    def test_live_five_pool_loot_keeps_metadata_and_different_looting_chances(self):
        loot=json.loads(generate_zombie_soldier_content()[RESOURCES+'data/techguns/loot_table/entities/zombiesoldier.json'])
        entries=[p['entries'][0] for p in loot['pools']]
        self.assertEqual([e['name'] for e in entries],['techguns:heavycloth','minecraft:gunpowder','minecraft:rotten_flesh','techguns:pistolrounds','techguns:shotgunrounds'])
        self.assertEqual([e['conditions'][0]['enchanted_chance']['per_level_above_first'] for e in entries],[.05,.05,.05,.1,.1])
        self.assertEqual([e['functions'][0]['count']['max'] for e in entries],[2,2,1,4,8])
        self.assertEqual([len(e['functions']) for e in entries],[1,2,2,2,2])

    def test_overworld_table_is_read_in_danger_bucket_order(self):
        table=overworld_table()
        self.assertEqual([e['npc'] for e in table],['ZombieFarmer','ZombieMiner','ZombieSoldier','SkeletonSoldier','PsychoSteve','Bandit'])
        self.assertEqual([e['weight'] for e in table],[200,200,100,100,3,50])
        self.assertEqual([e['danger'] for e in table],[0,0,1,1,1,2])
        self.assertEqual([e['npc'] for e in table if e['implemented']],['ZombieFarmer','ZombieMiner','ZombieSoldier'])

    def test_all_ported_undead_npcs_keep_merged_tags_and_translations(self):
        files=generate()
        for tag in ('undead','sensitive_to_smite','ignores_poison_and_regen','inverted_healing_and_harm'):
            self.assertEqual(set(json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']),
                             {'techguns:cyberdemon','techguns:zombiepigmansoldier','techguns:zombiesoldier','techguns:zombiefarmer','techguns:zombieminer'})
        for lang in ('en_us','ru_ru'):
            self.assertIn('entity.techguns.zombiesoldier',json.loads(files[RESOURCES+f'assets/techguns/lang/{lang}.json']))

    def test_equipment_catalog_matches_live_source_branches(self):
        source=(LEGACY/'java/techguns/entities/npcs/ZombieSoldier.java').read_text()
        self.assertEqual(source.count('Math.random() <= chance'),4)
        self.assertIn('double chance = 0.5;',source)
        self.assertIn('r.nextInt(4)',source)
        self.assertIn('Items.IRON_SHOVEL',source); self.assertIn('Items.STONE_SHOVEL',source)
        info=json.loads(generate_zombie_soldier_content()['content/zombiesoldier.json'])
        self.assertEqual(info['armor'],'T1_COMBAT'); self.assertFalse(info['fire_immune'])


if __name__=='__main__': unittest.main()
