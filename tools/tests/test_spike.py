import json
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_alienbug import alienbug_definition, generate_alienbug_content, alienbug_translations, SOUNDS
from legacy_spike import spike_definition, generate_spike_content, spike_translations
from legacy_locations import location_nbt
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt

class OreSpikePortTests(unittest.TestCase):
    def test_original_bug_attributes_and_hierarchy_including_flat_plane(self):
        d=alienbug_definition(); self.assertEqual(d['attributes'],{'ATTACK_DAMAGE':4,'MAX_HEALTH':20,'MOVEMENT_SPEED':1})
        self.assertEqual(len(d['parts']),17); self.assertEqual(len(d['parents']),8); self.assertEqual(d['texture_size'],[64,64])
        parts={p['name']:p for p in d['parts']}; self.assertEqual(parts['h3']['box'],[-4,1,-7,14,0,14]); self.assertEqual(d['parents']['h3'],'h1')
        self.assertAlmostEqual(parts['L1_B_2']['rotation'][2],1.7453292519943295); self.assertAlmostEqual(parts['b2']['rotation'][0],.5918411493512771)
        mesh=generate_alienbug_content()['platforms/neoforge-26.2/src/main/java/techguns/modern/client/AlienBugMesh.java'].decode()
        self.assertEqual(mesh.count('.addOrReplaceChild'),17); self.assertIn('part_h1.addOrReplaceChild("h3"',mesh)

    def test_original_bug_texture_sounds_empty_loot_and_arthropod_tag(self):
        f=generate_alienbug_content(); path='assets/techguns/textures/entity/alienbug.png'
        self.assertEqual(f[RESOURCES+path],(LEGACY/'resources'/path).read_bytes())
        sounds=json.loads((LEGACY/'resources/assets/techguns/sounds.json').read_text(encoding='utf-8'))
        self.assertEqual([len(sounds[s]['sounds']) for s in SOUNDS],[6,4,2,3,4,4])
        self.assertEqual(json.loads(f[RESOURCES+'data/techguns/loot_table/entities/alienbug.json'])['pools'],[])
        self.assertEqual(json.loads(f[RESOURCES+'data/minecraft/tags/entity_type/arthropod.json'])['values'],['techguns:alienbug'])
        self.assertFalse(alienbug_definition()['natural_spawn_entry']); self.assertEqual(alienbug_translations('ru_ru')['entity.techguns.alienbug'],'Чужой')

    def test_all_original_scan_cells_palette_and_clearing_footprint(self):
        d=spike_definition(); raw=(LEGACY/'resources/assets/techguns/structures/orecluster_spike').read_text().splitlines()
        self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in raw[1:]]); self.assertEqual(len(d['cells']),88)
        self.assertEqual(d['size'],[8,7,8]); self.assertEqual(d['pivot'],[4,0,4]); self.assertEqual(d['clear_bottom_columns'],13); self.assertEqual(d['clear_above'],[1,6]); self.assertEqual(d['foundation_depth'],0)
        self.assertEqual(Counter(c[3] for c in d['cells']),{0:35,1:2,2:7,3:2,4:3,5:7,6:12,7:5,8:10,9:3,10:2})

    def test_native_template_keeps_both_finite_bug_encounters(self):
        d=spike_definition(); encoded=location_nbt(d); self.assertEqual(encoded,location_nbt(d)); tree=read_nbt(encoded)
        self.assertEqual([b['pos']+[b['state']] for b in tree['blocks']],d['cells'])
        holes=[b for b in tree['blocks'] if b['state']==1]; self.assertEqual([b['pos'] for b in holes],[[1,0,1],[6,0,6]])
        for b in holes: self.assertEqual(b['nbt'],{'id':'techguns:tg_spawner','mobsLeft':4,'maxActive':2,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:alienbug','weight':1}]})

    def test_source_cluster_weights_ladder_metadata_and_fixed_core(self):
        d=spike_definition(); self.assertEqual(d['cluster_weights'],[5,10,10,10,5,2,3]); self.assertEqual(d['cluster_roll_bound'],46)
        self.assertEqual(d['cluster_types'],['coal','common_metal','common_gem','rare_metal','shiny_metal','shiny_gem','uranium'])
        self.assertEqual([d['palette'][i]['Properties']['facing'] for i in (3,7,9)],['west','north','south'])
        self.assertEqual([c[:3] for c in d['cells'] if c[3]==10],[[3,2,4],[3,3,4]])

    def test_medium_native_grid_and_unported_ticket_reservation(self):
        f=generate_spike_content(); d=spike_definition()['generation']; self.assertEqual((d['ordinary_land_total'],d['sandy_wasteland_total'],d['sandy_wasteland_with_block_oil_total']),(35,55,70))
        self.assertTrue(d['unported_candidates_retain_weight']); self.assertTrue(d['ocean_excluded'])
        structure=json.loads(f[RESOURCES+'data/techguns/worldgen/structure/orecluster_spike.json']); self.assertEqual(structure['reserved_big_grid'],64); self.assertEqual(structure['step'],'top_layer_modification')
        placement=json.loads(f[RESOURCES+'data/techguns/worldgen/structure_set/orecluster_spike.json'])['placement']; self.assertEqual((placement['spacing'],placement['separation']),(32,31))

    def test_slimy_assets_keep_all_trail_boxes_and_animation_bytes(self):
        f=generate_spike_content(); assets=LEGACY/'resources/assets/techguns'; model=json.loads(f[RESOURCES+'assets/techguns/models/block/slimyladder.json']); original=json.loads((assets/'models/block/slimyladder.json').read_text())
        self.assertEqual(len(model['elements']),22)
        for modern,old in zip(model['elements'],original['elements'],strict=True): self.assertEqual(modern,{k:v for k,v in old.items() if k!='type'})
        for atlas in ('block','item'):
            for name in ('bugnest_eggs','bugnestslimy'): self.assertEqual(f[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png'],(assets/f'textures/blocks/{name}.png').read_bytes())
            self.assertEqual(f[RESOURCES+f'assets/techguns/textures/{atlas}/bugnest_eggs.png.mcmeta'],(assets/'textures/blocks/bugnest_eggs.png.mcmeta').read_bytes())
        item=json.loads(f[RESOURCES+'assets/techguns/models/item/slimyladder.json']); self.assertTrue(all(e['rotation']=={'origin':[8,8,8],'x':-90,'y':-180,'z':0} for e in item['elements']))

    def test_slimy_names_self_drops_and_real_climbing_tag(self):
        f=generate_spike_content(); self.assertEqual(spike_translations('en_us')['block.techguns.slimyladder'],'Slimy Trail')
        self.assertEqual(json.loads(f[RESOURCES+'data/minecraft/tags/block/climbable.json'])['values'],['techguns:slimyladder'])
        for name in ('bugnest_eggs','slimyladder'):
            loot=json.loads(f[RESOURCES+f'data/techguns/loot_table/blocks/{name}.json']); self.assertEqual(loot['pools'][0]['entries'],[{'type':'minecraft:item','name':'techguns:'+name}])

if __name__=='__main__': unittest.main()
