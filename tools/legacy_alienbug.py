"""Original AlienBug geometry, texture, empty loot and six sound families."""
import json
import re
from legacy_models import strip_comments, extract_shapes
from legacy_npcs import LEGACY, RESOURCES

SOUNDS=tuple('npcs.alienbug'+suffix for suffix in ('idle','hurt','death','step','aggro','bite'))

def alienbug_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/AlienBug.java').read_text(encoding='utf-8'))
    model=strip_comments((LEGACY/'java/techguns/client/models/npcs/ModelAlienBug.java').read_text(encoding='utf-8'))
    parents={child:parent for parent,child in re.findall(r'this\.(\w+)\.addChild\(this\.(\w+)\)',model)}
    flat=re.sub(r'this\.\w+\.addChild\(this\.\w+\);','',model).replace('setRotateAngle(', 'setRotation(')
    width,height,parts=extract_shapes(flat,'ModelAlienBug')
    attributes={name:float(value) for name,value in re.findall(r'SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([\d.]+)D\)',source)}
    assert 'LOOT = null;' in source and 'extends EntitySpider' in source
    return {'source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/AlienBug.java',
            'attributes':attributes,'size':[1.1,1.2],'eye_height':.65,'faction':'HOSTILE',
            'retaliation':'vanilla HurtByTarget; GenericNPC allies ignore AlienBug attacks',
            'natural_spawn_entry':False,'loot':[], 'original_egg_colors':[0xc6a96b,0x71552e],
            'attack_timer':10,'attack_reach_squared':'4.0f + target.width',
            'typed_armor':{'PHYSICAL':10,'PROJECTILE':10,'ENERGY':5,'EXPLOSION':5,'ICE':5,'LIGHTNING':5,'POISON':20,'RADIATION':20,'FIRE':0,'UNRESISTABLE':0},
            'sounds':list(SOUNDS),'texture_size':[width,height],'parts':parts,'parents':parents}

def generate_alienbug_content():
    files={}; d=alienbug_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/alienbug.json',d)
    data(RESOURCES+'assets/techguns/items/alienbug_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':d['original_egg_colors'][0]}]}})
    data(RESOURCES+'data/techguns/loot_table/entities/alienbug.json',{'type':'minecraft:entity','pools':[]})
    data(RESOURCES+'data/minecraft/tags/entity_type/arthropod.json',{'replace':False,'values':['techguns:alienbug']})
    texture='assets/techguns/textures/entity/alienbug.png'
    files[RESOURCES+texture]=(LEGACY/'resources'/texture).read_bytes()
    emitted=set(); pending=list(d['parts']); lines=[]
    while pending:
        ready=[s for s in pending if s['name'] not in d['parents'] or d['parents'][s['name']] in emitted]
        if not ready: raise ValueError('Cyclic AlienBug hierarchy')
        for shape in ready:
            name=shape['name']; parent='root' if name not in d['parents'] else 'part_'+d['parents'][name]
            box=', '.join(f'{v}f' for v in shape['box']); pose=', '.join(f'{v}f' for v in shape['pivot']+shape['rotation'])
            lines.append(f'        PartDefinition part_{name}={parent}.addOrReplaceChild("{name}",CubeListBuilder.create().texOffs({shape["uv"][0]},{shape["uv"][1]})'+('.mirror()' if shape['mirror'] else '')+f'.addBox({box}),PartPose.offsetAndRotation({pose}));')
            emitted.add(name); pending.remove(shape)
    files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/AlienBugMesh.java']=('''package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelAlienBug, including the flat h3 plane. Techguns Mod License. */
public final class AlienBugMesh {
    public static LayerDefinition create() {
        var mesh=new MeshDefinition(); var root=mesh.getRoot();
'''+ '\n'.join(lines)+'''
        return LayerDefinition.create(mesh,64,64);
    }
    private AlienBugMesh() {}
}
''').encode()
    return files

def alienbug_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.alienbug':source['entity.techguns.AlienBug.name'],
            'item.techguns.alienbug_spawn_egg':'Яйцо призыва инопланетного жука' if lang=='ru_ru' else 'Alien Bug Spawn Egg'}
