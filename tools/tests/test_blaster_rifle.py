import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate, parse_weapons, resolve_asset
from legacy_crafting import plan_crafting, convert_recipe
from legacy_grinder import grinder_data
from legacy_items import LEGACY, shared_items
from legacy_models import extract_shapes
from legacy_scatterbeam import RESOURCES, blaster_parameters


class BlasterRifleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = parse_weapons()
        cls.gun = next(g for g in cls.weapons if g['id'] == 'blasterrifle')
        cls.files = generate()
        cls.graph = plan_crafting(cls.weapons)

    def test_source_parameters_keep_the_rifle_distinct_from_scatterbeam(self):
        self.assertEqual([self.gun[k] for k in ('capacity', 'fire_delay', 'reload_ticks', 'damage', 'minimum_damage', 'drop_start', 'drop_end', 'speed', 'lifetime', 'penetration')],
                         [50, 5, 45, 10, 8, 25, 35, 2, 30, 1])
        self.assertEqual(self.gun['projectile'], 'blaster'); self.assertEqual(self.gun['extra_pellets'], 0)
        self.assertTrue(self.gun['automatic']); self.assertTrue(self.gun['zoom_centered'])
        self.assertEqual(self.gun['npc_ai'], {'range':24, 'interval':30, 'burst':5, 'shot_delay':3, 'forward_offset':0})
        for name, expected in (('blaster-rifle-weapon', 'blasterrifle'), ('scatterbeam-weapon', 'scatterbeamrifle')):
            self.assertEqual([g['id'] for g in json.loads(self.files['content/'+name+'.json'])], [expected])

    def test_loaded_and_empty_recipes_keep_source_costs_and_hardened_glass_fallback(self):
        for name, rounds in (('blasterrifle', 50), ('blasterrifle_alt', 0)):
            source = json.loads((LEGACY / f'resources/assets/techguns/recipes/{name}.json').read_text())
            actual = self.graph['recipes'][name]
            self.assertEqual(actual, convert_recipe(source, shared_items(), {g['id']:g for g in self.weapons}))
            self.assertEqual(actual['result']['components'], {'techguns:rounds':rounds})
            self.assertEqual(actual['key']['g'], {'neoforge:ingredient_type':'techguns:tag_fallback', 'preferred':'c:glass_blocks/hardened', 'fallback':'c:glass_blocks'})
        self.assertEqual(self.graph['recipes']['laserbarrel']['key']['f']['preferred'], 'c:ingots/electrum')
        self.assertEqual(self.graph['recipes']['laserbarrel']['key']['g']['preferred'], 'c:glass_blocks/hardened')

    def test_grinder_preserves_carbon_plastic_redstone_and_preferred_electrum(self):
        recipe = next(r for r in grinder_data()['recipes'] if r['id'] == 'blasterrifle')
        self.assertFalse(recipe['random'])
        self.assertEqual([o['result'] for o in recipe['outputs']], [
            {'id':'techguns:carbonfibers','count':3}, {'id':'techguns:plasticsheet','count':1},
            {'id':'minecraft:redstone','count':20}, {'id':'minecraft:gold_ingot','count':3}])
        self.assertEqual(recipe['outputs'][-1]['preferred_tag'], 'c:ingots/electrum')

    def test_generic_flight_contract_uses_displacement_before_collision_movement(self):
        params = blaster_parameters()
        self.assertIn('before movement', params['distance_damage'])
        self.assertEqual(params['drag'], .99); self.assertEqual(params['gravity'], 0)
        self.assertIn('same drag as air', params['water'])

    def test_original_model_retains_all_twenty_three_parts(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelBlasterRifle.java').read_text()
        width, height, shapes = extract_shapes(source, 'ModelBlasterRifle')
        model = json.loads(self.files[RESOURCES + 'assets/techguns/models/item/blasterrifle.json'])
        self.assertEqual((width, height, len(shapes)), (128, 64, 23))
        self.assertEqual([s['name'] for s in model['elements']], [s['name'] for s in shapes])
        self.assertTrue(any(s.get('rotation') for s in model['elements']), 'Original rotated barrel and scope retained')

    def test_source_texture_audio_and_names_are_copied(self):
        self.assertEqual(self.files[RESOURCES + 'assets/techguns/textures/item/blasterrifle.png'], resolve_asset('textures/guns/blasterrifle.png').read_bytes())
        sounds = json.loads(self.files[RESOURCES + 'assets/techguns/sounds.json'])
        source = json.loads(resolve_asset('sounds.json').read_text())
        for name in (self.gun['fire_sound'], self.gun['reload_sound']):
            self.assertEqual(sounds[name]['sounds'], source[name]['sounds'])
            for sound in sounds[name]['sounds']:
                path = 'sounds/' + sound.removeprefix('techguns:') + '.ogg'
                self.assertEqual(self.files[RESOURCES + 'assets/techguns/' + path], resolve_asset(path).read_bytes())
        for lang in ('en_us', 'ru_ru'):
            names = json.loads(self.files[RESOURCES + f'assets/techguns/lang/{lang}.json'])
            self.assertNotEqual(names['item.techguns.blasterrifle'], 'blasterrifle')


if __name__ == '__main__': unittest.main()
