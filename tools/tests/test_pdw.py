import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate, parse_weapons, resolve_asset, SELECTION
from legacy_advanced import advanced_parameters, RESOURCES
from legacy_crafting import plan_crafting, convert_recipe
from legacy_grinder import grinder_data
from legacy_items import LEGACY, shared_items
from legacy_models import extract_shapes, convert_mesh
from legacy_machines import metal_press_data


class PdwTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.weapons = parse_weapons(); cls.gun = next(g for g in cls.weapons if g['id'] == 'pdw')
        cls.files = generate(); cls.graph = plan_crafting(cls.weapons)

    def test_active_parameters_and_no_commented_burst(self):
        self.assertEqual([self.gun[k] for k in ('capacity','fire_delay','reload_ticks','damage','minimum_damage','drop_start','drop_end','speed','lifetime','penetration')],
                         [40,1,40,5,3,18,25,2,20,1])
        self.assertEqual(self.gun['projectile'], 'advanced_bullet'); self.assertTrue(self.gun['automatic'])
        self.assertEqual(self.gun['extra_pellets'], 0); self.assertEqual(self.gun['accuracy'], .03)
        self.assertEqual(self.gun['npc_ai'], {'range':18,'interval':30,'burst':4,'shot_delay':2,'forward_offset':0})
        self.assertEqual(json.loads(self.files['content/pdw-weapon.json']), [self.gun])
        with patch.dict(SELECTION, {'pulserifle':'ModelPulseRifle'}, clear=True):
            with self.assertRaisesRegex(ValueError, 'burst behavior not ported yet'): parse_weapons()

    def test_advanced_ammo_uses_three_bundles_and_original_metal_press(self):
        self.assertEqual(self.gun['ammo'], {'item':'advancedmagazine','empty_item':'advancedmagazineempty','loose_item':'advancedrounds','bundles_per_magazine':3,'individual':False})
        recipe = self.graph['recipes']['advancedmagazine']
        self.assertEqual(recipe['ingredients'], ['techguns:advancedmagazineempty'] + ['techguns:advancedrounds']*3)
        press = next(r for r in metal_press_data() if r['result']['id'] == 'techguns:advancedrounds')
        self.assertEqual((press['first'],press['second'],press['result']['count'],press['duration'],press['power_per_tick'],press['allow_swap']),
                         ('#c:plates/obsidian_steel','techguns:tgx',16,100,20,True))

    def test_both_source_crafts_and_grinder_costs(self):
        for name, rounds in [('pdw',40), ('pdw_alt',0)]:
            source = json.loads((LEGACY/f'resources/assets/techguns/recipes/{name}.json').read_text())
            actual = self.graph['recipes'][name]
            self.assertEqual(actual, convert_recipe(source, shared_items(), {g['id']:g for g in self.weapons}))
            self.assertEqual(actual['result']['components'], {'techguns:rounds':rounds})
        recipe = next(r for r in grinder_data()['recipes'] if r['id'] == 'pdw')
        self.assertFalse(recipe['random']); self.assertEqual([r['result'] for r in recipe['outputs']],
            [{'id':'techguns:carbonfibers','count':6},{'id':'techguns:plasticsheet','count':1},{'id':'techguns:ingotobsidiansteel','count':1}])

    def test_generic_flight_and_projectile_damage_are_distinct_from_energy(self):
        p = advanced_parameters(); self.assertEqual((p['air_drag'],p['water_drag'],p['gravity']),(.99,.85,0))
        self.assertIn('displacement',p['falloff']); self.assertIn('before movement',p['falloff'])
        self.assertIn('0.01 PHYSICAL',p['damage']); self.assertIn('no explosion',p['block_impact'])
        for tag in ('is_projectile','bypasses_cooldown','no_knockback'):
            self.assertIn('techguns:advanced_bullet', json.loads(self.files[RESOURCES+f'data/minecraft/tags/damage_type/{tag}.json'])['values'])
        self.assertNotIn('techguns:advanced_bullet', json.loads(self.files[RESOURCES+'data/neoforge/tags/damage_type/is_magic.json'])['values'])

    def test_all_forty_one_parts_keep_uv_repeat_seams(self):
        source = (LEGACY/'java/techguns/client/models/guns/ModelPDW.java').read_text()
        w,h,parts = extract_shapes(source,'ModelPDW'); self.assertEqual((w,h,len(parts)),(64,64,41))
        model,obj,mtl = convert_mesh(source,'ModelPDW','pdw','techguns:item/pdw','+x',repeat_texture=True)
        self.assertEqual(self.files[RESOURCES+'assets/techguns/models/item/pdw.obj'],obj.encode())
        self.assertEqual(obj.count('\no '),41)
        _, unsplit, _ = convert_mesh(source,'ModelPDW','pdw','techguns:item/pdw','+x')
        self.assertGreater(obj.count('\nf '),unsplit.count('\nf '), 'Seam-spanning surfaces are split, not clipped')
        for line in obj.splitlines():
            if line.startswith('vt '): self.assertTrue(all(0 <= float(n) <= 1 for n in line.split()[1:]))
        self.assertEqual(json.loads(self.files[RESOURCES+'assets/techguns/models/item/pdw.json']),model)
        self.assertEqual(self.files[RESOURCES+'assets/techguns/models/item/pdw.mtl'],mtl.encode())

    def test_three_camos_share_geometry_use_native_component_and_keep_source_names(self):
        definition = json.loads(self.files[RESOURCES+'assets/techguns/items/pdw.json'])['model']
        self.assertEqual((definition['type'],definition['property'],definition['component']),('minecraft:select','minecraft:component','techguns:gun_camo'))
        self.assertEqual([c['when'] for c in definition['cases']],[1,2]); self.assertEqual(definition['fallback']['model'],'techguns:item/pdw')
        base = json.loads(self.files[RESOURCES+'assets/techguns/models/item/pdw.json'])
        for i,camo in enumerate(self.gun['camos']):
            name = 'pdw'+(f'_{i}' if i else '')
            self.assertEqual(self.files[RESOURCES+f'assets/techguns/textures/item/{name}.png'],resolve_asset(camo['texture']+'.png').read_bytes())
            model = json.loads(self.files[RESOURCES+f'assets/techguns/models/item/{name}.json'])
            self.assertEqual(model['model'],base['model']); self.assertEqual(model['textures']['gun'],'techguns:item/'+name)
            for lang in ('en_us','ru_ru'):
                original = dict(line.split('=',1) for line in resolve_asset('lang/'+lang+'.lang').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
                names = json.loads(self.files[RESOURCES+f'assets/techguns/lang/{lang}.json'])
                self.assertEqual(names[camo['name_key']],original[camo['name_key']])
        self.assertEqual(json.loads(self.files['content/camo-bench.json'])['weapons'],['pdw'])

    def test_original_gun_and_material_impact_audio(self):
        sounds = json.loads(self.files[RESOURCES+'assets/techguns/sounds.json']); source = json.loads(resolve_asset('sounds.json').read_text())
        for event in [self.gun['fire_sound'],self.gun['reload_sound'],*advanced_parameters()['impact_sounds'].values()]:
            self.assertEqual(sounds[event]['sounds'],source[event]['sounds'])
            for entry in sounds[event]['sounds']:
                name = entry if isinstance(entry,str) else entry['name']; path='sounds/'+name.removeprefix('techguns:')+'.ogg'
                self.assertEqual(self.files[RESOURCES+'assets/techguns/'+path],resolve_asset(path).read_bytes())

    def test_original_blue_projectile_dimensions_texture_and_delay(self):
        p = advanced_parameters()['render']
        self.assertEqual(p,{'texture':'textures/entity/bullet_blue.png','half_length':.2,'half_width':.025,'delay_factor':2.5})
        self.assertEqual(self.files[RESOURCES+'assets/techguns/'+p['texture']],resolve_asset(p['texture']).read_bytes())


if __name__ == '__main__': unittest.main()
