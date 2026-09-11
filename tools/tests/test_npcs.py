from pathlib import Path
import json, re, sys, unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_npcs import mutant_geometry, mutant_loot, generate_npc_content, SOUNDS, LEGACY, RESOURCES
from generate_weapon_content import parse_weapons, generate
from legacy_models import strip_comments


class NpcPortTests(unittest.TestCase):
    def test_hierarchy_and_mirrored_parts_are_not_flattened(self):
        width,height,parts,parents=mutant_geometry()
        self.assertEqual((width,height,len(parts)),(64,64,14))
        self.assertEqual(len(parents),8)
        self.assertEqual(parents['rightBoot'],'bipedRightLeg')
        self.assertEqual(parents['leftarm_upper'],'bipedLeftArm')
        self.assertEqual(parents['bipedHeadwear'],'bipedHead')
        self.assertEqual({p['name'] for p in parts if p['mirror']},{'bipedLeftArm','leftBoot','leftarm_upper','bipedLeftLeg'})
        self.assertEqual(next(p['inflate'] for p in parts if p['name']=='bipedHeadwear'),.5)

    def test_generated_mesh_preserves_every_source_box_and_parent(self):
        files=generate_npc_content()
        mesh=files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/SuperMutantMesh.java'].decode()
        self.assertEqual(mesh.count('.addOrReplaceChild('),14)
        self.assertIn('part_bipedRightLeg.addOrReplaceChild("rightBoot"',mesh)
        self.assertIn('part_bipedHead.addOrReplaceChild("hat"',mesh)
        self.assertEqual(mesh.count('.mirror()'),4)
        texture='assets/techguns/textures/entity/supermutant_texture_1.png'
        self.assertEqual(files[RESOURCES+texture],(LEGACY/'resources'/texture).read_bytes())
        self.assertFalse(any('texture_2' in p or 'texture_3' in p for p in files))

    def test_loot_keeps_cybernetic_parts_probability_and_does_not_add_looting_quantity(self):
        pools=mutant_loot()['pools']; self.assertEqual(len(pools),3)
        cyber=pools[0]['entries'][0]
        self.assertEqual(cyber['name'],'techguns:cyberneticparts')
        self.assertEqual(cyber['functions'],[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':1,'max':2}}])
        self.assertEqual(cyber['conditions'][0]['unenchanted_chance'],.75)
        self.assertEqual(cyber['conditions'][0]['enchanted_chance'],{'type':'minecraft:linear','base':.8,'per_level_above_first':.05})
        for pool,chance in zip(pools[1:],[.2,.05]):
            entry=pool['entries'][0]
            self.assertEqual(entry['conditions'][0]['unenchanted_chance'],chance)
            self.assertEqual(entry['functions'][1]['function'],'minecraft:enchanted_count_increase')

    def test_selected_weapon_ai_values_are_extracted_from_source(self):
        guns={g['id']:g for g in parse_weapons()}
        for name,stats in {'ak47':(24,30,3,3),'combatshotgun':(12,30,0,0),'lasergun':(24,30,0,0),'rocketlauncher':(24,80,0,0)}.items():
            self.assertEqual(tuple(guns[name]['npc_ai'][k] for k in ('range','interval','burst','shot_delay')),stats)
        self.assertEqual(guns['rocketlauncher']['npc_ai']['forward_offset'],.35)

    def test_no_natural_spawn_entry_is_invented(self):
        source=strip_comments((LEGACY/'java/techguns/TGEntities.java').read_text())
        entries=re.findall(r'new TGNpcSpawn\((\w+)\.class',source)
        self.assertNotIn('SuperMutantBasic',entries)
        files=generate_npc_content()
        self.assertFalse(any('biome_modifier' in path for path in files))
        self.assertFalse(json.loads(files['content/supermutantbasic.json'])['natural_spawn_entry'])

    def test_source_sounds_and_basic_name_are_available(self):
        files=generate(); sounds=json.loads(files[RESOURCES+'assets/techguns/sounds.json'])
        for name in SOUNDS:
            self.assertIn(name,sounds)
            for sound in sounds[name]['sounds']:
                name=(sound if isinstance(sound,str) else sound['name']).split(':')[-1]
                path='assets/techguns/sounds/'+name+'.ogg'
                self.assertEqual(files[RESOURCES+path],(LEGACY/'resources'/path).read_bytes())
        for lang in ('en_us','ru_ru'):
            self.assertIn('entity.techguns.supermutantbasic',json.loads(files[RESOURCES+f'assets/techguns/lang/{lang}.json']))


if __name__=='__main__': unittest.main()
