"""Check rocket source balance, dependency closure, original meshes and component-driven item models."""
from pathlib import Path
import json
import re
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import parse_weapons, generate, LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from legacy_rockets import parse_variants, VARIANTS, rocket_item_model
from legacy_models import extract_shapes, shape_vertices


class RocketPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guns = parse_weapons()
        cls.files = generate()
        cls.crafting = plan_crafting(cls.guns)

    def resource(self, path): return json.loads(self.files[(RESOURCES / path).as_posix()])

    def test_source_launcher_and_three_factories(self):
        gun = next(g for g in self.guns if g['id'] == 'rocketlauncher')
        self.assertEqual([gun[k] for k in ('capacity','fire_delay','reload_ticks','damage','minimum_damage','drop_start','drop_end','speed','lifetime','accuracy','gravity')],
                         [1,10,40,50,10,3,5,1,200,.05,.01])
        self.assertEqual((gun['projectile'], gun['automatic'], gun['forward_axis']), ('rocket',False,'+x'))
        expected = {'default': (1,1,1,1), 'nuke': (5,5,1,1), 'high_velocity': (1,.75,2,.75)}
        source_enum = (LEGACY.parents[3] / 'core/src/main/java/techguns/core/RocketVariant.java').read_text()
        for variant in parse_variants():
            fields = tuple(variant[k] for k in ('damage_multiplier','blast_radius_multiplier','velocity_multiplier','lifetime_multiplier'))
            self.assertEqual(fields, expected[variant['id']])
            match = re.search(r'\("' + variant['id'] + r'", "' + variant['ammo'] + r'", ([^\)]+)\)', source_enum)
            self.assertEqual(tuple(map(float, match[1].split(','))), fields, 'Runtime enum stays tied to original factories')

    def test_workbench_graph_preserves_loaded_ammo_and_nuclear_costs(self):
        recipes = self.crafting['recipes']
        self.assertEqual(recipes['rocketlauncher']['pattern'], ['rbb',' f '])
        self.assertEqual(recipes['rocketlauncher_alt']['result']['components']['techguns:rounds'], 0)
        for variant, ammo in VARIANTS.items():
            recipe = recipes['rocketlauncher_ammo_' + variant]
            self.assertEqual(recipe['type'], 'techguns:ammo_change_crafting')
            self.assertEqual(recipe['ingredients'], ['techguns:rocketlauncher', 'techguns:' + ammo])
            self.assertEqual(recipe['result']['components'], {'techguns:rounds':1, 'techguns:rocket_variant':variant})
        warhead = recipes['tacticalnukewarhead']
        self.assertEqual(warhead['pattern'], ['pcp','tut','pcp'])
        self.assertEqual(warhead['result']['count'], 2)
        self.assertEqual(warhead['key']['u'], '#c:ingots/uranium_enriched')
        self.assertEqual(warhead['key']['t'], 'techguns:tgx')
        self.assertIn('rocket_nuke', self.crafting['extra_ammo'])
        self.assertIn('tacticalnukewarhead', self.crafting['materials'])
        self.assertEqual(self.crafting['tags']['c:ingots/uranium_enriched'], ['techguns:enricheduranium'])

    def test_conditional_rocket_part_keeps_every_variant(self):
        model = rocket_item_model()
        self.assertEqual(model['models'][0]['model'], 'techguns:item/rocketlauncher')
        part = model['models'][1]
        self.assertEqual(part['property'], 'techguns:rocket_loaded')
        self.assertEqual(part['on_false']['type'], 'minecraft:empty')
        self.assertEqual(part['on_true']['component'], 'techguns:rocket_variant')
        self.assertEqual([case['when'] for case in part['on_true']['cases']], list(VARIANTS))
        body = self.resource('assets/techguns/models/item/rocketlauncher.json')
        for variant in VARIANTS:
            loaded = self.resource('assets/techguns/models/item/rocketlauncher_' + variant + '.json')
            self.assertEqual(loaded['display'], body['display'], 'Both parts receive the same hand and GUI transforms')

    def test_all_original_parts_and_projectile_coordinates_survive(self):
        files = self.files
        for name, count in [('rocketlauncher',22)] + [('rocketlauncher_'+v,7) for v in VARIANTS] + [('rocket_projectile_'+v,11) for v in VARIANTS]:
            obj = files[(RESOURCES / ('assets/techguns/models/item/' + name + '.obj')).as_posix()].decode()
            self.assertEqual(obj.count('\no '), count)
            # The body contains three zero-thickness sights: two real faces each, no degenerate edge quads.
            self.assertEqual(sum(line.startswith('f ') for line in obj.splitlines()), 120 if name == 'rocketlauncher' else count * 6)
        source = (LEGACY / 'java/techguns/client/models/projectiles/ModelRocket.java').read_text()
        _, _, shapes = extract_shapes(source, 'ModelRocket')
        source_points = [p for shape in shapes for p in shape_vertices(shape)]
        obj = files[(RESOURCES / 'assets/techguns/models/item/rocket_projectile_default.obj').as_posix()].decode()
        points = [list(map(float, line.split()[1:])) for line in obj.splitlines() if line.startswith('v ')]
        for i in range(3):
            self.assertAlmostEqual(min(p[i] for p in points), .5 + min(p[i] for p in source_points)/16, places=8)
            self.assertAlmostEqual(max(p[i] for p in points), .5 + max(p[i] for p in source_points)/16, places=8)
        self.assertEqual(self.resource('assets/techguns/models/item/rocket_projectile_default.json')['display'], {})

    def test_original_assets_and_explosion_damage_tags(self):
        for name, texture in [('rocketlauncher','rocketlauncher'), ('rocket','rocket'), ('rocket_high_velocity','rocket_hv'), ('rocket_nuke','rocket_nuke')]:
            self.assertEqual(self.files[(RESOURCES / f'assets/techguns/textures/item/{name}.png').as_posix()],
                             (LEGACY / f'resources/assets/techguns/textures/guns/{texture}.png').read_bytes())
        sound = 'assets/techguns/sounds/effects/nukeexplosion.ogg'
        self.assertEqual(self.files[(RESOURCES / sound).as_posix()], (LEGACY / 'resources' / sound).read_bytes())
        self.assertIn('techguns:rocket', self.resource('data/minecraft/tags/damage_type/is_explosion.json')['values'])
        for tag in ('bypasses_cooldown', 'no_knockback'):
            self.assertNotIn('techguns:rocket', self.resource(f'data/minecraft/tags/damage_type/{tag}.json')['values'])
        self.assertNotIn('techguns:rocket', self.resource('data/neoforge/tags/damage_type/is_magic.json')['values'])

    def test_launcher_and_loaded_rocket_share_one_origin(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelRocketLauncher.java').read_text()
        _, _, body = extract_shapes(source, 'ModelRocketLauncher')
        _, _, rocket = extract_shapes(source, 'ModelRocket')
        body_points = [p for shape in body for p in shape_vertices(shape)]
        source_points = [p for shape in rocket for p in shape_vertices(shape)]
        obj = self.files[(RESOURCES / 'assets/techguns/models/item/rocketlauncher_default.obj').as_posix()].decode()
        points = [list(map(float,line.split()[1:])) for line in obj.splitlines() if line.startswith('v ')]
        center_x = (min(p[0] for p in body_points) + max(p[0] for p in body_points))/2
        self.assertAlmostEqual(min(p[0] for p in points), .5 + (min(p[0] for p in source_points) - center_x)/32, places=8)
        self.assertGreater(min(p[0] for p in points), .5, 'Loaded rocket remains at the positive-X muzzle instead of being recentered')


if __name__ == '__main__': unittest.main()
