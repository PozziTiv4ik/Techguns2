import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_armors import armor_definitions, generate_armor_content, armor_translations
from legacy_npcs import LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from legacy_grinder import grinder_data
from legacy_camo import camo_data
from legacy_repair import generate_repair_content
from generate_weapon_content import parse_weapons


class T1ScoutPortTests(unittest.TestCase):
    def test_source_material_and_different_jump_bonuses(self):
        armor=armor_definitions('t1_scout')
        self.assertEqual([a['physical'] for a in armor],[3.25,3.9,3.25,2.6])
        self.assertEqual([a['elemental'] for a in armor],[2.4375,2.925,2.4375,1.95])
        self.assertEqual([a['jump'] for a in armor],[.02,.02,.02,.1])
        self.assertEqual([(a['fall_reduction'],a['free_fall_height']) for a in armor],[(0,0)]*3+[(.2,1)])
        for a in armor:
            self.assertEqual((a['durability'],a['toughness'],a['speed'],a['mining'],a['knockback'],a['radiation_resistance']),(825,0,.125,0,0,0))

    def test_original_icons_layers_and_four_equipment_assets(self):
        files=generate_armor_content()
        for a in armor_definitions('t1_scout'):
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/item/{a["id"]}.png'],(LEGACY/f'resources/assets/techguns/textures/items/{a["id"]}.png').read_bytes())
        for texture in ('t1_scout','t1_scout_forest','t1_scout_snow','t1_scout_black'):
            model=json.loads(files[RESOURCES+f'assets/techguns/equipment/{texture}.json'])
            for n,layer in [(1,'humanoid'),(2,'humanoid_leggings')]:
                self.assertEqual(model['layers'][layer],[{'texture':'techguns:'+texture}])
                self.assertEqual(files[RESOURCES+f'assets/techguns/textures/entity/equipment/{layer}/{texture}.png'],(LEGACY/f'resources/assets/techguns/textures/models/armor/{texture}_layer_{n}.png').read_bytes())

    def test_original_bandit_names_and_labels_for_missing_camo_translations(self):
        for lang,labels in [('en_us',['Default','Forest','Snow','Black']),('ru_ru',['Обычный','Лесной','Снежный','Чёрный'])]:
            text=armor_translations(lang)
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            for a in armor_definitions('t1_scout'):
                self.assertEqual(text['item.techguns.'+a['id']],source['techguns.item.'+a['id']+'.name'])
                self.assertEqual(a['camo_name_suffix'],'')
            for i,label in enumerate(labels):
                self.assertNotIn(f'techguns.item.t1_scout.camoname.{i}',source)
                self.assertEqual(text[f'tooltip.techguns.armor.t1_scout.camo.{i}'],label)

    def test_cloth_only_crafting_keeps_original_shapes_and_cost(self):
        recipes=plan_crafting(parse_weapons())['recipes']
        expected=[['ccc','c c'],['c c','ccc','ccc'],['ccc','c c','c c'],['c c','c c']]
        for part,pattern,count in zip(('helmet','chestplate','leggings','boots'),expected,(5,8,7,4)):
            recipe=recipes['t1_scout_'+part]
            self.assertEqual(recipe['pattern'],pattern)
            self.assertEqual(recipe['key'],{'c':'techguns:heavycloth'})
            self.assertEqual(''.join(pattern).count('c'),count)

    def test_repair_and_grinder_use_cloth_without_metal(self):
        armor=armor_definitions('t1_scout')
        self.assertEqual([a['repair_parts'] for a in armor],[2,4,3,2])
        recipes={r['id']:r for r in grinder_data()['recipes']}
        for a in armor:
            self.assertEqual((a['repair_metal'],a['repair_cloth'],a['repair_metal_ratio']),('','heavycloth',0))
            self.assertTrue(recipes[a['id']]['armor'])
        self.assertIn('T1_SCOUT',json.loads(generate_repair_content()['content/repair-bench.json'])['implemented_equipment'])
        self.assertIn('T1_SCOUT',camo_data()['armor'])

    def test_each_piece_has_native_armor_and_book_tags(self):
        files=generate_armor_content()
        for part,slot in [('helmet','head'),('chestplate','chest'),('leggings','leg'),('boots','foot')]:
            for tag in ('enchantable/armor','enchantable/durability',slot+'_armor','enchantable/'+slot+'_armor'):
                self.assertIn('techguns:t1_scout_'+part,json.loads(files[RESOURCES+f'data/minecraft/tags/item/{tag}.json'])['values'])

    def test_jump_tooltip_accepts_per_item_value_in_both_languages(self):
        for lang in ('en_us','ru_ru'):
            self.assertEqual(armor_translations(lang)['tooltip.techguns.armor.jump'].count('%s'),1)


if __name__=='__main__': unittest.main()
