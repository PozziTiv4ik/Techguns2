import json
import re
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_camonets import *
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons


class CamouflageNetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = generate_camonet_content()
        cls.graph = plan_crafting(parse_weapons())

    def test_original_enum_and_metadata_are_complete(self):
        variants = net_definitions()
        self.assertEqual(len(variants), 6)
        for family in FAMILIES:
            self.assertEqual([v['color'] for v in variants if v['family'] == family], ['wood', 'desert', 'snow'])
            for meta in range(3):
                identifier = 'techguns:' + net_id(family, meta)
                self.assertEqual(self.graph['catalog']['block_metadata'][f'techguns:{family}@{meta}'], identifier)
        with self.assertRaises(StopIteration): net_id('camonet', 3)

    def test_all_sixteen_source_connections_render_all_submodels(self):
        # Forge's submodel objects become simultaneous multipart entries, never random choices.
        for v in net_definitions():
            old = json.loads((ASSETS / f'blockstates/{v["family"]}.json').read_text(encoding='utf-8'))['variants']['connection']
            modern = json.loads(self.files[RESOURCES + f'assets/techguns/blockstates/{v["id"]}.json'])['multipart']
            self.assertEqual(len(old), 16)
            for mask, connection in enumerate(old):
                props = {side: str(bool(mask & (8 >> i))).lower() for i, side in enumerate(('north', 'east', 'south', 'west'))}
                expected = old[connection]['submodel']
                expected = [{'model': expected}] if isinstance(expected, str) else list(expected.values())
                expected = [dict(part, model=part['model'].replace('techguns:', 'techguns:block/') + '_' + v['color'],
                                 **({'y': part['y'] % 360} if 'y' in part else {})) for part in expected]
                actual = [p['apply'] for p in modern if p['when'] == props]
                self.assertEqual(actual, expected, (v['id'], connection))
            self.assertEqual(len(modern), sum(len(e['submodel']) if isinstance(e['submodel'], dict) else 1 for e in old.values()))

    def test_canopy_bounds_match_source_table_and_asymmetric_edges(self):
        boxes = canopy_boxes()
        self.assertEqual(boxes, [
            [4,0,4,12,1,12], [0,0,4,9,1,12], [4,0,7,12,1,16], [0,0,7,9,1,16],
            [7,0,4,16,1,12], [0,0,4,16,1,12], [7,0,7,16,1,16], [0,0,7,16,1,16],
            [4,0,0,12,1,9], [0,0,0,9,1,9], [4,0,0,12,1,16], [0,0,0,9,1,16],
            [7,0,0,16,1,9], [0,0,0,16,1,9], [7,0,0,16,1,16], [0,0,0,16,1,16]])
        java = self.files['core/src/main/java/techguns/core/CamouflageNets.java'].decode('utf-8')
        self.assertEqual([[int(v) for v in row.split(',')] for row in re.findall(r'new Box\(([^)]+)\)', java)], boxes)

    def test_all_original_geometry_uv_and_inventory_display_survive(self):
        count = 0
        for path, raw in self.files.items():
            if '/models/' not in path: continue
            model = json.loads(raw)
            name = Path(path).stem
            family = 'camonet_top' if name.startswith('camonet_top') else 'camonet'
            source = family + '_inventory' if '/models/item/' in path else name.rsplit('_', 1)[0]
            original = json.loads((ASSETS / f'models/block/{source}.json').read_text(encoding='utf-8'))
            self.assertEqual(model['elements'], original['elements'])
            self.assertEqual(model.get('display'), original.get('display'))
            self.assertNotIn('render_type', model)  # 26.2 uses material/sprite alpha.
            count += 1
        self.assertEqual(count, 30)

    def test_three_camo_textures_are_byte_exact_in_both_atlases(self):
        pngs = {p: v for p, v in self.files.items() if p.endswith('.png')}
        self.assertEqual(len(pngs), 6)
        for path, raw in pngs.items(): self.assertEqual(raw, (ASSETS / 'textures/blocks' / Path(path).name).read_bytes())
        for v in net_definitions():
            model = json.loads(self.files[RESOURCES + f'assets/techguns/models/item/{v["id"]}.json'])
            expected = 'camonet' + ('' if v['color'] == 'wood' else '_' + v['color'])
            self.assertEqual(model['textures']['particle'], 'techguns:item/' + expected)
            if v['family'] == 'camonet':
                self.assertEqual(model['textures']['0'], 'minecraft:block/' + {'wood':'oak', 'desert':'acacia', 'snow':'spruce'}[v['color']] + '_planks')

    def test_recipes_keep_original_pattern_quantities_and_ingredients(self):
        for name, output in zip(RECIPES, ('camonet_wood', 'camonet_top_wood')):
            source = json.loads((ASSETS / f'recipes/{name}.json').read_text(encoding='utf-8'))
            modern = self.graph['recipes'][name]
            self.assertEqual(modern['pattern'], source['pattern'])
            self.assertEqual(modern['result'], {'id':'techguns:' + output, 'count':source['result']['count']})
            tags = {'#STICKWOOD':'#c:rods/wooden', '#DIRT':'#techguns:legacy_dirt', '#STRING':'#c:strings'}
            self.assertEqual(modern['key'], {key:tags[value['item']] for key, value in source['key'].items()})

    def test_dirt_tag_does_not_accept_grass_podzol_or_modern_mud(self):
        tag = json.loads(self.files[RESOURCES + 'data/techguns/tags/item/legacy_dirt.json'])
        self.assertEqual(tag, {'replace':False, 'values':['minecraft:dirt']})

    def test_drops_retain_the_exact_family_and_camo(self):
        for v in net_definitions():
            loot = json.loads(self.files[RESOURCES + f'data/techguns/loot_table/blocks/{v["id"]}.json'])
            self.assertEqual(loot['pools'], [{'rolls':1, 'conditions':[{'condition':'minecraft:survives_explosion'}],
                                            'entries':[{'type':'minecraft:item', 'name':'techguns:' + v['id']}]}])

    def test_labels_and_item_definitions_cover_every_variant(self):
        for lang in ('en_us', 'ru_ru'):
            labels = camonet_translations(lang)
            source = dict(line.split('=',1) for line in (ASSETS / f'lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual(len(labels), 12)
            for v in net_definitions():
                for kind in ('block', 'item'):
                    self.assertEqual(labels[kind + '.techguns.' + v['id']], source[f'tile.techguns.{v["family"]}.{v["metadata"]}.name'])
                item = json.loads(self.files[RESOURCES + f'assets/techguns/items/{v["id"]}.json'])
                self.assertEqual(item, {'model':{'type':'minecraft:model', 'model':'techguns:item/' + v['id']}})


if __name__ == '__main__': unittest.main()
