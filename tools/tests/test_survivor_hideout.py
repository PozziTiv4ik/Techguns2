import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_survivor_hideout import survivor_hideout_definition, survivor_loot, generate_survivor_hideout_content
from legacy_locations import location_nbt
from legacy_npcs import LEGACY, RESOURCES
from legacy_items import shared_items
from test_locations import read_nbt


class SurvivorHideoutTests(unittest.TestCase):
    def test_all_source_cells_and_rectangular_bounds(self):
        d=survivor_hideout_definition(); rows=(LEGACY/'resources/assets/techguns/structures/survivor_hideout').read_text(encoding='utf-8').splitlines()
        self.assertEqual(d['source_cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual((len(d['cells']),d['size'],d['pivot']),(1277,[11,11,19],[5,0,9]))
        self.assertEqual(Counter(c[3] for c in d['source_cells']),{0:284,1:24,2:545,3:7,4:48,5:5,6:4,7:3,8:12,9:2,10:37,11:3,12:1,13:201,14:25,15:14,16:50,17:1,18:1,19:1,20:1,21:1,22:1,23:4,24:1,25:1})
        self.assertEqual([c[:3] for c in d['cells']],[c[:3] for c in d['source_cells']])

    def test_paired_chests_bed_and_original_facings(self):
        d=survivor_hideout_definition(); p=d['palette']
        self.assertEqual([c for c in d['cells'] if c[3]>=26],[[1,2,17,26],[2,2,17,27],[9,3,5,28],[9,3,6,29]])
        self.assertEqual([p[i]['Properties']['type'] for i in range(26,30)],['left','right','right','left'])
        for i in (7,11,25): self.assertEqual(p[i]['Properties']['type'],'single')
        self.assertEqual([p[i]['Properties']['facing'] for i in (6,7,11,12,17,18,19,22,23,24,25)],['down','north','west','east','north','north','east','south','south','north','south'])
        self.assertEqual([p[i]['Properties']['part'] for i in (17,18)],['head','foot'])
        self.assertEqual(p[17]['Name'],'minecraft:red_bed'); self.assertEqual(p[15]['Name'],'minecraft:cobblestone_slab')

    def test_native_nbt_three_holes_and_seven_deferred_rewards(self):
        d=survivor_hideout_definition(); n=read_nbt(location_nbt(d)); self.assertEqual(n['palette'],d['palette'])
        self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells'])
        posts=[b for b in n['blocks'] if b['state'] in (9,21)]
        self.assertEqual([b['pos'] for b in posts],[[3,2,15],[8,3,14],[8,7,3]])
        for b in posts:
            v=b['nbt']; rifle=b['state']==21
            self.assertEqual((v['mobsLeft'],v['maxActive'],v['spawnDelay'],v['delay'],v['spawnRange'],v['spawnHeightOffset']),(2 if rifle else 3,1 if rifle else 2,200,200,1.,0))
            self.assertEqual(v['mobtypes'],[{'id':'techguns:bandit','weight':1}])
            self.assertEqual(v.get('weapon'),{'id':'techguns:boltaction','count':1} if rifle else None)
        chests=[b for b in n['blocks'] if d['palette'][b['state']]['Name']=='minecraft:chest']; self.assertEqual(len(chests),7)
        for b in chests: self.assertEqual(b['nbt'],{'id':'minecraft:chest','LootTable':'techguns:chests/survivor_hideout'})

    def test_shared_panel_roll_and_biome_canopy(self):
        d=survivor_hideout_definition()
        self.assertEqual(d['panel_variants'],['metalpanel_container_'+v for v in ('red','green','blue','orange')])
        self.assertEqual(d['canopy_variants'],['camonet_top_'+v for v in ('wood','desert','snow')])
        self.assertEqual(d['palette'][13]['Name'],'techguns:'+d['panel_variants'][0]); self.assertEqual(d['palette'][16]['Name'],'techguns:'+d['canopy_variants'][0])
        source=(LEGACY/'java/techguns/world/structures/SurvivorHideout.java').read_text(encoding='utf-8'); self.assertIn('int indexroll = rnd.nextInt(4);',source)

    def test_loot_preserves_each_source_weight_count_and_roll(self):
        source=json.loads((LEGACY/'resources/assets/techguns/loot_tables/chests/survivor_hideout.json').read_text(encoding='utf-8'))
        pools=survivor_loot()['pools']; self.assertEqual([len(p['entries']) for p in pools],[13,14,12,2])
        for old,new in zip(source['pools'],pools):
            self.assertEqual(new['rolls'],{'type':'minecraft:uniform',**old['rolls']})
            for a,b in zip(old['entries'],new['entries']):
                name=a['name']; functions=[]
                for f in a.get('functions',[]):
                    if f['function']=='set_data': name='techguns:'+shared_items()[f['data']]
                    else: functions.append({'function':'minecraft:set_count','count':{'type':'minecraft:uniform',**f['count']}})
                self.assertEqual((b['name'],b['weight'],b.get('functions',[])),(name,a['weight'],functions))
        self.assertEqual(sum('_incendiary' in e['name'] for e in pools[1]['entries']),7)

    def test_foundation_is_sparse_and_lantern_supports_are_present(self):
        d=survivor_hideout_definition(); cells={tuple(c[:3]):c[3] for c in d['cells']}
        self.assertEqual(sum(c[1]==0 and c[3]==0 for c in d['cells']),149)
        self.assertEqual((d['foundation_depth'],d['clear_height'],d['worldgen_floor_offset']),(3,7,-1))
        self.assertEqual([cells[x,y-1,z] for (x,y,z),v in cells.items() if v==6],[0,13,22,8])

    def test_native_medium_slot_and_overworld_only_data(self):
        d=survivor_hideout_definition(); self.assertEqual(d['generation']['height_sample_count'],15); self.assertFalse(d['generation']['ore_toggle_required'])
        f=generate_survivor_hideout_content(); p=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/survivor_hideout.json'])['placement']
        self.assertEqual((p['spacing'],p['separation'],p['salt']),(32,31,1337262))
        self.assertEqual(json.loads(f[RESOURCES+'data/techguns/tags/worldgen/biome/has_survivor_hideout.json'])['values'],['#minecraft:is_overworld'])
