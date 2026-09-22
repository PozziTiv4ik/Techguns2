import copy
import json
import re
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_fortifications import *
from legacy_crafting import plan_crafting
from generate_weapon_content import generate, parse_weapons


class FortificationTests(unittest.TestCase):
    def test_lamp_enum_and_metadata_flattening(self):
        text=(LEGACY/'java/techguns/blocks/EnumLampType.java').read_text(encoding='utf-8')
        names=re.search(r'implements IStringSerializable\s*\{(.*?);',text,re.S)[1]
        self.assertEqual([n.strip().lower() for n in names.split(',')],[v[2] for v in LAMPS])
        self.assertEqual([lamp_id(i) for i in range(14)],['lamp_yellow']*6+['lamp_white']*6+['lantern_yellow','lantern_white'])
        graph=plan_crafting(parse_weapons())
        for name,meta,_ in LAMPS: self.assertEqual(graph['catalog']['block_metadata']['techguns:lamp0@'+str(meta)],'techguns:'+name)

    def test_sandbag_multipart_preserves_all_eight_connections(self):
        files=generate_fortification_content(); old=json.loads((LEGACY/'resources/assets/techguns/blockstates/sandbags.json').read_text(encoding='utf-8'))
        modern=json.loads(files[RESOURCES+'assets/techguns/blockstates/sandbags.json'])
        for part in old['multipart']: part['apply']['model']=part['apply']['model'].replace('techguns:','techguns:block/')
        self.assertEqual(modern,old); self.assertEqual(len(modern['multipart']),9)
        self.assertEqual({next(iter(p['when'])) for p in modern['multipart'] if 'when' in p},{'north','east','south','west','corner_ne','corner_es','corner_sw','corner_wn'})

    def test_lamps_preserve_every_source_orientation_and_bracket(self):
        files=generate_fortification_content(); old=json.loads((LEGACY/'resources/assets/techguns/blockstates/lamp0.json').read_text(encoding='utf-8'))['multipart']
        for name,_,kind in LAMPS:
            expected=[]
            for entry in old:
                if entry['when']['lamp_type']!=kind: continue
                entry=copy.deepcopy(entry); del entry['when']['lamp_type']
                if not entry['when']: del entry['when']
                entry['apply']['model']=entry['apply']['model'].replace('techguns:','techguns:block/'); expected.append(entry)
            actual=json.loads(files[RESOURCES+f'assets/techguns/blockstates/{name}.json'])['multipart']
            self.assertEqual(actual,expected); self.assertEqual(len(actual),7 if kind.endswith('lantern') else 6)

    def test_door_keeps_all_sixty_four_native_state_combinations(self):
        files=generate_fortification_content(); old=json.loads((LEGACY/'resources/assets/techguns/blockstates/bunkerdoor.json').read_text(encoding='utf-8'))
        for v in old['variants'].values(): v['model']=v['model'].replace('techguns:','techguns:block/')
        actual=json.loads(files[RESOURCES+'assets/techguns/blockstates/bunkerdoor.json']); self.assertEqual(actual,old); self.assertEqual(len(actual['variants']),64)
        for old_parent,new_parent in DOOR_PARENTS.items():
            name=old_parent.replace('door_','bunker_door_',1)
            model=json.loads(files[RESOURCES+f'assets/techguns/models/block/{name}.json'])
            self.assertEqual(model['parent'],'minecraft:block/'+new_parent)

    def test_original_geometry_and_uv_of_all_custom_parts(self):
        files=generate_fortification_content(); checked=0
        for path,data in files.items():
            if '/models/' not in path or not path.endswith('.json'): continue
            model=json.loads(data)
            if 'elements' not in model: continue
            name=Path(path).stem
            source=LEGACY/f'resources/assets/techguns/models/block/{name}.json'
            if name=='sandbags': source=LEGACY/'resources/assets/techguns/models/item/sandbags_inventory.json'
            old=json.loads(source.read_text(encoding='utf-8'))
            self.assertEqual(model['elements'],old['elements']); self.assertEqual(model.get('display'),old.get('display')); checked+=1
        self.assertGreaterEqual(checked,9)

    def test_every_copied_png_is_byte_identical(self):
        files=generate_fortification_content(); checked=0
        for path,data in files.items():
            if not path.endswith('.png'): continue
            name=Path(path).name; folder='items' if name=='item_bunkerdoor.png' else 'blocks'
            self.assertEqual(data,(LEGACY/f'resources/assets/techguns/textures/{folder}/{name}').read_bytes()); checked+=1
        self.assertGreaterEqual(checked,14)

    def test_eight_original_recipes_have_no_cheaper_substitutions(self):
        graph=plan_crafting(parse_weapons()); values=graph['recipes']
        for name in RECIPES:
            old=json.loads((LEGACY/f'resources/assets/techguns/recipes/{name}.json').read_text(encoding='utf-8')); modern=values[name]
            self.assertEqual(modern.get('pattern'),old.get('pattern')); self.assertEqual(modern['result']['count'],old['result'].get('count',1))
        self.assertEqual(values['sandbags']['ingredients'],['#c:rubbers']+['#minecraft:sand']*8)
        self.assertEqual(values['item_bunkerdoor']['key'],{'p':'#c:plates/iron'})
        self.assertEqual(values['lamp0_0']['key'],{'r':'#c:dusts/redstone','g':'#c:glass_panes','i':'#c:nuggets/iron'})
        for a,b in [('lamp0_0_alt','lamp_white'),('lamp0_6','lamp_yellow'),('lamp0_12_alt','lantern_white'),('lamp0_13','lantern_yellow')]: self.assertEqual(values[a]['ingredients'],['techguns:'+b])

    def test_source_labels_and_inventory_definitions(self):
        files=generate_fortification_content()
        for lang in ('en_us','ru_ru'):
            names=fortification_translations(lang); self.assertEqual(len(names),13)
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual(names['item.techguns.item_bunkerdoor'],source['item.techguns.item_bunkerdoor.name'])
            for name,meta,_ in LAMPS: self.assertEqual(names['block.techguns.'+name],source[f'tile.techguns.lamp0.{meta}.name'])
        for n in ['sandbags','item_bunkerdoor']+[v[0] for v in LAMPS]: self.assertIn(RESOURCES+f'assets/techguns/items/{n}.json',files)
        self.assertNotIn(RESOURCES+'assets/techguns/items/bunkerdoor.json',files)

    def test_door_lower_only_loot_and_appropriate_mining_tags(self):
        files=generate_fortification_content()
        pool=json.loads(files[RESOURCES+'data/techguns/loot_table/blocks/bunkerdoor.json'])['pools'][0]
        self.assertEqual(pool['entries'],[{'type':'minecraft:item','name':'techguns:item_bunkerdoor'}]); self.assertIn({'condition':'minecraft:block_state_property','block':'techguns:bunkerdoor','properties':{'half':'lower'}},pool['conditions'])
        values=json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])['values']
        self.assertEqual(set(values),{'techguns:bunkerdoor'}|{'techguns:'+n for n,_,_ in LAMPS}); self.assertNotIn('techguns:sandbags',values)

    def test_original_door_ogg_and_subtitle_are_in_merged_catalog(self):
        files=generate(); data=json.loads(files[RESOURCES+'assets/techguns/sounds.json']); sound=data['blocks.metaldooropen']
        source=json.loads((LEGACY/'resources/assets/techguns/sounds.json').read_text(encoding='utf-8'))['blocks.metaldooropen']
        self.assertEqual(sound['sounds'],source['sounds'])
        for entry in sound['sounds']:
            name=entry if isinstance(entry,str) else entry['name']; name=name.split(':')[-1]
            self.assertEqual(files[RESOURCES+f'assets/techguns/sounds/{name}.ogg'],(LEGACY/f'resources/assets/techguns/sounds/{name}.ogg').read_bytes())
        for lang in ('en_us','ru_ru'): self.assertIn(sound['subtitle'],json.loads(files[RESOURCES+f'assets/techguns/lang/{lang}.json']))


if __name__=='__main__': unittest.main()
