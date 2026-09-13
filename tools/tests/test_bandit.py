import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_bandit import bandit_definition, generate_bandit_content, bandit_translations
from legacy_npcs import LEGACY, RESOURCES
from legacy_zombie_soldier import overworld_table
from generate_weapon_content import generate


class BanditPortTests(unittest.TestCase):
    def test_original_living_stats_and_six_reachable_guns(self):
        d=bandit_definition()
        self.assertEqual(d['attributes'],{'MOVEMENT_SPEED':.3,'MAX_HEALTH':20,'ATTACK_DAMAGE':5,'FOLLOW_RANGE':40})
        self.assertEqual(d['intrinsic_armor'],5)
        self.assertEqual(d['weapons'],['pistol','ak47','sawedoff','thompson','revolver','boltaction'])
        self.assertEqual(d['weapon_roll_bound'],6)
        self.assertEqual((d['undead'],d['burns_in_daylight'],d['fire_immune']),(False,False,False))

    def test_three_mandatory_scout_parts_and_optional_mask(self):
        d=bandit_definition()
        self.assertEqual(d['armor'],'T1_SCOUT'); self.assertEqual(d['always_equipped'],['CHEST','LEGS','FEET'])
        self.assertEqual((d['helmet_chance'],d['equipment_camo']),(.5,0))

    def test_six_loot_pools_keep_metadata_and_different_looting_rates(self):
        files=generate_bandit_content(); table=json.loads(files[RESOURCES+'data/techguns/loot_table/entities/bandit.json'])
        entries=[p['entries'][0] for p in table['pools']]
        self.assertEqual([e['name'] for e in entries],['techguns:heavycloth','minecraft:iron_ingot','minecraft:gunpowder','techguns:shotgunrounds','techguns:pistolrounds','techguns:riflerounds'])
        self.assertEqual([e['functions'][0]['count']['max'] for e in entries],[2,2,2,7,7,3])
        for i,e in enumerate(entries):
            self.assertEqual(e['functions'][0]['count']['min'],1)
            self.assertEqual(len(e['functions']),1 if i==0 else 2)
            if i>0: self.assertEqual(e['functions'][1]['count'],{'type':'minecraft:uniform','min':1,'max':2})
            condition=e['conditions'][0]; self.assertEqual(condition['unenchanted_chance'],.2)
            self.assertEqual(condition['enchanted_chance'],{'type':'minecraft:linear','base':.25 if i<3 else .3,'per_level_above_first':.05 if i<3 else .1})

    def test_loot_path_is_normalized_for_the_modern_registry(self):
        d=bandit_definition(); files=generate_bandit_content()
        self.assertEqual((d['source_loot_id'],d['loot_id']),('entities/Bandit','entities/bandit'))
        self.assertIn(RESOURCES+'data/techguns/loot_table/entities/bandit.json',files)
        self.assertNotIn(RESOURCES+'data/techguns/loot_table/entities/Bandit.json',files)

    def test_skin_and_egg_colors_come_from_the_original(self):
        d=bandit_definition(); files=generate_bandit_content()
        self.assertEqual(d['texture_size'],[64,64]); self.assertEqual(d['texture'],'textures/entity/bandit.png')
        path='assets/techguns/'+d['texture']; self.assertEqual(files[RESOURCES+path],(LEGACY/'resources'/path).read_bytes())
        self.assertEqual(d['egg_colors'],[0x8f9d59,0x2c3117])

    def test_original_names_and_generic_villager_sounds(self):
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual(bandit_translations(lang)['entity.techguns.bandit'],source['entity.techguns.Bandit.name'])
        generic=(LEGACY/'java/techguns/entities/npcs/GenericNPC.java').read_text()
        for kind in ('AMBIENT','HURT','DEATH'): self.assertIn('return SoundEvents.ENTITY_VILLAGER_'+kind,generic)

    def test_spawn_pool_keeps_weights_and_bandit_is_not_tagged_undead(self):
        entries=overworld_table(); self.assertEqual([e['weight'] for e in entries],[200,200,100,100,3,50])
        self.assertEqual([e['npc'] for e in entries if not e['implemented']],['PsychoSteve'])
        self.assertEqual(next(e['danger'] for e in entries if e['npc']=='Bandit'),2)
        files=generate()
        for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
            values=json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']
            self.assertEqual(len(values),6); self.assertNotIn('techguns:bandit',values)


if __name__=='__main__': unittest.main()
