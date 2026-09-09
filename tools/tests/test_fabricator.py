"""Legacy Fabricator recipe semantics, shape orientation, GUI and shared resource graph."""
import json
import math
from pathlib import Path
import re
import struct
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_fabricator import fabricator_recipes,generate_fabricator_content,ingredients_and_slots,PARTS,LEGACY,RESOURCES
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons,generate


class FabricatorPortTests(unittest.TestCase):
    def test_all_source_recipes_and_fixed_slot_quantities(self):
        recipes=fabricator_recipes()
        self.assertEqual(len(recipes),6)
        self.assertEqual([(r['id'],*[r[k]['count'] for k in ('input','wire','powder','plate')],r['result']['count']) for r in recipes],[
            ('energycellempty',1,1,3,1,1),('cyberneticparts',1,1,1,1,1),('powerplating',2,4,1,4,2),
            ('sonicemitter',1,2,1,1,1),('rademitter',1,2,2,2,1),('nuclearpowercelldepleted',1,1,4,2,1)])
        self.assertTrue(all(r['duration']==100 and r['power_per_tick']==80 for r in recipes))

    def test_ore_wrapper_amount_does_not_override_actual_consumption(self):
        recipe=next(r for r in fabricator_recipes() if r['id']=='nuclearpowercelldepleted')
        self.assertEqual(recipe['input'],{'ingredient':'#c:ingots/steel','count':1})
        source=(LEGACY/'java/techguns/TGMachineRecipes.java').read_text()
        self.assertIn('new ItemStackOreDict("ingotSteel", 2), 1, FabricatorRecipe.circuit_basic',source)

    def test_slot_categories_come_from_all_source_lists(self):
        _,slots=ingredients_and_slots()
        self.assertEqual(slots['wire'],['#c:wires/copper','#c:wires/gold','#c:circuits/basic','#c:circuits/elite'])
        self.assertEqual(slots['powder'],['#c:dusts/redstone','techguns:mechanicalpartscarbon'])
        self.assertEqual(slots['plate'],['#c:sheets/plastic','#c:plates/carbon','#c:plates/titanium','#c:plates/lead'])

    def test_fabricator_and_reaction_metadata_share_one_workbench_map(self):
        plan=plan_crafting(parse_weapons()); catalog=plan['catalog']['block_metadata']
        self.assertEqual([catalog[f'techguns:multiblockmachine@{i}'] for i in range(3)],['techguns:'+p for p in PARTS])
        self.assertEqual(catalog['techguns:multiblockmachine@3'],'techguns:reactionchamber_housing')
        self.assertEqual(plan['recipes']['fabricator_housing']['result']['count'],4)
        self.assertEqual(plan['recipes']['fabricator_controller']['result']['id'],'techguns:fabricator_controller')
        self.assertNotIn('cyberneticparts',plan['recipes'])

    def test_model_keeps_source_idle_parts_orientation_and_pixels(self):
        files=generate_fabricator_content(); source=(LEGACY/'java/techguns/client/models/machines/ModelFabricator.java').read_text()
        self.assertEqual(len(re.findall(r'new ModelRenderer\(',source)),39)
        mesh=files[RESOURCES+'assets/techguns/models/block/fabricator.obj'].decode(); objects=[l[2:] for l in mesh.splitlines() if l.startswith('o ')]
        self.assertEqual(len(objects),38); self.assertNotIn('WorkingLaser1',objects)
        for name in ('Glass','Controller','WorkingLaserBeam','WorkingDrill2','WorkingTube2'): self.assertIn(name,objects)
        vertices=[list(map(float,l.split()[1:])) for l in mesh.splitlines() if l.startswith('v ')]
        self.assertTrue(all(math.isfinite(v) for p in vertices for v in p))
        self.assertAlmostEqual(min(p[0] for p in vertices),-1,places=6); self.assertAlmostEqual(min(p[2] for p in vertices),-1,places=6)
        states=json.loads(files[RESOURCES+'assets/techguns/blockstates/fabricator_controller.json'])['variants']
        self.assertEqual([states[f'facing={name},formed=true']['y'] for name in ('south','west','north','east')],[0,90,180,270])
        self.assertEqual(files[RESOURCES+'assets/techguns/textures/block/fabricator.png'],(LEGACY/'resources/assets/techguns/textures/blocks/fabricator.png').read_bytes())
        for slot in ('wires','powder','plate'):
            png=files[RESOURCES+f'assets/techguns/textures/gui/emptyslot_{slot}.png']
            self.assertEqual(png,(LEGACY/f'resources/assets/techguns/textures/gui/emptyslots/emptyslot_{slot}.png').read_bytes())
            self.assertEqual(struct.unpack('>II',png[16:24]),(16,16))

    def test_combined_tags_keep_all_multiblock_families(self):
        files=generate(); values=json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])['values']
        for item in (*PARTS,'reactionchamber_housing','chem_lab','ore_titanium'): self.assertIn('techguns:'+item,values)
        self.assertEqual(len(values),len(set(values)))


if __name__=='__main__': unittest.main()
