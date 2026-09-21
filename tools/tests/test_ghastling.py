import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_ghastling import ghastling_definition, generate_ghastling_content, ghastling_translations
from legacy_locations import soul_location_definition, location_nbt, generate_location_content
from legacy_npcs import LEGACY, RESOURCES
from test_locations import read_nbt


class GhastlingPortTests(unittest.TestCase):
    def test_registered_ground_mob_goal_not_unused_alien_attack(self):
        d=ghastling_definition(); source=(LEGACY/'java/techguns/entities/npcs/Ghastling.java').read_text()
        self.assertIn('extends EntityMob',source); self.assertEqual(d['registered_goal'],'AIFireballAttack')
        self.assertEqual((d['health'],d['armor_attribute'],d['typed_armor'],d['xp'],d['follow_range']),(20,10,0,10,64))
        self.assertFalse(d['undead']); self.assertFalse(d['natural_spawn_entry']); self.assertTrue(d['fire_immune'])
        self.assertEqual(d['size'],[1,2.1]); self.assertEqual(d['eye_height'],1.5)

    def test_registered_burst_and_projectile_are_the_six_damage_profile(self):
        d=ghastling_definition(); self.assertEqual((d['warmup'],d['burst_shots'],d['burst_interval'],d['rest'],d['melee_interval']),(30,3,6,50,20))
        self.assertEqual(d['projectile'],{'damage':6,'speed':1.5,'spread':.05,'lifetime':200,'ignite_seconds':3,'block_damage':False,'kind':'FIRE'})
        source=(LEGACY/'java/techguns/entities/npcs/Ghastling.java').read_text().split('protected static class AIFireballAttack',1)[1]
        self.assertNotIn('canEntityBeSeen',source); self.assertFalse(d['line_of_sight_in_attack_goal'])

    def test_model_has_original_ten_parts_and_translation_without_vanilla_scaling(self):
        d=ghastling_definition()['model']; parts=d['parts']; self.assertEqual(len(parts),10); self.assertEqual(d['texture_size'],[64,32]); self.assertEqual(d['translation'],[0,-.6,0])
        self.assertEqual(parts[0]['box'],[-8,-8,-8,16,16,16]); self.assertEqual(parts[0]['pivot'],[0,8,0])
        self.assertEqual([p['pivot'] for p in parts[1:]],[[-3.75,15,-5],[1.25,15,-5],[6.25,15,-5],[-6.25,15,0],[-1.25,15,0],[3.75,15,0],[-3.75,15,5],[1.25,15,5],[6.25,15,5]])
        files=generate_ghastling_content(); mesh=files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/GhastlingMesh.java'].decode()
        self.assertEqual(mesh.count('root.addOrReplaceChild'),10); self.assertIn('PartPose.offset(0.0f,-1.6f,0.0f)',mesh); self.assertEqual(mesh.count(',5.4f,'),9)
        self.assertNotIn('scaling(4.5',mesh); self.assertFalse(any('textures/' in path for path in files))

    def test_original_two_unconditional_loot_pools_and_looting(self):
        files=generate_ghastling_content(); table=json.loads(files[RESOURCES+'data/techguns/loot_table/entities/ghastling.json'])
        self.assertEqual(len(table['pools']),2)
        for pool,item,maximum in zip(table['pools'],('ghast_tear','gunpowder'),(1,2),strict=True):
            entry=pool['entries'][0]; self.assertEqual(pool['rolls'],1); self.assertEqual(entry['name'],'minecraft:'+item); self.assertEqual(entry['conditions'],[])
            self.assertEqual(entry['functions'][0],{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':0,'max':maximum}})
            self.assertEqual(entry['functions'][1],{'function':'minecraft:enchanted_count_increase','count':{'type':'minecraft:uniform','min':0,'max':1},'enchantment':'minecraft:looting'})

    def test_source_names_egg_and_magic_fire_damage_tags(self):
        files=generate_ghastling_content(); self.assertEqual(ghastling_translations('ru_ru')['entity.techguns.ghastling'],'Гастёныш'); self.assertEqual(ghastling_translations('en_us')['entity.techguns.ghastling'],'Ghastling')
        self.assertEqual(ghastling_definition()['original_egg_colors'],[0xaeaeae,0xce81ff])
        self.assertFalse(any('entity_type/undead' in p for p in files))
        for ns,name in [('minecraft','bypasses_cooldown'),('minecraft','witch_resistant_to'),('neoforge','is_magic')]: self.assertEqual(json.loads(files[RESOURCES+f'data/{ns}/tags/damage_type/{name}.json'])['values'],['techguns:alien_blast'])

    def test_soul_scan_dimensions_differ_from_registered_rotation_centre(self):
        d=soul_location_definition(); self.assertEqual(len(d['cells']),898); self.assertEqual(d['size'],[13,10,13]); self.assertEqual(d['declared_size'],[13,10,13]); self.assertEqual(d['registered_xz_size'],[11,11]); self.assertEqual(d['pivot'],[5,0,5])
        source=(LEGACY/'resources/assets/techguns/structures/nether_soul_platform').read_text().splitlines()
        self.assertEqual(d['cells'],[list(map(int,s.split(','))) for s in source[1:]])
        self.assertEqual(d['foundation_cells'],9); self.assertEqual({c[3] for c in d['cells'] if c[1]==0},{6}); self.assertEqual(d['palette'][6],{'Name':'techguns:nethermetal_plate_black'})

    def test_soul_nbt_twenty_four_skulls_and_ghastling_encounter(self):
        d=soul_location_definition(); data=location_nbt(d); self.assertEqual(data,location_nbt(d)); tree=read_nbt(data)
        self.assertEqual([b['pos']+[b['state']] for b in tree['blocks']],d['cells']); self.assertEqual(sum(c[3]==3 for c in d['cells']),24)
        spawners=[b for b in tree['blocks'] if 'nbt' in b]; self.assertEqual(len(spawners),1); self.assertEqual(spawners[0]['pos'],[6,6,6])
        self.assertEqual(spawners[0]['nbt'],{'id':'techguns:tg_spawner','mobsLeft':3,'maxActive':2,'spawnDelay':200,'delay':200,'spawnRange':1.0,'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:ghastling','weight':1}]})

    def test_soul_uses_original_second_ticket_and_late_native_placement(self):
        d=soul_location_definition(); self.assertEqual([c['implemented'] for c in d['generation']['candidates']],[True,True,True,True,True]); self.assertEqual(d['generation']['candidates'][1]['id'],'nether_soul_platform')
        files=generate_location_content(); s=json.loads(files[RESOURCES+'data/techguns/worldgen/structure/nether_soul_platform.json']); self.assertEqual(s['step'],'top_layer_modification'); self.assertEqual(s['spawn_overrides'],{})
        grid=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/nether_soul_platform.json'])['placement']; self.assertEqual((grid['spacing'],grid['separation']),(16,15))

if __name__=='__main__': unittest.main()
