"""Minigun contracts against the untouched gun, tick handler, recipes and multipart model."""
import json
import re
import sys
import unittest
from collections import Counter
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_weapon_content import parse_weapons, generate, resolve_asset
from legacy_crafting import plan_crafting
from legacy_grinder import grinder_data
from legacy_minigun import minigun_parts, LEGACY, RESOURCES
from legacy_models import strip_comments, extract_shapes, shape_vertices


class MinigunPortTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guns = parse_weapons()
        cls.gun = next(g for g in cls.guns if g['id'] == 'minigun')
        cls.files = generate()
        cls.graph = plan_crafting(cls.guns)

    def test_source_zero_delay_and_held_client_tick(self):
        self.assertEqual([self.gun[k] for k in ('capacity','fire_delay','reload_ticks','damage','minimum_damage','drop_start','drop_end','speed','lifetime','accuracy','penetration')],
                         [200,0,100,5,3,30,50,3,75,.025,.5])
        self.assertTrue(self.gun['automatic'])
        tick = strip_comments((LEGACY/'java/techguns/events/TGTickHandler.java').read_text())
        primary = tick.split('if (event.phase == Phase.START)',1)[1].split('if (!stackOff.isEmpty()',1)[0]
        self.assertEqual(primary.count('new PacketShootGun('),1)
        self.assertIn('props.getFireDelay(EnumHand.MAIN_HAND) <= 0',primary)
        source = strip_comments((LEGACY/'java/techguns/items/guns/GenericGun.java').read_text())
        self.assertIn('extendedPlayer.setFireDelay(hand,this.minFiretime)',source)
        self.assertIn('if (!player.capabilities.isCreativeMode)',source)

    def test_full_and_empty_gun_recipes_keep_six_barrels_and_engine(self):
        for suffix, rounds, drum in [('',200,'minigundrum'),('_alt',0,'minigundrumempty')]:
            recipe = self.graph['recipes']['minigun'+suffix]
            self.assertEqual(recipe['pattern'],['bbe','bbr','bbm'])
            self.assertEqual(recipe['key'],{'b':'techguns:obsidiansteelbarrel','r':'techguns:obsidiansteelreceiver','e':'techguns:electricengine','m':'techguns:'+drum})
            self.assertEqual(recipe['result'],{'id':'techguns:minigun','count':1,'components':{'techguns:rounds':rounds}})

    def test_drums_use_four_packed_bundles_and_keep_original_empty_output(self):
        recipes = self.graph['recipes']
        self.assertEqual(recipes['minigundrumempty']['result'],{'id':'techguns:minigundrumempty','count':4})
        self.assertEqual(recipes['minigundrumempty']['pattern'],['sss','pmp','sss'])
        for suffix in ('','_incendiary'):
            self.assertEqual(Counter(recipes['minigundrum'+suffix]['ingredients']),Counter({'techguns:minigundrumempty':1,'techguns:rifleroundsstack'+suffix:4}))
            self.assertEqual(recipes['minigundrum'+suffix]['result']['count'],1)
        self.assertEqual(self.gun['ammo'],{'item':'minigundrum','empty_item':'minigundrumempty','loose_item':'riflerounds','bundles_per_magazine':16,'individual':False})

    def test_grinder_recovers_original_metals_and_engine(self):
        recipe = next(r for r in grinder_data()['recipes'] if r['id']=='minigun')
        self.assertEqual(recipe['outputs'],[{'result':{'id':'techguns:'+name,'count':count}} for name,count in [('ingotobsidiansteel',20),('ingotsteel',2),('electricengine',1)]])

    def test_split_mesh_preserves_every_source_part_and_full_mesh_vertex(self):
        source, body, rotor, center = minigun_parts()
        self.assertEqual((len(body),len(rotor)),(46,27))
        complete = self.files[RESOURCES+'assets/techguns/models/item/minigun.obj'].decode().splitlines()
        split = []
        for name, expected in [('minigun_body',body),('minigun_rotor',rotor)]:
            rows = self.files[RESOURCES+f'assets/techguns/models/item/{name}.obj'].decode().splitlines()
            self.assertEqual({row[2:] for row in rows if row.startswith('o ')},set(expected))
            split.extend(row for row in rows if row.startswith(('v ','vt ')))
        self.assertEqual(Counter(split),Counter(row for row in complete if row.startswith(('v ','vt '))))
        catalog = json.loads(self.files['content/minigun-behavior.json'])
        self.assertEqual(catalog['rotor_pivot'],[0,center[1]/32,-center[2]/32])
        self.assertEqual(catalog['rotor_degrees'],-360)
        self.assertEqual(catalog['spin_ticks'],5)

    def test_source_textures_and_finite_fire_reload_audio(self):
        for name in ('minigundrum','minigundrumempty','minigundrum_incendiary'):
            self.assertEqual(self.files[RESOURCES+f'assets/techguns/textures/item/{name}.png'],resolve_asset(f'textures/items/{name}.png').read_bytes())
        self.assertEqual(self.files[RESOURCES+'assets/techguns/textures/item/minigun.png'],resolve_asset('textures/guns/minigun.png').read_bytes())
        sounds = json.loads(self.files[RESOURCES+'assets/techguns/sounds.json'])
        original = json.loads(resolve_asset('sounds.json').read_text())
        for key in ('fire_sound','reload_sound'):
            sound = self.gun[key]
            self.assertEqual(sounds[sound]['sounds'],original[sound]['sounds'])
        self.assertNotIn('guns.minigunstart',sounds)


if __name__ == '__main__': unittest.main()
