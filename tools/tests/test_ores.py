"""Independent original ore/drop/processing contracts; not snapshots of generated Java."""
from pathlib import Path
import json
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_ores import ore_data, smelting_data, generate_ore_content, RESOURCES, LEGACY
from generate_weapon_content import generate
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons


class OrePortTests(unittest.TestCase):
    def test_original_metadata_differs_from_generation_order(self):
        ores = ore_data()
        self.assertEqual([o['id'] for o in ores], ['ore_copper','ore_tin','ore_lead','ore_uranium','ore_titanium'])
        self.assertEqual([o['legacy_metadata'] for o in ores], [0,1,2,4,3])
        self.assertEqual([o['hardness'] for o in ores], [4,4,6,7,8])
        self.assertEqual([o['mining_level'] for o in ores], [1,1,2,2,3])
        self.assertEqual([o['light'] for o in ores], [0,0,0,4,0])

    def test_original_sizes_attempts_and_absolute_height_intervals(self):
        self.assertEqual([(o['vein_size_min'],o['vein_size_max'],o['attempts'],o['min_y'],o['max_y_exclusive']) for o in ore_data()],
                         [(5,7,12,5,80),(4,6,10,5,60),(4,5,8,5,50),(4,5,4,4,24),(4,7,5,4,32)])
        self.assertTrue(all(o['enabled_by_default'] for o in ore_data()))

    def test_block_and_item_keep_original_pixels(self):
        files = generate_ore_content()
        for ore in ore_data():
            for atlas in ('block','item'):
                self.assertEqual(files[RESOURCES+f'assets/techguns/textures/{atlas}/{ore["id"]}.png'],
                                 (LEGACY/f'resources/assets/techguns/textures/blocks/{ore["id"]}.png').read_bytes())

    def test_all_furnace_recipes_preserve_multi_item_yields_and_no_shortcuts(self):
        recipes = {r['id']:r for r in smelting_data()}
        self.assertEqual(len(recipes),19)
        self.assertEqual([recipes['ore_'+metal]['experience'] for metal in ('copper','tin','lead')],[.5,.5,1])
        self.assertEqual(recipes['ironbarrel']['result'], {'id':'minecraft:iron_ingot','count':6})
        self.assertEqual(recipes['turretarmorobsidiansteel']['result'], {'id':'techguns:ingotobsidiansteel','count':5})
        self.assertEqual(recipes['oretitanium']['ingredient'], 'techguns:oretitanium')
        self.assertNotIn('ore_titanium', recipes)
        self.assertNotIn('ore_uranium', recipes)
        self.assertTrue(all(r['cookingtime'] == 200 for r in recipes.values()))

    def test_ore_tags_keep_ilmenite_separate_from_processed_titanium(self):
        files = generate_ore_content()
        self.assertEqual(json.loads(files[RESOURCES+'data/c/tags/item/ores/titanium.json'])['values'], ['techguns:oretitanium'])
        self.assertEqual(json.loads(files[RESOURCES+'data/c/tags/item/ores/ilmenite.json'])['values'], ['techguns:ore_titanium'])

    def test_source_copper_and_tool_tags_survive_domain_merge(self):
        files = generate()
        pickaxes = json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])['values']
        self.assertEqual(len(pickaxes),8)
        self.assertIn('techguns:blast_furnace',pickaxes)
        self.assertTrue(all('techguns:'+ore['id'] in pickaxes for ore in ore_data()))
        plan = plan_crafting(parse_weapons())
        self.assertEqual(plan['tags']['c:ingots/copper'], ['techguns:ingotcopper'])
        self.assertEqual(plan['recipes']['nuggetcopper']['result']['count'],9)


if __name__ == '__main__': unittest.main()
