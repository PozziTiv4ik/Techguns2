import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import generate, parse_weapons
from legacy_military_crates import crate_definition, crate_loot, crate_id, LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from legacy_items import shared_items
from legacy_models import strip_comments


class MilitaryCrateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.files = generate(); cls.tables = crate_loot(parse_weapons())

    def test_nine_source_variants_keep_metadata_and_six_pools(self):
        d = crate_definition()
        self.assertEqual([v['type'] for v in d['variants']], ['ammo','gun','armor','medical','explosive','generic_oak','generic_jungle','generic_birch','generic_spruce'])
        self.assertEqual(len(set(v['loot'] for v in d['variants'])),6)
        self.assertEqual({k:sum(len(p['entries']) for p in v['pools']) for k,v in self.tables.items()},
                         {'military_crate_ammo':14,'military_crate_armor':12,'military_crate_explosives':6,'military_crate_generic':13,'military_crate_gun':10,'military_crate_medical':5})
        self.assertEqual(sum(len(p['entries']) for v in self.tables.values() for p in v['pools']),60)
        for i,v in enumerate(d['variants']): self.assertEqual(crate_id(i),v['id'])
        for bad in (-1,9):
            with self.assertRaises(ValueError): crate_id(bad)

    def test_wood_material_really_uses_stone_sound_and_inset_collision(self):
        root = LEGACY/'java/techguns'
        registration = strip_comments((root/'TGBlocks.java').read_text())
        parent = strip_comments((root/'blocks/GenericBlockMetaEnum.java').read_text())
        block = strip_comments((root/'blocks/BlockMilitaryCrate.java').read_text())
        self.assertIn('new BlockMilitaryCrate("military_crate", Material.WOOD).setHardness(4.0f)',registration)
        self.assertIn('mat.getMaterialMapColor(),SoundType.STONE, clazz',parent)
        self.assertIn('new AxisAlignedBB(0.03125, 0, 0.03125, 0.96875, 1, 0.96875)',block)
        self.assertIn('BlockFaceShape.CENTER_BIG',block)
        self.assertEqual(crate_definition()['bounds'],[.03125,0,.03125,.96875,1,.96875])

    def test_every_source_weight_count_function_and_bonus_roll_survives(self):
        shared = shared_items()
        for key,modern in self.tables.items():
            source = json.loads((LEGACY/f'resources/assets/techguns/loot_tables/blocks/{key}.json').read_text())
            self.assertEqual(len(source['pools']),len(modern['pools']))
            for a,b in zip(source['pools'],modern['pools']):
                self.assertEqual(a['rolls'],b['rolls']); self.assertEqual(a['name'],b['name'])
                self.assertEqual(a.get('bonus_rolls'),None if 'bonus_rolls' not in b else {k:v for k,v in b['bonus_rolls'].items() if k!='type'})
                self.assertEqual(len(a['entries']),len(b['entries']))
                for old,new in zip(a['entries'],b['entries']):
                    self.assertEqual(old['weight'],new['weight']); name = old['name']
                    for f in old.get('functions',[]):
                        if f['function']=='set_data': name='techguns:'+shared[f['data']]
                        elif f['function']=='set_count': self.assertIn({'function':'minecraft:set_count','count':{'type':'minecraft:uniform',**f['count']}},new['functions'])
                        else: self.fail('Review newly introduced source loot function')
                    self.assertEqual(name,new['name'])
        explosive = self.tables['military_crate_explosives']['pools'][0]['entries']
        self.assertEqual([(e['name'],e['weight']) for e in explosive], [('techguns:'+n,w) for n,w in zip(('rocket','40mmgrenade','stielgranate','fraggrenade','rocketlauncher','grenadelauncher'),(2,2,2,2,1,1))])

    def test_all_rewards_exist_and_generic_gun_loot_is_loaded(self):
        guns = {g['id']:g['capacity'] for g in parse_weapons()}; loaded = set()
        for table in self.tables.values():
            for pool in table['pools']:
                for e in pool['entries']:
                    if e['name'].startswith('techguns:'):
                        name = e['name'].split(':')[1]; self.assertIn(RESOURCES+f'assets/techguns/items/{name}.json',self.files)
                        if name in guns:
                            self.assertIn({'function':'minecraft:set_components','components':{'techguns:rounds':guns[name]}},e['functions']); loaded.add(name)
        self.assertEqual(len(loaded),12); self.assertIn('flamethrower',loaded)
        source=(LEGACY/'java/techguns/items/guns/GenericGun.java').read_text()
        self.assertIn('dmg==0 ? (short)this.clipsize : (short)(this.clipsize-dmg)',source)

    def test_high_harvest_hook_uses_fortune_not_player_luck(self):
        source=strip_comments((LEGACY/'java/techguns/events/TGEventHandler.java').read_text())
        body=source.split('public static void MilitaryCrateDrops',1)[1].split('@',1)[0]
        self.assertIn('!event.isSilkTouching()',body); self.assertIn('ply!=null',body)
        self.assertIn('withLuck(fortune).withPlayer(ply)',body); self.assertIn('event.getDrops().clear()',body)
        self.assertIn('@SubscribeEvent(priority=EventPriority.HIGH)',source)

    def test_geometry_uvs_and_all_original_pngs_remain_exact(self):
        assets = LEGACY/'resources/assets/techguns'; geometry=json.loads((assets/'models/block/military_crate.json').read_text())
        source=json.loads((assets/'blockstates/military_crate.json').read_text())['variants']['type']; textures=set()
        for v in crate_definition()['variants']:
            for atlas in ('block','item'):
                model=json.loads(self.files[RESOURCES+f'assets/techguns/models/{atlas}/{v["id"]}.json'])
                self.assertEqual(model['elements'],geometry['elements']); self.assertEqual(len(model['elements']),2)
                for slot,path in source[v['type']]['textures'].items():
                    if path.startswith('techguns:'):
                        name=path.split('/')[-1]; textures.add(name)
                        self.assertEqual(model['textures'][slot],f'techguns:{atlas}/{name}')
                        self.assertEqual(self.files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png'],(assets/f'textures/blocks/{name}.png').read_bytes())
                    else:self.assertEqual(model['textures'][slot],'minecraft:block/'+path.split('planks_')[1]+'_planks')
        self.assertEqual(len(textures),10)

    def test_no_invented_crafting_storage_or_camo_cycle(self):
        recipes=LEGACY/'resources/assets/techguns/recipes'
        self.assertFalse(any(json.loads(p.read_text()).get('result',{}).get('item')=='techguns:military_crate' for p in recipes.glob('*.json') if not p.name.startswith('_')))
        graph=plan_crafting(parse_weapons());self.assertFalse(any('military_crate' in name for name in graph['recipes']))
        for v in crate_definition()['variants']:
            self.assertEqual(graph['catalog']['block_metadata'][f'techguns:military_crate@{v["metadata"]}'],'techguns:'+v['id'])
            table=json.loads(self.files[RESOURCES+f'data/techguns/loot_table/blocks/{v["id"]}_self.json'])
            self.assertEqual(table['pools'][0]['entries'],[{'type':'minecraft:item','name':'techguns:'+v['id']}])


if __name__=='__main__':unittest.main()
