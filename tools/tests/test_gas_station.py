import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_gas_station import gas_station_definition, gas_station_loot, generate_gas_station_content
from legacy_locations import location_nbt
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class GasStationTests(unittest.TestCase):
    def test_every_original_cell_and_rectangular_bounds(self):
        d=gas_station_definition(); rows=(LEGACY/'resources/assets/techguns/structures/gasstation').read_text(encoding='utf-8').splitlines()
        self.assertEqual(d['source_cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual((len(d['cells']),d['size'],d['pivot']),(719,[9,7,12],[4,0,6]))
        self.assertEqual(Counter(c[3] for c in d['source_cells']),{0:144,1:289,2:1,3:1,4:1,5:1,6:1,7:47,8:41,9:72,10:1,11:8,12:6,13:1,14:48,15:8,16:4,17:8,18:16,19:1,20:1,21:6,22:7,23:6})
        self.assertEqual([c[:3] for c in d['cells']],[c[:3] for c in d['source_cells']])

    def test_metadata_doors_lamps_slabs_lever_and_trapdoors(self):
        p=gas_station_definition()['palette']
        self.assertEqual(p[0]['Name'],'techguns:concrete_grey_dark'); self.assertEqual(p[2]['Name'],'minecraft:air')
        self.assertEqual(p[4]['Properties'],{'face':'wall','facing':'south','powered':'false'})
        self.assertEqual(p[5]['Name'],'minecraft:quartz_stairs'); self.assertEqual(p[5]['Properties']['facing'],'west')
        self.assertEqual([p[i]['Properties']['facing'] for i in (6,16)],['east','up'])
        for i in (10,13): self.assertEqual((p[i]['Properties']['facing'],p[i]['Properties']['hinge']),('west','left'))
        self.assertEqual([p[i]['Properties']['type'] for i in (14,15,17,18)],['bottom','top','double','top'])
        for i in (20,21): self.assertEqual((p[i]['Properties']['facing'],p[i]['Properties']['half'],p[i]['Properties']['open']),('west','top','true' if i==21 else 'false'))

    def test_three_vertical_native_double_chests(self):
        d=gas_station_definition()
        self.assertEqual(d['palette'][23]['Properties']['type'],'left'); self.assertEqual(d['palette'][24]['Properties']['type'],'right')
        self.assertEqual([c for c in d['cells'] if c[3]==24],[[7,y,6,24] for y in (2,3,4)])
        self.assertEqual([c for c in d['cells'] if c[3]==23],[[7,y,7,23] for y in (2,3,4)])

    def test_native_nbt_finite_zombies_and_six_deferred_rewards(self):
        d=gas_station_definition(); n=read_nbt(location_nbt(d)); self.assertEqual(n['palette'],d['palette'])
        self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells'])
        post=[b for b in n['blocks'] if b['state']==19]; self.assertEqual(len(post),1); self.assertEqual(post[0]['pos'],[5,2,6])
        self.assertEqual(post[0]['nbt'],{'id':'techguns:tg_spawner','mobsLeft':3,'maxActive':2,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:'+s,'weight':1} for s in ('zombiesoldier','zombiefarmer','zombieminer')]})
        chests=[b for b in n['blocks'] if b['state'] in (23,24)]; self.assertEqual(len(chests),6)
        for b in chests: self.assertEqual(b['nbt'],{'id':'minecraft:chest','LootTable':'techguns:chests/gasstation'})

    def test_all_source_loot_entries_weights_counts_and_luck(self):
        pools=gas_station_loot()['pools']; self.assertEqual([len(p['entries']) for p in pools],[13,2])
        self.assertEqual([sum(e['weight'] for e in p['entries']) for p in pools],[97,2])
        self.assertEqual(pools[0]['rolls'],{'type':'minecraft:uniform','min':1,'max':3}); self.assertEqual(pools[1]['rolls'],2)
        self.assertEqual(pools[1]['bonus_rolls'],{'type':'minecraft:uniform','min':0,'max':2})
        self.assertEqual([e['name'] for e in pools[1]['entries']],['techguns:fueltank','techguns:fueltankempty'])
        for e in pools[1]['entries']: self.assertEqual(e['functions'],[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':1,'max':2}}])
        self.assertEqual(pools[0]['entries'][-1]['functions'][0]['count'],{'type':'minecraft:uniform','min':1,'max':2})
        self.assertEqual([e['name'] for e in pools[0]['entries']],['minecraft:'+s for s in ('iron_ingot','redstone','coal','gunpowder','gold_ingot','diamond','ender_pearl')]+['techguns:'+s for s in ('heavycloth','mechanicalpartsiron','mechanicalpartsobsidiansteel','plasticsheet','rubberbar','ingotobsidiansteel')])

    def test_full_foundation_and_cleanup_above_template(self):
        d=gas_station_definition(); self.assertEqual(len([c for c in d['cells'] if c[1]==0 and c[3]==0]),108)
        self.assertEqual((d['foundation_depth'],d['clear_height'],d['worldgen_floor_offset']),(3,7,-1))
        self.assertEqual(d['size'][1],7); self.assertEqual(d['generation']['height_samples_x'],[0,4,8]); self.assertEqual(d['generation']['height_samples_z'],[0,4,8,12])

    def test_small_grid_reserves_medium_and_big_and_other_tickets(self):
        d=gas_station_definition(); g=d['generation']; self.assertFalse(g['ore_toggle_required'])
        self.assertEqual([c['weight'] for c in g['candidates']],[10]*4); self.assertEqual([c['implemented'] for c in g['candidates']],[True,True,False,True])
        f=generate_gas_station_content(); p=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/gasstation.json'])['placement']
        self.assertEqual((p['spacing'],p['separation'],p['salt']),(16,15,1337262))
        s=json.loads(f[RESOURCES+'data/techguns/worldgen/structure/gasstation.json']); self.assertEqual((s['reserved_medium_grid'],s['reserved_big_grid']),(32,64))
