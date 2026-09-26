import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_ghast_spawner import ghast_spawner_definition, generate_ghast_spawner_content
from legacy_locations import location_nbt, factory_chest_loot
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class GhastSpawnerPortTests(unittest.TestCase):
    def test_all_original_cells_and_bounds(self):
        d=ghast_spawner_definition(); rows=(LEGACY/'resources/assets/techguns/structures/nether_ghast_spawner').read_text().splitlines()
        self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual((len(d['cells']),d['size'],d['declared_size'],d['pivot']),(738,[10,14,10],[10,14,10],[5,0,5]))
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:174,1:473,2:6,3:7,4:4,5:34,6:18,7:3,8:12,9:1,10:3,11:2,12:1})

    def test_original_rock_aliases_and_skull_yaw(self):
        p=ghast_spawner_definition()['palette']
        for i in (0,2,3,10,11): self.assertEqual(p[i],{'Name':'minecraft:netherrack'})
        self.assertEqual(p[5],{'Name':'techguns:nethermetal_grey_dark'})
        self.assertEqual(p[7],{'Name':'minecraft:skeleton_skull','Properties':{'rotation':'0','powered':'false'}})

    def test_vanilla_spawner_has_species_without_finite_quota(self):
        d=ghast_spawner_definition(); n=read_nbt(location_nbt(d))
        self.assertEqual(n['palette'],d['palette']); self.assertEqual([c['pos']+[c['state']] for c in n['blocks']],d['cells'])
        spawners=[b for b in n['blocks'] if b['state']==9]
        self.assertEqual(spawners,[{'pos':[4,5,5],'state':9,'nbt':{'id':'minecraft:mob_spawner','SpawnData':{'entity':{'id':'techguns:ghastling'}}}}])
        source=(LEGACY/'java/techguns/util/MBlockVanillaSpawner.java').read_text()
        self.assertIn('spawner.getSpawnerBaseLogic().setEntityId(entityId);',source)

    def test_original_chest_direction_and_deferred_shared_table(self):
        d=ghast_spawner_definition(); n=read_nbt(location_nbt(d)); chest=[b for b in n['blocks'] if b['state']==12]
        self.assertEqual(chest,[{'pos':[7,4,2],'state':12,'nbt':{'id':'minecraft:chest','LootTable':'techguns:chests/factory_building'}}])
        self.assertEqual(d['palette'][12]['Properties'],{'facing':'south','type':'single','waterlogged':'false'})
        self.assertEqual(len(factory_chest_loot()['pools'][0]['entries']),13)

    def test_exact_thirteen_foundation_cells_and_height(self):
        d=ghast_spawner_definition()
        self.assertEqual([c[:3] for c in d['cells'] if c[1]==0],[[3,0,4],[3,0,5],[4,0,3],[4,0,4],[4,0,5],[4,0,6],[5,0,4],[5,0,5],[5,0,6],[6,0,3],[6,0,4],[6,0,5],[7,0,5]])
        self.assertEqual((d['foundation_cells'],d['foundation_depth'],d['foundation_stop_after_solids'],d['height_offset'],d['worldgen_floor_offset']),(13,16,2,-2,-1))

    def test_complete_medium_table_and_native_structure_set(self):
        d=ghast_spawner_definition(); self.assertFalse(d['generation']['ore_toggle_required'])
        self.assertEqual([(c['weight'],c['implemented']) for c in d['generation']['candidates']],[(10,True),(10,True),(1000,True)])
        files=generate_ghast_spawner_content()
        s=json.loads(files[RESOURCES+'data/techguns/worldgen/structure/nether_ghast_spawner.json'])
        self.assertEqual((s['step'],s['reserved_big_grid']),('top_layer_modification',64))
        placement=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/nether_ghast_spawner.json'])['placement']
        self.assertEqual((placement['spacing'],placement['separation'],placement['salt']),(32,31,1337262))
