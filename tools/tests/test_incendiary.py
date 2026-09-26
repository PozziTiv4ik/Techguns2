import json
import re
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import parse_weapons, generate, resolve_asset
from legacy_crafting import plan_crafting
from legacy_incendiary import *
from legacy_items import shared_items


class IncendiaryPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = parse_weapons()
        cls.graph = plan_crafting(cls.weapons)
        cls.files = generate()
        cls.selected = [g for g in cls.weapons if incendiary_for(g)]

    def test_original_ammo_families_and_factory_parameters(self):
        self.assertEqual(len(incendiary_families()), 9)  # Includes the still-unported minigun.
        self.assertEqual(len(self.selected), 16)
        self.assertNotIn('handcannon', {g['id'] for g in self.selected})
        self.assertEqual(incendiary_parameters(), {'damage_multiplier':1.1, 'burn_seconds':3, 'ignition_divisor':40})
        for g in self.selected:
            f = incendiary_for(g)
            self.assertEqual(f['empty'], g['ammo']['empty_item'])
            self.assertEqual(f['bundles'], g['ammo']['bundles_per_magazine'])
        self.assertEqual({f['item'] for f in incendiary_families() if f['bundles']},
                         {'smgmagazine_incendiary', 'pistolmagazine_incendiary', 'assaultriflemagazine_incendiary',
                          'lmgmagazine_incendiary', 'as50magazine_incendiary', 'minigundrum_incendiary'})

    def test_both_switch_recipes_for_every_supported_weapon(self):
        shared = shared_items()
        for gun in self.selected:
            for variant in ('default', 'incendiary'):
                name = gun['id'] + '_ammo_' + variant
                original = json.loads((LEGACY / f'resources/assets/techguns/recipes/{name}.json').read_text(encoding='utf-8'))
                recipe = self.graph['recipes'][name]
                self.assertEqual(recipe['type'], original['type'])
                self.assertEqual(recipe['ingredients'], ['techguns:' + gun['id'], 'techguns:' + shared[original['ingredients'][1]['data']]])
                self.assertEqual(recipe['result']['components'], {'techguns:rounds':1 if gun['ammo']['individual'] else gun['capacity'],
                                                                 'techguns:ballistic_variant':variant})
        self.assertNotIn('as50_ammo_explosive', self.graph['recipes'])
        self.assertNotIn('minigun_ammo_incendiary', self.graph['recipes'])

    def test_five_magazines_keep_all_six_original_workbench_recipes(self):
        names = ['smgmagazine_incendiary','pistolmagazine_incendiary','assaultriflemagazine_incendiary',
                 'as50magazine_incendiary','lmgmagazine_incendiary','lmgmagazine_incendiary_alt']
        for name in names:
            source = next((LEGACY / 'resources/assets/techguns/recipes').glob('itemshared_*_' + name + '.json'))
            old = json.loads(source.read_text(encoding='utf-8')); recipe = self.graph['recipes'][name]
            self.assertEqual(recipe['ingredients'], ['techguns:' + shared_items()[v['data']] for v in old['ingredients']])
            self.assertEqual(recipe['result']['count'], old['result'].get('count', 1))
        self.assertEqual(len(self.graph['recipes']['lmgmagazine_incendiary_alt']['ingredients']), 9)
        self.assertEqual(len(self.graph['recipes']['lmgmagazine_incendiary']['ingredients']), 3)

    def test_new_magazines_use_original_flat_or_three_dimensional_models(self):
        for name in ('smgmagazine_incendiary', 'pistolmagazine_incendiary', 'assaultriflemagazine_incendiary', 'lmgmagazine_incendiary', 'as50magazine_incendiary'):
            model = json.loads(self.files[RESOURCES + f'assets/techguns/models/item/{name}.json'])
            self.assertIn(name, self.graph['extra_ammo'])
            if name in ('smgmagazine_incendiary', 'pistolmagazine_incendiary'):
                self.assertEqual(model['parent'], 'minecraft:item/generated')
                expected = resolve_asset(f'textures/items/{name}.png').read_bytes()
            else:
                self.assertEqual(model['loader'], 'neoforge:obj')
                self.assertIn(RESOURCES + f'assets/techguns/models/item/{name}.obj', self.files)
                proxy = (LEGACY / 'java/techguns/client/ClientProxy.java').read_text(encoding='utf-8')
                texture = re.search(r'addRenderForType\("' + name + r'"[^\n]+new ResourceLocation\(Techguns.MODID,\s*"([^"]+)"', proxy)[1]
                expected = resolve_asset(texture).read_bytes()
            self.assertEqual(self.files[RESOURCES + f'assets/techguns/textures/item/{name}.png'], expected)

    def test_fire_damage_keeps_magic_and_cooldown_but_not_vanilla_immunity(self):
        for namespace, tag in [('minecraft','bypasses_cooldown'), ('minecraft','no_knockback'), ('minecraft','witch_resistant_to'), ('neoforge','is_magic')]:
            data = json.loads(self.files[RESOURCES + f'data/{namespace}/tags/damage_type/{tag}.json'])
            self.assertIn('techguns:incendiary', data['values'])
        for tag in ('is_fire','is_projectile','bypasses_armor'):
            data = json.loads(self.files.get(RESOURCES + f'data/minecraft/tags/damage_type/{tag}.json', b'{"values":[]}'))
            self.assertNotIn('techguns:incendiary', data['values'])
        cooldown = json.loads(self.files[RESOURCES + 'data/minecraft/tags/damage_type/bypasses_cooldown.json'])
        self.assertNotIn('techguns:incendiary_knockback', cooldown['values'])
        self.assertTrue({'techguns:bullet','techguns:laser','techguns:nether_blast'}.issubset(cooldown['values']))

    def test_other_special_recipes_remain_explicitly_separate(self):
        for variant in ('default','nuke','high_velocity'):
            recipe = self.graph['recipes']['rocketlauncher_ammo_' + variant]
            self.assertEqual(recipe['result']['components'], {'techguns:rounds':1,'techguns:rocket_variant':variant})
        self.assertEqual(set(self.graph['catalog']['pending_upgrade_recipes']), {'m4_infiltrator.json','m4_infiltrator_alt.json'})

    def test_all_new_names_and_hud_labels_exist_in_both_languages(self):
        for lang in ('en_us','ru_ru'):
            translated = json.loads(self.files[RESOURCES + f'assets/techguns/lang/{lang}.json'])
            for family in incendiary_families():
                if family['family'] != 'MINIGUN_AMMO_DRUM': self.assertIn('item.techguns.' + family['item'], translated)
            for key, value in incendiary_translations(lang).items(): self.assertEqual(translated[key], value)


if __name__ == '__main__': unittest.main()
