"""ArmySoldier and ModelBeret, using the original declarations, geometry, loot and pixels."""
import json
import re
import struct
from legacy_models import strip_comments, numeric, extract_shapes
from legacy_npcs import LEGACY, RESOURCES, npc_loot


def army_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/ArmySoldier.java').read_text())
    render=(LEGACY/'java/techguns/client/render/entities/npcs/RenderArmySoldier.java').read_text()
    texture=re.search(r'"(textures/entity/[^"]+)"',render)[1]
    entities=(LEGACY/'java/techguns/TGEntities.java').read_text()
    colors=re.search(r'registerModEntityWithEgg\(ArmySoldier.class, "ArmySoldier",\s*(0x\w+),\s*(0x\w+)\)',entities)
    return {'id':'armysoldier','source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/ArmySoldier.java',
            'attributes':{k:numeric(v) for k,v in re.findall(r'getEntityAttribute\(SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([^)]+)\)',source)},
            'intrinsic_armor':numeric(re.search(r'setTGArmorStats\(([^,]+),',source)[1]),
            'weapons':re.findall(r'weapon = TGuns\.(\w+)',source),
            'armor_chance':numeric(re.search(r'double chance = ([^;]+);',source)[1]),
            'head_fallback':'t2_beret','armor':'T2_COMBAT','camo':0,
            'undead':False,'burns_in_daylight':False,'fire_immune':False,'natural_spawn_entry':False,'faction':'HOSTILE',
            'loot_id':'entities/armysoldier','texture':texture,
            'texture_size':list(struct.unpack('>II',(LEGACY/'resources/assets/techguns'/texture).read_bytes()[16:24])),
            'model':'ModelGenericNPC; shared modern humanoid renderer with source skin',
            'sounds':{'ambient':'minecraft:entity.villager.ambient','hurt':'minecraft:entity.villager.hurt','death':'minecraft:entity.villager.death','step':'minecraft:entity.zombie_villager.step'},
            'egg_colors':[int(colors[i],16) for i in (1,2)]}


def beret_geometry():
    source=strip_comments((LEGACY/'java/techguns/client/models/armor/ModelBeret.java').read_text())
    parents=dict((child,parent) for parent,child in re.findall(r'this\.(\w+)\.addChild\((\w+)\)',source))
    if parents != {'berettop':'bipedHead','beretside':'bipedHead'}: raise ValueError('Unexpected beret hierarchy')
    flat=re.sub(r'this\.\w+\.addChild\(\w+\);','',source).replace('setRotateAngle(', 'setRotation(')
    width,height,shapes=extract_shapes(flat,'ModelBeret')
    return width,height,shapes


def generate_army_content():
    files={}; definition=army_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/armysoldier.json',definition)
    data(RESOURCES+'data/techguns/loot_table/entities/armysoldier.json',npc_loot('armysoldier'))
    data(RESOURCES+'assets/techguns/items/armysoldier_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':definition['egg_colors'][0]}]}})
    texture='assets/techguns/'+definition['texture']; files[RESOURCES+texture]=(LEGACY/'resources'/texture).read_bytes()
    width,height,shapes=beret_geometry()
    data('content/beret-model.json',{'source':'legacy/1.12.2/src/main/java/techguns/client/models/armor/ModelBeret.java','texture_size':[width,height],'parts':shapes})
    lines=[]
    for s in shapes:
        box=', '.join(f'{v}f' for v in s['box']); pose=', '.join(f'{v}f' for v in s['pivot']+s['rotation'])
        lines.append(f'        head.addOrReplaceChild("{s["name"]}", CubeListBuilder.create().texOffs({s["uv"][0]}, {s["uv"][1]}).addBox({box}, new CubeDeformation({s["inflate"]}f)), PartPose.offsetAndRotation({pose}));')
    files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/BeretMesh.java']=('''package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelBeret by pWn3d; Techguns Mod License. */
public final class BeretMesh {
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        for (String part : new String[]{"body", "right_arm", "left_arm", "right_leg", "left_leg"})
            root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
'''+'\n'.join(lines)+f'\n        return LayerDefinition.create(mesh, {width}, {height});\n'+'''    }
    private BeretMesh() {}
}
''').encode()
    return files


def army_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.armysoldier':source['entity.techguns.ArmySoldier.name'].strip(),
            'item.techguns.armysoldier_spawn_egg':'Яйцо призыва военного' if lang=='ru_ru' else 'Army Soldier Spawn Egg'}
