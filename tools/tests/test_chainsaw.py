import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import parse_weapons, generate, ROOT, RESOURCES, LEGACY
from legacy_chainsaw import generate_chainsaw_content
from legacy_crafting import plan_crafting
from legacy_models import extract_shapes, wrapped_quads
from legacy_grinder import grinder_data


class ChainsawTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons=parse_weapons(); cls.gun=next(g for g in cls.weapons if g['id']=='chainsaw')
        cls.files=generate(); cls.crafting=plan_crafting(cls.weapons)

    def test_constructor_and_factory(self):
        g=self.gun
        self.assertEqual([g[k] for k in ('capacity','fire_delay','reload_ticks','damage','lifetime','speed','accuracy','penetration')],[300,3,45,10,2,2,0,1])
        self.assertEqual(g['projectile'],'chainsaw'); self.assertTrue(g['automatic']); self.assertEqual(g['forward_axis'],'+x')
        self.assertEqual(g['npc_ai'],{'range':3,'interval':10,'burst':0,'shot_delay':0,'forward_offset':0})

    def test_fueled_and_empty_recipes(self):
        recipes=self.crafting['recipes']
        self.assertEqual(recipes['chainsaw']['result']['components']['techguns:rounds'],300)
        self.assertEqual(recipes['chainsaw_alt']['result']['components']['techguns:rounds'],0)
        self.assertEqual(recipes['chainsaw']['key']['f'],'techguns:fueltank')
        self.assertEqual(recipes['chainsaw_alt']['key']['f'],'techguns:fueltankempty')

    def test_source_upgrade_dependencies(self):
        recipes=self.crafting['recipes']
        upgrades=[r for r in recipes.values() if r['type']=='techguns:miningtool_upgrade']
        self.assertEqual(len(upgrades),2)
        self.assertTrue({'chainsawblades_obsidian','chainsawblades_carbon'} <= set(self.crafting['materials']))
        for name in ('chainsawblades_obsidian','chainsawblades_carbon'):
            self.assertEqual(recipes[name]['pattern'],[' pm','m  ',' pm'])
        self.assertEqual(recipes['chainsawblades_carbon']['key']['m'],'techguns:mechanicalpartscarbon')

    def test_single_visible_blade_and_original_colors(self):
        path=(RESOURCES/'assets/techguns/models/item').as_posix()+'/'
        source=(LEGACY/'java/techguns/client/models/guns/ModelChainsaw.java').read_text()
        _,_,shapes=extract_shapes(source,'ModelChainsaw')
        for suffix in ('','_obsidian','_carbon'):
            obj=self.files[path+'chainsaw'+suffix+'.obj'].decode()
            self.assertEqual(obj.count('\no '),len(shapes)-1)
            self.assertIn('\no blade1\n',obj); self.assertNotIn('\no blade2\n',obj)
        self.assertIn('Kd 0.9 0.55 1',self.files[path+'chainsaw_obsidian.mtl'].decode())
        self.assertIn('Kd 0.4 0.4 0.4',self.files[path+'chainsaw_carbon.mtl'].decode())

    def test_assets_are_original(self):
        self.assertEqual(self.files[(RESOURCES/'assets/techguns/textures/item/chainsaw.png').as_posix()],
                         (LEGACY/'resources/assets/techguns/textures/guns/chainsaw.png').read_bytes())
        sounds=json.loads(self.files[(RESOURCES/'assets/techguns/sounds.json').as_posix()])
        for name in ('guns.chainsawloop','guns.chainsawloopstart','guns.chainsawhit','guns.powerhammerimpactground'):
            self.assertIn(name,sounds)

    def test_physical_attack_keeps_hurt_cooldown(self):
        tags=json.loads(self.files[(RESOURCES/'data/minecraft/tags/damage_type/bypasses_cooldown.json').as_posix()])
        self.assertNotIn('techguns:chainsaw',tags['values'])
        self.assertEqual(self.gun['ammo'],{'item':'fueltank','empty_item':'fueltankempty','loose_item':'','bundles_per_magazine':0,'individual':False})

    def test_grinder_source_return(self):
        r=next(r for r in grinder_data()['recipes'] if r['id']=='chainsaw')
        self.assertEqual(r['outputs'],[{'result':{'id':'minecraft:iron_ingot','count':5}}, {'result':{'id':'techguns:plasticsheet','count':1}}])

    def test_repeating_uvs_split_without_changing_surface(self):
        vertices=[[0,0,0],[4,0,0],[4,2,0],[0,2,0]]
        for uvs in [[(-.25,0),(1.25,0),(1.25,1),(-.25,1)],[(1.25,1),(-.25,1),(-.25,0),(1.25,0)]]:
            quads=list(wrapped_quads(vertices,uvs)); self.assertEqual(len(quads),3)
            area=0
            for points,coords in quads:
                self.assertTrue(all(0<=v<=1 for uv in coords for v in uv))
                self.assertTrue(all(0<=p[0]<=4 and 0<=p[1]<=2 and p[2]==0 for p in points))
                area+=(points[1][0]-points[0][0])*(points[3][1]-points[0][1])
            self.assertAlmostEqual(area,8)
