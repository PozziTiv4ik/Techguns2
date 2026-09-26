import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_nether_castle import nether_castle_definition, generate_nether_castle_content
from legacy_locations import location_nbt
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class NetherCastlePortTests(unittest.TestCase):
    def test_all_642_source_cells_and_sixteen_foundation_columns(self):
        d=nether_castle_definition(); rows=(LEGACY/'resources/assets/techguns/structures/nether_orecluster_castle').read_text().splitlines()
        self.assertEqual(len(d['cells']),642); self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual(d['size'],[11,9,11]); self.assertEqual(d['declared_size'],[11,9,11]); self.assertEqual(d['pivot'],[5,0,5])
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:165,1:80,2:116,3:226,4:12,5:19,6:4,7:13,8:7})
        self.assertEqual(Counter(c[3] for c in d['cells'] if c[1]==0),{1:16})
        self.assertEqual((d['height_offset'],d['worldgen_floor_offset'],d['foundation_depth'],d['foundation_stop_after_solids']),(-1,-1,16,2))

    def test_nine_palette_entries_keep_all_original_metadata(self):
        palette=nether_castle_definition()['palette']
        self.assertEqual([p['Name'] for p in palette],['techguns:nethermetal_panel','techguns:nethermetal_plate_black','minecraft:nether_bricks',
                         'minecraft:air','minecraft:nether_brick_fence','techguns:nethermetal_border_red','techguns:tg_spawner',
                         'minecraft:structure_block','techguns:ore_cluster_nether_crystal'])
        self.assertEqual(palette[7]['Properties'],{'mode':'data'})

    def test_all_four_finite_guards_keep_serialized_bolt_action_override(self):
        d=nether_castle_definition(); n=read_nbt(location_nbt(d)); guards=[c for c in n['blocks'] if c['state']==6]
        self.assertEqual([c['pos'] for c in guards],[[2,7,2],[2,7,8],[8,7,2],[8,7,8]])
        for c in guards:
            self.assertEqual(c['nbt'],{'id':'techguns:tg_spawner','mobsLeft':2,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':0.0,
                                      'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombiepigmansoldier','weight':1}],
                                      'weapon':{'id':'techguns:boltaction','count':1}})

    def test_native_nbt_keeps_seven_fixed_and_thirteen_random_cluster_cells(self):
        d=nether_castle_definition(); data=location_nbt(d); self.assertEqual(data,location_nbt(d)); n=read_nbt(data)
        self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells']); self.assertEqual(n['palette'],d['palette'])
        markers=[b for b in n['blocks'] if b['state']==7]; self.assertEqual(len(markers),13)
        self.assertTrue(all(b['nbt']=={'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:castle_cluster_or_air'} for b in markers))
        self.assertEqual(len([b for b in n['blocks'] if b['state']==8]),7)
        self.assertEqual((d['mixture_weights'],d['mixture_effective_tickets']),([40,60],[41,60]))

    def test_medium_nether_registry_and_original_unported_weights(self):
        d=nether_castle_definition(); files=generate_nether_castle_content(); name='nether_ore_cluster_castle'
        self.assertEqual([(c['weight'],c['implemented']) for c in d['generation']['candidates']],[(10,False),(10,False),(1000,True)])
        self.assertTrue(d['generation']['ore_toggle_required'])
        placement=json.loads(files[RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json'])['placement']
        self.assertEqual((placement['spacing'],placement['separation'],placement['salt']),(32,31,1337262))
        s=json.loads(files[RESOURCES+f'data/techguns/worldgen/structure/{name}.json'])
        self.assertEqual((s['step'],s['reserved_big_grid']),('top_layer_modification',64))
        self.assertEqual(json.loads(files[RESOURCES+f'data/techguns/tags/worldgen/biome/has_{name}.json'])['values'],['#minecraft:is_nether'])


if __name__=='__main__': unittest.main()
