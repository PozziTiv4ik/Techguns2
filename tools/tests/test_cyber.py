import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from legacy_cyber import cyber_geometry, generate_cyber_content
from legacy_npcs import LEGACY, RESOURCES, npc_loot
from generate_weapon_content import parse_weapons, generate, MISSING_SOURCE_SOUNDS


class CyberTests(unittest.TestCase):
    def test_twenty_cuboids_follow_seven_empty_bones(self):
        width, height, shapes, parents = cyber_geometry()
        self.assertEqual((width, height), (64, 64))
        self.assertEqual(sum(not s['empty'] for s in shapes), 20)
        self.assertEqual(sum(s['empty'] for s in shapes), 7)
        self.assertEqual(len(parents), 21)
        self.assertEqual(parents['h4'], 'bipedHead')
        self.assertEqual(parents['ll3'], 'bipedLeftLeg')
        parts = {s['name']:s for s in shapes}
        self.assertEqual(parts['bipedRightArm']['pivot'], [-5,2,0])
        self.assertEqual(parts['rightArm']['pivot'], [0,0,0])
        self.assertEqual(parts['ra2']['rotation'], [-.5205006,0,0])
        # In this export mirror assignments follow addBox, so existing ModelBox geometry is not mirrored.
        self.assertFalse(any(s['mirror'] for s in shapes if not s['empty']))

    def test_generated_mesh_and_original_skin(self):
        files = generate_cyber_content()
        mesh = files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/CyberDemonMesh.java'].decode()
        self.assertEqual(mesh.count('.addBox('), 20)
        self.assertEqual(mesh.count('.addOrReplaceChild('), 27)
        texture = 'assets/techguns/textures/entity/cyberdemon.png'
        self.assertEqual(files[RESOURCES + texture], (LEGACY / 'resources' / texture).read_bytes())

    def test_loot_has_source_chances_and_no_cyber_quantity_bonus(self):
        pools = npc_loot('cyberdemon')['pools']
        self.assertEqual(len(pools), 3)
        for pool, chance in zip(pools, (.5,.05,.05)):
            condition = pool['entries'][0]['conditions'][0]
            self.assertEqual(condition['unenchanted_chance'], chance)
            self.assertEqual(condition['enchanted_chance']['per_level_above_first'], .05)
        cyber = pools[0]['entries'][0]
        self.assertEqual(cyber['name'], 'techguns:cyberneticparts')
        self.assertEqual(cyber['functions'], [{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':1,'max':3}}])

    def test_source_weapon_and_missing_reload_sound(self):
        gun = next(g for g in parse_weapons() if g['id'] == 'netherblaster')
        self.assertEqual(gun['projectile'], 'nether_blaster')
        self.assertEqual(gun['npc_ai'], {'range':24,'interval':40,'burst':0,'shot_delay':0,'forward_offset':0})
        self.assertEqual(gun['ammo']['item'], 'nethercharge')
        self.assertEqual(gun['reload_sound'], 'guns.cyberdemonblasterreload')
        sounds = json.loads((LEGACY / 'resources/assets/techguns/sounds.json').read_text())
        self.assertNotIn(gun['reload_sound'], sounds)
        self.assertIn(gun['reload_sound'], MISSING_SOURCE_SOUNDS)

    def test_damage_tags_preserve_non_vanilla_fire_behavior(self):
        files = generate()
        for path in ('data/minecraft/tags/damage_type/bypasses_cooldown.json', 'data/neoforge/tags/damage_type/is_magic.json'):
            values = json.loads(files[RESOURCES + path])['values']
            self.assertIn('techguns:nether_blast', values)
            self.assertIn('techguns:laser', values)
        fire_tag = files.get(RESOURCES + 'data/minecraft/tags/damage_type/is_fire.json', b'{"values":[]}')
        self.assertNotIn('techguns:nether_blast', json.loads(fire_tag)['values'])

    def test_original_nether_pool_not_direct_cyber_spawn(self):
        files = generate_cyber_content()
        info = json.loads(files['content/cyberdemon.json'])
        self.assertEqual((info['nether_pool_weight'], info['spawn_weight'], info['danger_level']), (300,30,0))
        self.assertEqual(info['other_nether_entries'], {'ZombiePigmanSoldier':100})
        self.assertEqual(json.loads(files[RESOURCES + 'data/techguns/neoforge/biome_modifier/nether_npcs.json']), {'type':'techguns:nether_npcs'})


if __name__ == '__main__': unittest.main()
