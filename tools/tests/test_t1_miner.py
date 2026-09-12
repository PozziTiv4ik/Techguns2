import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_armors import ARMOR_SETS, armor_definitions, generate_armor_content, armor_translations
from legacy_npcs import LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from legacy_grinder import grinder_data
from legacy_camo import camo_data
from legacy_repair import generate_repair_content
from generate_weapon_content import parse_weapons


class T1MinerPortTests(unittest.TestCase):
    def test_material_and_per_piece_bonuses(self):
        armor=armor_definitions('t1_miner')
        self.assertEqual([a['physical'] for a in armor],[3.25,3.9,3.25,2.6])
        self.assertEqual([a['elemental'] for a in armor],[2.4375,2.925,2.4375,1.95])
        for a in armor:
            self.assertEqual((a['durability'],a['toughness'],a['speed'],a['mining'],a['knockback']),(825,0,.08,.05,0))
        self.assertEqual([(a['jump'],a['fall_reduction'],a['free_fall_height']) for a in armor],[(0,0,0)]*3+[(.1,.2,1)])

    def test_all_four_icons_and_eight_layers_are_original_pixels(self):
        files=generate_armor_content()
        for a in armor_definitions('t1_miner'):
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/item/{a["id"]}.png'],(LEGACY/f'resources/assets/techguns/textures/items/{a["id"]}.png').read_bytes())
        for texture in ('t1_miner','t1_miner_red','t1_miner_green','t1_miner_black'):
            model=json.loads(files[RESOURCES+f'assets/techguns/equipment/{texture}.json'])
            for n,layer in [(1,'humanoid'),(2,'humanoid_leggings')]:
                self.assertEqual(model['layers'][layer],[{'texture':'techguns:'+texture}])
                self.assertEqual(files[RESOURCES+f'assets/techguns/textures/entity/equipment/{layer}/{texture}.png'],(LEGACY/f'resources/assets/techguns/textures/models/armor/{texture}_layer_{n}.png').read_bytes())

    def test_helmet_keeps_its_distinct_color_names(self):
        for lang in ('en_us','ru_ru'):
            text=armor_translations(lang)
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            for suffix in ('','helmet.'):
                for i in range(4): self.assertEqual(text[f'tooltip.techguns.armor.t1_miner.{suffix}camo.{i}'],source[f'techguns.item.t1_miner.{suffix}camoname.{i}'])
            self.assertNotEqual(text['tooltip.techguns.armor.t1_miner.camo.0'],text['tooltip.techguns.armor.t1_miner.helmet.camo.0'])
        self.assertEqual([a['camo_name_suffix'] for a in armor_definitions('t1_miner')],['helmet','','',''])

    def test_original_item_names(self):
        for lang in ('en_us','ru_ru'):
            text=armor_translations(lang)
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            for part in ('helmet','chestplate','leggings','boots'):
                self.assertEqual(text['item.techguns.t1_miner_'+part],source['techguns.item.t1_miner_'+part+'.name'])

    def test_original_crafting_uses_yellow_dye_and_boot_nuggets(self):
        recipes=plan_crafting(parse_weapons())['recipes']; counts=[]
        for part in ('helmet','chestplate','leggings','boots'):
            recipe=recipes['t1_miner_'+part]
            self.assertEqual(recipe['key']['i'],'#c:nuggets/iron' if part=='boots' else '#c:ingots/iron')
            self.assertEqual(recipe['key']['c'],'techguns:heavycloth')
            if part=='helmet': self.assertEqual(recipe['key']['y'],'#c:dyes/yellow')
            pattern=''.join(recipe['pattern']); counts.append((pattern.count('i'),pattern.count('c'),pattern.count('y')))
        self.assertEqual(counts,[(2,2,1),(2,6,0),(2,5,0),(2,2,0)])

    def test_repair_and_salvage_keep_source_ingots_even_for_boots(self):
        for a in armor_definitions('t1_miner'):
            self.assertEqual(a['repair_metal'],'minecraft:iron_ingot'); self.assertEqual(a['repair_cloth'],'heavycloth')
            self.assertTrue(next(r for r in grinder_data()['recipes'] if r['id']==a['id'])['armor'])
        self.assertEqual([a['repair_parts'] for a in armor_definitions('t1_miner')],[2,4,2,2])

    def test_both_benches_list_the_new_supported_set(self):
        repair=json.loads(generate_repair_content()['content/repair-bench.json'])
        self.assertIn('T1_MINER',repair['implemented_equipment']); self.assertIn('T1_MINER',camo_data()['armor'])
        self.assertNotIn('T1_COMBAT',camo_data()['armor'])

    def test_optional_mining_and_suffix_do_not_change_other_sets(self):
        for name in ARMOR_SETS:
            if name=='t1_miner': continue
            for a in armor_definitions(name): self.assertEqual((a['mining'],a['camo_name_suffix']),(0,''))


if __name__=='__main__': unittest.main()
