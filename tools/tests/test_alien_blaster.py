import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate, parse_weapons, resolve_asset
from legacy_alien_blaster import alien_parameters, ghastling_projectile
from legacy_crafting import plan_crafting
from legacy_grinder import grinder_data
from legacy_items import LEGACY
from legacy_models import extract_shapes, strip_comments

RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


class AlienBlasterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = parse_weapons()
        cls.gun = next(g for g in cls.weapons if g['id'] == 'alienblaster')
        cls.files = generate()

    def test_weapon_factory_ttl_and_energy_cell_are_extracted(self):
        self.assertEqual([self.gun[k] for k in ('capacity', 'fire_delay', 'reload_ticks', 'damage', 'minimum_damage', 'speed', 'lifetime', 'penetration')],
                         [10, 8, 35, 16, 16, 1, 40, 1])
        self.assertEqual(self.gun['projectile'], 'alien_blaster'); self.assertEqual(self.gun['extra_pellets'], 0)
        self.assertTrue(self.gun['automatic']); self.assertEqual(self.gun['accuracy'], 0)
        self.assertEqual(self.gun['ammo'], {'item':'energycell', 'empty_item':'energycellempty', 'loose_item':'', 'bundles_per_magazine':0, 'individual':False})
        self.assertEqual(self.gun['npc_ai'], {'range':24, 'interval':40, 'burst':0, 'shot_delay':0, 'forward_offset':0})
        self.assertEqual(json.loads(self.files['content/alien-blaster-weapon.json']), [self.gun])

    def test_shared_behavior_has_ordinary_knockback_water_drag_and_no_explosion(self):
        d = alien_parameters()
        self.assertEqual((d['ignite_seconds'], d['block_ignite_chance'], d['air_drag'], d['water_drag'], d['gravity']), (3, .35, .99, .85, 0))
        self.assertIn('ordinary knockback', d['damage']); self.assertIn('no explosion', d['block_impact'])
        tags = {name: json.loads(self.files[RESOURCES + f'data/minecraft/tags/damage_type/{name}.json'])['values']
                for name in ('bypasses_cooldown', 'no_knockback', 'witch_resistant_to')}
        self.assertIn('techguns:alien_blast', tags['bypasses_cooldown']); self.assertIn('techguns:alien_blast', tags['witch_resistant_to'])
        self.assertNotIn('techguns:alien_blast', tags['no_knockback'])

    def test_ghastling_factory_stays_separate_and_drives_both_catalogs(self):
        d = ghastling_projectile()
        self.assertEqual(d, {'damage':6, 'speed':1.5, 'spread':.05, 'lifetime':200, 'ignite_seconds':3, 'block_damage':False, 'kind':'FIRE'})
        self.assertEqual(json.loads(self.files['content/ghastling.json'])['projectile'], d)
        self.assertEqual(json.loads(self.files['content/alien-blaster-behavior.json'])['ghastling_projectile'], d)

    def test_no_survival_or_grinder_recipe_is_invented(self):
        for path in (LEGACY / 'resources/assets/techguns/recipes').glob('*.json'):
            if path.name.startswith('_'): continue
            self.assertNotEqual(json.loads(path.read_text()).get('result', {}).get('item'), 'techguns:alienblaster', path.name)
        source = strip_comments((LEGACY / 'java/techguns/TGMachineRecipes.java').read_text())
        self.assertNotIn('TGuns.alienblaster', source)
        graph = plan_crafting(self.weapons)
        self.assertFalse(any(r.get('result', {}).get('id') == 'techguns:alienblaster' for r in graph['recipes'].values()))
        self.assertNotIn('alienblaster', {r['id'] for r in grinder_data()['recipes']})

    def test_all_twenty_four_parts_and_original_renderer_binding(self):
        source = (LEGACY / 'java/techguns/client/models/guns/ModelAlienBlaster.java').read_text()
        width, height, shapes = extract_shapes(source, 'ModelAlienBlaster')
        self.assertEqual((width, height, len(shapes)), (128, 64, 24))
        model = json.loads(self.files[RESOURCES + 'assets/techguns/models/item/alienblaster.json'])
        self.assertEqual([s['name'] for s in model['elements']], [s['name'] for s in shapes])
        self.assertEqual(self.gun['forward_axis'], '-z')
        self.assertTrue(any(s.get('rotation') for s in model['elements']))

    def test_source_texture_audio_and_localizations(self):
        self.assertEqual(self.files[RESOURCES + 'assets/techguns/textures/item/alienblaster.png'], resolve_asset('textures/guns/alien_blaster.png').read_bytes())
        sounds = json.loads(self.files[RESOURCES + 'assets/techguns/sounds.json'])
        source = json.loads(resolve_asset('sounds.json').read_text())
        for name in (self.gun['fire_sound'], self.gun['reload_sound']):
            self.assertEqual(sounds[name]['sounds'], source[name]['sounds'])
            for sound in sounds[name]['sounds']:
                path = 'sounds/' + sound.removeprefix('techguns:') + '.ogg'
                self.assertEqual(self.files[RESOURCES + 'assets/techguns/' + path], resolve_asset(path).read_bytes())
        for lang in ('en_us', 'ru_ru'):
            names = json.loads(self.files[RESOURCES + f'assets/techguns/lang/{lang}.json'])
            self.assertNotEqual(names['item.techguns.alienblaster'], 'alienblaster')


if __name__ == '__main__': unittest.main()
