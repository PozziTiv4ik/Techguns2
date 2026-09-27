import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import parse_weapons, generate, resolve_asset
from legacy_flamethrower import flame_parameters, LEGACY, RESOURCES
from legacy_models import strip_comments, extract_shapes
from legacy_crafting import plan_crafting
from legacy_grinder import grinder_data


class FlamethrowerPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.files = generate()

    def test_active_factory_parameters_not_incendiary_or_chainsaw(self):
        g = next(g for g in parse_weapons() if g['id'] == 'flamethrower')
        self.assertEqual([g[k] for k in ('projectile','capacity','fire_delay','reload_ticks','damage','minimum_damage','drop_start','drop_end','speed','lifetime','accuracy','gravity')],
                         ['flame',100,2,45,5,2,4,16,.5,16,.05,.01])
        self.assertEqual(g['npc_ai'], {'range':12, 'interval':20, 'burst':5, 'shot_delay':5, 'forward_offset':.35})
        self.assertTrue(g['automatic']); self.assertEqual(g['forward_axis'], '+x')
        p = flame_parameters(); self.assertEqual([p[k] for k in ('ignition_chance','burn_seconds','loop_delay','recoil_ticks','muzzle_ticks')], [.5,3,10,10,10])
        source = strip_comments((LEGACY/'java/techguns/entities/projectiles/FlamethrowerProjectile.java').read_text())
        self.assertIn('this.gravity=gravity;', source); self.assertIn('this.isWet()', source)
        self.assertIn('src.setNoKnockback()', source); self.assertIn('ent.setFire(this.entityIgniteTime)', source)
        self.assertIn('this.blockdamage', source); self.assertIn('Math.random() <= chanceToIgnite', (LEGACY/'java/techguns/entities/projectiles/GenericProjectile.java').read_text())

    def test_source_audio_is_finite_per_shot_with_player_restart_and_npc_fire(self):
        source = strip_comments((LEGACY/'java/techguns/items/guns/GenericGun.java').read_text())
        self.assertIn('extendedPlayer.getLoopSoundDelay(hand)<=0', source)
        self.assertIn('this.firesoundStart, SOUND_DISTANCE, 1.0F, false, false', source)
        self.assertIn('this.firesound, SOUND_DISTANCE, 1.0F, false, false', source)
        npc = source.split('public void fireWeaponFromNPC',1)[1]
        self.assertIn('shooter ,firesound, SOUND_DISTANCE', npc)
        old = json.loads(resolve_asset('sounds.json').read_text()); new = json.loads(self.files[RESOURCES+'assets/techguns/sounds.json'])
        count = 0
        for event in ('guns.flamethrowerstart','guns.flamethrowerfire','guns.flamethrowerreload'):
            self.assertEqual(old[event]['sounds'], new[event]['sounds'])
            for name in old[event]['sounds']:
                path = 'sounds/'+(name if isinstance(name,str) else name['name']).split(':')[-1]+'.ogg'
                self.assertEqual(self.files[RESOURCES+'assets/techguns/'+path], resolve_asset(path).read_bytes()); count += 1
        self.assertEqual(count, 7)

    def test_all_36_model_parts_and_original_texture(self):
        source = (LEGACY/'java/techguns/client/models/guns/ModelFlamethrower.java').read_text()
        w,h,parts = extract_shapes(source,'ModelFlamethrower')
        # Source assigns mirror after addBox: the already constructed ModelBox stays unmirrored.
        self.assertEqual((w,h,len(parts)),(64,64,36)); self.assertTrue(all(not p['mirror'] for p in parts))
        cuboids = json.loads(self.files[RESOURCES+'assets/techguns/models/item/flamethrower.json'])['elements']
        self.assertEqual([p['name'] for p in cuboids],[p['name'] for p in parts])
        self.assertEqual(sum(len(p['faces']) for p in cuboids),36*6)
        self.assertEqual(self.files[RESOURCES+'assets/techguns/textures/item/flamethrower.png'],resolve_asset('textures/guns/flamethrower.png').read_bytes())
        model = json.loads(self.files[RESOURCES+'assets/techguns/items/flamethrower.json'])
        self.assertEqual(model['model'],{'type':'techguns:flamethrower'})
        renderer = (LEGACY/'java/techguns/client/ClientProxy.java').read_text()
        self.assertIn('setRecoilAnim(GunAnimation.swayRecoil, 0.025f, 2.5f)',renderer)

    def test_both_tank_recipes_and_no_grinder_fuel_refund(self):
        recipes = plan_crafting(parse_weapons())['recipes']
        for suffix,tank,rounds in [('', 'fueltank',100),('_alt','fueltankempty',0)]:
            r = recipes['flamethrower'+suffix]
            self.assertEqual(r['pattern'],['prs','fm ']); self.assertEqual(r['key']['m'],'techguns:'+tank)
            self.assertEqual(r['key']['f'],'minecraft:flint_and_steel')
            self.assertEqual(r['result']['components'],{'techguns:rounds':rounds})
        r = next(r for r in grinder_data()['recipes'] if r['id']=='flamethrower')
        self.assertEqual(r['outputs'],[{'result':{'id':'techguns:plasticsheet','count':2}}, {'result':{'id':'minecraft:iron_ingot','count':2}}])

    def test_damage_flags_preserve_fire_resistance_and_magic_semantics(self):
        for ns,name in [('minecraft','bypasses_cooldown'),('minecraft','no_knockback'),('minecraft','witch_resistant_to'),('neoforge','is_magic')]:
            self.assertIn('techguns:flame',json.loads(self.files[RESOURCES+f'data/{ns}/tags/damage_type/{name}.json'])['values'])
        for path,data in self.files.items():
            if path.endswith('/damage_type/is_fire.json'): self.assertNotIn('techguns:flame',json.loads(data)['values'])


if __name__ == '__main__': unittest.main()
