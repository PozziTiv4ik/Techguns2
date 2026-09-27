import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_neon import LEGACY,RESOURCES,neon_definition,neon_id,generate_neon_content,neon_translations
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons


class NeonTests(unittest.TestCase):
    def test_original_enum_properties_and_helipad_dependency(self):
        d=neon_definition()
        self.assertEqual([v['id'] for v in d['variants']],['neontubes2','neontubes2_rotated','neontubes4','neontubes4_rotated','neonsquare_white'])
        self.assertEqual([v['metadata'] for v in d['variants']],list(range(5)))
        self.assertEqual((d['hardness'],d['explosion_resistance'],d['light_level']),(4,4,15))
        self.assertTrue(d['full_cube'] and d['opaque_cube']);self.assertFalse(d['requires_tool'] or d['redstone_conductor'])
        self.assertEqual(neon_id(d['helipad_metadata']),'neonsquare_white')
        with self.assertRaises(ValueError):neon_id(-1)
        with self.assertRaises(ValueError):neon_id(5)
        helipad=(LEGACY/'java/techguns/world/structures/Helipad.java').read_text(encoding='utf-8')
        self.assertEqual(helipad.count('MBlockRegister.HELIPAD_GLOWBLOCK.getState()'),7)

    def test_each_rotated_style_retains_its_own_cube_texture_in_both_atlases(self):
        files=generate_neon_content();textures=set()
        for v in neon_definition()['variants']:
            source=json.loads((LEGACY/f'resources/assets/techguns/models/block/{v["source_model"]}.json').read_text(encoding='utf-8'))
            self.assertEqual(source['parent'],'block/cube_all');self.assertEqual(set(source['textures']),{'all'})
            original=source['textures']['all'];textures.add(original)
            for atlas in ('block','item'):
                model=json.loads(files[RESOURCES+f'assets/techguns/models/{atlas}/{v["id"]}.json'])
                self.assertEqual(model,{'parent':'minecraft:block/cube_all','textures':{'all':original.replace(':blocks/',':'+atlas+'/')}})
                name=original.split(':blocks/')[1]
                self.assertEqual(files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png'],(LEGACY/f'resources/assets/techguns/textures/blocks/{name}.png').read_bytes())
            state=json.loads(files[RESOURCES+f'assets/techguns/blockstates/{v["id"]}.json'])
            self.assertEqual(state,{'variants':{'':{'model':'techguns:block/'+v['id']}}})
        self.assertEqual(len(textures),5)

    def test_original_recipe_grid_count_tags_and_all_metadata_mappings(self):
        graph=plan_crafting(parse_weapons());recipe=graph['recipes']['neonlights_0']
        source=json.loads((LEGACY/'resources/assets/techguns/recipes/neonlights_0.json').read_text(encoding='utf-8'))
        self.assertEqual(recipe['pattern'],source['pattern']);self.assertEqual(recipe['pattern'],['nnn','gsg','nnn'])
        self.assertEqual(recipe['result'],{'id':'techguns:neontubes2','count':16})
        self.assertEqual(recipe['key'],{'n':'#c:nuggets/iron','g':'#c:glass_panes','s':'#c:dusts/glowstone'})
        for v in neon_definition()['variants']:self.assertEqual(graph['catalog']['block_metadata'][f'techguns:neonlights@{v["metadata"]}'],'techguns:'+v['id'])

    def test_each_variant_drops_itself_without_silk_touch_or_invented_tool_tag(self):
        files=generate_neon_content()
        for v in neon_definition()['variants']:
            table=json.loads(files[RESOURCES+f'data/techguns/loot_table/blocks/{v["id"]}.json'])
            self.assertEqual(table,{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:'+v['id']}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        self.assertFalse(any('/tags/block/mineable/' in name for name in files))

    def test_original_names_are_shared_by_items_blocks_and_bench(self):
        for lang in ('en_us','ru_ru'):
            labels=neon_translations(lang);self.assertEqual(len(labels),10)
            source=dict(s.split('=',1) for s in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in s)
            for v in neon_definition()['variants']:
                for kind in ('item','block'):self.assertEqual(labels[f'{kind}.techguns.{v["id"]}'],source[f'tile.techguns.neonlights.{v["metadata"]}.name'])

    def test_five_style_camo_order_includes_both_rotated_variants(self):
        d=neon_definition();self.assertEqual(d['camo_order'],[v['id'] for v in d['variants']])
        source=(LEGACY/'java/techguns/blocks/GenericBlockMetaEnumCamoChangeable.java').read_text(encoding='utf-8')
        self.assertIn('type=clazz.getEnumConstants().length-1',source)
        self.assertIn('type>=clazz.getEnumConstants().length',source)
        generated=generate_neon_content()['core/src/main/java/techguns/core/NeonLights.java'].decode()
        for v in d['variants']:self.assertIn(f'new Variant("{v["id"]}", {v["metadata"]})',generated)


if __name__=='__main__':unittest.main()
