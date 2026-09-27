import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_factory_house import factory_house_definition, generate_factory_house_content
from legacy_locations import factory_chest_loot, location_nbt, small_overworld_candidates
from legacy_gas_station import gas_station_definition
from legacy_train_station import train_station_definition
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class FactoryHouseTests(unittest.TestCase):
    def test_every_source_record_and_final_post_overwrite(self):
        d=factory_house_definition(); rows=(LEGACY/'resources/assets/techguns/structures/factory_building_small').read_text(encoding='utf-8').splitlines()
        self.assertEqual(d['source_cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual((len(d['source_cells']),len(d['cells'])),(463,462))
        self.assertEqual([c for c in d['source_cells'] if c[:3]==[3,1,3]],[[3,1,3,7],[3,1,3,21]])
        self.assertEqual([c for c in d['cells'] if c[:3]==[3,1,3]],[[3,1,3,21]])
        self.assertEqual(Counter(c[3] for c in d['source_cells']),{0:58,1:82,2:62,3:18,4:35,5:4,6:2,7:133,8:3,9:1,10:26,11:1,12:2,13:2,14:2,15:3,16:2,17:1,18:8,19:16,20:1,21:1})

    def test_registered_size_sparse_two_colour_foundation_and_clearance(self):
        d=factory_house_definition(); self.assertEqual((d['size'],d['registered_xz_size'],d['pivot']),([9,7,11],[11,10],[5,0,5]))
        self.assertEqual((d['foundation_cells'],d['foundation_depth'],d['clear_height']),(93,3,7)); self.assertFalse(d['surface_swap_xz'])
        self.assertEqual(Counter(c[3] for c in d['cells'] if c[1]==0),{0:58,4:35})
        self.assertEqual(d['generation']['height_samples_x'],[0,4,8]); self.assertEqual(d['generation']['height_samples_z'],[0,4,8])

    def test_active_aliases_ignore_plain_clay_metadata(self):
        p=factory_house_definition()['palette']
        self.assertEqual([p[i]['Name'] for i in (0,1,2,4,5,10)],['techguns:concrete_grey','techguns:metalpanel_steelframe_scaffold','minecraft:bricks','techguns:concrete_brown_light','techguns:metalpanel_container_red','minecraft:terracotta'])
        self.assertEqual(p[18],{'Name':'techguns:ladder_metal','Properties':{'facing':'south'}})
        self.assertEqual(p[16],{'Name':'minecraft:furnace','Properties':{'facing':'south','lit':'false'}})

    def test_yellow_lamps_and_both_door_pairs(self):
        d=factory_house_definition(); p=d['palette']; counts=Counter(c[3] for c in d['cells'])
        self.assertEqual(sum(counts[i] for i in (8,9,14,15,17,20)),11)
        for i,facing in ((8,'north'),(9,'west'),(14,'up'),(15,'south'),(17,'east'),(20,'west')):
            self.assertEqual(p[i],{'Name':'techguns:lamp_yellow','Properties':{'facing':facing}})
        for i,half in ((12,'lower'),(13,'upper')):
            self.assertEqual(p[i]['Properties'],{'facing':'south','hinge':'left','half':half,'open':'false','powered':'false'}); self.assertEqual(counts[i],2)

    def test_native_double_chest_and_five_death_post_nbt(self):
        d=factory_house_definition(); n=read_nbt(location_nbt(d)); self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells']); self.assertEqual(n['palette'],d['palette'])
        self.assertEqual((d['palette'][6]['Properties']['type'],d['palette'][22]['Properties']['type']),('left','right'))
        self.assertEqual([c for c in d['cells'] if c[3]==22],[[1,1,4,22]])
        chests=[b for b in n['blocks'] if b['state'] in (6,22)]; self.assertEqual(len(chests),2)
        for b in chests: self.assertEqual(b['nbt'],{'id':'minecraft:chest','LootTable':'techguns:chests/factory_building'})
        posts=[b for b in n['blocks'] if b['state']==21]; self.assertEqual(len(posts),1)
        self.assertEqual(posts[0]['nbt'],{'id':'techguns:tg_spawner','mobsLeft':5,'maxActive':2,'spawnDelay':150,'delay':150,'spawnRange':2.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombieminer','weight':1}]})

    def test_shared_original_factory_chest_table(self):
        d=factory_house_definition(); self.assertEqual(d['loot_table'],'techguns:chests/factory_building')
        pools=factory_chest_loot()['pools']; self.assertEqual(len(pools),1); p=pools[0]
        self.assertEqual(p['rolls'],{'type':'minecraft:uniform','min':1,'max':3}); self.assertEqual(len(p['entries']),13); self.assertEqual(sum(e['weight'] for e in p['entries']),97)
        existing=Path(RESOURCES+'data/techguns/loot_table/chests/factory_building.json'); self.assertEqual(json.loads(existing.read_text(encoding='utf-8')),factory_chest_loot())

    def test_shared_table_preserves_last_unported_mine(self):
        d=factory_house_definition(); g=d['generation']; self.assertEqual(g['candidates'],small_overworld_candidates())
        self.assertEqual(g['candidates'],gas_station_definition()['generation']['candidates']); self.assertEqual(g['candidates'],train_station_definition()['generation']['candidates'])
        self.assertEqual([c['implemented'] for c in g['candidates']],[True,True,False,True]); self.assertEqual(g['tickets'],[0,9]); self.assertFalse(g['ore_toggle_required'])
        f=generate_factory_house_content(); p=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/factory_house_small.json'])['placement']
        self.assertEqual((p['spacing'],p['separation'],p['salt']),(16,15,1337262)); self.assertEqual(len(f),5)
