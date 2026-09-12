"""Grinder source contracts: helper quirks, expected quantities, recipes and original artwork."""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_grinder import grinder_data, generate_grinder_content, LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons, generate


class GrinderPortTests(unittest.TestCase):
    def test_selected_recipes_and_missing_source_are_explicit(self):
        data = grinder_data(); recipes = data['recipes']
        self.assertEqual(len(recipes), 34)
        self.assertEqual(sum(r.get('random', False) for r in recipes), 9)
        self.assertEqual(sum(r.get('armor', False) for r in recipes), 4)
        self.assertEqual(data['ported_weapons_without_source_recipe'], ['laserpistol'])
        self.assertEqual(len(data['unported_weapon_inputs']), 18)
        self.assertEqual((data['duration'], data['power_per_tick'], data['energy_capacity']), (100, 5, 20000))
        self.assertEqual(data['output_slots'], list(range(2, 11)))
        gun=(LEGACY / 'java/techguns/items/guns/GenericGun.java').read_text()
        self.assertIn('stack.setItemDamage(0)', gun)
        self.assertIn('return tags.getShort("ammo")', gun)
        self.assertIn('tags.setShort("ammo", (short) (ammo-amount))', gun)

    def test_steel_helper_third_argument_really_is_ingots(self):
        recipes = {r['id']: r for r in grinder_data()['recipes']}
        for name in ('mac10', 'rocketlauncher'):
            steel = [r['result']['count'] for r in recipes[name]['outputs'] if r['result']['id'] == 'techguns:ingotsteel']
            self.assertEqual(steel, [1, 5])
        self.assertIn({'result': {'id': 'minecraft:oak_log', 'count': 1}}, recipes['revolver']['outputs'])
        gold = recipes['lasergun']['outputs'][-1]
        self.assertEqual(gold, {'result': {'id': 'minecraft:gold_ingot', 'count': 3}, 'preferred_tag': 'c:ingots/electrum'})

    def test_ammunition_factors_are_not_clamped_to_probability(self):
        recipes = {r['id']: r for r in grinder_data()['recipes']}
        expected = {'riflerounds': [1, 1, .125], 'pistolrounds': [.75, 1.5, 1/12], 'sniperrounds': [1, 1, .25], 'shotgunrounds': [.5, 1, .0625]}
        for name, factors in expected.items():
            self.assertEqual([o['factor'] for o in recipes[name]['outputs']], factors)
            self.assertEqual([o['factor'] for o in recipes[name + '_incendiary']['outputs']], factors + [.125])
        self.assertEqual([o['factor'] for o in recipes['sniperrounds_explosive']['outputs']], [1, 1, .25, .125, .5])
        self.assertEqual([o['result']['count'] for o in recipes['sniperrounds']['outputs']], [2, 4, 1])

    def test_block_and_inventory_geometry_and_pixels_survive(self):
        files = generate_grinder_content(); assets = LEGACY / 'resources/assets/techguns'
        for atlas, source, count in [('block', 'models/block/grinder.json', 19), ('item', 'models/item/simplemachine2_grinder_inv.json', 21)]:
            original = json.loads((assets / source).read_text()); actual = json.loads(files[RESOURCES + f'assets/techguns/models/{atlas}/grinder.json'])
            self.assertEqual(actual['elements'], original['elements']); self.assertEqual(len(actual['elements']), count)
            self.assertEqual(actual.get('display'), original.get('display'))
            for texture in set(original['textures'].values()):
                name = texture.split('/')[-1]
                self.assertEqual(files[RESOURCES + f'assets/techguns/textures/{atlas}/{name}.png'], (assets / f'textures/blocks/{name}.png').read_bytes())
        self.assertEqual(files[RESOURCES + 'assets/techguns/textures/gui/grinder.png'], (assets / 'textures/gui/grinder_gui.png').read_bytes())

    def test_four_sided_roller_has_original_dimensions_and_uv_direction(self):
        model = json.loads(generate_grinder_content()[RESOURCES + 'assets/techguns/models/item/grinder_roll.json'])['elements'][0]
        self.assertEqual(model['from'], [2, 6.5, 6.5]); self.assertEqual(model['to'], [14, 9.5, 9.5])
        self.assertEqual(set(model['faces']), {'up', 'down', 'north', 'south'})
        self.assertEqual(model['faces']['north']['uv'], [14, 5, 2, 2])
        self.assertEqual(model['faces']['south']['uv'], [2, 2, 14, 5])

    def test_original_machine_recipe_and_shared_graph(self):
        graph = plan_crafting(parse_weapons()); recipe = graph['recipes']['grinder']
        self.assertEqual(recipe['pattern'], ['imi', 'mem', 'iri'])
        self.assertEqual(recipe['key'], {'i': '#c:plates/iron', 'm': 'techguns:mechanicalpartsiron', 'e': 'techguns:electricengine', 'r': '#c:dusts/redstone'})
        self.assertEqual(recipe['result'], {'id': 'techguns:grinder', 'count': 1})
        self.assertEqual(graph['catalog']['block_metadata']['techguns:simplemachine2@8'], 'techguns:grinder')
        files = generate()
        for recipe in grinder_data()['recipes']:
            for identifier in [recipe['input']] + [o['result']['id'] for o in recipe['outputs']]:
                if identifier.startswith('techguns:'): self.assertIn(RESOURCES + 'assets/techguns/items/' + identifier.split(':')[1] + '.json', files)

    def test_original_grinder_audio_is_copied_without_reencoding(self):
        files = generate(); sounds = json.loads(files[RESOURCES + 'assets/techguns/sounds.json'])
        original = json.loads((LEGACY / 'resources/assets/techguns/sounds.json').read_text())
        for name in ('machines.grinder.start', 'machines.grinder.work'):
            self.assertEqual(sounds[name]['sounds'], original[name]['sounds'])
            for sound in sounds[name]['sounds']:
                path = (sound if isinstance(sound, str) else sound['name']).split(':')[-1]
                self.assertEqual(files[RESOURCES + 'assets/techguns/sounds/' + path + '.ogg'], (LEGACY / ('resources/assets/techguns/sounds/' + path + '.ogg')).read_bytes())


if __name__ == '__main__': unittest.main()
