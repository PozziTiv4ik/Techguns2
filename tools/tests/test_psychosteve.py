import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_psychosteve import psycho_definition, generate_psycho_content, psycho_translations
from legacy_npcs import LEGACY, RESOURCES
from legacy_zombie_soldier import overworld_table
from generate_weapon_content import generate


class PsychoStevePortTests(unittest.TestCase):
    def test_original_attributes_armor_and_experience(self):
        d=psycho_definition()
        self.assertEqual(d['attributes'],{'MOVEMENT_SPEED':.6,'MAX_HEALTH':75,'ATTACK_DAMAGE':7,'FOLLOW_RANGE':60,'ARMOR_TOUGHNESS':1})
        self.assertEqual((d['intrinsic_armor'],d['declared_base_experience']),(5,25))

    def test_fixed_saw_and_matching_miner_suit(self):
        d=psycho_definition(); self.assertEqual(d['weapon'],'chainsaw'); self.assertEqual(d['armor'],'T1_MINER')
        self.assertEqual(d['always_equipped'],['HEAD','CHEST','LEGS','FEET']); self.assertTrue(d['shared_random_camo'])
        source=(LEGACY/'java/techguns/entities/npcs/PsychoSteve.java').read_text()
        self.assertEqual(source.count('getNewWithCamo('),4); self.assertEqual(source.count('getRandomCamoIndexFor('),1)

    def test_original_loot_pools_and_fuel_initialization(self):
        files=generate_psycho_content(); table=json.loads(files[RESOURCES+'data/techguns/loot_table/entities/psychosteve.json'])
        entries=[p['entries'][0] for p in table['pools']]
        self.assertEqual([e['name'] for e in entries],['techguns:chainsaw','techguns:fueltank'])
        for i,e in enumerate(entries):
            count=next(f['count'] for f in e['functions'] if f['function']=='minecraft:set_count')
            self.assertEqual(count,{'type':'minecraft:uniform','min':1 if i==0 else 2,'max':2 if i==0 else 4})
            condition=e['conditions'][0]; self.assertEqual(condition['unenchanted_chance'],.5)
            self.assertEqual(condition['enchanted_chance'],{'type':'minecraft:linear','base':.55,'per_level_above_first':.05})
        self.assertEqual(entries[0]['functions'][0],{'function':'minecraft:set_components','components':{'techguns:rounds':300,'techguns:mining_head':0}})
        self.assertEqual(entries[1]['functions'][1]['count'],{'type':'minecraft:uniform','min':1,'max':2})
        self.assertNotIn('minecraft:enchanted_count_increase',[f['function'] for f in entries[0]['functions']])
        gun=(LEGACY/'java/techguns/items/guns/GenericGun.java').read_text()
        self.assertIn('dmg==0 ? (short)this.clipsize',gun)

    def test_unmodified_army_skin_and_original_egg_colors(self):
        d=psycho_definition(); self.assertEqual(d['texture'],'textures/entity/army_soldier.png'); self.assertEqual(d['texture_size'],[128,128])
        self.assertEqual(d['egg_colors'],[0x757468,0xf0f0f0])
        path='assets/techguns/'+d['texture']; self.assertEqual(generate_psycho_content()[RESOURCES+path],(LEGACY/'resources'/path).read_bytes())

    def test_names_and_distinct_voice_step_sounds(self):
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual(psycho_translations(lang)['entity.techguns.psychosteve'],source['entity.techguns.PsychoSteve.name'])
        d=psycho_definition(); self.assertEqual(d['sounds']['step'],'minecraft:entity.zombie.step')
        self.assertEqual(d['sounds']['ambient'],'minecraft:entity.villager.ambient')

    def test_all_six_overworld_entries_keep_original_weights(self):
        table=overworld_table(); self.assertTrue(all(e['implemented'] for e in table))
        self.assertEqual([e['weight'] for e in table],[200,200,100,100,3,50])
        self.assertEqual([e['danger'] for e in table],[0,0,1,1,1,2])
        d=psycho_definition(); self.assertEqual((d['danger_level'],d['spawn_weight']),(1,3))

    def test_living_npc_does_not_join_undead_tags(self):
        d=psycho_definition(); self.assertEqual((d['undead'],d['burns_in_daylight'],d['fire_immune']),(False,False,False))
        files=generate()
        for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
            values=json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']
            self.assertEqual(len(values),6); self.assertNotIn('techguns:psychosteve',values)


if __name__=='__main__': unittest.main()
