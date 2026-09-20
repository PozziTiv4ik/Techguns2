import gzip
import io
import json
import struct
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_locations import metal_definitions, altar_definition, altar_nbt, generate_location_content, location_translations
from legacy_spawner import generate_spawner_content
from legacy_npcs import LEGACY, RESOURCES
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons, generate


def read_nbt(data):
    stream=io.BytesIO(gzip.decompress(data))
    def read(fmt): return struct.unpack(fmt,stream.read(struct.calcsize(fmt)))[0]
    def string(): return stream.read(read('>H')).decode('utf-8')
    def payload(kind):
        if kind==3: return read('>i')
        if kind==6: return read('>d')
        if kind==8: return string()
        if kind==9:
            child=read('>B'); count=read('>i'); return [payload(child) for _ in range(count)]
        if kind==10:
            result={}
            while (child:=read('>B'))!=0:
                name=string(); result[name]=payload(child)
            return result
        raise AssertionError('Unexpected NBT type '+str(kind))
    assert read('>B')==10 and string()==''
    result=payload(10); assert stream.read()==b''; return result


class LocationPortTests(unittest.TestCase):
    def test_metal_enum_order_light_and_original_names(self):
        values=metal_definitions()
        self.assertEqual([v['id'] for v in values],['nethermetal_'+n for n in ('panel','grate1','grate2','grey_dark','grey','grey_tiles','border_red','plate_black','plate_red','border_lava')])
        self.assertEqual([v['metadata'] for v in values],list(range(10)))
        self.assertEqual([v['light'] for v in values],[0]*9+[15])
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual([location_translations(lang)['block.techguns.'+v['id']] for v in values],[source[f'tile.techguns.nethermetal.{i}.name'] for i in range(10)])

    def test_block_and_item_atlases_keep_original_pixels(self):
        files=generate_location_content(); assets=LEGACY/'resources/assets/techguns'
        for v in metal_definitions():
            name=v['id']; block=json.loads(files[RESOURCES+f'assets/techguns/models/block/{name}.json']); item=json.loads(files[RESOURCES+f'assets/techguns/models/item/{name}.json'])
            self.assertNotIn('render_type',block)
            self.assertEqual(item['textures'],{k:t.replace('techguns:block/','techguns:item/') for k,t in block['textures'].items()})
            for t in block['textures'].values():
                if t.startswith('techguns:block/'):
                    texture=t.split('/')[-1]
                    for atlas in ('block','item'): self.assertEqual(files[RESOURCES+f'assets/techguns/textures/{atlas}/{texture}.png'],(assets/f'textures/blocks/{texture}.png').read_bytes())
            loot=json.loads(files[RESOURCES+f'data/techguns/loot_table/blocks/{name}.json'])
            self.assertEqual(loot['pools'][0]['entries'][0]['name'],'techguns:'+name)

    def test_lava_frame_has_native_emission_and_explicit_vanilla_item_sprite(self):
        files=generate_location_content()
        for atlas in ('block','item'):
            model=json.loads(files[RESOURCES+f'assets/techguns/models/{atlas}/cube_glow_frame.json'])
            self.assertEqual(len(model['elements']),2); self.assertEqual(model['elements'][0]['light_emission'],15)
            self.assertNotIn('light_emission',model['elements'][1]); self.assertNotIn('ctm_overrides',model)
            self.assertFalse(any('tintindex' in f for f in model['elements'][0]['faces'].values()))
        self.assertEqual(json.loads(files[RESOURCES+'assets/minecraft/atlases/items.json']),{'sources':[{'type':'minecraft:single','resource':'minecraft:block/lava_still'}]})
        self.assertFalse(any(path.startswith(RESOURCES+'assets/minecraft/textures/') for path in files))

    def test_original_sixteen_panel_recipe_and_metadata_mapping(self):
        graph=plan_crafting(parse_weapons()); recipe=graph['recipes']['nethermetal_panel']
        self.assertEqual(recipe['pattern'],['nsn','sis','nsn']); self.assertEqual(recipe['key'],{'s':'#c:stones','i':'#c:ingots/iron','n':'#c:netherracks'})
        self.assertEqual(recipe['result'],{'id':'techguns:nethermetal_panel','count':16})
        for m in metal_definitions(): self.assertEqual(graph['catalog']['block_metadata'][f'techguns:nethermetal@{m["metadata"]}'],'techguns:'+m['id'])

    def test_scan_preserves_all_cells_including_air_and_original_base(self):
        d=altar_definition(); self.assertEqual(len(d['cells']),769); self.assertEqual(d['size'],[11,9,11]); self.assertEqual(d['declared_size'],[11,10,11])
        raw=(LEGACY/'resources/assets/techguns/structures/nether_altar_small').read_text().splitlines()
        self.assertEqual(d['cells'],[list(map(int,line.split(','))) for line in raw[1:]])
        self.assertEqual([c for c in d['cells'] if c[1]==0],[[x,0,z,6] for x in (4,5,6) for z in (4,5,6)])
        self.assertEqual([c for c in d['cells'] if c[3]==9],[[5,7,5,9]])
        self.assertEqual((d['height_offset'],d['worldgen_floor_offset'],d['foundation_depth'],d['foundation_stop_after_solids']),(-2,-1,16,2))

    def test_template_nbt_roundtrip_and_determinism(self):
        first=altar_nbt(); self.assertEqual(first,altar_nbt()); d=altar_definition(); tree=read_nbt(first)
        self.assertEqual(tree['DataVersion'],4903); self.assertEqual(tree['size'],[11,9,11]); self.assertEqual(tree['entities'],[])
        self.assertEqual(tree['palette'],d['palette'])
        self.assertEqual([b['pos']+[b['state']] for b in tree['blocks']],d['cells'])
        spawners=[b for b in tree['blocks'] if 'nbt' in b]; self.assertEqual(len(spawners),1)
        nbt=spawners[0]['nbt']; self.assertEqual(nbt['mobtypes'],[{'id':'techguns:cyberdemon','weight':1}])
        self.assertEqual((nbt['mobsLeft'],nbt['maxActive'],nbt['spawnDelay'],nbt['delay'],nbt['spawnRange']),(3,2,200,200,1))
        self.assertNotIn('instance',nbt); self.assertNotIn('active',nbt)

    def test_stair_metadata_maps_to_original_directions(self):
        palette=altar_definition()['palette']
        for index,facing in [(4,'east'),(7,'south'),(8,'north'),(10,'west')]:
            self.assertEqual(palette[index]['Properties'],{'facing':facing,'half':'bottom','shape':'straight','waterlogged':'false'})

    def test_natural_set_grid_and_unported_tickets_remain_explicit(self):
        d=altar_definition(); g=d['generation']; self.assertEqual([e['weight'] for e in g['candidates']],[10]*5)
        self.assertEqual([e['implemented'] for e in g['candidates']],[True,False,False,False,False])
        files=generate_location_content(); structure=json.loads(files[RESOURCES+'data/techguns/worldgen/structure/nether_altar_small.json'])
        self.assertEqual((structure['reserved_medium_grid'],structure['reserved_big_grid']),(32,64)); self.assertEqual(structure['spawn_overrides'],{})
        placement=json.loads(files[RESOURCES+'data/techguns/worldgen/structure_set/nether_altar_small.json'])['placement']
        self.assertEqual((placement['type'],placement['spacing'],placement['separation']),('minecraft:random_spread',16,15))

    def test_spawner_item_models_have_their_own_atlas_textures(self):
        files=generate_spawner_content()
        for identifier,texture in [('tg_spawner','hole'),('soldier_spawn','soldier_spawn')]:
            block=json.loads(files[RESOURCES+f'assets/techguns/models/block/{identifier}.json']); item=json.loads(files[RESOURCES+f'assets/techguns/models/item/{identifier}.json'])
            self.assertEqual(item['elements'],block['elements']); self.assertEqual(item['textures'],{k:v.replace(':block/',':item/') for k,v in block['textures'].items()})
            self.assertEqual(files[RESOURCES+f'assets/techguns/textures/item/{texture}.png'],files[RESOURCES+f'assets/techguns/textures/block/{texture}.png'])

    def test_whole_resource_graph_keeps_existing_mining_tags(self):
        files=generate(); tag=json.loads(files[RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json'])
        self.assertTrue({'techguns:ore_copper','techguns:repair_bench',*('techguns:'+m['id'] for m in metal_definitions())}.issubset(tag['values']))
        self.assertEqual(len(set(tag['values'])),len(tag['values']))


if __name__=='__main__': unittest.main()
