"""Independent checks of the first machine's original quantities and render coordinates."""
from pathlib import Path
import json
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_machines import ammo_press_data, metal_press_data, blast_furnace_data, generate_machine_content, RESOURCES, LEGACY
from legacy_items import parse_stack
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


class MetalPressPortTests(unittest.TestCase):
    def test_all_original_recipes_are_selected(self):
        recipes = metal_press_data()
        self.assertEqual(len(recipes), 19)
        by_output = {r['result']['id']: r for r in recipes}
        self.assertEqual(by_output['techguns:copperwire']['result']['count'], 8)
        self.assertEqual(by_output['techguns:mechanicalpartscarbon']['result']['count'], 2)
        self.assertEqual(by_output['techguns:40mmgrenade']['second'], 'minecraft:tnt')
        self.assertEqual(by_output['techguns:sniperrounds_explosive']['first'], 'techguns:sniperrounds_incendiary')
        for recipe in recipes:
            self.assertTrue(recipe['allow_swap'])
            self.assertEqual((recipe['duration'], recipe['power_per_tick']), (100,20))

    def test_material_tags_have_original_items(self):
        plan = plan_crafting(parse_weapons())
        self.assertEqual(plan['tags']['c:ingots/tin'], ['techguns:ingottin'])
        self.assertEqual(plan['tags']['c:plates/titanium'], ['techguns:platetitanium'])
        self.assertIn('sniperrounds_explosive', plan['extra_ammo'])
        self.assertNotIn('sniperrounds_explosive', plan['materials'])

    def test_machine_crafting_preserves_cheaper_plate_variant(self):
        recipes = plan_crafting(parse_weapons())['recipes']
        self.assertEqual(recipes['metal_press']['key']['b'], '#c:storage_blocks/iron')
        self.assertEqual(recipes['metal_press_alt']['key']['b'], '#c:plates/iron')
        self.assertEqual(recipes['metal_press']['result']['id'], 'techguns:metal_press')

    def test_idle_machine_preserves_all_static_parts(self):
        files = generate_machine_content()
        mesh = files[RESOURCES+'assets/techguns/models/block/metal_press.obj'].decode()
        self.assertEqual(mesh.count('\no '), 35)
        self.assertNotIn('\no MetalPiece\n', mesh)
        for moving_part in range(1,7): self.assertIn(f'\no p{moving_part}\n', mesh)


class BlastFurnacePortTests(unittest.TestCase):
    def test_four_original_counted_recipes(self):
        recipes = blast_furnace_data()
        self.assertEqual(len(recipes), 4)
        self.assertEqual([r['first_count'] for r in recipes], [4,4,1,3])
        self.assertEqual([r['second_count'] for r in recipes], [1,1,1,1])
        self.assertEqual([r['duration'] for r in recipes], [800,800,200,100])
        self.assertEqual([r['result']['count'] for r in recipes], [4,4,1,4])
        self.assertTrue(all(r['power_per_tick'] == 10 for r in recipes))
        self.assertEqual(recipes[1]['second'], 'minecraft:charcoal')
        self.assertEqual(recipes[3]['second'], '#c:ingots/tin')

    def test_vanilla_metadata_is_explicit(self):
        self.assertEqual(parse_stack('new ItemStack(Items.COAL,1,1)'), {'id':'minecraft:charcoal','count':1})
        with self.assertRaises(ValueError): parse_stack('new ItemStack(Items.COAL,1,7)')
        recipe = plan_crafting(parse_weapons())['recipes']['blast_furnace']
        self.assertEqual(recipe['key']['s'], 'minecraft:stone_bricks')
        self.assertEqual(recipe['result']['id'], 'techguns:blast_furnace')

    def test_original_furnace_geometry_and_uvs_remain_exact(self):
        original = json.loads((LEGACY / 'resources/assets/techguns/models/block/blast_furnace.json').read_text())
        files = generate_machine_content()
        self.assertEqual(len(original['elements']), 7)
        for atlas in ('block','item'):
            model = json.loads(files[RESOURCES+f'assets/techguns/models/{atlas}/blast_furnace.json'])
            self.assertEqual(model['elements'], original['elements'])
            self.assertTrue(all(texture.startswith(f'techguns:{atlas}/') for texture in model['textures'].values()))


if __name__ == '__main__': unittest.main()
