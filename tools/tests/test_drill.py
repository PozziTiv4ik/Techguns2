import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_drill import drill_heads,cluster_outputs,generate_drill_content,drill_cube,PARTS,SOUNDS
from legacy_crafting import plan_crafting
from generate_weapon_content import generate,parse_weapons
from legacy_npcs import LEGACY,RESOURCES


class OreDrillPortTests(unittest.TestCase):
    def test_drill_slice_retains_source_uv_orientation_on_each_face(self):
        obj,mtl=drill_cube(); vertices=[list(map(float,l.split()[1:])) for l in obj.splitlines() if l.startswith('v ')]; uv=[list(map(float,l.split()[1:])) for l in obj.splitlines() if l.startswith('vt ')]
        self.assertEqual(len(vertices),24); self.assertEqual(len(uv),24); self.assertEqual(len([l for l in obj.splitlines() if l.startswith('f ')]),6)
        self.assertEqual(vertices[:4],[[0,1,0],[0,1,1],[1,1,1],[1,1,0]])
        self.assertEqual(uv[:4],[[0,1],[0,0],[1,0],[1,1]])
        self.assertEqual(vertices[8:12],[[0,0,0],[0,1,0],[1,1,0],[1,0,0]])
        self.assertEqual(uv[8:12],[[0,1],[0,0],[1,0],[1,1]])
        self.assertEqual(uv[12:16],[[1,0],[1,1],[0,1],[0,0]])
        self.assertIn('map_Kd #slice',mtl)
    def test_nine_original_single_stack_heads_and_tiers(self):
        h=drill_heads(); self.assertEqual(len(h),9)
        self.assertEqual([v['size'] for v in h],[0,0,0,1,1,1,2,2,2]); self.assertEqual([v['level'] for v in h],[1,2,3]*3)
        self.assertEqual(h[4]['id'],'oredrillmedium_obsidiansteel')

    def test_all_source_weighted_entries_and_no_raw_ore_substitution(self):
        out=cluster_outputs()
        self.assertEqual(out['nether_crystal'],[{'kind':'item','id':'minecraft:nether_quartz_ore','weight':50},{'kind':'item','id':'minecraft:glowstone','weight':40},{'kind':'item','id':'minecraft:blaze_rod','weight':10}])
        self.assertEqual(out['coal'],[{'kind':'item','id':'minecraft:coal_ore','weight':99},{'kind':'item','id':'minecraft:diamond','weight':1}])
        self.assertEqual([v['weight'] for v in out['common_metal']],[45,30,25]); self.assertEqual(out['uranium'][0]['id'],'techguns:ore_uranium')
        self.assertEqual(out['oil'],[{'kind':'oil','id':'','weight':10}])

    def test_optional_ore_dictionary_entries_remain_separate_weighted_candidates(self):
        entries=[v for values in cluster_outputs().values() for v in values if v['kind']=='tag']
        self.assertEqual({v['legacy_ore_dictionary']:v['weight'] for v in entries},{'oreSilver':40,'oreOsmium':50,'oreAluminium':50,'oreCertusQuartz':40,'oreChargedCertusQuartz':5})

    def test_source_block_pixels_and_formed_empty_rod_models(self):
        files=generate_drill_content(); assets=LEGACY/'resources/assets/techguns'
        for p in (assets/'models/block').glob('oredrill*.json'):
            original=json.loads(p.read_text()); converted=json.loads(files[RESOURCES+'assets/techguns/models/block/'+p.name])
            self.assertEqual(converted['parent'],'minecraft:'+original['parent'])
            self.assertEqual(converted['textures'],{k:v.replace(':blocks/',':block/') for k,v in original['textures'].items()})
            for v in original['textures'].values():
                texture=v.split(':blocks/')[1]; self.assertEqual(files[RESOURCES+f'assets/techguns/textures/block/{texture}.png'],(assets/f'textures/blocks/{texture}.png').read_bytes())
        self.assertEqual(files[RESOURCES+'assets/techguns/textures/gui/ore_drill_gui.png'],(assets/'textures/gui/ore_drill_gui.png').read_bytes())

    def test_fourteen_workbench_recipes_keep_original_patterns_and_counts(self):
        graph=plan_crafting(parse_weapons()); recipes=graph['recipes']; catalog=graph['catalog']['block_metadata']
        for i,part in enumerate(PARTS):
            self.assertEqual(catalog[f'techguns:oredrill@{i}'],'techguns:oredrill_'+part)
            original=json.loads((LEGACY/f'resources/assets/techguns/recipes/oredrill_{i}_{part}.json').read_text())
            self.assertEqual(recipes['oredrill_'+part]['pattern'],original['pattern']); self.assertEqual(recipes['oredrill_'+part]['result']['count'],original['result'].get('count',1))
        for h in drill_heads(): self.assertIn(h['id'],recipes)
        self.assertEqual(recipes['oredrillsmall_steel']['pattern'],['sps','sps',' s '])

    def test_all_twelve_work_sounds_and_shared_pickaxe_tags_are_kept(self):
        files=generate(); sounds=json.loads(files[RESOURCES+'assets/techguns/sounds.json']); src=json.loads((LEGACY/'resources/assets/techguns/sounds.json').read_text())
        for name in SOUNDS:
            self.assertEqual(sounds[name]['sounds'],src[name]['sounds']); self.assertEqual(len(sounds[name]['sounds']),4)
            for sound in sounds[name]['sounds']:
                path='sounds/'+sound.removeprefix('techguns:')+'.ogg'; self.assertEqual(files[RESOURCES+'assets/techguns/'+path],(LEGACY/'resources/assets/techguns'/path).read_bytes())
        values=json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])['values']
        for part in PARTS: self.assertIn('techguns:oredrill_'+part,values)
        self.assertIn('techguns:nethermetal_panel',values)

if __name__=='__main__': unittest.main()
