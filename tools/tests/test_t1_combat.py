import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_armors import armor_definitions, generate_armor_content, armor_translations, call_arguments
from legacy_npcs import LEGACY, RESOURCES
from legacy_grinder import grinder_data
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons


class T1CombatPortTests(unittest.TestCase):
    def test_nested_iron_repair_constructor_and_optional_bonuses(self):
        armor=armor_definitions('t1_combat')
        self.assertEqual([a['physical'] for a in armor],[3.75,4.5,3.75,3])
        self.assertEqual([a['elemental'] for a in armor],[2.8125,3.375,2.8125,2.25])
        self.assertEqual([a['knockback'] for a in armor],[.05,.2,.1,.05])
        for a in armor:
            self.assertEqual((a['durability'],a['toughness'],a['speed'],a['jump']),(825,.5,0,0))
            self.assertEqual((a['repair_metal'],a['repair_cloth']),('minecraft:iron_ingot','heavycloth'))
        self.assertEqual(call_arguments('x.setRepairMats(new ItemStack(Items.IRON_INGOT,1), TGItems.HEAVY_CLOTH, 0.5f, 2).setKnockbackResistance(0.05f)', '.setRepairMats'),['new ItemStack(Items.IRON_INGOT,1)','TGItems.HEAVY_CLOTH','0.5f','2'])

    def test_single_source_appearance_and_exact_pixels(self):
        files=generate_armor_content()
        for a in armor_definitions('t1_combat'):
            self.assertEqual(a['textures'],['t1_combat'])
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/item/{a["id"]}.png'],(LEGACY/f'resources/assets/techguns/textures/items/{a["id"]}.png').read_bytes())
        model=json.loads(files[RESOURCES+'assets/techguns/equipment/t1_combat.json'])
        for n,layer in [(1,'humanoid'),(2,'humanoid_leggings')]:
            self.assertEqual(model['layers'][layer],[{'texture':'techguns:t1_combat'}])
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/entity/equipment/{layer}/t1_combat.png'],(LEGACY/f'resources/assets/techguns/textures/models/armor/t1_combat_layer_{n}.png').read_bytes())

    def test_original_crafting_costs_are_eleven_iron_and_thirteen_cloth(self):
        recipes=plan_crafting(parse_weapons())['recipes']; counts=[]
        for part in ('helmet','chestplate','leggings','boots'):
            recipe=recipes['t1_combat_'+part]
            self.assertEqual(recipe['key'],{'c':'techguns:heavycloth','i':'#c:ingots/iron'})
            self.assertEqual(recipe['result'],{'id':'techguns:t1_combat_'+part,'count':1})
            pattern=''.join(recipe['pattern']); counts.append((pattern.count('i'),pattern.count('c')))
        self.assertEqual(counts,[(3,2),(3,5),(3,4),(2,2)])

    def test_all_four_dynamic_recycling_recipes_and_catalogs(self):
        files=generate_armor_content(); catalog=json.loads(files['content/t1-combat-armor.json'])
        self.assertEqual(len(catalog['items']),4)
        for item in catalog['items']:
            recipe=next(r for r in grinder_data()['recipes'] if r['id']==item['id'])
            self.assertTrue(recipe['armor']); self.assertEqual(recipe['outputs'],[])
        self.assertIn('content/t2-combat-armor.json',files); self.assertIn('content/hazmat-armor.json',files)

    def test_original_localized_names(self):
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            text=armor_translations(lang)
            for part in ('helmet','chestplate','leggings','boots'):
                self.assertEqual(text['item.techguns.t1_combat_'+part],source['techguns.item.t1_combat_'+part+'.name'])

    def test_generic_armor_does_not_implement_changeable_camo(self):
        source=(LEGACY/'java/techguns/TGArmors.java').read_text()
        self.assertIn('t1_combat_Helmet = new GenericArmor(',source)
        self.assertNotIn('t1_combat_Helmet = new GenericArmorMultiCamo(',source)
        station=(LEGACY/'java/techguns/tileentities/CamoBenchTileEnt.java').read_text()
        self.assertIn('item.getItem() instanceof ICamoChangeable',station)


if __name__=='__main__': unittest.main()
