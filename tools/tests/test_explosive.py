import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate, parse_weapons, resolve_asset
from legacy_crafting import plan_crafting, convert_recipe
from legacy_explosive import *
from legacy_items import shared_items
from legacy_machines import metal_press_data
from legacy_grinder import grinder_data


class ExplosiveAmmoPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = parse_weapons()
        cls.files = generate()
        cls.graph = plan_crafting(cls.weapons)

    def test_active_vanilla_explosion_not_commented_tgexplosion(self):
        self.assertEqual(explosive_parameters(), {'damage_multiplier': 1.15, 'explosion_power': 1.5, 'direct_knockback': 3})
        self.assertEqual([g['id'] for g in self.weapons if explosive_for(g)], ['as50'])
        catalog = json.loads(self.files['content/explosive-ammo.json'])
        self.assertIn('not TGExplosion', catalog['blast'])
        self.assertIn('independent', catalog['blast_source'])

    def test_original_metadata_and_two_bundle_magazine(self):
        self.assertEqual(explosive_family(), {'family':'AS50_MAGAZINE', 'default':'as50magazine', 'item':'as50magazine_explosive',
                                             'empty':'as50magazineempty', 'loose':'sniperrounds_explosive', 'bundles':2})
        self.assertEqual(shared_items()[143], 'as50magazine_explosive')
        self.assertIn('as50magazine_explosive', self.graph['extra_ammo'])

    def test_all_three_original_switches_and_magazine_recipe(self):
        weapons = {g['id']:g for g in self.weapons}
        for variant in ('default', 'incendiary', 'explosive'):
            name = 'as50_ammo_' + variant
            old = json.loads((LEGACY / f'resources/assets/techguns/recipes/{name}.json').read_text())
            self.assertEqual(self.graph['recipes'][name], convert_recipe(old, shared_items(), weapons))
            self.assertEqual(self.graph['recipes'][name]['result']['components'], {'techguns:rounds':10, 'techguns:ballistic_variant':variant})
        magazine = self.graph['recipes']['as50magazine_explosive']
        self.assertEqual(magazine['ingredients'], ['techguns:as50magazineempty', 'techguns:sniperrounds_explosive', 'techguns:sniperrounds_explosive'])
        self.assertEqual(magazine['result'], {'id':'techguns:as50magazine_explosive', 'count':1})

    def test_existing_press_and_grinder_chain_is_retained(self):
        recipes = json.loads(self.files['content/metal-press.json'])
        self.assertIn('sniperrounds_explosive', json.dumps(recipes))
        press = json.loads(self.files[RESOURCES + 'data/techguns/recipe/metal_press/sniperrounds_explosive.json'])
        self.assertIn('techguns:sniperrounds_incendiary', json.dumps(press))
        self.assertIn('techguns:tgx', json.dumps(press))
        grinder = next(r for r in grinder_data()['recipes'] if r['id'] == 'sniperrounds_explosive')
        self.assertEqual([o['factor'] for o in grinder['outputs']], [1, 1, .25, .125, .5])
        self.assertEqual([o['result']['count'] for o in grinder['outputs']], [2, 4, 1, 1, 1])

    def test_magazine_uses_original_3d_model_and_texture(self):
        model = json.loads(self.files[RESOURCES + 'assets/techguns/models/item/as50magazine_explosive.json'])
        self.assertEqual(model['loader'], 'neoforge:obj')
        obj = self.files[RESOURCES + 'assets/techguns/models/item/as50magazine_explosive.obj']
        self.assertGreater(obj.count(b'\nf '), 0)
        self.assertEqual(self.files[RESOURCES + 'assets/techguns/textures/item/as50magazine_explosive.png'], resolve_asset('textures/guns/as50_mag_exp.png').read_bytes())

    def test_direct_explosion_keeps_cooldown_and_armor(self):
        for tag in ('bypasses_cooldown','bypasses_armor','is_projectile','is_fire','no_knockback'):
            data = json.loads(self.files.get(RESOURCES + f'data/minecraft/tags/damage_type/{tag}.json', b'{"values":[]}'))
            self.assertNotIn('techguns:explosive_bullet', data['values'])
        tag = json.loads(self.files[RESOURCES + 'data/minecraft/tags/damage_type/is_explosion.json'])
        self.assertIn('techguns:explosive_bullet', tag['values'])
        self.assertIn('techguns:rocket', tag['values'])

    def test_original_item_and_new_hud_names_in_both_languages(self):
        for lang in ('en_us','ru_ru'):
            actual = json.loads(self.files[RESOURCES + f'assets/techguns/lang/{lang}.json'])
            self.assertIn('item.techguns.as50magazine_explosive', actual)
            for key, value in explosive_translations(lang).items(): self.assertEqual(actual[key], value)


if __name__ == '__main__': unittest.main()
