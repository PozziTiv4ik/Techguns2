import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_structure_grids import grid_definition, grid_placement
from generate_weapon_content import generate
from legacy_npcs import RESOURCES


class StructureGridTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.files=generate()

    def test_source_defaults_ranges_and_all_active_candidates(self):
        d=grid_definition()
        self.assertEqual([(v['default'],v['minimum'],v['maximum']) for v in d['sizes'].values()],[(16,4,100000),(32,8,100000),(64,16,100000)])
        self.assertEqual([len(v['candidates']) for v in d['sizes'].values()],[9,9,3])
        self.assertEqual(d['sizes']['big']['candidates'],['MilitaryBaseStructure','CastleStructure','AircraftCarrier'])
        self.assertEqual(d['priority'],['big','medium','small'])

    def test_every_native_set_uses_the_shared_configured_grid(self):
        expected={
            'small':{'factory_house_small','small_trainstation','small_mine','gasstation','nether_altar_small','nether_soul_platform','nether_loot_01','nether_acid_hole','nether_ore_cluster_small'},
            'medium':{'alienbug_nest','policestation','survivor_hideout','orecluster_spike','orecluster_meteor_basis','desert_oil_cluster','nether_altar_medium','nether_ghast_spawner','nether_ore_cluster_castle'},
            'big':{'military_camp','castle','aircraft_carrier'}}
        actual={size:set() for size in expected}
        prefix=RESOURCES+'data/techguns/worldgen/structure_set/'
        for path,value in self.files.items():
            if not path.startswith(prefix):continue
            d=json.loads(value);name=Path(path).stem;p=d['placement']
            self.assertEqual(p,{'type':'techguns:structure_grid','size':p['size'],'salt':1337262})
            self.assertEqual(d['structures'],[{'structure':'techguns:'+name,'weight':1}]);actual[p['size']].add(name)
            structure=json.loads(self.files[RESOURCES+f'data/techguns/worldgen/structure/{name}.json'])
            self.assertNotIn('reserved_big_grid',structure);self.assertNotIn('reserved_medium_grid',structure)
        self.assertEqual(actual,expected)

    def test_catalog_records_restart_and_empty_nether_big_reservation(self):
        d=json.loads(self.files['content/structure-grids.json'])
        self.assertEqual(d,grid_definition());self.assertEqual(d['restart'],'world/server')
        self.assertFalse(d['random_offsets']);self.assertTrue(d['frequency_is_not_a_probability'])
        self.assertIn('no active big Nether',d['nether_big'])

    def test_shared_placement_rejects_unknown_size_instead_of_silently_using_small(self):
        for invalid in ('','large','SMALL','nether'):
            with self.assertRaises(ValueError):grid_placement(invalid)


if __name__=='__main__':unittest.main()
