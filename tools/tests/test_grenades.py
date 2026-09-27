import json
import re
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_grenades import LEGACY, RESOURCES, grenade_definitions, generate_grenade_content, grenade_translations
from legacy_crafting import plan_crafting
from legacy_models import strip_comments, extract_shapes, shape_vertices
from generate_weapon_content import parse_weapons, generate


class GrenadeTests(unittest.TestCase):
    def test_active_factory_balance_and_gravity_only_charge(self):
        definitions=grenade_definitions()
        self.assertEqual([(g['id'],g['damage'],g['inner_radius'],g['minimum_damage'],g['outer_radius'],g['bounces']) for g in definitions],
                         [('stielgranate',10,3,5,5,3),('fraggrenade',12,3.5,6,7,2)])
        for g in definitions:
            self.assertEqual([g[k] for k in ('stack_size','use_duration','charge_ticks','lifetime','speed','spread','penetration')],[16,72000,30,200,.75,.1,0])
            source=strip_comments((LEGACY/f'java/techguns/entities/projectiles/{g["projectile"]}.java').read_text())
            factory=re.search(r'return new '+g['projectile']+r'\(world, p,([^;]+);',source)[1]
            self.assertNotIn('charge',factory)
        source=strip_comments((LEGACY/'java/techguns/entities/projectiles/GrenadeProjectile.java').read_text())
        self.assertIn('this.damageDropEnd, 0)',source)
        self.assertIn('this.bounces = other.bounces-1',source)

    def test_real_recipes_keep_grid_output_tags_and_consume_flint_and_steel(self):
        graph=plan_crafting(parse_weapons())
        for name, pattern in [('stielgranate',[' it',' wi','i  ']),('fraggrenade',[' if','iti',' i '])]:
            recipe=graph['recipes'][name]
            self.assertEqual(recipe['result'],{'id':'techguns:'+name,'count':16}); self.assertEqual(recipe['pattern'],pattern)
        self.assertEqual(graph['recipes']['stielgranate']['key'],{'i':'#c:ingots/iron','w':'#minecraft:planks','t':'minecraft:tnt'})
        self.assertEqual(graph['recipes']['fraggrenade']['key'],{'i':'#c:ingots/steel','f':'minecraft:flint_and_steel','t':'minecraft:tnt'})
        self.assertNotIn('stielgranate',graph['materials']); self.assertNotIn('fraggrenade',graph['extra_ammo'])

    def test_model_parts_original_textures_and_pinless_flight(self):
        files=generate_grenade_content()
        for name,count in [('stielgranate',3),('stielgranate_projectile',3),('fraggrenade',11),('fraggrenade_primed',10),('fraggrenade_projectile',10)]:
            obj=files[RESOURCES+f'assets/techguns/models/item/{name}.obj'].decode()
            self.assertEqual(obj.count('\no '),count); self.assertEqual('\no Ring\n' in obj,name=='fraggrenade')
        for name,texture in [('stielgranate','stielgranate'),('fraggrenade','frag_grenade_texture')]:
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/item/{name}.png'],(LEGACY/f'resources/assets/techguns/textures/guns/{texture}.png').read_bytes())
        # Every shared part uses exactly the same coordinates when the ring disappears.
        def parts(name):
            obj=files[RESOURCES+f'assets/techguns/models/item/{name}.obj'].decode()
            return {p.splitlines()[0]:[line for line in p.splitlines()[1:] if line.startswith(('v ','vt '))] for p in obj.split('\no ')[1:]}
        ordinary,primed=parts('fraggrenade'),parts('fraggrenade_primed')
        for part in primed:self.assertEqual(ordinary[part],primed[part])

    def test_projectile_model_keeps_original_origin_and_dimensions(self):
        files=generate_grenade_content()
        for name,clazz in [('stielgranate','ModelStielgranate'),('fraggrenade','ModelFragGrenade')]:
            source=(LEGACY/f'java/techguns/client/models/guns/{clazz}.java').read_text()
            _,_,shapes=extract_shapes(source,clazz,{'showRing':False} if name=='fraggrenade' else None)
            original=[p for shape in shapes for p in shape_vertices(shape)]
            obj=files[RESOURCES+f'assets/techguns/models/item/{name}_projectile.obj'].decode()
            points=[list(map(float,line.split()[1:])) for line in obj.splitlines() if line.startswith('v ')]
            for i in range(3):
                self.assertAlmostEqual(min(p[i] for p in points),.5+min(p[i] for p in original)/16,places=8)
                self.assertAlmostEqual(max(p[i] for p in points),.5+max(p[i] for p in original)/16,places=8)

    def test_item_pin_condition_is_first_person_only(self):
        files=generate_grenade_content()
        model=json.loads(files[RESOURCES+'assets/techguns/items/fraggrenade.json'])['model']
        self.assertEqual(model['fallback']['model'],'techguns:item/fraggrenade')
        case=model['cases'][0]; self.assertEqual(case['when'],['firstperson_lefthand','firstperson_righthand'])
        self.assertEqual(case['model']['property'],'minecraft:using_item')
        self.assertEqual(case['model']['on_true']['model'],'techguns:item/fraggrenade_primed')

    def test_damage_categories_merge_and_pin_sound_is_byte_exact(self):
        files=generate()
        def data(path):return json.loads(files[RESOURCES+path])
        self.assertIn('techguns:grenade',data('data/minecraft/tags/damage_type/is_explosion.json')['values'])
        self.assertIn('techguns:rocket',data('data/minecraft/tags/damage_type/is_explosion.json')['values'])
        self.assertIn('techguns:bullet',data('data/minecraft/tags/damage_type/is_projectile.json')['values'])
        for tag in ('is_projectile','no_knockback','bypasses_cooldown'):
            values=data(f'data/minecraft/tags/damage_type/{tag}.json')['values']
            self.assertIn('techguns:grenade_impact',values); self.assertNotIn('techguns:grenade',values)
        sounds=data('assets/techguns/sounds.json')['guns.grenade_pin']['sounds']
        original=json.loads((LEGACY/'resources/assets/techguns/sounds.json').read_text())['guns.grenade_pin']['sounds']
        self.assertEqual(sounds,original)
        for sound in sounds:
            name=sound if isinstance(sound,str) else sound['name']
            path='assets/techguns/sounds/'+name.split(':')[-1]+'.ogg'
            self.assertEqual(files[RESOURCES+path],(LEGACY/'resources'/path).read_bytes())

    def test_names_and_runtime_enum_follow_source(self):
        files=generate_grenade_content(); source=files['core/src/main/java/techguns/core/HandGrenade.java'].decode()
        for g in grenade_definitions():self.assertIn(g['id'].upper()+'("'+g['id']+'", '+str(g['damage'])+'f',source)
        for lang in ('en_us','ru_ru'):
            labels=grenade_translations(lang)
            self.assertTrue(all(labels['item.techguns.'+g['id']] for g in grenade_definitions()))


if __name__=='__main__':unittest.main()
