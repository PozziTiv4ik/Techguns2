import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_train_station import train_station_definition, generate_train_station_content
from legacy_locations import location_nbt, factory_chest_loot
from legacy_gas_station import gas_station_definition
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class TrainStationTests(unittest.TestCase):
    def test_all_source_records_and_final_air_overwrite(self):
        d=train_station_definition(); rows=(LEGACY/'resources/assets/techguns/structures/small_trainstation').read_text(encoding='utf-8').splitlines()
        self.assertEqual(d['source_cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual((len(d['source_cells']),len(d['cells'])),(328,327))
        self.assertEqual([c for c in d['source_cells'] if c[:3]==[7,1,4]],[[7,1,4,6],[7,1,4,21]])
        self.assertEqual([c for c in d['cells'] if c[:3]==[7,1,4]],[[7,1,4,21]])
        self.assertEqual(Counter(c[3] for c in d['source_cells']),{0:21,1:21,2:18,3:27,4:35,5:51,6:58,7:9,8:4,9:31,10:2,11:2,12:9,13:3,14:1,15:1,16:1,17:1,18:1,19:4,20:27,21:1})

    def test_scan_bounds_differ_from_registered_pivot(self):
        d=train_station_definition(); self.assertEqual((d['size'],d['registered_xz_size'],d['pivot']),([11,7,11],[11,12],[5,0,6]))
        self.assertEqual((d['foundation_cells'],d['foundation_depth'],d['clear_height']),(74,1,7))
        self.assertEqual(Counter(c[3] for c in d['cells'] if c[1]==0),{0:21,2:18,4:35})

    def test_damage_probabilities_include_source_zero_roll(self):
        d=train_station_definition(); groups={w['palette_index']:w for w in d['weighted_cells']}
        self.assertEqual({i:w['roll_counts'] for i,w in groups.items()},{1:[3,1],2:[3,1],3:[5,1],7:[2,1],8:[5,1],9:[4,3,3,2],19:[5,1],20:[5,1]})
        self.assertEqual(sum(c[3] in groups for c in d['cells']),141)
        for w in groups.values(): self.assertEqual(w['roll_bound'],sum(w['roll_counts'])); self.assertEqual(w['roll_counts'],[w['weights'][0]+1,*w['weights'][1:]])

    def test_active_aliases_and_rotatable_states(self):
        d=train_station_definition(); p=d['palette']; w={w['palette_index']:w for w in d['weighted_cells']}
        self.assertEqual(p[4]['Name'],'minecraft:stone'); self.assertEqual(p[5]['Name'],'minecraft:bricks')
        self.assertEqual([s['Name'] for s in w[9]['states']],['minecraft:'+s for s in ('stone_bricks','mossy_stone_bricks','cracked_stone_bricks','air')])
        for i,facing,half in ((3,'east','bottom'),(8,'west','top'),(19,'east','top'),(20,'west','bottom')):
            self.assertEqual((w[i]['states'][0]['Properties']['facing'],w[i]['states'][0]['Properties']['half']),(facing,half))
        for i,facing in ((15,'south'),(16,'north')): self.assertEqual((p[i]['Properties']['facing'],p[i]['Properties']['half']),(facing,'top'))
        self.assertEqual(w[1]['states'][0]['Properties']['shape'],'north_south'); self.assertEqual(p[12]['Properties']['type'],'top')
        self.assertEqual(p[13]['Properties']['facing'],'south'); self.assertEqual(p[17]['Properties'],{'facing':'west','lit':'false'})

    def test_native_nbt_keeps_variants_single_reward_and_post(self):
        d=train_station_definition(); n=read_nbt(location_nbt(d)); self.assertEqual(n['palette'],d['palette'])
        self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells'])
        for w in d['weighted_cells']:
            for b in n['blocks']:
                if b['state']==w['palette_index']:
                    self.assertEqual((b['nbt']['Variants'],b['nbt']['Weights'],b['nbt']['metadata']),(w['states'],w['weights'],w['marker']))
        post=[b for b in n['blocks'] if b['state']==21]; self.assertEqual(len(post),1)
        self.assertEqual(post[0]['nbt'],{'id':'techguns:tg_spawner','mobsLeft':3,'maxActive':2,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombieminer','weight':1}]})
        chest=[b for b in n['blocks'] if b['state']==14]; self.assertEqual(len(chest),1); self.assertEqual(chest[0]['pos'],[7,4,6])
        self.assertEqual(chest[0]['nbt'],{'id':'minecraft:chest','LootTable':'techguns:chests/small_trainstation'})

    def test_thirteen_original_resources_and_four_maximum_rolls(self):
        loot=factory_chest_loot('small_trainstation'); self.assertEqual(len(loot['pools']),1); p=loot['pools'][0]
        self.assertEqual(p['rolls'],{'type':'minecraft:uniform','min':1,'max':4}); self.assertEqual(len(p['entries']),13); self.assertEqual(sum(e['weight'] for e in p['entries']),97)
        self.assertEqual([e['name'] for e in p['entries']],['minecraft:'+s for s in ('iron_ingot','redstone','coal','gunpowder','gold_ingot','diamond','ender_pearl')]+['techguns:'+s for s in ('heavycloth','mechanicalpartsiron','mechanicalpartsobsidiansteel','plasticsheet','rubberbar','ingotobsidiansteel')])
        self.assertEqual(p['entries'][-1]['functions'][0]['count'],{'type':'minecraft:uniform','min':1,'max':2})

    def test_shared_small_grid_and_unported_tickets(self):
        d=train_station_definition(); g=d['generation']; self.assertEqual(g['candidates'],gas_station_definition()['generation']['candidates'])
        self.assertEqual([c['implemented'] for c in g['candidates']],[False,True,False,True]); self.assertEqual(g['tickets'],[10,19]); self.assertFalse(g['ore_toggle_required'])
        f=generate_train_station_content(); p=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/small_trainstation.json'])['placement']
        self.assertEqual((p['spacing'],p['separation'],p['salt']),(16,15,1337262))
        s=json.loads(f[RESOURCES+'data/techguns/worldgen/structure/small_trainstation.json']); self.assertEqual((s['reserved_medium_grid'],s['reserved_big_grid']),(32,64))
