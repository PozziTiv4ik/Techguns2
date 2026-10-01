import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate, parse_weapons, resolve_asset
from legacy_crafting import plan_crafting
from legacy_grinder import grinder_data
from legacy_models import extract_shapes, convert_mesh
from legacy_scatterbeam import *


class ScatterbeamPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = parse_weapons()
        cls.gun = next(g for g in cls.weapons if g['id'] == 'scatterbeamrifle')
        cls.files = generate()

    def test_final_speed_scaled_ttl_and_pellet_factory(self):
        self.assertEqual([self.gun[k] for k in ('capacity', 'fire_delay', 'reload_ticks', 'damage', 'speed', 'lifetime', 'extra_pellets', 'pellet_spread')],
                         [40, 7, 45, 6, 2, 15, 4, .15])
        self.assertEqual(self.gun['projectile'], 'blaster')
        self.assertTrue(self.gun['automatic']); self.assertFalse(self.gun['zoom_centered'])
        self.assertEqual(self.gun['ammo'], {'item':'energycell', 'empty_item':'energycellempty', 'loose_item':'', 'bundles_per_magazine':0, 'individual':False})
        self.assertEqual(scaled_projectile_lifetime(90, 5), 18, 'Gauss also calls GenericGun.getScaledTTL')
        self.assertEqual(scaled_projectile_lifetime(31, 2), 16, 'Round up partial ticks')

    def test_active_blaster_overrides_keep_water_and_block_contracts(self):
        params = blaster_parameters()
        self.assertEqual(params['drag'], .99); self.assertEqual(params['gravity'], 0)
        self.assertIn('extinguish', params['water']); self.assertIn('no explosion', params['block_impact'])
        self.assertIn('ENERGY', params['damage'])
        self.assertTrue(json.loads(self.files['content/scatterbeam-behavior.json'])['pending'])

    def test_missing_source_recipes_are_preserved_not_borrowed_from_blasterrifle(self):
        for path in (LEGACY / 'resources/assets/techguns/recipes').glob('*.json'):
            if path.name.startswith('_'): continue  # _constants.json contains a list of ore aliases.
            source = json.loads(path.read_text())
            self.assertNotEqual(source.get('result', {}).get('item'), 'techguns:scatterbeamrifle', path.name)
        graph = plan_crafting(self.weapons)
        self.assertNotIn('scatterbeamrifle', graph['recipes']); self.assertNotIn('scatterbeamrifle_alt', graph['recipes'])
        grinder = grinder_data()
        self.assertIn('scatterbeamrifle', grinder['ported_weapons_without_source_recipe'])
        self.assertFalse(any(r['input'] == 'techguns:scatterbeamrifle' for r in grinder['recipes']))

    def test_energy_damage_keeps_armor_magic_and_separate_impulse(self):
        for namespace, tag in (('minecraft','bypasses_cooldown'), ('minecraft','no_knockback'), ('minecraft','witch_resistant_to'), ('neoforge','is_magic')):
            self.assertIn('techguns:blaster', json.loads(self.files[RESOURCES + f'data/{namespace}/tags/damage_type/{tag}.json'])['values'])
        for tag in ('bypasses_armor', 'is_projectile', 'is_fire', 'is_explosion'):
            self.assertNotIn('techguns:blaster', json.loads(self.files.get(RESOURCES + f'data/minecraft/tags/damage_type/{tag}.json', b'{"values":[]}'))['values'])

    def test_model_keeps_all_parts_and_splits_wrapped_uv_faces(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelLasergun2.java').read_text()
        _, _, shapes = extract_shapes(source, 'ModelLasergun2')
        mesh = self.files[RESOURCES + 'assets/techguns/models/item/scatterbeamrifle.obj'].decode().splitlines()
        self.assertEqual([line[2:] for line in mesh if line.startswith('o ')], [s['name'] for s in shapes])
        _, raw, _ = convert_mesh(source, 'ModelLasergun2', 'scatterbeamrifle', 'techguns:item/scatterbeamrifle', '-z')
        self.assertGreater(sum(l.startswith('f ') for l in mesh), raw.count('\nf '), 'Repeat seams split nondegenerate faces')
        vertices = [list(map(float, l.split()[1:])) for l in mesh if l.startswith('v ')]
        area = 0
        for n in range(0, len(vertices), 4):
            a, b, _, d = vertices[n:n+4]
            u, v = [x-y for x,y in zip(b,a)], [x-y for x,y in zip(d,a)]
            area += math.sqrt(sum(x*x for x in (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])))
        expected = sum(2 * (s['box'][3]*s['box'][4] + s['box'][3]*s['box'][5] + s['box'][4]*s['box'][5]) for s in shapes) / 32**2
        self.assertAlmostEqual(area, expected, places=7, msg='UV splitting preserves the original surface area, including flat sheets')
        for line in mesh:
            if line.startswith('vt '): self.assertTrue(all(0 <= float(v) <= 1 for v in line.split()[1:]))
        model = json.loads(self.files[RESOURCES + 'assets/techguns/models/item/scatterbeamrifle.json'])
        self.assertEqual(model['loader'], 'neoforge:obj')

    def test_source_texture_sprite_audio_and_both_names(self):
        self.assertEqual(self.files[RESOURCES + 'assets/techguns/textures/item/scatterbeamrifle.png'], resolve_asset('textures/guns/lasergunnew.png').read_bytes())
        self.assertEqual(self.files[RESOURCES + 'assets/techguns/textures/fx/laser3.png'], resolve_asset('textures/fx/laser3.png').read_bytes())
        actual = json.loads(self.files[RESOURCES + 'assets/techguns/sounds.json'])
        source = json.loads(resolve_asset('sounds.json').read_text())
        for name in ('guns.lasergunfire', 'guns.lasergunreload'):
            self.assertEqual(actual[name]['sounds'], source[name]['sounds'])
            for sound in actual[name]['sounds']:
                path = 'sounds/' + sound.removeprefix('techguns:') + '.ogg'
                self.assertEqual(self.files[RESOURCES + 'assets/techguns/' + path], resolve_asset(path).read_bytes())
        for lang in ('ru_ru', 'en_us'):
            text = json.loads(self.files[RESOURCES + f'assets/techguns/lang/{lang}.json'])
            self.assertNotEqual(text['item.techguns.scatterbeamrifle'], 'scatterbeamrifle')


if __name__ == '__main__': unittest.main()
