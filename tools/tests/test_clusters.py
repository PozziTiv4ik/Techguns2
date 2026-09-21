import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_clusters import cluster_definitions, generate_cluster_content, cluster_translations
from legacy_locations import cluster_location_definition, location_nbt, generate_location_content
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class ClusterPortTests(unittest.TestCase):
    def test_actual_config_defaults_not_obsolete_enum_multipliers(self):
        variants=cluster_definitions()
        self.assertEqual([v['metadata'] for v in variants],list(range(9)))
        self.assertEqual([v['type'] for v in variants],['coal','common_metal','rare_metal','shiny_metal','uranium','common_gem','shiny_gem','nether_crystal','oil'])
        self.assertEqual([v['mining_level'] for v in variants],[0,0,1,2,3,1,3,2,2])
        self.assertEqual([v['ore_multiplier'] for v in variants],[10,5,2.5,1,.5,5,.2,4,4])
        self.assertEqual([v['power_multiplier'] for v in variants],[.1,.2,.4,1,1,.2,1,.5,1])

    def test_original_pixels_animation_and_separate_atlases(self):
        files=generate_cluster_content()
        for v in cluster_definitions():
            name=v['id']
            for atlas in ('block','item'):
                for suffix in ('.png','.png.mcmeta'):
                    self.assertEqual(files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}{suffix}'],(LEGACY/f'resources/assets/techguns/textures/blocks/{name}{suffix}').read_bytes())
                model=json.loads(files[RESOURCES+f'assets/techguns/models/{atlas}/{name}.json'])
                self.assertEqual(model['textures'],{'all':f'techguns:{atlas}/{name}'})
            self.assertNotIn(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json',files)

    def test_translations_preserve_all_source_names_and_tooltip_labels(self):
        for lang in ('en_us','ru_ru'):
            source=dict(l.split('=',1) for l in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in l)
            converted=cluster_translations(lang)
            for v in cluster_definitions(): self.assertEqual(converted['block.techguns.'+v['id']],source[f'tile.techguns.orecluster.{v["metadata"]}.name'])
            for key in ('mininglevel','powermult','amountmult'): self.assertEqual(converted['techguns.orecluster.'+key],source['techguns.orecluster.'+key])

    def test_scan_dimensions_every_cell_and_magma_floor(self):
        d=cluster_location_definition(); raw=(LEGACY/'resources/assets/techguns/structures/nether_orecluster_small').read_text().splitlines()
        self.assertEqual(d['cells'],[list(map(int,line.split(','))) for line in raw[1:]])
        self.assertEqual(len(d['cells']),27); self.assertEqual(d['size'],[3,3,3]); self.assertEqual(d['declared_size'],[3,3,3])
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:8,1:12,2:5,3:1,4:1})
        self.assertEqual(d['palette'][0],{'Name':'minecraft:magma_block'})
        self.assertEqual([c for c in d['cells'] if c[3]==3],[[1,1,1,3]])
        self.assertEqual((d['height_offset'],d['worldgen_floor_offset']),(1,-1))

    def test_weighted_centre_includes_two_separately_rolled_foundation_cells(self):
        d=cluster_location_definition()
        self.assertEqual((d['foundation_cells'],d['foundation_depth'],d['foundation_stop_after_solids']),(9,2,2))
        self.assertEqual([c for c in d['cells'] if c[1]==0 and c[3]!=0],[[1,0,1,2]])
        self.assertEqual([w['palette_index'] for w in d['weighted_cells']],[2,4])
        for w in d['weighted_cells']:
            self.assertEqual((w['weights'],w['roll_bound'],w['cluster_roll_max']),([50,50],101,50))
        self.assertEqual(d['block_entities'][4]['metadata'],'techguns:cluster_or_air')
        self.assertEqual(d['block_entities'][2]['metadata'],'techguns:cluster_or_netherrack')

    def test_template_contains_no_invented_spawners_or_resource_drops(self):
        d=cluster_location_definition(); data=location_nbt(d); self.assertEqual(data,location_nbt(d)); tree=read_nbt(data)
        self.assertEqual(tree['palette'],d['palette']); self.assertEqual([b['pos']+[b['state']] for b in tree['blocks']],d['cells'])
        self.assertEqual(len([b for b in tree['blocks'] if 'nbt' in b]),6)
        self.assertEqual(tree['entities'],[])
        self.assertEqual({b['nbt']['id'] for b in tree['blocks'] if 'nbt' in b},{'minecraft:structure_block'})

    def test_fifth_candidate_uses_same_grid_and_late_native_step(self):
        d=cluster_location_definition(); self.assertTrue(all(c['implemented'] for c in d['generation']['candidates']))
        self.assertEqual(d['generation']['candidates'][4],{'id':'nether_ore_cluster_small','weight':10,'implemented':True})
        files=generate_location_content(); root=RESOURCES+'data/techguns/worldgen/'
        setting=json.loads(files[root+'structure/nether_ore_cluster_small.json'])
        self.assertEqual(setting['step'],'top_layer_modification'); self.assertEqual(setting['spawn_overrides'],{})
        cluster=json.loads(files[root+'structure_set/nether_ore_cluster_small.json']); altar=json.loads(files[root+'structure_set/nether_altar_small.json'])
        self.assertEqual(cluster['placement'],altar['placement'])

if __name__=='__main__': unittest.main()
