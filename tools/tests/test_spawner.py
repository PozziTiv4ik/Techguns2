import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_spawner import spawner_definition, generate_spawner_content, spawner_translations
from legacy_npcs import LEGACY, RESOURCES
from generate_weapon_content import generate


class NpcSpawnerPortTests(unittest.TestCase):
    def test_source_default_limits_and_origin(self):
        d=spawner_definition()
        self.assertEqual(d['defaults'],{'delay':200,'spawndelay':200,'mobsLeft':5,'maxActive':3,'spawnHeightOffset':0,'spawnrange':2})
        self.assertEqual(d['home_radius'],10)

    def test_hole_and_military_preset_are_distinct(self):
        d=spawner_definition(); self.assertEqual([v['legacy_metadata'] for v in d['variants']],[0,1])
        self.assertEqual(d['variants'][0]['npc'],'techguns:zombiesoldier'); self.assertTrue(d['variants'][0]['implemented'])
        self.assertEqual(d['variants'][1]['npc'],'techguns:armysoldier'); self.assertTrue(d['variants'][1]['implemented'])
        self.assertIn(RESOURCES+'assets/techguns/items/soldier_spawn.json',generate_spawner_content())

    def test_military_original_geometry_texture_and_names(self):
        files=generate_spawner_content()
        legacy=json.loads((LEGACY/'resources/assets/techguns/models/block/soldier_spawn.json').read_text())
        model=json.loads(files[RESOURCES+'assets/techguns/models/block/soldier_spawn.json'])
        self.assertEqual(model['elements'],legacy['elements'])
        self.assertEqual(model['textures'],{'0':'techguns:block/soldier_spawn','particle':'techguns:block/soldier_spawn'})
        self.assertEqual(files[RESOURCES+'assets/techguns/textures/block/soldier_spawn.png'],(LEGACY/'resources/assets/techguns/textures/blocks/soldier_spawn.png').read_bytes())
        self.assertEqual(json.loads(files[RESOURCES+'data/techguns/loot_table/blocks/soldier_spawn.json'])['pools'],[])
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual(spawner_translations(lang)['block.techguns.soldier_spawn'],source['tile.techguns.tg_spawner.1.name'])

    def test_original_cuboids_and_uvs_are_unmodified(self):
        legacy=json.loads((LEGACY/'resources/assets/techguns/models/block/hole.json').read_text())
        model=json.loads(generate_spawner_content()[RESOURCES+'assets/techguns/models/block/tg_spawner.json'])
        self.assertEqual(len(model['elements']),13); self.assertEqual(model['elements'],legacy['elements'])
        self.assertEqual(model['textures'],{'0':'techguns:block/hole','particle':'techguns:block/hole'})

    def test_original_pixels_and_names(self):
        files=generate_spawner_content()
        self.assertEqual(files[RESOURCES+'assets/techguns/textures/block/hole.png'],(LEGACY/'resources/assets/techguns/textures/blocks/hole.png').read_bytes())
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual(spawner_translations(lang)['block.techguns.tg_spawner'],source['tile.techguns.tg_spawner.0.name'])

    def test_no_invented_collision_recipe_or_drops(self):
        d=spawner_definition(); self.assertEqual(d['outline'],[2,0,2,14,2,14]); self.assertFalse(d['collision']); self.assertFalse(d['survival_breaking'])
        files=generate_spawner_content(); self.assertEqual(json.loads(files[RESOURCES+'data/techguns/loot_table/blocks/tg_spawner.json'])['pools'],[])
        self.assertFalse(any('/recipe/' in path for path in files))

    def test_source_death_quota_and_unfiltered_spawn_checks(self):
        d=spawner_definition(); self.assertEqual(d['checks'],{'peaceful':True,'nearby_player':False,'daylight':False,'supporting_ground':False,'collision':False})
        source=(LEGACY/'java/techguns/tileentities/TGSpawnerTileEnt.java').read_text()
        death=source[source.index('public void killedEntity'):source.index('public void relinkNPC')]
        despawn=source[source.index('public void despawnedEntity'):source.index('public void killedEntity')]
        self.assertIn('this.mobsLeft--',death); self.assertNotIn('mobsLeft--',despawn)
        self.assertIn('Math.min(maxActive, mobsLeft)',source)

    def test_spawner_source_marker_not_vanilla_undead_tag(self):
        source=(LEGACY/'java/techguns/entities/npcs/GenericNPCUndead.java').read_text()
        self.assertIn('return !data.hasSpawner()',source)
        files=generate()
        for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
            values=json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']
            self.assertEqual(len(values),6)


if __name__=='__main__': unittest.main()
