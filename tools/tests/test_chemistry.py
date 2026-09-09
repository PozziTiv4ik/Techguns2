"""Source contracts for chemical recipes, fluid assets and the conditional recipe graph."""
from pathlib import Path
import json
import struct
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_chemistry import chemical_recipes,fluid_group_defaults
from legacy_fluids import generate_fluid_content,FLUIDS,LEGACY,RESOURCES
from legacy_machines import generate_machine_content
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons


class ChemistryPortTests(unittest.TestCase):
    def test_all_live_source_calls_and_activation_branches(self):
        recipes=chemical_recipes()
        self.assertEqual(len(recipes),31)
        expected={'coal_dust_present':1,'coal_dust_absent':1,'oils_present':1,'oils_absent':1,
                  'biofuel_present':2,'fuels_present':3,'lava_fuel_fallback':3,'always':19}
        self.assertEqual({key:sum(r['activation']==key for r in recipes) for key in expected},expected)
        self.assertTrue(all(r['duration']==100 for r in recipes))

    def test_source_volumes_and_unequal_amounts(self):
        recipes={r['id']:r for r in chemical_recipes()}
        self.assertEqual(recipes['04_creeper_acid']['fluid_output'],{'id':'techguns:creeper_acid','amount':1000})
        self.assertNotIn('result',recipes['04_creeper_acid'])
        self.assertEqual(recipes['18_yellowcake']['fluid_input'],{'ingredient':'techguns:creeper_acid','amount':250})
        self.assertEqual(recipes['18_yellowcake']['result']['count'],3)
        self.assertEqual(recipes['29_radpills']['first']['count'],4)
        self.assertEqual(recipes['29_radpills']['second']['count'],1)
        self.assertEqual(recipes['29_radpills']['bottle']['ingredient'],'minecraft:glass_bottle')
        self.assertEqual(recipes['23_light_gray_concrete']['result'],{'id':'minecraft:light_gray_concrete','count':2})
        self.assertEqual(recipes['20_slime_ball']['first']['ingredient'],'minecraft:green_dye')

    def test_fuel_recipe_keeps_flask_and_halves_lava_volumes(self):
        recipes={r['id']:r for r in chemical_recipes()}
        self.assertNotIn('first',recipes['08_fueltank'])
        self.assertEqual(recipes['08_fueltank']['bottle']['ingredient'],'techguns:fueltankempty')
        self.assertEqual(recipes['08_fueltank']['fluid_input']['amount'],250)
        self.assertEqual(recipes['11_fueltank']['fluid_input']['amount'],500)
        self.assertEqual(recipes['09_rocket_high_velocity']['fluid_input']['amount'],125)
        self.assertEqual(recipes['12_rocket_high_velocity']['fluid_input']['amount'],250)

    def test_original_config_lists_keep_aliases_and_historical_typo(self):
        defaults=fluid_group_defaults()
        self.assertEqual(len(defaults['Fuel']),12)
        self.assertIn('fliudnitrofuel',defaults['Fuel'])
        self.assertEqual(defaults['Oil'],['oil','tree_oil','crude_oil','fluidoil','seed_oil'])

    def test_fluid_pixels_and_animation_metadata_remain_exact(self):
        files=generate_fluid_content()
        for identifier,texture,_ in FLUIDS:
            for state in ('still','flow'):
                for suffix in ('.png','.png.mcmeta'):
                    self.assertEqual(files[RESOURCES+f'assets/techguns/textures/block/{texture}_{state}{suffix}'],
                                     (LEGACY/f'resources/assets/techguns/textures/blocks/{texture}_{state}{suffix}').read_bytes())
                png=files[RESOURCES+f'assets/techguns/textures/block/{texture}_{state}.png']
                width,height=struct.unpack('>II',png[16:24]); self.assertEqual(height%width,0)
            bucket=json.loads(files[RESOURCES+f'assets/techguns/items/{identifier}_bucket.json'])['model']
            self.assertEqual(bucket['type'],'neoforge:fluid_container')
            self.assertEqual(bucket['fluid'],'techguns:'+identifier)

    def test_chemical_machine_model_keeps_glass_and_hides_idle_liquid(self):
        files=generate_machine_content(); mesh=files[RESOURCES+'assets/techguns/models/block/chem_lab.obj'].decode()
        for index in range(1,12): self.assertIn(f'\no G{index}\n',mesh)
        for index in range(1,9): self.assertNotIn(f'\no L{index}\n',mesh)
        plan=plan_crafting(parse_weapons())
        self.assertEqual(plan['recipes']['chem_lab']['result']['id'],'techguns:chem_lab')
        self.assertIn('biomass',plan['materials'])
        self.assertIn('nuclearpowercell',plan['extra_ammo'])
        self.assertIn('radaway',plan['materials'])


if __name__=='__main__': unittest.main()
