import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_meteor import meteor_definition, generate_meteor_content
from legacy_locations import location_nbt
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class MeteorPortTests(unittest.TestCase):
    def test_all_scanned_cells_and_exact_cleaning_footprint(self):
        d=meteor_definition(); rows=(LEGACY/'resources/assets/techguns/structures/orecluster_meteorbase').read_text(encoding='utf-8').splitlines()
        self.assertEqual(len(d['cells']),2265); self.assertEqual(d['cells'],[list(map(int,r.split(','))) for r in rows[1:]])
        self.assertEqual((d['size'],d['pivot'],d['surface_offset'],d['clear_above_surface'],d['clear_bottom_columns']),([17,12,17],[8,0,8],-5,30,289))
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:1304,1:161,2:85,3:62,4:483,5:5,6:40,7:33,8:30,9:3,10:4,11:3,12:1,13:1,14:1,15:20,16:12,17:4,18:8,19:4,20:1})
        self.assertEqual(Counter(c[3] for c in d['cells'] if c[1]==0),{0:288,20:1})

    def test_six_fixed_resource_families_keep_metadata_and_attachment(self):
        d=meteor_definition(); p=d['palette']; self.assertEqual(len(p),21)
        self.assertEqual(p[6]['Name'],'techguns:metalpanel_panel_large_border'); self.assertEqual(p[8]['Name'],'techguns:concrete_brown_light_scaff')
        self.assertEqual(p[11]['Name'],'techguns:metalpanel_steelframe_scaffold')
        self.assertEqual([(p[i]['Name'],p[i].get('Properties',{}).get('facing')) for i in (9,14)],[('techguns:lamp_white','down'),('techguns:lamp_white','up')])
        self.assertEqual([p[i]['Properties']['facing'] for i in (17,19)],['east','south'])
        self.assertEqual([p[i]['Properties']['half'] for i in (12,13)],['lower','upper'])
        self.assertEqual([p[i]['Properties']['facing'] for i in (12,13)],['south','south'])
        self.assertEqual([c[:3] for c in d['cells'] if c[3] in (12,13)],[[5,6,3],[5,7,3]])

    def test_five_distinct_army_commando_encounters_and_markers_survive_nbt(self):
        d=meteor_definition(); data=location_nbt(d); self.assertEqual(data,location_nbt(d)); n=read_nbt(data)
        self.assertEqual(n['palette'],d['palette']); self.assertEqual([x['pos']+[x['state']] for x in n['blocks']],d['cells'])
        holes=[c for c in n['blocks'] if c['state']==5]; self.assertEqual([c['pos'] for c in holes],[[1,7,1],[1,7,15],[4,10,4],[15,7,1],[15,7,15]])
        for hole in holes: self.assertEqual(hole['nbt'],{'id':'techguns:tg_spawner','mobsLeft':2,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:armysoldier','weight':4},{'id':'techguns:commando','weight':1}]})
        for index,label,count in [(7,'meteor_magma_or_stone',33),(16,'meteor_cluster_ore',12),(18,'meteor_cluster',8)]:
            entries=[c for c in n['blocks'] if c['state']==index]; self.assertEqual(len(entries),count)
            self.assertTrue(all(c['nbt']=={'id':'minecraft:structure_block','mode':'DATA','metadata':'techguns:'+label} for c in entries))

    def test_inclusive_weights_and_netheritic_mixture(self):
        d=meteor_definition(); self.assertEqual(d['cluster_types'],['coal','common_metal','common_gem','rare_metal','shiny_metal','shiny_gem','uranium','nether_crystal'])
        self.assertEqual(d['cluster_weights'],[5]*7+[15]); self.assertEqual(d['cluster_roll_bound'],51)
        self.assertEqual(d['mixtures']['ore_weights'],[[1],[1,1,1],[1,1],[1],[1,1],[2,1,3],[1,1],[2,1,1]])
        self.assertEqual(d['mixtures']['ores'][-1],['minecraft:netherrack','minecraft:nether_quartz_ore','minecraft:glowstone'])

    def test_disjoint_medium_ticket_and_native_structure_set(self):
        f=generate_meteor_content(); d=meteor_definition()['generation']
        self.assertEqual((d['ordinary_land_total'],d['sandy_wasteland_total'],d['sandy_wasteland_with_block_oil_total']),(35,55,70)); self.assertEqual(d['height_samples'],[0,4,8,12,16])
        self.assertTrue(d['ocean_excluded'] and d['unported_candidates_retain_weight'])
        structure=json.loads(f[RESOURCES+'data/techguns/worldgen/structure/orecluster_meteor_basis.json']); self.assertEqual(structure['reserved_big_grid'],64); self.assertEqual(structure['step'],'top_layer_modification')
        placement=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/orecluster_meteor_basis.json'])['placement']; self.assertEqual((placement['spacing'],placement['separation'],placement['salt']),(32,31,1337262))
        self.assertEqual(f[RESOURCES+'data/techguns/structure/orecluster_meteor_basis.nbt'],location_nbt(meteor_definition()))


if __name__=='__main__': unittest.main()
