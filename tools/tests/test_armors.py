import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_armors import armor_definitions,generate_armor_content
from legacy_npcs import LEGACY,RESOURCES
from generate_weapon_content import generate,parse_weapons
from legacy_crafting import plan_crafting


class ArmorPortTests(unittest.TestCase):
    def test_material_fraction_and_bonus_extraction(self):
        armor=armor_definitions()
        self.assertEqual([a['physical'] for a in armor],[4.5,5.4,4.5,3.6])
        self.assertEqual([a['elemental'] for a in armor],[3.375,4.05,3.375,2.7])
        self.assertEqual([a['durability'] for a in armor],[990]*4)
        self.assertEqual([a['knockback'] for a in armor],[.1,.25,.15,.1])
        self.assertEqual([a['jump'] for a in armor],[0,0,0,.1])
        self.assertTrue(all(a['enchantability']==0 for a in armor))

    def test_all_six_camouflages_keep_both_source_layers(self):
        files=generate_armor_content()
        for texture in armor_definitions()[0]['textures']:
            model=json.loads(files[RESOURCES+f'assets/techguns/equipment/{texture}.json'])
            for number,layer in [(1,'humanoid'),(2,'humanoid_leggings')]:
                self.assertEqual(model['layers'][layer],[{'texture':'techguns:'+texture}])
                source=LEGACY/f'resources/assets/techguns/textures/models/armor/{texture}_layer_{number}.png'
                self.assertEqual(files[RESOURCES+f'assets/techguns/textures/entity/equipment/{layer}/{texture}.png'],source.read_bytes())

    def test_recipes_keep_obsidian_steel_and_white_wool_costs(self):
        recipes=plan_crafting(parse_weapons())['recipes']
        for part in ('helmet','chestplate','leggings','boots'):
            recipe=recipes['t2_combat_'+part]
            self.assertIn('techguns:heavycloth',recipe['key'].values())
            self.assertIn('#c:ingots/obsidian_steel',recipe['key'].values())
        self.assertEqual(recipes['heavycloth']['result'],{'id':'techguns:heavycloth','count':3})
        self.assertIn('minecraft:white_wool',recipes['heavycloth']['key'].values())

    def test_six_pigman_loot_pools_keep_metadata_and_probabilities(self):
        loot=json.loads(generate_armor_content()[RESOURCES+'data/techguns/loot_table/entities/zombiepigmansoldier.json'])
        self.assertEqual(len(loot['pools']),6)
        names=[p['entries'][0]['name'] for p in loot['pools']]
        self.assertEqual(names,['minecraft:gunpowder','minecraft:redstone','techguns:ingotobsidiansteel','minecraft:gold_ingot','techguns:shotgunrounds','techguns:riflerounds'])
        for pool in loot['pools']:
            entry=pool['entries'][0]
            self.assertEqual(entry['conditions'][0]['unenchanted_chance'],.2)
            self.assertIn('minecraft:enchanted_count_increase',[f['function'] for f in entry['functions']])

    def test_entity_tags_merge_with_existing_cyberdemon(self):
        files=generate()
        for tag in ('undead','sensitive_to_smite','ignores_poison_and_regen','inverted_healing_and_harm'):
            self.assertEqual(set(json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']),{'techguns:cyberdemon','techguns:zombiepigmansoldier','techguns:zombiesoldier','techguns:zombiefarmer','techguns:zombieminer'})
        self.assertNotIn(RESOURCES+'assets/minecraft/textures/entity/piglin/zombified_piglin.png',files)

    def test_pigman_defaults_and_source_control_flow(self):
        info=json.loads(generate_armor_content()['content/zombiepigmansoldier.json'])
        self.assertEqual(info['weapons'],['thompson']*3+['revolver']*2+['ak47']*2+['pistol']*2)
        self.assertEqual((info['helmet_chance'],info['other_armor_chances'],info['camo']),(1,.5,3))
        source=(LEGACY/'java/techguns/entities/npcs/ZombiePigmanSoldier.java').read_text()
        self.assertIn('int bound = 9;',source)
        self.assertIn('int camo=3;',source)


if __name__=='__main__': unittest.main()
