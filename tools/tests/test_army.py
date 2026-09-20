import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_army import army_definition, beret_geometry, generate_army_content, army_translations
from legacy_armors import armor_definitions, generate_armor_content, armor_translations
from legacy_npcs import LEGACY, RESOURCES, npc_loot
from legacy_crafting import plan_crafting
from legacy_grinder import grinder_data
from legacy_zombie_soldier import overworld_table
from generate_weapon_content import generate, parse_weapons


class ArmyPortTests(unittest.TestCase):
    def test_living_soldier_original_attributes_and_choices(self):
        d=army_definition()
        self.assertEqual(d['attributes'],{'MOVEMENT_SPEED':.3,'MAX_HEALTH':25,'ATTACK_DAMAGE':4,'FOLLOW_RANGE':75})
        self.assertEqual(d['intrinsic_armor'],8)
        self.assertEqual(d['weapons'],['m4','combatshotgun','boltaction'])
        self.assertEqual((d['armor_chance'],d['head_fallback'],d['camo']),(.5,'t2_beret',0))
        self.assertFalse(d['undead']); self.assertFalse(d['natural_spawn_entry'])
        self.assertNotIn('ArmySoldier',[e['npc'] for e in overworld_table()])

    def test_original_soldier_pixels_egg_and_names(self):
        files=generate_army_content(); d=army_definition()
        self.assertEqual(d['egg_colors'],[0x74806e,0x191512])
        self.assertEqual(files[RESOURCES+'assets/techguns/'+d['texture']],(LEGACY/'resources/assets/techguns'/d['texture']).read_bytes())
        self.assertEqual(d['sounds']['step'],'minecraft:entity.zombie_villager.step')
        for lang in ('en_us','ru_ru'):
            self.assertIn('entity.techguns.armysoldier',army_translations(lang))

    def test_soldier_loot_retains_five_independent_pools(self):
        loot=npc_loot('armysoldier'); entries=[p['entries'][0] for p in loot['pools']]
        self.assertEqual([e['name'] for e in entries],['techguns:heavycloth','minecraft:iron_ingot','minecraft:gunpowder','techguns:shotgunrounds','techguns:riflerounds'])
        self.assertEqual([e['functions'][0]['count']['max'] for e in entries],[2,2,2,7,3])
        self.assertEqual([len(e['functions']) for e in entries],[1,2,2,2,2])
        self.assertEqual([e['conditions'][0]['enchanted_chance']['per_level_above_first'] for e in entries],[.05,.05,.05,.1,.1])

    def test_single_beret_material_repair_and_bonuses(self):
        definitions=armor_definitions('t2_beret'); self.assertEqual(len(definitions),1); a=definitions[0]
        self.assertEqual((a['id'],a['slot'],a['durability'],a['physical'],a['elemental']),('t2_beret','HEAD',825,2,1.5))
        self.assertEqual((a['toughness'],a['speed'],a['jump'],a['mining'],a['enchantability']),(0,.1,0,0,0))
        self.assertEqual((a['repair_metal'],a['repair_cloth'],a['repair_metal_ratio'],a['repair_parts']),('heavycloth','heavycloth',1,2))

    def test_beret_mesh_preserves_rotation_inflation_uv_and_hierarchy(self):
        width,height,parts=beret_geometry(); self.assertEqual((width,height),(32,32)); self.assertEqual(len(parts),2)
        top,side=parts
        self.assertEqual(top['box'],[-2,-8.7,-4,6,2,8]); self.assertEqual(top['uv'],[0,0]); self.assertEqual(top['inflate'],.7)
        self.assertEqual(side['box'],[-3,0,0,3,2,8]); self.assertEqual(side['uv'],[0,11]); self.assertEqual(side['inflate'],.65)
        self.assertEqual(side['pivot'],[-2.7,-8.5,-4]); self.assertAlmostEqual(side['rotation'][2],-.6108652381980153)
        mesh=generate_army_content()['platforms/neoforge-26.2/src/main/java/techguns/modern/client/BeretMesh.java'].decode()
        self.assertEqual(mesh.count('head.addOrReplaceChild('),3)  # Two beret boxes plus empty vanilla hat child.
        self.assertEqual(mesh.count('.addBox('),2)

    def test_three_beret_skins_keep_source_pixels_and_labels(self):
        files=generate_armor_content(); a=armor_definitions('t2_beret')[0]
        self.assertEqual(a['textures'],['beret_texture','beret_texture_black','beret_texture_green'])
        for texture in a['textures']:
            asset=json.loads(files[RESOURCES+f'assets/techguns/equipment/{texture}.json'])
            self.assertEqual(set(asset['layers']),{'humanoid','humanoid_baby'})
            for layer in asset['layers']:
                self.assertEqual(files[RESOURCES+f'assets/techguns/textures/entity/equipment/{layer}/{texture}.png'],(LEGACY/f'resources/assets/techguns/textures/models/armor/{texture}.png').read_bytes())
        self.assertEqual(files[RESOURCES+'assets/techguns/textures/item/t2_beret.png'],(LEGACY/'resources/assets/techguns/textures/items/t2_beret.png').read_bytes())
        for lang,labels in [('en_us',['Red','Black','Green']),('ru_ru',['Красный','Черный','Зеленый'])]:
            self.assertEqual([armor_translations(lang)[f'tooltip.techguns.armor.t2_beret.camo.{i}'] for i in range(3)],labels)

    def test_asymmetric_four_cloth_recipe_and_head_tags(self):
        recipe=plan_crafting(parse_weapons())['recipes']['t2_beret']
        self.assertEqual(recipe['pattern'],[' cc','c c']); self.assertEqual(recipe['key'],{'c':'techguns:heavycloth'})
        files=generate_armor_content()
        for tag in ('head_armor','enchantable/head_armor','enchantable/armor','enchantable/durability'):
            self.assertIn('techguns:t2_beret',json.loads(files[RESOURCES+f'data/minecraft/tags/item/{tag}.json'])['values'])
        for tag in ('chest_armor','leg_armor','foot_armor'):
            self.assertNotIn('techguns:t2_beret',json.loads(files[RESOURCES+f'data/minecraft/tags/item/{tag}.json'])['values'])
        recipes=[r for r in grinder_data()['recipes'] if r['id'].startswith('t2_beret')]
        self.assertEqual(recipes,[{'id':'t2_beret','input':'techguns:t2_beret','outputs':[],'armor':True}])

    def test_new_soldier_keeps_existing_undead_tags_and_shared_skin(self):
        files=generate()
        for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
            values=json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']
            self.assertEqual(len(values),6); self.assertNotIn('techguns:armysoldier',values)
        self.assertEqual(files[RESOURCES+'assets/techguns/textures/entity/army_soldier.png'],(LEGACY/'resources/assets/techguns/textures/entity/army_soldier.png').read_bytes())


if __name__=='__main__': unittest.main()
