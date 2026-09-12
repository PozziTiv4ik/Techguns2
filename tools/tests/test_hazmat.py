import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_armors import armor_definitions, generate_armor_content, armor_translations
from legacy_npcs import LEGACY, RESOURCES
from legacy_grinder import grinder_data
from legacy_crafting import plan_crafting
from generate_weapon_content import generate, parse_weapons


class HazmatPortTests(unittest.TestCase):
    def test_material_overrides_and_per_piece_bonuses(self):
        armor=armor_definitions('hazmat')
        self.assertEqual([a['physical'] for a in armor],[2.5,3,2.5,2])
        self.assertEqual([a['elemental'] for a in armor],[4,4.8,4,3.2])
        for field in ('poison','radiation'): self.assertEqual([a[field] for a in armor],[5,6,5,4])
        self.assertEqual([a['dark'] for a in armor],[1.875,2.25,1.875,1.5])
        self.assertEqual([a['durability'] for a in armor],[1100]*4)
        self.assertEqual([a['radiation_resistance'] for a in armor],[1]*4)
        self.assertEqual([a['fall_reduction'] for a in armor],[0,0,0,.1])
        self.assertEqual([a['free_fall_height'] for a in armor],[0,0,0,.5])

    def test_four_original_skins_and_icons_are_byte_identical(self):
        files=generate_armor_content()
        for a in armor_definitions('hazmat'):
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/item/{a["id"]}.png'],(LEGACY/f'resources/assets/techguns/textures/items/{a["id"]}.png').read_bytes())
            for skin in a['textures']:
                definition=json.loads(files[RESOURCES+f'assets/techguns/equipment/{skin}.json'])
                for n,layer in [(1,'humanoid'),(2,'humanoid_leggings')]:
                    self.assertEqual(definition['layers'][layer],[{'texture':'techguns:'+skin}])
                    self.assertEqual(files[RESOURCES+f'assets/techguns/textures/entity/equipment/{layer}/{skin}.png'],(LEGACY/f'resources/assets/techguns/textures/models/armor/{skin}_layer_{n}.png').read_bytes())

    def test_clothing_and_fiber_recipes_keep_source_costs(self):
        graph=plan_crafting(parse_weapons()); recipes=graph['recipes']
        for part,count in [('helmet',5),('chestplate',8),('leggings',7),('boots',4)]:
            recipe=recipes['hazmat_'+part]; self.assertEqual(recipe['key'],{'f':'techguns:protectivefiber'})
            self.assertEqual(''.join(recipe['pattern']).count('f'),count)
            self.assertEqual(recipe['result'],{'id':'techguns:hazmat_'+part,'count':1})
        self.assertEqual(recipes['protectivefiber']['ingredients'],['techguns:heavycloth','techguns:rubberbar','techguns:platelead'])
        self.assertEqual(recipes['protectivefiber']['result'],{'id':'techguns:protectivefiber','count':3})
        self.assertEqual(graph['catalog']['shared_metadata']['133'],'techguns:protectivefiber')

    def test_grinder_and_repair_use_dynamic_fiber_only_cost(self):
        for a in armor_definitions('hazmat'):
            self.assertEqual((a['repair_metal'],a['repair_cloth'],a['repair_metal_ratio']),('','protectivefiber',0))
            recipe=next(r for r in grinder_data()['recipes'] if r['id']==a['id'])
            self.assertTrue(recipe['armor']); self.assertEqual(recipe['outputs'],[])
        self.assertEqual([a['repair_parts'] for a in armor_definitions('hazmat')],[2,4,3,2])

    def test_armor_tags_merge_without_dropping_combat_armor(self):
        files=generate()
        for part,tag in [('helmet','head'),('chestplate','chest'),('leggings','leg'),('boots','foot')]:
            for path in (tag+'_armor','enchantable/'+tag+'_armor'):
                values=json.loads(files[RESOURCES+'data/minecraft/tags/item/'+path+'.json'])['values']
                self.assertEqual(set(values),{'techguns:hazmat_'+part,'techguns:t2_combat_'+part,'techguns:t1_combat_'+part,'techguns:t1_miner_'+part})
        self.assertEqual(len(json.loads(files[RESOURCES+'data/minecraft/tags/item/enchantable/durability.json'])['values']),16)

    def test_original_names_and_camouflage_colors(self):
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            result=armor_translations(lang)
            for part in ('helmet','chestplate','leggings','boots'): self.assertEqual(result['item.techguns.hazmat_'+part],source['techguns.item.hazmat_'+part+'.name'])
            for i in range(4): self.assertEqual(result['tooltip.techguns.armor.hazmat.camo.'+str(i)],source['techguns.item.hazmatsuit.camoname.'+str(i)])

    def test_radiation_attribute_has_no_original_wear_guard(self):
        source=(LEGACY/'java/techguns/items/armors/GenericArmor.java').read_text()
        attributes=source[source.index('public Multimap<String, AttributeModifier> getAttributeModifiers'):]
        self.assertIn('slot==this.armorType&&radresistance>0',attributes)
        self.assertNotIn('getItemDamage()',attributes)
        fall=(LEGACY/'java/techguns/events/TGEventHandler.java').read_text()
        self.assertIn('event.setDistance(event.getDistance() * reduction)',fall)


if __name__=='__main__': unittest.main()
