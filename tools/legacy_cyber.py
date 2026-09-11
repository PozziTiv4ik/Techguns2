"""CyberDemon geometry, loot and damage tags from the unchanged 1.12.2 source."""
import json
import re
from legacy_models import strip_comments, extract_shapes
from legacy_npcs import LEGACY, RESOURCES, NAMES, npc_loot


def cyber_geometry():
    source = strip_comments((LEGACY / 'java/techguns/client/models/npcs/ModelCyberDemon.java').read_text())
    parents = dict((child, parent) for parent, child in re.findall(r'this\.(\w+)\.addChild\((\w+)\)', source))
    parents['bipedHeadwear'] = 'bipedHead'
    flat = re.sub(r'this\.\w+\.addChild\(\w+\);', '', source)
    # This model has seven empty animated bones and twenty child cuboids. Unset pivots default to zero.
    starts = list(re.finditer(r'(\w+)\s*=\s*new ModelRenderer\(this,\s*-?\d+,\s*-?\d+\);', flat))
    empty = set()
    for index in range(len(starts)-1, -1, -1):
        match = starts[index]; name = match[1]
        end = starts[index+1].start() if index+1 < len(starts) else flat.index('public void render', match.end())
        chunk = flat[match.end():end]; additions = ''
        if not re.search(re.escape(name) + r'\.setRotationPoint\(', chunk): additions += f'\n{name}.setRotationPoint(0F, 0F, 0F);'
        if not re.search(re.escape(name) + r'\.addBox\(', chunk):
            empty.add(name); additions += f'\n{name}.addBox(0F, 0F, 0F, 0, 0, 0);'
        flat = flat[:match.end()] + additions + flat[match.end():]
    width, height, shapes = extract_shapes(flat, 'ModelCyberDemon')
    for shape in shapes: shape['empty'] = shape['name'] in empty
    return width, height, shapes, parents


def generate_cyber_content():
    files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    width, height, shapes, parents = cyber_geometry()
    data('content/cyberdemon.json', {'source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/CyberDemon.java',
        'weapon':'netherblaster', 'undead':True, 'fire_immune':True, 'natural_spawn_entry':True,
        'nether_pool_weight':300, 'spawn_weight':30, 'other_nether_entries':{'ZombiePigmanSoldier':100},
        'danger_level':0, 'group':[1,3], 'original_egg_colors':[0xff1111,0x777777],
        'texture_size':[width,height], 'parts':shapes, 'parents':parents})
    data(RESOURCES + 'data/techguns/loot_table/entities/cyberdemon.json', npc_loot('cyberdemon'))
    data(RESOURCES + 'data/techguns/neoforge/biome_modifier/nether_npcs.json', {'type':'techguns:nether_npcs'})
    data(RESOURCES + 'assets/techguns/items/cyberdemon_spawn_egg.json', {'model':{'type':'minecraft:model',
        'model':'minecraft:item/egg', 'tints':[{'type':'minecraft:constant','value':0xff1111}]}})
    texture = 'assets/techguns/textures/entity/cyberdemon.png'
    files[RESOURCES + texture] = (LEGACY / 'resources' / texture).read_bytes()
    for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
        data(RESOURCES + f'data/minecraft/tags/entity_type/{tag}.json', {'replace':False,'values':['techguns:cyberdemon']})
    # Original FIRE is flagged as magic, deliberately not vanilla fire: fire resistance must not grant immunity.
    for namespace, tag in (('minecraft','bypasses_cooldown'),('minecraft','witch_resistant_to'),('neoforge','is_magic')):
        data(RESOURCES + f'data/{namespace}/tags/damage_type/{tag}.json', {'replace':False,'values':['techguns:nether_blast']})
    data(RESOURCES + 'data/techguns/damage_type/nether_blast.json',
         {'message_id':'techguns.nether_blast','scaling':'when_caused_by_living_non_player','exhaustion':.1})
    emitted = set(); lines = []; pending = list(shapes)
    while pending:
        ready = [s for s in pending if s['name'] not in parents or parents[s['name']] in emitted]
        if not ready: raise ValueError('Cyclic CyberDemon model')
        for shape in ready:
            name = shape['name']; parent = 'root' if name not in parents else 'part_' + parents[name]
            cube = 'CubeListBuilder.create()'
            if not shape['empty']:
                box = ', '.join(f'{v}f' for v in shape['box'])
                cube += f'.texOffs({shape["uv"][0]}, {shape["uv"][1]})' + ('.mirror()' if shape['mirror'] else '') + f'.addBox({box})'
            pose = ', '.join(f'{v}f' for v in shape['pivot'] + shape['rotation'])
            lines.append(f'        PartDefinition part_{name} = {parent}.addOrReplaceChild("{NAMES.get(name,name)}", {cube}, PartPose.offsetAndRotation({pose}));')
            pending.remove(shape); emitted.add(name)
    files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/CyberDemonMesh.java'] = ('''package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelCyberDemon; Techguns Mod License. */
public final class CyberDemonMesh {
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
''' + '\n'.join(lines) + f'\n        return LayerDefinition.create(mesh, {width}, {height});\n' + '''    }
    private CyberDemonMesh() {}
}
''').encode()
    return files


def cyber_translations(lang):
    return {'entity.techguns.cyberdemon':'Кибердемон' if lang == 'ru_ru' else 'Cyber Demon',
        'item.techguns.cyberdemon_spawn_egg':'Яйцо призыва кибердемона' if lang == 'ru_ru' else 'Cyber Demon Spawn Egg',
        'entity.techguns.nether_blast':'Заряд адского бластера' if lang == 'ru_ru' else 'Nether Blaster Charge',
        'death.attack.techguns.nether_blast':'%1$s испепелён зарядом %2$s' if lang == 'ru_ru' else '%1$s was incinerated by %2$s',
        'death.attack.techguns.nether_blast.player':'%1$s испепелён зарядом %2$s' if lang == 'ru_ru' else '%1$s was incinerated by %2$s',
        'death.attack.techguns.nether_blast.item':'%1$s испепелён %2$s с помощью %3$s' if lang == 'ru_ru' else '%1$s was incinerated by %2$s using %3$s'}
