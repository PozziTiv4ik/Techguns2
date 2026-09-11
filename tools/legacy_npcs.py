"""Source-backed first NPC: hierarchy, loot table and original Basic texture (Heavy/Elite skins are separate NPCs)."""
from pathlib import Path
import json
import re
from legacy_models import strip_comments, extract_shapes
from legacy_items import shared_items

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'
SOUNDS = ('npcs.cyberdemonidle', 'npcs.cyberdemonhurt', 'npcs.cyberdemondeath', 'npcs.cyberdemonstep')
NAMES = {'bipedHead':'head','bipedHeadwear':'hat','bipedBody':'body','bipedRightArm':'right_arm',
         'bipedLeftArm':'left_arm','bipedRightLeg':'right_leg','bipedLeftLeg':'left_leg'}


def mutant_geometry():
    source = strip_comments((LEGACY / 'java/techguns/client/models/npcs/ModelSuperMutant.java').read_text())
    parents = {child: parent for parent, child in re.findall(r'this\.(\w+)\.addChild\(this\.(\w+)\)', source)}
    # Modern HumanoidModel owns the hat under the head; legacy ModelBiped copies the head's pose to it.
    parents['bipedHeadwear'] = 'bipedHead'
    flat = re.sub(r'this\.\w+\.addChild\(this\.\w+\);', '', source)
    width, height, shapes = extract_shapes(flat, 'ModelSuperMutant')
    return width, height, shapes, parents


def mutant_loot():
    return npc_loot('supermutantbasic')


def npc_loot(name):
    source = json.loads((LEGACY / f'resources/assets/techguns/loot_tables/entities/{name}.json').read_text())
    pools = []
    for pool in source['pools']:
        entries = []
        for entry in pool['entries']:
            name = entry['name']; functions = []
            for function in entry['functions']:
                kind = function['function']
                if kind == 'set_data':
                    if name != 'techguns:itemshared': raise ValueError('Unported metadata loot')
                    name = 'techguns:' + shared_items()[function['data']]
                elif kind in ('set_count','looting_enchant'):
                    modern = {'function':'minecraft:' + ('enchanted_count_increase' if kind == 'looting_enchant' else 'set_count'),
                              'count':{'type':'minecraft:uniform', **function['count']}}
                    if kind == 'looting_enchant': modern['enchantment'] = 'minecraft:looting'
                    functions.append(modern)
                else: raise ValueError('Unported loot function: ' + kind)
            conditions = []
            for condition in entry['conditions']:
                if condition['condition'] != 'random_chance_with_looting': raise ValueError('Unported loot condition')
                chance, bonus = condition['chance'], condition['looting_multiplier']
                conditions.append({'condition':'minecraft:random_chance_with_enchanted_bonus', 'enchantment':'minecraft:looting',
                    'unenchanted_chance':chance, 'enchanted_chance':{'type':'minecraft:linear', 'base':round(chance+bonus,8), 'per_level_above_first':bonus}})
            entries.append({'type':'minecraft:item','name':name,'weight':entry['weight'],'functions':functions,'conditions':conditions})
        pools.append({'rolls':pool['rolls'],'entries':entries})
    return {'type':'minecraft:entity','pools':pools}


def generate_npc_content():
    files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    width, height, shapes, parents = mutant_geometry()
    data('content/supermutantbasic.json', {'source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/SuperMutantBasic.java',
        'weapons':['rocketlauncher','ak47','combatshotgun','lasergun','lasergun'],
        'natural_spawn_entry':False, 'egg_icon':'minecraft:item/egg tinted with original primary color',
        'original_egg_colors':[0xc6a96b,0x71552e], 'texture_size':[width,height], 'parts':shapes, 'parents':parents})
    data(RESOURCES + 'data/techguns/loot_table/entities/supermutantbasic.json', mutant_loot())
    data(RESOURCES + 'assets/techguns/items/supermutantbasic_spawn_egg.json', {'model':{'type':'minecraft:model',
        'model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':0xc6a96b}]}})
    texture = 'assets/techguns/textures/entity/supermutant_texture_1.png'
    files[RESOURCES + texture] = (LEGACY / 'resources' / texture).read_bytes()
    emitted = set(); lines = []
    pending = list(shapes)
    while pending:
        ready = [s for s in pending if s['name'] not in parents or parents[s['name']] in emitted]
        if not ready: raise ValueError('Cyclic NPC model hierarchy')
        for shape in ready:
            name = shape['name']; parent = 'root' if name not in parents else 'part_' + parents[name]
            box = ', '.join(f'{v}f' for v in shape['box'])
            pose = ', '.join(f'{v}f' for v in shape['pivot'] + shape['rotation'])
            lines.append(f'        PartDefinition part_{name} = {parent}.addOrReplaceChild("{NAMES.get(name,name)}", CubeListBuilder.create().texOffs({shape["uv"][0]}, {shape["uv"][1]})' +
                         ('.mirror()' if shape['mirror'] else '') + f'.addBox({box}, new CubeDeformation({shape["inflate"]}f)), PartPose.offsetAndRotation({pose}));')
            emitted.add(name); pending.remove(shape)
    files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/SuperMutantMesh.java'] = ('''package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelSuperMutant by pWn3d; Techguns Mod License. */
public final class SuperMutantMesh {
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
''' + '\n'.join(lines) + f'\n        return LayerDefinition.create(mesh, {width}, {height});\n' + '''    }
    private SuperMutantMesh() {}
}
''').encode()
    return files


def npc_translations(lang):
    return {'entity.techguns.supermutantbasic':'Супермутант' if lang == 'ru_ru' else 'Super Mutant',
            'item.techguns.supermutantbasic_spawn_egg':'Яйцо призыва супермутанта' if lang == 'ru_ru' else 'Super Mutant Spawn Egg'}
