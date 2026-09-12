import json
import sys
import unittest
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_camo import camo_data, generate_camo_content, LEGACY, RESOURCES
from legacy_repair import generate_repair_content
from legacy_crafting import plan_crafting
from generate_weapon_content import generate, parse_weapons


class CamoBenchPortTests(unittest.TestCase):
    def test_original_palette_families_and_banner_direction(self):
        palettes = {p['id']:p for p in camo_data()['palettes']}
        self.assertEqual(set(palettes), {'wool','concrete','concrete_powder','terracotta','stained_glass','stained_glass_pane','banner','carpet'})
        self.assertTrue(all(len(p['items']) == 16 for p in palettes.values()))
        self.assertEqual(palettes['banner']['items'][:2], ['minecraft:black_banner','minecraft:red_banner'])
        self.assertEqual(palettes['banner']['items'][-1], 'minecraft:white_banner')
        self.assertEqual(palettes['wool']['items'][:2], ['minecraft:white_wool','minecraft:orange_wool'])
        self.assertEqual(palettes['terracotta']['items'][8], 'minecraft:light_gray_terracotta')

    def test_original_recipe_uses_dyes_only_to_build_the_bench(self):
        graph = plan_crafting(parse_weapons()); recipe = graph['recipes']['camo_bench']
        self.assertEqual(recipe['pattern'], ['ddd','ici','iii'])
        self.assertEqual(recipe['key'], {'d':'#c:dyes','i':'#c:nuggets/iron','c':'#c:player_workstations/crafting_tables'})
        self.assertEqual(recipe['result'], {'id':'techguns:camo_bench','count':1})
        self.assertEqual(graph['catalog']['block_metadata']['techguns:simplemachine@8'], 'techguns:camo_bench')
        self.assertEqual(camo_data()['recolor_cost'], 0)

    def test_source_cube_and_gui_pixels_are_unchanged(self):
        files = generate_camo_content(); assets = LEGACY / 'resources/assets/techguns'
        source = json.loads((assets / 'models/block/camo_bench.json').read_text())
        for atlas in ('block','item'):
            model = json.loads(files[RESOURCES + f'assets/techguns/models/{atlas}/camo_bench.json'])
            self.assertEqual(model['textures'], {key:value.replace(':blocks/', ':' + atlas + '/') for key,value in source['textures'].items()})
            for value in source['textures'].values():
                name = value.split('/')[-1]
                self.assertEqual(files[RESOURCES + f'assets/techguns/textures/{atlas}/{name}.png'], (assets / f'textures/blocks/{name}.png').read_bytes())
        self.assertEqual(files[RESOURCES + 'assets/techguns/textures/gui/camo_bench.png'], (assets / 'textures/gui/camo_bench_gui.png').read_bytes())

    def test_shared_side_texture_and_tags_coexist(self):
        files = generate(); camo = generate_camo_content(); repair = generate_repair_content()
        for atlas in ('block','item'):
            key = RESOURCES + f'assets/techguns/textures/{atlas}/paintmachine_side.png'
            self.assertEqual(files[key], camo[key]); self.assertEqual(camo[key], repair[key])
        tag = json.loads(files[RESOURCES + 'data/minecraft/tags/block/mineable/pickaxe.json'])
        self.assertTrue({'techguns:camo_bench','techguns:repair_bench','techguns:ammo_press','techguns:ore_copper'}.issubset(tag['values']))
        self.assertEqual(len(tag['values']), len(set(tag['values'])))

    def test_conflicting_shared_texture_is_rejected(self):
        key = RESOURCES + 'assets/techguns/textures/block/paintmachine_side.png'
        with patch('generate_weapon_content.generate_camo_content', return_value={key:b'different pixels'}):
            with self.assertRaisesRegex(ValueError, 'Colliding generated resource'):
                generate()


if __name__ == '__main__': unittest.main()
