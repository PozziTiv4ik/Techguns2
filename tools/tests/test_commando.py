import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from legacy_armors import armor_definitions, generate_armor_content, armor_translations
from legacy_commando import commando_definition, generate_commando_content, commando_translations
from legacy_crafting import plan_crafting
from generate_weapon_content import parse_weapons
from legacy_npcs import LEGACY, RESOURCES

class CommandoPortTests(unittest.TestCase):
    def test_npc_uses_original_fixed_loadout_and_material_stats(self):
        d=commando_definition(); self.assertEqual(d['attributes'],{'MOVEMENT_SPEED':.35,'MAX_HEALTH':30,'ATTACK_DAMAGE':5,'FOLLOW_RANGE':75})
        self.assertEqual(d['intrinsic_armor'],8); self.assertEqual(d['weapon'],'m4_infiltrator'); self.assertEqual(d['armor_chance'],1)
        self.assertEqual(d['armor'],['t2_commando_Helmet','t2_commando_Chestplate','t2_commando_Leggings','t2_commando_Boots'])
        self.assertFalse(d['natural_spawn_entry']); self.assertFalse(d['undead']); self.assertFalse(d['fire_immune']); self.assertEqual(d['egg_colors'],[0x191512,0x74806e])

    def test_original_shared_skin_and_distinct_suit_pixels(self):
        f=generate_commando_content(); d=commando_definition(); self.assertEqual(d['texture'],'textures/entity/army_soldier.png')
        key='assets/techguns/'+d['texture']; self.assertEqual(f[RESOURCES+key],(LEGACY/'resources'/key).read_bytes())
        f=generate_armor_content()
        for n,layer in [(1,'humanoid'),(2,'humanoid_leggings')]: self.assertEqual(f[RESOURCES+f'assets/techguns/textures/entity/equipment/{layer}/t2_commando.png'],(LEGACY/f'resources/assets/techguns/textures/models/armor/t2_commando_layer_{n}.png').read_bytes())
        for part in ('helmet','chestplate','leggings','boots'): self.assertEqual(f[RESOURCES+f'assets/techguns/textures/item/t2_commando_{part}.png'],(LEGACY/f'resources/assets/techguns/textures/items/t2_commando_{part}.png').read_bytes())

    def test_water_accuracy_oxygen_and_wear_parameters_come_from_declarations(self):
        armor=armor_definitions('t2_commando'); self.assertEqual(len(armor),4)
        for a in armor:
            self.assertEqual((a['water_mining'],a['gun_accuracy'],a['mining']),(1.25,.05,0))
            self.assertEqual((a['durability'],a['toughness'],a['enchantability']),(990,1,0)); self.assertEqual(a['radiation_resistance'],.25)
            self.assertEqual(a['oxygen_gear'],1 if a['slot']=='HEAD' else 0); self.assertEqual(a['repair_cloth'],'rubberbar'); self.assertEqual(a['textures'],['t2_commando'])
        self.assertEqual([sum(a[k] for a in armor) for k in ('physical','elemental','explosion','poison','dark','radiation')],[18,16,16,10,13.5,5])

    def test_all_six_loot_pools_keep_source_metadata_and_different_looting(self):
        pools=json.loads(generate_commando_content()[RESOURCES+'data/techguns/loot_table/entities/commando.json'])['pools']; self.assertEqual(len(pools),6)
        ids=['techguns:ingotobsidiansteel','techguns:rubberbar','minecraft:gunpowder','techguns:riflerounds','techguns:shotgunrounds','techguns:pistolrounds']
        for i,p in enumerate(pools):
            e=p['entries'][0]; self.assertEqual(e['name'],ids[i]); self.assertEqual(e['functions'][0]['count']['max'],[2,2,2,7,3,3][i])
            c=e['conditions'][0]; self.assertEqual(c['unenchanted_chance'],.1 if i==0 else .2); self.assertEqual(c['enchanted_chance']['per_level_above_first'],.05 if i<3 else .1)
            self.assertEqual(len(e['functions']),1 if i==0 else 2)
            if i: self.assertEqual(e['functions'][1]['count'],{'type':'minecraft:uniform','min':1,'max':2})

    def test_original_recipes_names_and_processed_rubber_tag(self):
        # The same workbench graph used by the generator selects the four original files.
        plan=plan_crafting(parse_weapons()); encoded=json.dumps(plan)
        for part in ('helmet','chestplate','leggings','boots'):
            self.assertIn('t2_commando_'+part,encoded); self.assertIn('item.techguns.t2_commando_'+part,armor_translations('ru_ru'))
        self.assertIn('c:rubbers',encoded)
        from legacy_items import ORE_TAGS
        self.assertEqual(ORE_TAGS['ITEMRUBBER'],('c:rubbers','rubberbar')); self.assertNotEqual(ORE_TAGS['ITEMRUBBER'],ORE_TAGS['ITEMRAWRUBBER'])
        self.assertEqual(commando_translations('en_us')['entity.techguns.commando'],'Commando')

    def test_new_armor_has_full_equipment_and_enchantment_tags(self):
        f=generate_armor_content(); equipment=json.loads(f[RESOURCES+'assets/techguns/equipment/t2_commando.json'])
        self.assertEqual(set(equipment['layers']),{'humanoid','humanoid_leggings'})
        for part,tag in [('helmet','head_armor'),('chestplate','chest_armor'),('leggings','leg_armor'),('boots','foot_armor')]:
            self.assertIn('techguns:t2_commando_'+part,json.loads(f[RESOURCES+'data/minecraft/tags/item/'+tag+'.json'])['values'])

if __name__=='__main__': unittest.main()
