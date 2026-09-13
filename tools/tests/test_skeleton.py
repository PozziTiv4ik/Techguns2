import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_skeleton import skeleton_definition, generate_skeleton_content, skeleton_translations
from legacy_npcs import LEGACY, RESOURCES
from legacy_zombie_soldier import overworld_table
from generate_weapon_content import generate


class SkeletonPortTests(unittest.TestCase):
    def test_source_stats_three_weapons_and_always_equipped_slots(self):
        d=skeleton_definition()
        self.assertEqual(d['attributes'],{'MOVEMENT_SPEED':.25,'MAX_HEALTH':25,'ATTACK_DAMAGE':4,'FOLLOW_RANGE':50})
        self.assertEqual(d['weapons'],['revolver','thompson','handcannon']); self.assertEqual(d['weapon_roll_bound'],3)
        self.assertEqual(d['always_equipped'],['HEAD','FEET']); self.assertEqual(d['empty_slots'],['CHEST','LEGS'])
        self.assertEqual((d['independent_scout_chance'],d['equipment_camo'],d['intrinsic_armor'],d['height']),(.5,0,0,1.95))

    def test_thin_limbs_keep_source_uv_mirroring_and_deformation(self):
        d=skeleton_definition()
        self.assertEqual((d['texture_size'],d['body_inflation'],d['armor_inflations']),([64,32],.0625,[.5,1]))
        self.assertEqual([p['name'] for p in d['limbs']],['right_arm','left_arm','right_leg','left_leg'])
        self.assertEqual([p['mirror'] for p in d['limbs']],[False,True,False,True])
        self.assertEqual([p['uv'] for p in d['limbs']],[[40,16],[40,16],[0,16],[0,16]])
        self.assertEqual([p['pivot'] for p in d['limbs']],[[-5,2,0],[5,2,0],[-2,12,0],[2,12,0]])
        self.assertEqual([p['box'] for p in d['limbs']],[[-1,-2,-1,2,12,2]]*2+[[-1,0,-1,2,12,2]]*2)

    def test_weapon_offsets_are_held_item_translations(self):
        self.assertEqual(skeleton_definition()['held_item_offsets'],[-.06,-.06])
        layer=(LEGACY/'java/techguns/client/render/entities/LayerHeldItemTranslateGun.java').read_text()
        self.assertIn('lefthand ? -shooter.getWeaponPosX() : shooter.getWeaponPosX()',layer)
        self.assertLess(layer.index('this.setEntityTranslation(ent,flag)'),layer.index('renderItemSide(ent'))

    def test_four_loot_pools_preserve_coal_quantity_exception(self):
        table=json.loads(generate_skeleton_content()[RESOURCES+'data/techguns/loot_table/entities/skeletonsoldier.json'])
        entries=[p['entries'][0] for p in table['pools']]
        self.assertEqual([e['name'] for e in entries],['minecraft:coal','minecraft:gunpowder','minecraft:bone','techguns:heavycloth'])
        for i,e in enumerate(entries):
            self.assertEqual(e['functions'][0]['count'],{'type':'minecraft:uniform','min':1,'max':2})
            self.assertEqual(len(e['functions']),1 if i==0 else 2)
            condition=e['conditions'][0]
            self.assertEqual(condition['unenchanted_chance'],.2)
            self.assertEqual(condition['enchanted_chance'],{'type':'minecraft:linear','base':.25,'per_level_above_first':.05})

    def test_vanilla_skin_is_referenced_without_copying(self):
        d=skeleton_definition(); files=generate_skeleton_content()
        self.assertEqual(d['texture'],'minecraft:textures/entity/skeleton/skeleton.png')
        self.assertFalse(any(path.endswith('.png') for path in files))
        self.assertEqual(d['egg_colors'],[0x404040,0xf0f0f0])
        for lang in ('en_us','ru_ru'):
            source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
            self.assertEqual(skeleton_translations(lang)['entity.techguns.skeletonsoldier'],source['entity.techguns.SkeletonSoldier.name'])

    def test_danger_table_keeps_original_weights_and_two_reserved_choices(self):
        entries=overworld_table()
        self.assertEqual([e['weight'] for e in entries],[200,200,100,100,3,50])
        self.assertEqual([e['npc'] for e in entries if not e['implemented']],['PsychoSteve','Bandit'])
        self.assertEqual(next(e['danger'] for e in entries if e['npc']=='SkeletonSoldier'),1)

    def test_all_six_undead_tags_are_preserved(self):
        files=generate()
        expected={'techguns:'+n for n in ('cyberdemon','zombiepigmansoldier','zombiesoldier','zombiefarmer','zombieminer','skeletonsoldier')}
        for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
            self.assertEqual(set(json.loads(files[RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json'])['values']),expected)


if __name__=='__main__': unittest.main()
