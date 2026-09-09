"""Independent geometric checks based on Minecraft's documented/model-source coordinate conventions."""
from pathlib import Path
import math
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_models import extract_shapes, convert_model, convert_mesh, display_transforms, texture_faces, shape_vertices, repeat_uv_interval
from generate_weapon_content import parse_weapons, SELECTION, LEGACY, generate


class ModelPortTests(unittest.TestCase):
    def test_muzzle_points_away_from_camera_in_both_hands(self):
        for forward in ('+x', '-z'):
            for hand in ('righthand', 'lefthand'):
                display = display_transforms(forward)[f'firstperson_{hand}']
                # Minecraft negates Y for the left hand before quaternion construction.
                theta = math.radians(display['rotation'][1] * (-1 if hand == 'lefthand' else 1))
                x, z = (1, 0) if forward == '+x' else (0, -1)
                self.assertAlmostEqual(x*math.cos(theta) + z*math.sin(theta), 0, places=6)
                self.assertLess(-x*math.sin(theta) + z*math.cos(theta), -.99, 'Muzzle must face camera-forward (-Z)')
                self.assertEqual(display['rotation'][0], 0, 'Gun grip must remain below barrel')

    def test_uvs_preserve_legacy_vertices_after_y_reflection(self):
        faces = texture_faces(4, 3, 5, 2, 3, 64, 32)
        # Legacy west quad vertex t0=(minX,minY,minZ) has UV=(u+depth,v+depth).
        # Reflecting Y maps it to modern FaceInfo.WEST vertex 0.
        self.assertEqual(faces['west']['uv'][:2], [7/4, 6/2])
        self.assertEqual(faces['west']['uv'][2:], [4/4, 8/2])
        # Legacy DOWN becomes JSON UP and its V coordinates reverse.
        self.assertEqual(faces['up']['uv'], [7/4, 6/2, 12/4, 3/2])
        self.assertEqual(faces['down']['uv'], [12/4, 3/2, 17/4, 6/2])

    def test_arbitrary_legacy_rotation_is_not_rounded(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelGoldenRevolver.java').read_text()
        model = convert_model(source, 'ModelGoldenRevolver', 'techguns:item/goldenrevolver')
        shape = next(e for e in model['elements'] if e['name'] == 'Shape1')
        self.assertAlmostEqual(shape['rotation']['x'], -math.degrees(.3626969), places=6)

    def test_negative_laser_uv_keeps_repeat_span_inside_sprite(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelLasergun.java').read_text()
        model = convert_model(source, 'ModelLasergun', 'techguns:item/lasergun')
        glow = next(e for e in model['elements'] if e['name'] == 'Glow1')
        self.assertEqual(glow['faces']['west']['uv'], [16, 13.25, 14.5, 14.25])
        for element in model['elements']:
            for face in element['faces'].values():
                self.assertTrue(all(0 <= uv <= 16 for uv in face['uv']), '26.2 transparency scan requires bounded UVs')
        self.assertEqual(repeat_uv_interval(58,64,64), (58,64))
        self.assertEqual(repeat_uv_interval(128,134,64), (0,6))
        with self.assertRaisesRegex(ValueError,'repeat seam'): repeat_uv_interval(-2,4,64)

    def test_all_selected_constructor_boxes_survive_and_fit(self):
        for identifier, class_name in SELECTION.items():
            source = (LEGACY / f'java/techguns/client/models/guns/{class_name}.java').read_text()
            _, _, shapes = extract_shapes(source, class_name)
            if any(s['inflate'] != 0 or s['render_scale'] != [1,1,1] or s['mirror'] for s in shapes) or identifier == 'm4_infiltrator':
                _, mesh, _ = convert_mesh(source, class_name, identifier, 'techguns:item/'+identifier, '-z')
                self.assertEqual(mesh.count('\no '), len(shapes), identifier)
                continue
            model = convert_model(source, class_name, f'techguns:item/{identifier}')
            self.assertEqual(len(model['elements']), len(shapes), identifier)
            for element in model['elements']:
                for lower, upper in zip(element['from'], element['to']):
                    self.assertLessEqual(lower, upper)
                    self.assertGreaterEqual(lower, -16)
                    self.assertLessEqual(upper, 32)

    def test_mac10_negative_inflation_and_rotation_are_preserved(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelMac10.java').read_text()
        _, _, shapes = extract_shapes(source, 'ModelMac10')
        grip = next(s for s in shapes if s['name'] == 'Grip4')
        first = shape_vertices(grip)[0]
        self.assertAlmostEqual(first[0], -1.4, places=6)
        angle = -.2181661564992912
        self.assertAlmostEqual(first[1], 2.5 + .1*math.cos(angle) - .1*math.sin(angle), places=6)
        stock = next(s for s in shapes if s['name'] == 'Stock2')
        self.assertEqual(stock['rotation'], [0,0,0], 'Omitted rotation means identity')

    def test_vector_render_scales_are_not_lost(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelVector.java').read_text()
        _, _, shapes = extract_shapes(source, 'ModelVector')
        self.assertEqual(next(s for s in shapes if s['name'] == 'Receiver05')['render_scale'], [.98,1,1])
        self.assertEqual(next(s for s in shapes if s['name'] == 'Eotech04')['render_scale'], [.8,.8,.8])

    def test_empty_magazine_really_omits_cartridges(self):
        for name in ('ModelARMagazine', 'ModelLmgMag', 'ModelAS50Mag'):
            source = (LEGACY / f'java/techguns/client/models/items/{name}.java').read_text()
            _, _, full = extract_shapes(source, name, {'empty': False})
            _, _, empty = extract_shapes(source, name, {'empty': True})
            self.assertLess(len(empty), len(full), name)
            self.assertTrue({s['name'] for s in empty} < {s['name'] for s in full})

    def test_new_ammo_and_renderer_metadata_follow_original_sources(self):
        guns = {g['id']: g for g in parse_weapons()}
        self.assertEqual(guns['pistol']['ammo']['item'], 'pistolmagazine')
        self.assertEqual(guns['m4']['ammo']['bundles_per_magazine'], 3)
        self.assertEqual(guns['lmg']['ammo']['bundles_per_magazine'], 8)
        self.assertEqual(guns['as50']['ammo']['item'], 'as50magazine')
        self.assertEqual(guns['pistol']['forward_axis'], '-z')
        self.assertEqual(guns['revolver']['forward_axis'], '+x')
        self.assertEqual(guns['m4_infiltrator']['fire_sound'], 'guns.silencedm4fire')

    def test_source_balance_is_not_flattened_to_the_revolver(self):
        guns = {g['id']: g for g in parse_weapons()}
        self.assertEqual(guns['thompson']['capacity'], 20)
        self.assertTrue(guns['thompson']['automatic'])
        self.assertEqual(guns['boltaction']['damage'], 16)
        self.assertEqual(guns['handcannon']['gravity'], .015)
        self.assertEqual(guns['sawedoff']['extra_pellets'] + 1, 8)
        self.assertTrue(guns['sawedoff']['ammo']['individual'])
        self.assertFalse(guns['revolver']['ammo']['individual'])

    def test_generation_is_deterministic(self):
        self.assertEqual(generate(), generate())


if __name__ == '__main__': unittest.main()
