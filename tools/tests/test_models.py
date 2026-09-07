"""Independent geometric checks based on Minecraft's documented/model-source coordinate conventions."""
from pathlib import Path
import math
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_models import extract_shapes, convert_model, display_transforms, texture_faces
from generate_weapon_content import parse_weapons, SELECTION, LEGACY, generate


class ModelPortTests(unittest.TestCase):
    def test_muzzle_points_away_from_camera_in_both_hands(self):
        for hand in ('righthand', 'lefthand'):
            display = display_transforms()[f'firstperson_{hand}']
            # +X is the original model's muzzle direction. Minecraft applies a sign
            # reversal to a left-hand item's Y rotation before quaternion construction.
            theta = math.radians(display['rotation'][1] * (-1 if hand == 'lefthand' else 1))
            self.assertAlmostEqual(math.cos(theta), 0, places=6)
            self.assertLess(-math.sin(theta), -.99, 'Muzzle must face camera-forward (-Z)')
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

    def test_all_selected_constructor_boxes_survive_and_fit(self):
        for identifier, class_name in SELECTION.items():
            source = (LEGACY / f'java/techguns/client/models/guns/{class_name}.java').read_text()
            _, _, shapes = extract_shapes(source, class_name)
            model = convert_model(source, class_name, f'techguns:item/{identifier}')
            self.assertEqual(len(model['elements']), len(shapes), identifier)
            for element in model['elements']:
                for lower, upper in zip(element['from'], element['to']):
                    self.assertLessEqual(lower, upper)
                    self.assertGreaterEqual(lower, -16)
                    self.assertLessEqual(upper, 32)

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
