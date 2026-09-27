import json
import struct
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_castle import FAMILIES, scans, generated_maze, generate_castle_content, native_state
from legacy_java_serialization import read_java
from legacy_npcs import LEGACY, RESOURCES
from legacy_military_camp import generate_camp_content


class CastleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.templates=scans()

    def test_all_serialized_cells_and_sixteen_shapes_survive(self):
        self.assertEqual(tuple(self.templates),FAMILIES)
        for family in self.templates.values():
            self.assertEqual(len(family['segments']),16)
            self.assertEqual(sum(len(s['cells']) for s in family['segments'].values()),2125)
            for s in family['segments'].values():
                self.assertEqual([c[:3] for c in s['cells']],[c[:3] for c in s['source_cells']])
                for original,cell in zip(s['source_cells'],s['cells']):
                    name,meta=s['source_palette'][original[3]]
                    if name!='minecraft:spruce_door': self.assertEqual(s['palette'][cell[3]],native_state(name,meta))

    def test_doors_reconstruct_both_halves_and_hinges(self):
        count=0
        for family in self.templates.values():
            for s in family['segments'].values():
                cells={tuple(c[:3]):s['palette'][c[3]] for c in s['cells']}
                for (x,y,z),state in cells.items():
                    if state['Name']!='minecraft:spruce_door':continue
                    count+=1; props=state['Properties']; other=cells[(x,y+(1 if props['half']=='lower' else -1),z)]['Properties']
                    self.assertNotEqual(props['half'],other['half'])
                    self.assertEqual({k:v for k,v in props.items() if k!='half'},{k:v for k,v in other.items() if k!='half'})
        self.assertEqual(count,52)

    def test_palette_has_no_silent_substitutions_or_external_mods(self):
        entries={tuple(p) for f in self.templates.values() for s in f['segments'].values() for p in s['source_palette']}
        self.assertTrue(all(name.startswith('minecraft:') for name,meta in entries))
        self.assertIn(('minecraft:anvil',10),entries);self.assertIn(('minecraft:skull',1),entries)
        self.assertEqual(native_state('minecraft:stone_stairs',7)['Properties']['facing'],'north')
        self.assertEqual(native_state('minecraft:stone_stairs',7)['Name'],'minecraft:cobblestone_stairs')
        self.assertRaises(AssertionError,native_state,'other:missing',0)

    def test_data_reader_rejects_invalid_trailing_and_truncated_streams(self):
        data=(LEGACY/'resources/assets/techguns/dungeons/ncdung1.ser').read_bytes()
        for invalid in (b'not serialized',data[:-1],data+b'x',b'\xac\xed\x00\x05\x7b'):
            with self.assertRaises((ValueError,IndexError,struct.error)):read_java(invalid)

    def test_generated_maze_preserves_active_source_and_has_no_platform_dependency(self):
        for path,data in generated_maze().items():
            self.assertEqual(Path(path).read_bytes().replace(b'\r\n',b'\n'),data)
            self.assertNotIn(b'net.minecraft',data)
        maze=generated_maze()['core/src/main/java/techguns/core/castle/CastleMaze.java'].decode()
        for active in ('roll++ < maxRolls','bestl < 2','roomID = nextRoomID++','tries < maxTries','chanceStraight','chanceFork','usePillars','useFoundations','useRoof'):
            self.assertIn(active,maze)

    def test_loot_is_three_nested_pools_and_land_candidate_seed_is_shared(self):
        files=generate_castle_content(); loot=json.loads(files[RESOURCES+'data/techguns/loot_table/chests/castle.json'])
        self.assertEqual([(p['rolls']['min'],p['rolls']['max']) for p in loot['pools']],[(1,2),(3,6),(3,6)])
        self.assertEqual([e['weight'] for e in loot['pools'][2]['entries']],[3,1,2])
        self.assertTrue(all(e['type']=='minecraft:loot_table' and e['value'].startswith('techguns:blocks/military_crate_') for p in loot['pools'] for e in p['entries']))
        castle=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/castle.json'])
        camp=json.loads(generate_camp_content()[RESOURCES+'data/techguns/worldgen/structure_set/military_camp.json'])
        self.assertEqual(castle['placement'],camp['placement'])


if __name__=='__main__': unittest.main()
