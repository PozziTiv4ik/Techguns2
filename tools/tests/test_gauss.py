import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate, parse_weapons, resolve_asset
from legacy_items import arguments, ammo_components, shared_items
from legacy_crafting import plan_crafting, convert_recipe
from legacy_gauss import *


class GaussPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = parse_weapons()
        cls.gun = next(g for g in cls.weapons if g['id'] == 'gaussrifle')
        cls.files = generate()
        cls.graph = plan_crafting(cls.weapons)

    def test_nested_java_arrays_remain_distinct_arguments(self):
        self.assertEqual(arguments('new ItemStack[] {A, B}, new ItemStack[] {EMPTY, C}, f(1, 2), 1'),
                         ['new ItemStack[] {A, B}', 'new ItemStack[] {EMPTY, C}', 'f(1, 2)', '1'])
        self.assertEqual(ammo_components(self.gun['ammo']), [
            {'item': 'gaussrifleslugs', 'empty_item': '', 'loose_item': 'gaussrifleslugs'},
            {'item': 'energycell', 'empty_item': 'energycellempty', 'loose_item': ''}])
        self.assertNotIn('item', self.gun['ammo'], 'A compound family must not become one magazine')

    def test_source_stats_factory_and_constant_damage(self):
        self.assertEqual([self.gun[k] for k in ('capacity', 'fire_delay', 'reload_ticks', 'damage', 'minimum_damage', 'speed', 'lifetime', 'penetration')],
                         [8, 30, 60, 40, 40, 5, 18, 2])
        self.assertEqual(self.gun['projectile'], 'gauss')
        self.assertEqual(gauss_parameters(), {'air_drag': .99, 'water_drag': .85, 'gravity': 0, 'rechamber_sound': 'guns.gaussriflerechamber'})

    def test_loaded_and_empty_crafts_keep_both_inputs(self):
        for name in ('gaussrifle', 'gaussrifle_alt'):
            source = json.loads((LEGACY / f'resources/assets/techguns/recipes/{name}.json').read_text())
            self.assertEqual(self.graph['recipes'][name], convert_recipe(source, shared_items(), {g['id']: g for g in self.weapons}))
        self.assertEqual(self.graph['recipes']['gaussrifle']['result']['components'], {'techguns:rounds': 8})
        self.assertEqual(self.graph['recipes']['gaussrifle_alt']['result']['components'], {'techguns:rounds': 0})
        self.assertEqual(self.graph['recipes']['gaussbarrel']['pattern'], ['pww', 'bbc', 'pww'])

    def test_machine_recipes_preserve_source_costs(self):
        recipe = lambda name: json.loads(self.files[RESOURCES + 'data/techguns/recipe/' + name + '.json'])
        press = recipe('metal_press/gaussrifleslugs')
        self.assertEqual(press['result'], {'id': 'techguns:gaussrifleslugs', 'count': 4})
        self.assertIn('c:plates/obsidian_steel', json.dumps(press)); self.assertIn('c:plates/titanium', json.dumps(press))
        outputs = recipe('grinder/gaussrifle')['outputs']
        self.assertEqual([o['result'] for o in outputs], [{'id':'techguns:'+name,'count':count} for name,count in
                         [('carbonfibers',9),('ingottitanium',1),('plasticsheet',1),('circuitboardelite',1)]])
        self.assertFalse(recipe('grinder/gaussrifle')['random'])
        self.assertEqual(recipe('charging_station/energycell')['charge_amount'], 50000)

    def test_obj_preserves_faces_uvs_parts_and_source_orientation(self):
        source = (LEGACY / 'resources/assets/techguns/models/item/gaussrifle.obj').read_text().splitlines()
        actual = self.files[RESOURCES + 'assets/techguns/models/item/gaussrifle.obj'].decode().splitlines()
        for prefix in ('f ', 'vt ', 'o '):
            self.assertEqual([s for s in source if s.startswith(prefix)], [s for s in actual if s.startswith(prefix)])
        vertices = [list(map(float, s.split()[1:])) for s in actual if s.startswith('v ')]
        self.assertAlmostEqual(max(v[0] for v in vertices) - min(v[0] for v in vertices), 2.5)
        self.assertTrue(gauss_model()['flip_v'])
        catalog = json.loads(self.files['content/gauss-projectile.json'])
        self.assertEqual((catalog['model']['vertices'], catalog['model']['faces'], catalog['model']['parts']), (436, 326, 55))
        self.assertTrue(catalog['pending'])

    def test_primary_damage_keeps_armor_and_separate_impulse(self):
        for tag in ('is_projectile', 'bypasses_cooldown', 'no_knockback'):
            self.assertIn('techguns:gauss', json.loads(self.files[RESOURCES + f'data/minecraft/tags/damage_type/{tag}.json'])['values'])
        for tag in ('bypasses_armor', 'is_fire', 'is_explosion'):
            self.assertNotIn('techguns:gauss', json.loads(self.files.get(RESOURCES + f'data/minecraft/tags/damage_type/{tag}.json', b'{"values":[]}'))['values'])

    def test_original_png_ogg_and_translations(self):
        self.assertEqual(self.files[RESOURCES + 'assets/techguns/textures/item/gaussrifle.png'], resolve_asset('textures/guns/gaussrifle.png').read_bytes())
        sounds = json.loads(self.files[RESOURCES + 'assets/techguns/sounds.json'])
        original = json.loads(resolve_asset('sounds.json').read_text())
        for name in ('guns.gaussriflefire', 'guns.gaussriflereload', 'guns.gaussriflerechamber'):
            self.assertEqual(sounds[name]['sounds'], original[name]['sounds'])
            for sound in sounds[name]['sounds']:
                file = 'sounds/' + sound.removeprefix('techguns:') + '.ogg'
                self.assertEqual(self.files[RESOURCES + 'assets/techguns/' + file], resolve_asset(file).read_bytes())
        for lang in ('en_us', 'ru_ru'):
            actual = json.loads(self.files[RESOURCES + f'assets/techguns/lang/{lang}.json'])
            self.assertIn('item.techguns.gaussrifle', actual)
            for key, value in gauss_translations(lang).items(): self.assertEqual(actual[key], value)


if __name__ == '__main__': unittest.main()
