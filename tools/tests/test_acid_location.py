import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_locations import acid_location_definition, altar_definition, location_nbt, generate_location_content
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class AcidLocationTests(unittest.TestCase):
    def test_all_original_cells_dimensions_and_layers(self):
        d=acid_location_definition(); source=(LEGACY/'resources/assets/techguns/structures/nether_acid_hole').read_text().splitlines()
        self.assertEqual(len(d['cells']),269); self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in source[1:]])
        self.assertEqual(d['size'],[9,6,9]); self.assertEqual(d['declared_size'],[9,6,9]); self.assertEqual((d['height_offset'],d['worldgen_floor_offset']),(-1,-1))
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:167,1:60,2:21,3:12,4:9})

    def test_nine_fixed_sources_and_twelve_marked_rim_cells(self):
        d=acid_location_definition(); p=d['palette']
        self.assertEqual(p[4],{'Name':'techguns:block_creeper_acid','Properties':{'level':'0'}})
        self.assertEqual([c for c in d['cells'] if c[3]==4],[[x,3,z,4] for x in (3,4,5) for z in (3,4,5)])
        self.assertEqual(p[3],{'Name':'minecraft:structure_block','Properties':{'mode':'data'}})
        self.assertEqual(d['block_entities'],{3:{'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:acid_or_netherrack'}})
        self.assertEqual({c[1] for c in d['cells'] if c[3]==3},{3})

    def test_actual_legacy_one_one_weights_are_two_thirds_acid(self):
        d=acid_location_definition(); source=(LEGACY/'java/techguns/util/MultiMBlock.java').read_text()
        self.assertIn('rnd.nextInt(this.totalWeight+1)',source); self.assertIn('if (roll <= sum)',source)
        self.assertEqual(d['weighted_cells'],[{'palette_index':3,'states':['techguns:block_creeper_acid','minecraft:netherrack'],'weights':[1,1],'roll_bound':3,'acid_rolls':[0,1],'netherrack_rolls':[2]}])

    def test_foundation_is_only_sixty_netherrack_bottom_cells(self):
        d=acid_location_definition(); bottom=[c for c in d['cells'] if c[1]==0]
        self.assertEqual(len(bottom),60); self.assertEqual({c[3] for c in bottom},{0})
        self.assertEqual((d['foundation_cells'],d['foundation_depth'],d['foundation_stop_after_solids']),(60,16,2))
        self.assertEqual(d['palette'][0],{'Name':'minecraft:netherrack'})

    def test_native_template_roundtrip_has_no_invented_encounter_or_chest(self):
        d=acid_location_definition(); data=location_nbt(d); self.assertEqual(data,location_nbt(d)); tree=read_nbt(data)
        self.assertEqual(tree['size'],d['size']); self.assertEqual(tree['palette'],d['palette']); self.assertEqual(tree['entities'],[])
        self.assertEqual([b['pos']+[b['state']] for b in tree['blocks']],d['cells'])
        self.assertEqual(len([b for b in tree['blocks'] if 'nbt' in b]),12)
        self.assertEqual({b['nbt']['id'] for b in tree['blocks'] if 'nbt' in b},{'minecraft:structure_block'})

    def test_natural_set_keeps_shared_grid_and_conditional_candidates(self):
        files=generate_location_content(); d=acid_location_definition()
        self.assertEqual([v['implemented'] for v in d['generation']['candidates']],[True,False,True,True,False])
        self.assertEqual(d['generation'],altar_definition()['generation'])
        structure=json.loads(files[RESOURCES+'data/techguns/worldgen/structure/nether_acid_hole.json'])
        self.assertEqual((structure['reserved_medium_grid'],structure['reserved_big_grid']),(32,64)); self.assertEqual(structure['spawn_overrides'],{})
        self.assertEqual(structure['step'],'top_layer_modification')
        old=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/nether_altar_small.json'])
        new=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/nether_acid_hole.json'])
        self.assertEqual(new['placement'],old['placement']); self.assertEqual(new['structures'],[{'structure':'techguns:nether_acid_hole','weight':1}])

if __name__=='__main__': unittest.main()
