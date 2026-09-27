import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_small_mine import small_mine_definition, generate_small_mine_content
from legacy_locations import location_nbt, small_overworld_candidates
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class SmallMineTests(unittest.TestCase):
    def test_exact_sparse_source_scan_and_nbt(self):
        d=small_mine_definition(); rows=(LEGACY/'resources/assets/techguns/structures/small_mine').read_text().splitlines()
        self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual(len({tuple(c[:3]) for c in d['cells']}),972)
        n=read_nbt(location_nbt(d)); self.assertEqual(n['palette'],d['palette']); self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells'])
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:259,1:239,2:26,3:178,4:16,5:143,6:2,7:18,8:6,9:3,10:2,11:2,12:2,13:67,14:2,15:6,16:1})

    def test_only_one_clear_column_and_no_foundation(self):
        d=small_mine_definition(); self.assertEqual((d['size'],d['registered_xz_size'],d['pivot']),([17,11,11],[17,11],[8,0,5]))
        self.assertEqual((d['surface_offset'],d['worldgen_floor_offset'],d['clear_height'],d['foundation_depth']),(-5,-1,4,0))
        self.assertEqual(d['clear_bottom_cells'],[[12,0,5,15]])
        self.assertEqual((d['generation']['height_samples_x'],d['generation']['height_samples_z']),([0,4,8,12,16],[0,4,8]))

    def test_inclusive_cluster_and_nested_ore_probabilities(self):
        d=small_mine_definition(); self.assertEqual(d['cluster_roll_bound'],42); self.assertEqual(d['cluster_roll_counts'],[11,10,10,5,2,2,2])
        m=d['mixtures']; self.assertEqual(m['ore_roll_counts'],[[1],[2,1,1],[2,1],[1],[2,1],[3,1,3],[2,1]])
        self.assertEqual(m['ores'],[['minecraft:coal_ore'],['minecraft:iron_ore','techguns:ore_copper','techguns:ore_tin'],['minecraft:redstone_ore','minecraft:lapis_ore'],['techguns:ore_lead'],['minecraft:gold_ore','techguns:ore_titanium'],['minecraft:diamond_ore','minecraft:emerald_ore','minecraft:stone'],['techguns:ore_uranium','minecraft:stone']])
        self.assertEqual((m['stone_chance'],m['cluster_or_ore_chance'],m['patch_roll_counts']),(.75,.5,[5,1]))
        self.assertEqual(m['cover'],['minecraft:grass_block','minecraft:snow_block','minecraft:sand','minecraft:netherrack'])
        self.assertEqual(m['patch_cover'][1],'minecraft:grass_block')

    def test_oriented_logs_rails_torches_and_cobblestone_stairs(self):
        p=small_mine_definition()['palette']
        self.assertEqual([p[i]['Properties']['axis'] for i in (2,4,7)],['y','z','x'])
        self.assertEqual([p[i]['Properties']['shape'] for i in (6,9)],['east_west','ascending_west'])
        self.assertEqual([p[i]['Properties']['facing'] for i in (10,11,12)],['south','north','west'])
        self.assertEqual(p[8],{'Name':'minecraft:cobblestone_stairs','Properties':{'facing':'west','half':'bottom','shape':'straight','waterlogged':'false'}})

    def test_posts_and_markers_have_full_native_nbt(self):
        d=small_mine_definition(); n=read_nbt(location_nbt(d)); posts=[b for b in n['blocks'] if b['state']==14]
        self.assertEqual([b['pos'] for b in posts],[[10,2,4],[11,2,6]])
        for p in posts: self.assertEqual(p['nbt'],{'id':'techguns:tg_spawner','mobsLeft':2,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombieminer','weight':1}]})
        self.assertEqual(Counter(b['nbt']['metadata'] for b in n['blocks'] if b['state'] in (3,5,13,15,16)),{'techguns:mine_cover':178,'techguns:mine_patch':143,'techguns:mine_stone_ore':67,'techguns:mine_cluster_ore':6,'techguns:mine_cluster':1})

    def test_last_small_candidate_keeps_shared_native_salt_and_original_tickets(self):
        d=small_mine_definition(); g=d['generation']; self.assertEqual(g['candidates'],small_overworld_candidates())
        self.assertTrue(all(v['implemented'] and v['weight']==10 for v in g['candidates'])); self.assertEqual(g['tickets'],[20,29]); self.assertFalse(g['ore_toggle_required'])
        files=generate_small_mine_content(); self.assertEqual(len(files),5)
        for name in ('factory_house_small','small_trainstation','gasstation'):
            old=json.loads(Path(RESOURCES+f'data/techguns/worldgen/structure_set/{name}.json').read_text())
            new=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/small_mine.json'])
            self.assertEqual(new['placement'],old['placement'])
