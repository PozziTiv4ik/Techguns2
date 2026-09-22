import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_building import *
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons


class BuildingTests(unittest.TestCase):
    def test_enum_order_and_metadata(self):
        values = building_definitions()
        self.assertEqual(len(values), 18)
        self.assertEqual([v['id'] for v in values[:8]], ['metalpanel_'+n for n in ('container_red','container_green','container_blue','container_orange','panel_large_border','steelframe_blue','steelframe_dark','steelframe_scaffold')])
        self.assertEqual([v['id'] for v in values[8:14]], ['concrete_'+n for n in ('brown','brown_light','grey','grey_dark','brown_pipes','brown_light_scaff')])
        self.assertEqual([v['metadata'] for v in values[14:]], [8,9,10,11])
        for meta in range(16): self.assertEqual(building_id('ladder0', meta), ['ladder_metal','ladder_shiny','ladder_rusty','ladder_carbon'][meta & 3])

    def test_all_source_textures_are_byte_identical_in_both_atlases(self):
        files = generate_building_content(); assets = LEGACY/'resources/assets/techguns'
        for v in building_definitions():
            original = json.loads((assets/f'models/block/{v["model"]}.json').read_text(encoding='utf-8'))
            for atlas in ('block','item'):
                model = json.loads(files[RESOURCES+f'assets/techguns/models/{atlas}/{v["id"]}.json'])
                self.assertEqual(model['textures'], {k: t.replace(':blocks/', ':'+atlas+'/') for k,t in original['textures'].items()})
                for t in original['textures'].values():
                    name = t.split('/')[-1]
                    self.assertEqual(files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png'], (assets/f'textures/blocks/{name}.png').read_bytes())

    def test_ladder_world_models_match_original_four_facings(self):
        files = generate_building_content()
        original = json.loads((LEGACY/'resources/assets/techguns/blockstates/ladder0.json').read_text(encoding='utf-8'))['variants']
        for v in building_definitions()[14:]:
            states = json.loads(files[RESOURCES+f'assets/techguns/blockstates/{v["id"]}.json'])['variants']
            for facing in ('north','east','south','west'):
                source = original[f'facing={facing},type={v["id"].removeprefix("ladder_")}']
                self.assertEqual(states['facing='+facing].get('y',0), source.get('y',0))

    def test_inventory_bakes_north_rotation_before_source_display(self):
        files = generate_building_content()
        base = json.loads(files[RESOURCES+'assets/techguns/models/block/ladder_base.json'])
        item = json.loads(files[RESOURCES+'assets/techguns/models/item/ladder_base.json'])
        self.assertEqual(item['display'], base['display']); self.assertEqual(len(item['elements']), 6)
        self.assertEqual(item['elements'][0]['from'], [14,0,14]); self.assertEqual(item['elements'][0]['to'], [16,16,16])
        for before, after in zip(base['elements'], item['elements'], strict=True):
            self.assertEqual(after['faces']['south'], before['faces']['north'])
            self.assertEqual(after['faces']['west'], before['faces'].get('east')) if 'east' in before['faces'] else None
            for side in ('up','down'): self.assertEqual(after['faces'][side]['rotation'], 180)
        twice = ladder_inventory_model(item)
        for a,b in zip(base['elements'],twice['elements'],strict=True):
            self.assertEqual(a['from'],b['from']); self.assertEqual(a['to'],b['to'])

    def test_source_labels_include_separate_ladder_camo_names(self):
        for lang in ('en_us','ru_ru'):
            names = building_translations(lang)
            self.assertEqual(len(names),40)
            source = dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            for v in building_definitions(): self.assertEqual(names['block.techguns.'+v['id']],source[f'tile.techguns.{v["family"]}.{v["metadata"]}.name'])
            self.assertEqual(names['techguns.ladder0.camoname.0'],source['techguns.ladder0.camoname.0'])

    def test_original_recipe_grids_counts_and_unported_stair_dependencies(self):
        graph = plan_crafting(parse_weapons())
        for name in RECIPES:
            source = json.loads((LEGACY/f'resources/assets/techguns/recipes/{name}.json').read_text(encoding='utf-8'))
            recipe = graph['recipes'][name]
            self.assertEqual(recipe['pattern'],source['pattern']); self.assertEqual(recipe['result']['count'],source['result']['count'])
            self.assertEqual(recipe['result']['id'],'techguns:'+building_id(source['result']['item'].split(':')[1],source['result']['data']))
        self.assertEqual(graph['recipes']['concrete_0']['key'], {'b':'minecraft:iron_bars','c':'#techguns:legacy_concrete'})
        for name in PENDING_RECIPES: self.assertNotIn(name,graph['recipes'])

    def test_concrete_wildcard_excludes_powder_and_own_reinforced_blocks(self):
        files = generate_building_content(); tag = json.loads(files[RESOURCES+'data/techguns/tags/item/legacy_concrete.json'])
        self.assertEqual(len(tag['values']),16); self.assertEqual(len(set(tag['values'])),16)
        self.assertTrue(all(v.startswith('minecraft:') and v.endswith('_concrete') for v in tag['values']))

    def test_self_drops_and_native_climbing_tags(self):
        files = generate_building_content()
        for v in building_definitions():
            pool = json.loads(files[RESOURCES+f'data/techguns/loot_table/blocks/{v["id"]}.json'])['pools'][0]
            self.assertEqual(pool['entries'],[{'type':'minecraft:item','name':'techguns:'+v['id']}])
            self.assertEqual(pool['conditions'],[{'condition':'minecraft:survives_explosion'}])
        self.assertEqual(len(json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])['values']),18)
        self.assertEqual(json.loads(files[RESOURCES+'data/minecraft/tags/block/climbable.json'])['values'],['techguns:ladder_'+n for n in ('metal','shiny','rusty','carbon')])


if __name__ == '__main__': unittest.main()
