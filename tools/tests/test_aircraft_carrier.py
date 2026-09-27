import collections
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_aircraft_carrier import carrier_definition, generate_carrier_content
from legacy_metal_stairs import VARIANTS, generate_metal_stairs_content, metal_stairs_translations
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons
from legacy_npcs import LEGACY, RESOURCES


class AircraftCarrierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.d=carrier_definition()

    def test_scan_preserves_duplicates_and_both_passes(self):
        d=self.d
        raw=(LEGACY/'resources/assets/techguns/structures/aircraft_carrier').read_text().splitlines()
        self.assertEqual(d['source_cells'],[list(map(int,s.split(','))) for s in raw[1:] if s])
        ordered=sorted(d['source_cells'],key=lambda c:d['source_palette'][c[3]]['pass'])
        self.assertEqual([c[:3] for c in d['cells']],[c[:3] for c in ordered])
        self.assertEqual(collections.Counter(d['palette'][c[3]]['pass'] for c in d['cells']),{0:3898,1:69})
        duplicate=collections.Counter(tuple(c[:3]) for c in d['cells'])
        self.assertEqual(collections.Counter(duplicate.values()),{1:3891,2:38})
        final={tuple(c[:3]):d['palette'][c[3]] for c in d['cells']}
        self.assertEqual(final[18,1,9]['kind'],'guard')
        self.assertEqual(final[25,1,6]['kind'],'supply')

    def test_all_41_active_source_entries_have_explicit_native_states(self):
        expected=['minecraft:yellow_concrete','minecraft:light_gray_concrete','minecraft:gray_concrete','minecraft:terracotta',
                  'techguns:concrete_grey_dark','minecraft:crafting_table','minecraft:chest','techguns:lamp_yellow','techguns:lamp_yellow',
                  'minecraft:chest','minecraft:air','minecraft:air','minecraft:air','minecraft:glass_pane','techguns:concrete_brown_pipes',
                  'techguns:lamp_yellow','techguns:lamp_yellow','techguns:bunkerdoor','techguns:bunkerdoor','techguns:bunkerdoor',
                  'techguns:metalpanel_panel_large_border','minecraft:glass','minecraft:iron_bars','techguns:metalpanel_steelframe_scaffold',
                  'techguns:lamp_yellow','techguns:bunkerdoor','techguns:bunkerdoor','techguns:bunkerdoor','minecraft:chest','minecraft:chest',
                  'techguns:stairs_metal','techguns:stairs_metal','techguns:ladder_metal','minecraft:iron_block','techguns:metalpanel_steelframe_dark',
                  'minecraft:chest','techguns:metalpanel_steelframe_dark','minecraft:chest','techguns:tg_spawner','techguns:soldier_spawn','minecraft:air']
        self.assertEqual([v['state']['Name'] for v in self.d['source_palette']],expected)
        self.assertEqual([self.d['source_palette'][i]['state']['Properties']['facing'] for i in (7,8,15,16,24,30,31,32)],['north','west','south','east','up','west','east','south'])

    def test_all_door_halves_and_chest_pairs_reconstruct_source_connections(self):
        states={tuple(c[:3]):self.d['palette'][c[3]]['state'] for c in self.d['cells']}; doors=0;chests=collections.Counter()
        for (x,y,z),state in states.items():
            if state['Name']=='techguns:bunkerdoor':
                doors+=1; props=state['Properties']; other=states[x,y+(1 if props['half']=='lower' else -1),z]['Properties']
                self.assertNotEqual(props['half'],other['half'])
                self.assertEqual({k:v for k,v in props.items() if k!='half'},{k:v for k,v in other.items() if k!='half'})
            if state['Name']=='minecraft:chest': chests[state['Properties']['type']]+=1
        self.assertEqual(doors,58);self.assertEqual(chests,{'left':5,'right':5,'single':4})

    def test_supplies_and_posts_are_not_silently_pruned(self):
        kinds=collections.Counter(self.d['palette'][c[3]]['kind'] for c in self.d['cells'])
        self.assertEqual({k:v for k,v in kinds.items() if k!='block'},{'chest':14,'supply':24,'supply_chance':24,'guard':9,'helicopter':1})
        for p in self.d['posts'].values(): self.assertEqual(p['height_offset'],0)
        self.assertEqual(self.d['posts']['guard']['mobs'],{'armysoldier':1,'commando':1})

    def test_nested_loot_and_water_grid_leave_land_weights_untouched(self):
        files=generate_carrier_content()
        loot=json.loads(files[RESOURCES+'data/techguns/loot_table/chests/aircraftcarrier.json'])
        self.assertEqual([(p['rolls']['min'],p['rolls']['max']) for p in loot['pools']],[(1,3),(3,7)])
        self.assertEqual([p['entries'][0]['value'] for p in loot['pools']],['techguns:blocks/military_crate_gun','techguns:blocks/military_crate_generic'])
        carrier=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/aircraft_carrier.json'])
        for name in ('military_camp','castle'):
            land=json.loads(Path(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json').read_text())
            self.assertEqual(carrier['placement'],land['placement'])
            self.assertEqual(land['structures'],[{'structure':'techguns:'+name,'weight':1}])
        self.assertEqual(json.loads(files[RESOURCES+'data/techguns/worldgen/structure/aircraft_carrier.json'])['biomes'],'#minecraft:is_ocean')

    def test_metal_stair_variants_preserve_all_80_source_model_rotations(self):
        files=generate_metal_stairs_content();assets=LEGACY/'resources/assets/techguns'
        original=json.loads((assets/'blockstates/stairs_metal.json').read_text())['variants']
        for i,name in enumerate(VARIANTS):
            states=json.loads(files[RESOURCES+f'assets/techguns/blockstates/{name}.json'])['variants']
            self.assertEqual(len(states),40)
            for key,value in states.items():
                source=original[key+',type2='+str(bool(i)).lower()]
                self.assertEqual(value,{**source,'model':source['model'].replace(':',':block/')})
                model=json.loads(files[RESOURCES+'assets/techguns/models/'+value['model'].split(':')[1]+'.json'])
                for texture in model['textures'].values():self.assertTrue(Path(RESOURCES+'assets/techguns/textures/'+texture.split(':')[1]+'.png').is_file())
        for lang in ('en_us','ru_ru'):self.assertEqual(len(metal_stairs_translations(lang)),4)

    def test_native_stair_crafting_and_return_keep_metadata_and_yield(self):
        recipes=plan_crafting(parse_weapons())['recipes']
        for i,name in enumerate(VARIANTS):
            forward=recipes[f'stairs_metal_{7+8*i}'];reverse=recipes[f'metalpanel_{4+2*i}']
            self.assertEqual(forward['result'],{'id':'techguns:'+name,'count':6})
            self.assertEqual(forward['pattern'],['b  ','bb ','bbb'])
            self.assertEqual(reverse['ingredients'],['techguns:'+name])
            self.assertEqual(reverse['result'],{'id':forward['key']['b'],'count':1})


if __name__=='__main__':unittest.main()
