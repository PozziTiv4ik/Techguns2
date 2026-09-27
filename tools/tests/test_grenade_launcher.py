import json
import re
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate,parse_weapons
from legacy_grenade_launcher import LEGACY,RESOURCES,generate_launcher_content
from legacy_crafting import plan_crafting
from legacy_models import strip_comments


class GrenadeLauncherTests(unittest.TestCase):
    def test_factory_weapon_and_individual_ammo(self):
        g=next(g for g in parse_weapons() if g['id']=='grenadelauncher')
        self.assertEqual([g[k] for k in ('capacity','fire_delay','reload_ticks','damage','minimum_damage','drop_start','drop_end','speed','lifetime','accuracy','gravity')],[6,5,100,30,12,4,8,.5,160,.015,.01])
        self.assertEqual((g['automatic'],g['projectile'],g['ammo']['item'],g['ammo']['individual'],g['forward_axis']),(False,'grenade_40mm','40mmgrenade',True,'+x'))
        self.assertEqual(g['npc_ai'],{'range':24,'interval':40,'burst':3,'shot_delay':20,'forward_offset':0})
        source=strip_comments((LEGACY/'java/techguns/entities/projectiles/Grenade40mmProjectile.java').read_text())
        self.assertIn('firePos, gravity, radius, 2)',source);self.assertNotIn('void explode',source)

    def test_source_objs_keep_all_vertices_normals_uvs_faces_and_common_origin(self):
        files=generate_launcher_content()
        for old,new,scale in [('grenadelauncher','grenadelauncher',1/16),('grenadelauncher_1','grenadelauncher_drum',1/16),('grenade40mm','grenade40mm',1)]:
            original=(LEGACY/f'resources/assets/techguns/models/item/{old}.obj').read_text().splitlines()
            converted=files[RESOURCES+f'assets/techguns/models/item/{new}.obj'].decode().splitlines()
            self.assertEqual(len(original),len(converted))
            for a,b in zip(original,converted):
                if a.startswith('v '):
                    for x,y in zip(map(float,a.split()[1:]),map(float,b.split()[1:])):self.assertAlmostEqual(y,.5+x*scale,places=9)
                elif a.startswith('mtllib '):self.assertEqual(b,f'mtllib {new}.mtl')
                else:self.assertEqual(a.rstrip(),b)
            faces=[l for l in converted if l.startswith('f ')];self.assertEqual(len(faces),{'grenadelauncher':324,'grenadelauncher_drum':12,'grenade40mm':16}[new])
        # Source OBJ's third UV coordinate is not a 2D atlas coordinate and must not be clamped.
        self.assertIn('vt 0.2305 0.4374 -1.7500',files[RESOURCES+'assets/techguns/models/item/grenadelauncher.obj'].decode())

    def test_model_drum_pivot_and_non_tumbling_projectile(self):
        source=(LEGACY/'java/techguns/client/models/guns/ModelBaseBakedGrenadeLauncher.java').read_text()
        self.assertIn('translate(0, -2f, 0)',source);self.assertIn('rotate(60.0f*fireProgress, 1, 0, 0)',source)
        recoil=(LEGACY/'java/techguns/items/guns/GenericGun.java').read_text();self.assertRegex(recoil,r'int recoiltime\s*=\s*5;')
        files=generate();item=json.loads(files[RESOURCES+'assets/techguns/items/grenadelauncher.json'])
        self.assertEqual(item['model'],{'type':'techguns:grenade_launcher'})
        for name in ('grenadelauncher_body','grenadelauncher_drum','grenade40mm'):
            model=json.loads(files[RESOURCES+f'assets/techguns/models/item/{name}.json']);self.assertFalse(model['flip_v']);self.assertEqual(model['display'],{})
        flying=strip_comments((LEGACY/'java/techguns/client/render/entities/projectiles/RenderGrenade40mmProjectile.java').read_text())
        self.assertIn('float scale = 0.5f',flying);self.assertNotIn('ticksExisted',flying)

    def test_empty_recipe_and_existing_ammo_dependency(self):
        graph=plan_crafting(parse_weapons());recipe=graph['recipes']['grenadelauncher']
        self.assertEqual(recipe['pattern'],['mrs',' p ']);self.assertEqual(recipe['result'],{'id':'techguns:grenadelauncher','count':1,'components':{'techguns:rounds':0}})
        self.assertEqual(recipe['key'],{'p':'#c:plates/steel','m':'techguns:obsidiansteelbarrel','r':'techguns:steelreceiver','s':'techguns:plasticstock'})
        self.assertEqual(recipe['key']['p'],'#c:plates/steel');self.assertNotIn('40mmgrenade',graph['materials']);self.assertNotIn('40mmgrenade',graph['extra_ammo'])
        files=generate();metal=json.loads(files[RESOURCES+'data/techguns/recipe/metal_press/40mmgrenade.json']);self.assertEqual(metal['result']['id'],'techguns:40mmgrenade')

    def test_original_textures_and_audio_remain_byte_exact(self):
        files=generate()
        for name,path in [('grenadelauncher','textures/guns/grenadelauncher.png'),('grenade40mm','textures/entity/launchergrenade.png')]:
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/item/{name}.png'],(LEGACY/'resources/assets/techguns'/path).read_bytes())
        source=json.loads((LEGACY/'resources/assets/techguns/sounds.json').read_text());sounds=json.loads(files[RESOURCES+'assets/techguns/sounds.json'])
        for key in ('guns.grenadelauncherfire','guns.grenadelauncherreload'):
            self.assertEqual(sounds[key]['sounds'],source[key]['sounds'])
            for s in sounds[key]['sounds']:
                path='assets/techguns/sounds/'+(s if isinstance(s,str) else s['name']).split(':')[-1]+'.ogg'
                self.assertEqual(files[RESOURCES+path],(LEGACY/'resources'/path).read_bytes())


if __name__=='__main__':unittest.main()
