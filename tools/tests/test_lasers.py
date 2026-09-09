"""Legacy laser balance, materials and mirrored model geometry regression checks."""
from pathlib import Path
import json
import math
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import parse_weapons, generate, SELECTION, LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from legacy_models import extract_shapes, convert_mesh, shape_vertices


class LaserPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guns = {gun['id']: gun for gun in parse_weapons()}
        cls.files = generate()
        cls.recipes = plan_crafting(list(cls.guns.values()))['recipes']

    def test_two_original_laser_factories_override_nominal_gun_lifetime(self):
        for identifier, expected in {'lasergun': (45, 5, 45, 12, 0, 'energycell'),
                                     'laserpistol': (20, 6, 40, 9, .025, 'redstone_battery')}.items():
            gun = self.guns[identifier]
            self.assertEqual(tuple(gun[k] for k in ('capacity', 'fire_delay', 'reload_ticks', 'damage', 'accuracy')) + (gun['ammo']['item'],), expected)
            self.assertEqual((gun['projectile'], gun['lifetime'], gun['speed']), ('laser', 7, 100))
            self.assertEqual((gun['drop_start'], gun['drop_end'], gun['minimum_damage']), (90, 90, gun['damage']))
            self.assertTrue(gun['automatic'])
            self.assertEqual(gun['ammo']['bundles_per_magazine'], 0)
            self.assertEqual(gun['ammo']['loose_item'], '')
        self.assertEqual(self.guns['lasergun']['zoom'], .75)
        self.assertEqual(self.guns['laserpistol']['zoom'], 1)
        self.assertEqual(len(json.loads(self.files['content/ballistic-weapons.json'])), 17)
        self.assertEqual(len(json.loads(self.files['content/laser-weapons.json'])), 2)

    def test_other_energy_factories_cannot_silently_become_ballistic_guns(self):
        with patch.dict(SELECTION, {'scatterbeamrifle': 'ModelLasergun2'}, clear=True):
            with self.assertRaisesRegex(ValueError, 'Projectile factory not ported: BlasterProjectile'):
                parse_weapons()

    def test_battery_and_laser_material_costs_and_loaded_crafts(self):
        battery = self.recipes['redstone_battery']
        self.assertEqual(battery['pattern'], ['nwn', 'nrn', 'nrn'])
        self.assertEqual(battery['result']['count'], 2)
        self.assertEqual(self.recipes['redstone_battery_alt']['ingredients'], ['techguns:redstone_battery_empty', '#c:dusts/redstone'])
        barrel = self.recipes['laserbarrel']
        self.assertEqual(barrel['pattern'], ['fff', 'ggl', 'fff'])
        self.assertEqual(barrel['key']['f'], {'neoforge:ingredient_type':'techguns:tag_fallback', 'preferred':'c:ingots/electrum', 'fallback':'c:ingots/gold'})
        self.assertEqual(barrel['key']['l'], 'techguns:laserfocus')
        for identifier, capacity in [('lasergun',45), ('laserpistol',20)]:
            self.assertEqual(self.recipes[identifier]['result']['components'], {'techguns:rounds':capacity})
            self.assertEqual(self.recipes[identifier+'_alt']['result']['components'], {'techguns:rounds':0})
        self.assertNotIn('energycell', self.recipes, 'Charging station is still required; no substitute workbench recharge')

    def test_original_beam_textures_sounds_and_damage_tags(self):
        for name in ('laser3', 'laser3_start'):
            path = f'assets/techguns/textures/fx/{name}.png'
            self.assertEqual(self.files[(RESOURCES / path).as_posix()], (LEGACY / 'resources' / path).read_bytes())
        sounds = json.loads(self.files[(RESOURCES / 'assets/techguns/sounds.json').as_posix()])
        for identifier in ('lasergun', 'laserpistol'):
            for key in ('fire_sound', 'reload_sound'):
                self.assertIn(self.guns[identifier][key], sounds)
        tag = json.loads(self.files[(RESOURCES / 'data/minecraft/tags/damage_type/bypasses_cooldown.json').as_posix()])
        self.assertTrue({'techguns:bullet','techguns:laser','techguns:radiation'}.issubset(tag['values']))
        for path in ('data/neoforge/tags/damage_type/is_magic.json', 'data/minecraft/tags/damage_type/witch_resistant_to.json'):
            self.assertIn('techguns:laser', json.loads(self.files[(RESOURCES / path).as_posix()])['values'])

    def test_mirror_swaps_x_vertices_before_rotation(self):
        shape = {'box':[1,2,3,4,5,6], 'inflate':.25, 'pivot':[0,0,0], 'rotation':[0,0,0], 'render_scale':[1,1,1]}
        regular = shape_vertices(shape)
        mirrored = shape_vertices(dict(shape, mirror=True))
        for original, mirror in zip(regular, mirrored):
            self.assertAlmostEqual(original[0] + mirror[0], 6)
            self.assertEqual(original[1:], mirror[1:])
        self.assertEqual(sorted(regular), sorted(mirrored), 'Mirroring changes vertex/UV correspondence, not the box bounds')

    def test_laser_pistol_mirrored_plane_keeps_two_sides_and_uv_winding(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelLaserPistol.java').read_text()
        _, _, shapes = extract_shapes(source, 'ModelLaserPistol')
        self.assertEqual([s['name'] for s in shapes if s['mirror']], ['Front2'])
        _, obj, _ = convert_mesh(source, 'ModelLaserPistol', 'laserpistol', 'techguns:item/laserpistol', '-z')
        sections = dict((section.splitlines()[0], section) for section in obj.split('\no ')[1:])
        front = sections['Front2']
        self.assertEqual(sum(line.startswith('f ') for line in front.splitlines()), 2)
        faces = [list(map(lambda pair: int(pair.split('/')[0]), line.split()[1:])) for line in front.splitlines() if line.startswith('f ')]
        for face in faces: self.assertEqual(face, sorted(face), 'Mirroring cancels the Y-reflection winding reversal')
        normal_front = sections['Front1']
        faces = [list(map(lambda pair: int(pair.split('/')[0]), line.split()[1:])) for line in normal_front.splitlines() if line.startswith('f ')]
        for face in faces: self.assertEqual(face, sorted(face, reverse=True))
        self.assertEqual(obj.count('\no '), len(shapes), 'Every original part survives')


if __name__ == '__main__': unittest.main()
