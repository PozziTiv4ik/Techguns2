import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_desert_oil import desert_oil_definition, generate_desert_oil_content
from legacy_chemistry import fluid_group_defaults
from legacy_locations import location_nbt
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class DesertOilTests(unittest.TestCase):
    def test_every_source_cell_and_palette_index(self):
        d=desert_oil_definition(); rows=(LEGACY/'resources/assets/techguns/structures/desert_oil_cluster').read_text(encoding='utf-8').splitlines()
        self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual((len(d['cells']),d['size'],d['pivot']),(579,[11,10,11],[5,0,5]))
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:188,1:62,2:280,3:4,4:12,5:10,6:20,7:2,8:1})

    def test_native_template_keeps_four_finite_weighted_posts(self):
        d=desert_oil_definition(); n=read_nbt(location_nbt(d)); self.assertEqual(n['palette'],d['palette'])
        self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells'])
        posts=[b for b in n['blocks'] if b['state']==3]; self.assertEqual([b['pos'] for b in posts],[[2,6,2],[2,6,8],[8,6,2],[8,6,8]])
        for b in posts:
            self.assertEqual(b['nbt'],{'id':'techguns:tg_spawner','mobsLeft':3,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':1.,'spawnHeightOffset':0,
                                     'mobtypes':[{'id':'techguns:armysoldier','weight':4},{'id':'techguns:commando','weight':1}]})
        self.assertEqual(d['palette'][3]['Name'],'techguns:soldier_spawn')

    def test_markers_retain_source_probabilities_and_guaranteed_cells(self):
        d=desert_oil_definition(); self.assertEqual((d['rim_weights'],d['rim_effective_tickets'],d['cluster_alternative_chance']),([1,1],[2,1],.5))
        n=read_nbt(location_nbt(d)); counts=Counter(b['nbt']['metadata'] for b in n['blocks'] if b['state']>=4)
        self.assertEqual(counts,{'techguns:desert_oil_rim':12,'techguns:desert_oil_cluster_or_oil':10,'techguns:desert_oil_oil':21,'techguns:desert_oil_cluster':2})
        self.assertEqual([c[:3] for c in d['cells'] if c[3]==7],[[5,2,5],[5,3,5]])

    def test_no_bottom_cells_means_no_effective_clearing_or_foundation(self):
        d=desert_oil_definition(); self.assertEqual(min(c[1] for c in d['cells']),1)
        self.assertEqual((d['clear_height_parameter'],d['effective_clearing_columns'],d['foundation_depth'],d['height_offset'],d['worldgen_floor_offset']),(6,0,0,-4,-1))
        self.assertEqual(d['generation']['height_samples'],[0,4,8])

    def test_original_world_oil_names_are_separate_from_chemical_oils(self):
        groups=fluid_group_defaults(); self.assertEqual(groups['OilWorldspawn'],['oil','crude_oil'])
        self.assertEqual(groups['Oil'],['oil','tree_oil','crude_oil','fluidoil','seed_oil'])
        f=generate_desert_oil_content(); tag=json.loads(f[RESOURCES+'data/techguns/tags/fluid/worldgen_oils.json']); self.assertEqual(tag,{'replace':False,'values':[]})

    def test_conditional_medium_slot_and_original_unreachable_debug_fallback(self):
        d=desert_oil_definition(); self.assertTrue(d['generation']['ore_toggle_required'] and d['generation']['world_oil_required'])
        self.assertEqual(d['generation']['weight'],15)
        self.assertEqual(d['no_fluid_debug_palette'],{'rim':'minecraft:magma_block','cluster_alternative':'minecraft:sandstone','oil':'minecraft:lava'})
        f=generate_desert_oil_content(); p=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/desert_oil_cluster.json'])['placement']
        self.assertEqual(p,{'type':'techguns:structure_grid','size':'medium','salt':1337262})
