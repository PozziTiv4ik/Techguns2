"""Charging Station recipe, metadata and unchanged legacy asset checks."""
from pathlib import Path
import json
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_charging import charging_data, generate_charging_content, LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons, generate


class ChargingPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = generate_charging_content()
        cls.catalog = json.loads(cls.files['content/charging-station.json'])

    def test_original_rates_and_linear_batch_power(self):
        catalog = self.catalog
        self.assertEqual((catalog['energy_capacity'], catalog['charge_rate'], catalog['item_charge_rate']), (100000,800,1600))
        self.assertEqual((catalog['batch_power_exponent'], catalog['upgrade_limit']), (1,7))
        # MachineOperation.getPowerPerTick includes the batch multiplier, including in the charger's own tick method.
        self.assertEqual(catalog['charge_rate'] * 8, 6400)

    def test_two_original_recipes_and_truncated_cost(self):
        recipes = self.catalog['recipes']
        self.assertEqual([(r['input']['ingredient'],r['result']['id'],r['charge_amount']) for r in recipes], [
            ('techguns:energycellempty','techguns:energycell',50000),
            ('techguns:redstone_battery_empty','techguns:redstone_battery',20000)])
        self.assertTrue(all(r['input']['count']==r['result']['count']==1 for r in recipes))
        self.assertEqual([r['charge_amount']//800 for r in recipes], [62,25])
        self.assertEqual([r['charge_amount']//800*800 for r in recipes], [49600,20000])

    def test_original_block_metadata_and_workbench_cost(self):
        plan = plan_crafting(parse_weapons())
        self.assertEqual(plan['catalog']['block_metadata']['techguns:simplemachine@10'], 'techguns:charging_station')
        recipe = plan['recipes']['charging_station']
        self.assertEqual(recipe['pattern'], ['sgs','cbc','sgs'])
        self.assertEqual(recipe['key'], {'b':'#c:circuits/basic','s':'#c:plates/steel','c':'techguns:coil','g':'#c:wires/gold'})
        self.assertEqual(recipe['result'], {'id':'techguns:charging_station','count':1})

    def test_original_centered_mesh_and_uv_flag(self):
        source = (LEGACY / 'resources/assets/techguns/models/block/charging_station_centered.obj').read_text()
        normalized = '\n'.join(line.rstrip() for line in source.splitlines())+'\n'
        self.assertEqual(self.files[RESOURCES+'assets/techguns/models/block/charging_station.obj'].decode(), normalized)
        self.assertEqual((self.catalog['model']['parts'],self.catalog['model']['faces']), (22,132))
        for atlas in ('block','item'):
            model=json.loads(self.files[RESOURCES+'assets/techguns/models/'+atlas+'/charging_station.json'])
            self.assertTrue(model['flip_v'])
            self.assertEqual(model['textures']['body'], 'techguns:'+atlas+'/charging_station')
        variants=json.loads(self.files[RESOURCES+'assets/techguns/blockstates/charging_station.json'])['variants']
        self.assertEqual({k:v['y'] for k,v in variants.items()}, {'facing=north':0,'facing=east':90,'facing=south':180,'facing=west':270})

    def test_original_gui_and_block_pixels_are_unchanged(self):
        assets=LEGACY/'resources/assets/techguns'
        for atlas in ('block','item'):
            self.assertEqual(self.files[RESOURCES+'assets/techguns/textures/'+atlas+'/charging_station.png'], (assets/'textures/blocks/charging_station.png').read_bytes())
        self.assertEqual(self.files[RESOURCES+'assets/techguns/textures/gui/charging_station.png'], (assets/'textures/gui/charging_station_gui.png').read_bytes())

    def test_generation_keeps_previous_tags_and_charge_audio(self):
        files=generate()
        tools=json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])['values']
        self.assertIn('techguns:charging_station',tools)
        self.assertTrue({'techguns:blast_furnace','techguns:fabricator_controller','techguns:reactionchamber_controller'}.issubset(tools))
        sounds=json.loads(files[RESOURCES+'assets/techguns/sounds.json'])
        self.assertIn('machines.chargingstationwork',sounds)
        self.assertIn('guns.lasergunfire',sounds)
        self.assertEqual(charging_data(), {k:v for k,v in self.catalog.items() if k not in ('model','rendering')})


if __name__ == '__main__': unittest.main()
