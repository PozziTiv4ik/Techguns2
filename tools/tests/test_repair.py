"""Repair Bench original resources, metadata and modern recipe dependencies."""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_repair import generate_repair_content, LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from generate_weapon_content import generate, parse_weapons


class RepairBenchPortTests(unittest.TestCase):
    def test_original_cube_faces_and_pixels_in_both_atlases(self):
        files = generate_repair_content()
        source = json.loads((LEGACY / 'resources/assets/techguns/models/block/repair_bench.json').read_text())
        for atlas in ('block', 'item'):
            model = json.loads(files[RESOURCES + f'assets/techguns/models/{atlas}/repair_bench.json'])
            self.assertEqual(model['parent'], 'minecraft:block/cube')
            self.assertEqual(model['textures'], {key: value.replace(':blocks/', ':' + atlas + '/') for key, value in source['textures'].items()})
            for texture in set(source['textures'].values()):
                name = texture.split('/')[-1]
                self.assertEqual(files[RESOURCES + f'assets/techguns/textures/{atlas}/{name}.png'], (LEGACY / f'resources/assets/techguns/textures/blocks/{name}.png').read_bytes())
        self.assertEqual(files[RESOURCES + 'assets/techguns/textures/gui/repair_bench.png'], (LEGACY / 'resources/assets/techguns/textures/gui/repair_bench_gui.png').read_bytes())

    def test_original_recipe_metadata_and_workbench_tag(self):
        graph = plan_crafting(parse_weapons()); recipe = graph['recipes']['repair_bench']
        self.assertEqual(recipe['pattern'], ['pmp', 'ici', 'iii'])
        self.assertEqual(recipe['key'], {'p': '#c:plates/iron', 'm': 'techguns:mechanicalpartsiron', 'c': '#c:player_workstations/crafting_tables', 'i': '#c:nuggets/iron'})
        self.assertEqual(recipe['result'], {'id': 'techguns:repair_bench', 'count': 1})
        self.assertEqual(graph['catalog']['block_metadata']['techguns:simplemachine@9'], 'techguns:repair_bench')

    def test_pickaxe_tag_merges_with_existing_machines_and_ores(self):
        files = generate()
        tag = json.loads(files[RESOURCES + 'data/minecraft/tags/block/mineable/pickaxe.json'])
        self.assertTrue({'techguns:repair_bench', 'techguns:charging_station', 'techguns:ammo_press', 'techguns:ore_copper'}.issubset(tag['values']))
        self.assertFalse(tag['replace'])
        variants = json.loads(files[RESOURCES + 'assets/techguns/blockstates/repair_bench.json'])['variants']
        self.assertEqual({key: value['y'] for key, value in variants.items()}, {'facing=north': 0, 'facing=east': 90, 'facing=south': 180, 'facing=west': 270})

    def test_inventory_contract_matches_original_station(self):
        catalog = json.loads(generate_repair_content()['content/repair-bench.json'])
        source = (LEGACY / 'java/techguns/tileentities/RepairBenchTileEnt.java').read_text()
        self.assertIn('super(10, false)', source)
        self.assertIn('item.setItemDamage(0)', source)
        self.assertEqual(catalog['material_slots'], list(range(9)))
        self.assertEqual(catalog['repair_slot'], 9)
        self.assertEqual(catalog['energy'], 0)
        self.assertEqual(len(catalog['targets']), 6)


if __name__ == '__main__': unittest.main()
