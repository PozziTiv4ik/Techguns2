"""Source contracts for the chamber's production graph, original geometry and shared tags."""
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_reactions import reaction_recipes,generate_reaction_content,LEGACY,RESOURCES
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons,generate
from legacy_radiation import generate_radiation_content


class ReactionPortTests(unittest.TestCase):
    def test_all_seven_live_reactions_keep_timing_and_cost(self):
        recipes={r['id']:r for r in reaction_recipes()}
        self.assertEqual(set(recipes),{'uv_wheat','laser_focus','titanium','blazerod','glowstone','antigrav','uranium'})
        self.assertEqual([recipes[k]['energy_per_check'] for k in ('uv_wheat','laser_focus','titanium','blazerod','glowstone','antigrav','uranium')],
                         [50000,100000,25000,250000,250000,500000,250000])
        self.assertEqual((recipes['uranium']['cycles'],recipes['uranium']['required_completion'],recipes['uranium']['instability']),(5,4,0))
        self.assertEqual(recipes['uv_wheat']['results'],[{'id':'minecraft:wheat','count':1},{'id':'minecraft:wheat_seeds','count':2}])

    def test_titanium_uses_ore_block_and_keeps_processed_ore_distinct(self):
        recipe=next(r for r in reaction_recipes() if r['id']=='titanium')
        self.assertEqual(recipe['input'],'techguns:ore_titanium')
        self.assertEqual(recipe['results'],[{'id':'techguns:oretitanium','count':2},{'id':'minecraft:iron_ore','count':1}])
        self.assertEqual((recipe['fluid'],recipe['liquid_level'],recipe['fluid_consumption']),('techguns:creeper_acid',3,100))
        self.assertEqual(recipe['focus'],'techguns:rcheatray')

    def test_workbench_dependency_graph_preserves_controller_and_focus_costs(self):
        plan=plan_crafting(parse_weapons()); recipes=plan['recipes']
        self.assertEqual(recipes['reactionchamber_controller']['key']['r'],'techguns:reactionchamber_housing')
        self.assertEqual(recipes['reactionchamber_controller']['key']['c'],'#c:circuits/elite')
        for name in ('rcheatray','rcuvemitter','quartzrod','circuitboardelite'): self.assertIn(name,plan['materials'])
        for product in ('laserfocus','antigravcore','enricheduranium','oretitanium'): self.assertNotIn(product,recipes)

    def test_original_multiblock_mesh_is_copied_and_rotates_towards_its_interior(self):
        files=generate_reaction_content()
        mesh=files[RESOURCES+'assets/techguns/models/block/reactionchamber.obj']
        original=(LEGACY/'resources/assets/techguns/models/block/reactionchamber.obj').read_bytes()
        self.assertEqual(mesh,b'\n'.join(line.rstrip() for line in original.splitlines())+b'\n')
        self.assertEqual(sum(line.startswith(b'f ') for line in mesh.splitlines()),334)
        states=json.loads(files[RESOURCES+'assets/techguns/blockstates/reactionchamber_controller.json'])['variants']
        self.assertEqual([states[f'facing={d},formed=true']['y'] for d in ('south','west','north','east')],[0,90,180,270])

    def test_overlapping_generated_tags_keep_ores_machines_acid_and_radiation(self):
        files=generate()
        mining=json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])['values']
        for item in ('ore_titanium','chem_lab','reactionchamber_controller'): self.assertIn('techguns:'+item,mining)
        damage=json.loads(files[RESOURCES+'data/minecraft/tags/damage_type/no_knockback.json'])['values']
        for item in ('acid','radiation','radiation_poisoning'): self.assertIn('techguns:'+item,damage)
        cooldown=json.loads(files[RESOURCES+'data/minecraft/tags/damage_type/bypasses_cooldown.json'])['values']
        for item in ('bullet','radiation','radiation_poisoning'): self.assertIn('techguns:'+item,cooldown)

    def test_radiation_icons_use_source_pixels_and_native_atlas_regions(self):
        files=generate_radiation_content()
        self.assertEqual(files[RESOURCES+'assets/techguns/textures/gui/radiation_icons_source.png'],
                         (LEGACY/'resources/assets/techguns/textures/gui/tgplayerinventory.png').read_bytes())
        source=json.loads(files[RESOURCES+'assets/minecraft/atlases/gui.json'])['sources'][0]
        self.assertEqual((source['divisor_x'],source['divisor_y']),(256,256))
        self.assertEqual([(r['x'],r['y'],r['width'],r['height']) for r in source['regions']],[(0,168,16,16),(16,168,16,16),(32,168,16,16)])


if __name__=='__main__': unittest.main()
