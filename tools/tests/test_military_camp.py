import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_military_camp import PARTS, JAVA, camp_definition, generated_geometry, generate_camp_content, nested_loot, source
from legacy_npcs import LEGACY, RESOURCES
from legacy_models import strip_comments


class MilitaryCampSourceTests(unittest.TestCase):
    def test_component_pool_order_variants_and_asymmetric_constraints(self):
        groups=camp_definition()['components']
        self.assertEqual([len(v) for v in groups.values()],[13,7,2])
        self.assertEqual([re.search(r'new (\w+)\(',x)[1] for x in groups['inside']],
                         ['Tent','Tent','Tent','Containers','CampProps','CampProps','Bunker','Barracks','Helipad','Tanks','CampProps','Bunker','Bunker'])
        self.assertTrue(groups['inside'][-1].endswith('.setSwapXZ(true)'))
        self.assertIn('WatchTowerSmall(3, 8, 3, 4, 8, -1, 3)',groups['corner'][0])

    def test_nested_chest_tables_preserve_source_weights_ranges_and_links(self):
        for name,ranges,names,weights in [
            ('militarybase_bunker',[(1,2),(1,3),(3,6)],['gun','generic','ammo','explosives'],[1,1,3,1]),
            ('militarybase_barracks',[(2,6),(1,4)],['medical','armor'],[1,1])]:
            table=nested_loot(name)
            self.assertEqual([(p['rolls']['min'],p['rolls']['max']) for p in table['pools']],ranges)
            entries=[e for p in table['pools'] for e in p['entries']]
            self.assertEqual([e['value'] for e in entries],['techguns:blocks/military_crate_'+n for n in names])
            self.assertEqual([e['weight'] for e in entries],weights)
            self.assertTrue(all(e['type']=='minecraft:loot_table' for e in entries))

    def test_geometry_is_reproducible_and_modern_only(self):
        root=Path(__file__).resolve().parents[2]
        for name,data in generated_geometry().items():
            self.assertEqual((root/name).read_bytes().replace(b'\r\n',b'\n'),data,name)
            self.assertNotRegex(data.decode(),r'import (?:techguns\.(?:TGBlocks|util|world)|net\.minecraft\.(?:init|tileentity|block\.state))')
        self.assertEqual(len(generated_geometry()),11)

    def test_active_source_quirks_are_not_silently_repaired(self):
        files=generated_geometry()
        tent=files[JAVA+'Tent.java'].decode()
        self.assertIn('int camoMeta;',tent)
        self.assertEqual(tent.count('camoMeta'),4)
        self.assertIn('float chestroll = rnd.nextFloat();',files[JAVA+'CampProps.java'].decode())
        terrain=files[JAVA+'CampTerrain.java'].decode()
        self.assertIn('heights.get((heights.size()/2)-1)',terrain)
        self.assertIn('adjustHeightAtPos(world, posX+x, posZ+z, y, p, Math.round(newY));',terrain)

    def test_big_land_and_water_candidates_stay_separate(self):
        raw=strip_comments((LEGACY/'java/techguns/world/TGStructureSpawnRegister.java').read_text())
        self.assertRegex(raw,r'new MilitaryBaseStructure\([^;]+,1,null,OVERWORLD,LAND,StructureSize.BIG')
        self.assertRegex(raw,r'new CastleStructure\(\), 1, null, OVERWORLD, LAND, StructureSize.BIG')
        self.assertRegex(raw,r'new AircraftCarrier\([^;]+,1,null, OVERWORLD, WATER, StructureSize.BIG')
        files=generate_camp_content()
        placement=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/military_camp.json'])['placement']
        self.assertEqual((placement['spacing'],placement['separation']),(64,63))
        self.assertNotIn('reserved_big_grid',json.loads(files[RESOURCES+'data/techguns/worldgen/structure/military_camp.json']))

    def test_spawn_flag_comment_does_not_replace_active_encounters(self):
        raw=source('MilitaryCamp')
        self.assertNotIn('CampFlagTileEnt',raw)
        self.assertIn('spawner.setParams(1, 1, 200,0)',raw)
        self.assertIn('spawner.setParams(3, 1, 200,0)',raw)
        port=generated_geometry()[JAVA+'CampLayout.java'].decode()
        self.assertIn('"attackhelicopter", 1, Math.min(y+64,world.getActualHeight())-y',port)
        self.assertIn('"armysoldier", 3, 0',port)


if __name__=='__main__':
    unittest.main()
