"""Independent checks of the first machine's original quantities and render coordinates."""
from pathlib import Path
import json
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_machines import ammo_press_data, generate_machine_content, RESOURCES
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons


class AmmoPressPortTests(unittest.TestCase):
    def test_original_four_build_plans_and_cost(self):
        recipes = ammo_press_data()
        self.assertEqual([r['result']['count'] for r in recipes], [12,16,8,4])
        self.assertEqual([r['plan'] for r in recipes], [0,1,2,3])
        for recipe in recipes:
            self.assertEqual(recipe['duration'], 100)
            self.assertEqual(recipe['power_per_tick'], 5)

    def test_base_uses_original_block_scale_and_world_origin(self):
        files = generate_machine_content()
        mesh = files[RESOURCES+'assets/techguns/models/block/ammo_press.obj'].decode()
        self.assertEqual(mesh.count('\no '), 36)
        base = mesh.split('\no Shape8\n')[1].split('\no ')[0]
        vertices = [list(map(float, line.split()[1:])) for line in base.splitlines() if line.startswith('v ')]
        self.assertEqual([min(v[i] for v in vertices) for i in range(3)], [0,0,0])
        self.assertEqual([max(v[i] for v in vertices) for i in range(3)], [1,.125,1])
        self.assertNotIn('\no MetalPiece\n', mesh, 'Feed material is invisible while idle in the original')
        self.assertNotIn('\no bullet1\n', mesh)

    def test_block_and_item_have_separate_atlas_references(self):
        files = generate_machine_content()
        for atlas in ('block','item'):
            model = json.loads(files[RESOURCES+f'assets/techguns/models/{atlas}/ammo_press.json'])
            self.assertEqual(model['textures']['gun'], f'techguns:{atlas}/ammo_press')
            self.assertIn(RESOURCES+f'assets/techguns/textures/{atlas}/ammo_press.png', files)

    def test_workbench_chain_keeps_engine_and_upgrade_requirements(self):
        plan = plan_crafting(parse_weapons())
        self.assertEqual(plan['recipes']['ammo_press']['result']['id'], 'techguns:ammo_press')
        self.assertEqual(plan['recipes']['ammo_press']['key']['e'], 'techguns:electricengine')
        self.assertEqual(plan['recipes']['machinestackupgrade']['key']['p'], '#c:plates/iron')
        self.assertIn('copperwire', plan['materials'])


if __name__ == '__main__': unittest.main()
