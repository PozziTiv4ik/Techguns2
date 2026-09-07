"""Migration checks for original metadata and workbench semantics."""
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_crafting import shared_items, plan_crafting
from generate_weapon_content import parse_weapons


class CraftingPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.plan = plan_crafting(parse_weapons())

    def test_metadata_counts_disabled_entries_but_not_commented_variants(self):
        shared = shared_items()
        self.assertEqual(shared[39], 'obsidiansteelbarrel')
        self.assertEqual(shared[42], 'woodstock')
        self.assertEqual(shared[57], 'mechanicalpartsiron')
        self.assertEqual(shared[82], 'ingotlead')
        self.assertEqual(shared[88], 'nuggetsteel')

    def test_loaded_and_empty_magazines_make_different_gun_states(self):
        recipes = self.plan['recipes']
        for gun, capacity in [('ak47',30), ('pistol',18), ('thompson',20), ('lmg',100), ('as50',10)]:
            self.assertEqual(recipes[gun]['result']['components']['techguns:rounds'], capacity)
            self.assertEqual(recipes[gun+'_alt']['result']['components']['techguns:rounds'], 0)
        self.assertEqual(recipes['revolver']['result']['components']['techguns:rounds'], 0)

    def test_rounds_and_magazine_yields_remain_original(self):
        recipes = self.plan['recipes']
        self.assertEqual(recipes['pistolrounds']['result']['count'], 8)
        self.assertEqual(recipes['shotgunrounds']['result']['count'], 5)
        self.assertEqual(recipes['pistolmagazine']['ingredients'].count('techguns:pistolrounds'), 3)
        self.assertEqual(recipes['riflerounds']['result']['count'], 4)
        self.assertEqual(recipes['lmgmagazine']['pattern'], ['bbb','bmb','bbb'])

    def test_advanced_materials_keep_production_requirements(self):
        recipes = self.plan['recipes']
        self.assertEqual(recipes['obsidiansteelbarrel']['key']['o'], 'techguns:ingotobsidiansteel')
        self.assertEqual(recipes['pistolrounds']['key']['l'], '#c:ingots/lead')
        self.assertNotIn('plasticsheet', recipes)
        self.assertNotIn('sniperrounds', recipes)
        self.assertEqual(self.plan['tags']['c:plates/iron'], ['techguns:plateiron'])

    def test_gilding_uses_copy_recipe_and_ambiguous_upgrades_are_explicit(self):
        self.assertEqual(self.plan['recipes']['goldenrevolver']['type'], 'techguns:copy_gun')
        self.assertEqual(self.plan['catalog']['pending_upgrade_recipes'], ['m4_infiltrator.json','m4_infiltrator_alt.json'])


if __name__ == '__main__': unittest.main()
