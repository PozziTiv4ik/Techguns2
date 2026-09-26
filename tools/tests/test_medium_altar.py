import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_medium_altar import medium_altar_definition, generate_medium_altar_content
from legacy_locations import location_nbt
from legacy_nether_castle import nether_castle_definition
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class MediumAltarPortTests(unittest.TestCase):
    def test_all_1668_cells_and_declared_height_distinction(self):
        d=medium_altar_definition(); rows=(LEGACY/'resources/assets/techguns/structures/nether_altar_medium').read_text().splitlines()
        self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in rows[1:]]); self.assertEqual(len(d['cells']),1668)
        self.assertEqual((d['size'],d['declared_size'],d['pivot']),([16,8,16],[16,9,16],[8,0,8]))
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:112,1:116,2:140,3:932,4:12,5:132,6:48,7:80,8:44,9:8,10:4,11:8,12:8,13:16,14:8})

    def test_stairs_keep_four_original_facings_and_glowing_metadata(self):
        palette=medium_altar_definition()['palette']; self.assertEqual(len(palette),15)
        self.assertEqual([palette[i]['Name'] for i in (0,1,4,5,6,7,8)],['techguns:nethermetal_'+n for n in ('panel','border_red','grate2','plate_red','grate1','plate_black','border_lava')])
        for index,direction in [(9,'east'),(11,'south'),(12,'north'),(14,'west')]:
            self.assertEqual(palette[index],{'Name':'minecraft:nether_brick_stairs','Properties':{'facing':direction,'half':'bottom','shape':'straight','waterlogged':'false'}})

    def test_four_distinct_cyberdemon_encounters_survive_nbt(self):
        d=medium_altar_definition(); data=location_nbt(d); self.assertEqual(data,location_nbt(d)); n=read_nbt(data)
        self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells']); self.assertEqual(n['palette'],d['palette'])
        holes=[b for b in n['blocks'] if b['state']==10]; self.assertEqual([b['pos'] for b in holes],[[4,5,4],[4,5,11],[11,5,4],[11,5,11]])
        for b in holes:
            self.assertEqual(b['nbt'],{'id':'techguns:tg_spawner','mobsLeft':3,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:cyberdemon','weight':1}]})

    def test_exact_foundation_footprint_and_source_offsets(self):
        d=medium_altar_definition()
        self.assertEqual([c for c in d['cells'] if c[1]==0],[[x,0,z,7] for x in (3,4,11,12) for z in (3,4,11,12)])
        self.assertEqual((d['foundation_cells'],d['foundation_depth'],d['foundation_stop_after_solids'],d['height_offset'],d['worldgen_floor_offset']),(16,16,2,-1,-1))

    def test_shared_medium_table_and_native_set_keep_original_chances(self):
        d=medium_altar_definition(); self.assertFalse(d['generation']['ore_toggle_required'])
        self.assertEqual(d['generation']['candidates'],nether_castle_definition()['generation']['candidates'])
        self.assertEqual([(c['weight'],c['implemented']) for c in d['generation']['candidates']],[(10,True),(10,False),(1000,True)])
        files=generate_medium_altar_content(); s=json.loads(files[RESOURCES+'data/techguns/worldgen/structure/nether_altar_medium.json'])
        self.assertEqual((s['step'],s['reserved_big_grid']),('top_layer_modification',64))
        placement=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/nether_altar_medium.json'])['placement']
        self.assertEqual((placement['spacing'],placement['separation'],placement['salt']),(32,31,1337262))

    def test_delta_protection_is_limited_to_original_tg_structure_blocks(self):
        files=generate_medium_altar_content(); tag=json.loads(files[RESOURCES+'data/techguns/tags/block/nether_structure_blocks.json'])
        self.assertFalse(tag['replace']); self.assertEqual(len(tag['values']),20)
        self.assertTrue(all(x.startswith(('techguns:nethermetal_','techguns:ore_cluster_')) or x=='techguns:tg_spawner' for x in tag['values']))
        self.assertIn('techguns:nethermetal_panel',tag['values']); self.assertIn('techguns:tg_spawner',tag['values'])
        self.assertIn('techguns:ore_cluster_nether_crystal',tag['values'])


if __name__=='__main__': unittest.main()
