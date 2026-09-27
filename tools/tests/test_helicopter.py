import hashlib
import json
import re
import sys
import unittest
from decimal import Decimal
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_helicopter import helicopter_definition,generate_helicopter_content,SOUNDS
from legacy_npcs import LEGACY,RESOURCES,npc_loot
from legacy_models import strip_comments


class HelicopterTests(unittest.TestCase):
    def test_source_attributes_and_active_attack(self):
        d=helicopter_definition();self.assertEqual((d['health'],d['follow_range'],d['armor'],d['armor_toughness']),(100,128,16,5))
        self.assertEqual((d['size'],d['eye_height'],d['xp']),([4,4],.5,5));self.assertFalse(d['natural_spawn_entry'])
        a=d['attack'];self.assertEqual((a['bullets'],a['rocket_tick'],a['rest_timer']),([14,16,18,20,22],35,-30))
        self.assertEqual((a['damage'],a['minimum_damage'],a['speed'],a['lifetime'],a['spread'],a['penetration']),(12,8,1,100,.05,.25))
        self.assertEqual(a['rocket_blast_radii'],[30,40]);self.assertFalse(a['block_damage']);self.assertFalse(a['npc_difficulty_penalty'])

    def test_radius_and_penetration_resistance_are_inactive_source_parameters(self):
        source=strip_comments((LEGACY/'java/techguns/entities/projectiles/RocketProjectile.java').read_text())
        explosion=re.search(r'new TGExplosion\((.*?)\);',source)[1]
        self.assertNotIn('radius',explosion);self.assertIn('this.damageDropStart,this.damageDropEnd',explosion)
        damage=strip_comments((LEGACY/'java/techguns/damagesystem/DamageSystem.java').read_text())
        self.assertNotIn('getPenetrationResistance',damage);self.assertIn('dmgsrc.armorPenetration*4',damage)

    def test_original_loot_ranges_and_looting_are_all_available(self):
        table=npc_loot('attackhelicopter');entries=[p['entries'][0] for p in table['pools']]
        self.assertEqual([e['name'] for e in entries],['techguns:cyberneticparts','techguns:riflerounds','techguns:rocket','minecraft:iron_block'])
        self.assertEqual([(e['functions'][0]['count']['min'],e['functions'][0]['count']['max']) for e in entries],[(2,4),(8,16),(4,8),(2,4)])
        self.assertEqual([(e['functions'][1]['count']['min'],e['functions'][1]['count']['max']) for e in entries],[(1,2),(4,8),(2,4),(1,2)])
        self.assertTrue(all(not e['conditions'] for e in entries))

    def test_all_three_obj_meshes_retain_faces_uv_normals_and_shared_origin(self):
        files=generate_helicopter_content();d=helicopter_definition()
        for i in range(3):
            old=(LEGACY/f'resources/assets/techguns/models/item/npc/helicopter{i}.obj').read_text().splitlines();new=files[RESOURCES+f'assets/techguns/models/item/helicopter{i}.obj'].decode().splitlines()
            self.assertEqual(len(old),len(new));vertices=0
            for a,b in zip(old,new):
                if a.startswith('v '):self.assertEqual([Decimal(x)+Decimal('.5') for x in a.split()[1:]],[Decimal(x) for x in b.split()[1:]]);vertices+=1
                else:self.assertEqual(a.rstrip(),b)
                self.assertEqual(b,b.rstrip())
            self.assertEqual(vertices,d['model']['parts'][i]['vertices'])
            model=json.loads(files[RESOURCES+f'assets/techguns/models/item/helicopter{i}.json']);self.assertTrue(model['flip_v']);self.assertEqual(model['display'],{});self.assertFalse(model['automatic_culling'])
        texture=(LEGACY/'resources/assets/techguns/textures/entity/apache.png').read_bytes()
        for kind in ('entity','item'):self.assertEqual(files[RESOURCES+f'assets/techguns/textures/{kind}/apache.png'],texture)

    def test_original_renderer_death_transform_is_visual_only(self):
        d=helicopter_definition();r=strip_comments((LEGACY/'java/techguns/client/render/entities/npcs/RenderAttackHelicopter.java').read_text())
        self.assertIn('GL11.glRotatef(angle, 0, 1.0f, 0)',r);self.assertIn('GlStateManager.rotate(angle, 0, 1f, 0)',r)
        self.assertEqual(d['death']['visual_yaw_degrees'],1440);self.assertEqual(d['death']['ticks'],100);self.assertFalse(d['death']['damaging_explosion'])
        self.assertEqual(d['model']['scale'],2.5);self.assertEqual(d['model']['gun_pivot_x'],1.2)

    def test_dedicated_profiles_and_native_resources_resolve(self):
        files=generate_helicopter_content();java=files['core/src/main/java/techguns/core/HelicopterWeapons.java'].decode()
        self.assertIn('12.0f,8.0f,30,40,1.0,100,0.05',java);self.assertIn('ProjectileKind.BALLISTIC',java);self.assertIn('ProjectileKind.ROCKET',java)
        self.assertNotIn('Weapons.ALL',java)
        for i in range(3):self.assertIn(RESOURCES+f'assets/techguns/items/helicopter{i}.json',files)
        sounds=json.loads((LEGACY/'resources/assets/techguns/sounds.json').read_text())
        for name in SOUNDS:
            for s in sounds[name]['sounds']:
                sound=s if isinstance(s,str) else s['name'];self.assertTrue((LEGACY/f'resources/assets/techguns/sounds/{sound.split(":")[-1]}.ogg').exists())
