import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_policeman import policeman_definition, generate_policeman_content, policeman_translations
from legacy_police_station import police_station_definition, police_loot, generate_police_station_content
from legacy_locations import location_nbt
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class PolicePortTests(unittest.TestCase):
    def test_source_npc_attributes_and_independent_equipment(self):
        d=policeman_definition()
        self.assertEqual(d['attributes'],{'MOVEMENT_SPEED':.25,'MAX_HEALTH':25,'ATTACK_DAMAGE':4,'FOLLOW_RANGE':50})
        self.assertEqual((d['intrinsic_armor'],d['weapons'],d['armor_chance'],d['camo']),(5,['revolver','pistol'],.5,5))
        self.assertEqual(d['armor'],['t2_combat_'+p for p in ('Helmet','Chestplate','Leggings','Boots')])
        self.assertTrue(d['undead'] and d['burns_in_daylight'] and d['spawner_origin_prevents_sunlight']); self.assertFalse(d['natural_spawn_entry'])

    def test_original_texture_egg_and_undead_tags(self):
        d=policeman_definition(); f=generate_policeman_content()
        self.assertEqual(d['texture_size'],[128,128]); self.assertEqual(d['egg_colors'],[0x303030,0x0000ff])
        self.assertEqual(f[RESOURCES+'assets/techguns/'+d['texture']],(LEGACY/'resources/assets/techguns'/d['texture']).read_bytes())
        for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
            self.assertEqual(json.loads(f[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values'],['techguns:zombiepoliceman'])
        for lang in ('en_us','ru_ru'): self.assertTrue(policeman_translations(lang)['entity.techguns.zombiepoliceman'])

    def test_five_source_npc_loot_pools(self):
        pools=json.loads(generate_policeman_content()[RESOURCES+'data/techguns/loot_table/entities/zombiepoliceman.json'])['pools']
        self.assertEqual([p['entries'][0]['name'] for p in pools],['techguns:heavycloth','minecraft:gunpowder','minecraft:rotten_flesh','techguns:pistolrounds','minecraft:iron_ingot'])
        for i,pool in enumerate(pools):
            e=pool['entries'][0]; self.assertEqual(e['conditions'][0]['unenchanted_chance'],.2)
            self.assertEqual(e['conditions'][0]['enchanted_chance']['per_level_above_first'],.1 if i==3 else .05)
            self.assertEqual(any(f['function']=='minecraft:enchanted_count_increase' for f in e['functions']),i!=0)

    def test_every_original_station_cell_and_declared_size(self):
        d=police_station_definition(); rows=(LEGACY/'resources/assets/techguns/structures/policestation').read_text().splitlines()
        self.assertEqual(d['source_cells'],[list(map(int,s.split(','))) for s in rows[1:]])
        self.assertEqual((len(d['cells']),d['size'],d['pivot']),(1212,[13,8,13],[6,0,6]))
        self.assertEqual(Counter(c[3] for c in d['source_cells']),{0:249,1:13,2:70,3:532,4:7,5:1,6:95,7:94,8:1,9:1,10:9,11:49,12:2,13:60,14:3,15:5,16:1,17:1,18:8,19:1,20:1,21:4,22:1,23:1,24:1,25:1,26:1})
        self.assertEqual([c[:3] for c in d['cells']],[c[:3] for c in d['source_cells']])

    def test_doors_lamps_ladder_rails_and_native_double_chest(self):
        d=police_station_definition(); p=d['palette']
        for i in (8,9): self.assertEqual((p[i]['Properties']['facing'],p[i]['Properties']['hinge']),('west','right'))
        for i in (25,26): self.assertEqual((p[i]['Properties']['facing'],p[i]['Properties']['hinge']),('south','left'))
        self.assertEqual((p[5]['Properties']['facing'],p[15]['Properties']['facing'],p[18]['Properties']['facing']),('east','up','west'))
        self.assertEqual([p[i]['Properties']['shape'] for i in (16,17,19,20)],['south_east','north_east','south_west','north_west'])
        self.assertEqual([p[i]['Properties']['type'] for i in (12,27,22)],['left','right','single'])
        self.assertEqual([c for c in d['cells'] if c[3]==27],[[6,2,7,27]])
        self.assertEqual(p[13]['Name'],'minecraft:polished_andesite'); self.assertEqual(p[21]['Properties']['type'],'top')

    def test_native_nbt_has_three_posts_and_three_deferred_tiles(self):
        d=police_station_definition(); n=read_nbt(location_nbt(d)); self.assertEqual(n['palette'],d['palette'])
        self.assertEqual([b['pos']+[b['state']] for b in n['blocks']],d['cells'])
        guards=[b for b in n['blocks'] if b['state']==14]; self.assertEqual([b['pos'] for b in guards],[[6,2,5],[10,2,3],[10,6,5]])
        for b in guards: self.assertEqual(b['nbt'],{'id':'techguns:tg_spawner','mobsLeft':3,'maxActive':1,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombiepoliceman','weight':1}]})
        chests=[b for b in n['blocks'] if b['state'] in (12,22,27)]; self.assertEqual(len(chests),3)
        for b in chests: self.assertEqual(b['nbt'],{'id':'minecraft:chest','LootTable':'techguns:chests/policestation'})

    def test_four_chest_pools_keep_all_entries_weights_and_rolls(self):
        pools=police_loot()['pools']; self.assertEqual([len(p['entries']) for p in pools],[6,8,12,6])
        self.assertEqual([sum(e['weight'] for e in p['entries']) for p in pools],[50,14,12,11])
        self.assertEqual([(p['rolls']['min'],p['rolls']['max']) for p in pools],[(1,3),(0,2),(1,3),(1,4)])
        self.assertEqual([e['name'] for e in pools[1]['entries']],['techguns:'+s for s in ('revolver','thompson','boltaction','m4','pistol','combatshotgun','mac10','aug')])
        self.assertEqual([e['functions'][0]['components']['techguns:rounds'] for e in pools[1]['entries']],[6,20,6,30,18,8,32,30])

    def test_foundation_clearing_and_native_medium_slot(self):
        d=police_station_definition(); self.assertEqual(len([c for c in d['cells'] if c[1]==0 and c[3]==0]),169)
        self.assertEqual((d['foundation_depth'],d['clear_height'],d['worldgen_floor_offset']),(3,7,-1))
        self.assertEqual(d['generation']['height_samples'],[0,4,8,12]); self.assertFalse(d['generation']['ore_toggle_required'])
        f=generate_police_station_content(); p=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/policestation.json'])['placement']
        self.assertEqual((p['spacing'],p['separation'],p['salt']),(32,31,1337262))
