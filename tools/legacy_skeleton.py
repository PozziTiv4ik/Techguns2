"""SkeletonSoldier source equipment, loot and thin-limb model; vanilla skin is referenced, not copied."""
import json
import re
from legacy_npcs import LEGACY, RESOURCES, NAMES, npc_loot
from legacy_models import strip_comments, numeric


def skeleton_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/SkeletonSoldier.java').read_text())
    model=strip_comments((LEGACY/'java/techguns/client/models/npcs/ModelSkeletonSoldier.java').read_text())
    constructors=list(re.finditer(r'this\.(\w+) = new ModelRenderer\(this,\s*([^)]*)\);',model))
    limbs=[]
    for i,match in enumerate(constructors):
        name=match[1]; body=model[match.end():constructors[i+1].start() if i+1<len(constructors) else len(model)]
        box=re.search(r'\.addBox\(([^)]*)\)',body)[1].split(',')
        assert box[-1].strip()=='f'
        limbs.append({'name':NAMES[name],'uv':[int(v) for v in match[2].split(',')],
                      'box':[numeric(v.strip()) for v in box[:-1]],
                      'pivot':[numeric(v.strip()) for v in re.search(r'\.setRotationPoint\(([^)]*)\)',body)[1].split(',')],
                      'mirror':'.mirror = true' in body})
    render=(LEGACY/'java/techguns/client/render/entities/npcs/RenderSkeletonSoldier.java').read_text()
    texture=re.search(r'new ResourceLocation\("([^"]+)"\)',render)[1]
    entities=(LEGACY/'java/techguns/TGEntities.java').read_text()
    colors=re.search(r'registerModEntityWithEgg\(SkeletonSoldier.class, "SkeletonSoldier",(0x\w+),(0x\w+)\)',entities)
    return {'id':'skeletonsoldier','source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/SkeletonSoldier.java',
            'attributes':{k:numeric(v) for k,v in re.findall(r'getEntityAttribute\(SharedMonsterAttributes\.(\w+)\)\.setBaseValue\(([^)]+)\)',source)},
            'weapons':re.findall(r'weapon = TGuns\.(\w+);',source),'weapon_roll_bound':int(re.search(r'r.nextInt\((\d+)\)',source)[1]),
            'unreachable_default':'minecraft:stone_shovel','always_equipped':['HEAD','FEET'],'empty_slots':['CHEST','LEGS'],
            'armor_choices':['T1_SCOUT','T1_COMBAT'],'independent_scout_chance':.5,'equipment_camo':0,
            'intrinsic_armor':0,'height':numeric(re.search(r'this.height=([^;]+)',source)[1]),'danger_level':1,'spawn_weight':100,
            'burns_in_daylight':True,'helmet_prevents_sun_ignition':False,'custom_techguns_spawner_exemption':'Pending with the custom spawner/link system',
            'texture':'minecraft:'+texture,'texture_size':[64,32],'body_inflation':numeric(re.search(r'this\(([^)]+)\)',model)[1]),
            'armor_inflations':[numeric(v) for v in re.findall(r'new ModelSkeletonSoldier\(([^)]+)\)',render)],'limbs':limbs,
            'held_item_offsets':[numeric(re.search(r'getWeaponPos'+axis+r'\(\)\s*\{\s*return ([^;]+)',source)[1]) for axis in ('X','Y')],
            'offset_scope':'Held-item render only; mirror X in the left hand. Projectile origin is unchanged.',
            'egg_colors':[int(colors[i],16) for i in (1,2)]}


def generate_skeleton_content():
    files={}; definition=skeleton_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/skeletonsoldier.json',definition)
    data(RESOURCES+'data/techguns/loot_table/entities/skeletonsoldier.json',npc_loot('skeletonsoldier'))
    data(RESOURCES+'assets/techguns/items/skeletonsoldier_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':definition['egg_colors'][0]}]}})
    for tag in ('undead','ignores_poison_and_regen','inverted_healing_and_harm','sensitive_to_smite'):
        data(RESOURCES+f'data/minecraft/tags/entity_type/{tag}.json',{'replace':False,'values':['techguns:skeletonsoldier']})
    lines=[]
    for limb in definition['limbs']:
        box=', '.join(f'{v}f' for v in limb['box']); pivot=', '.join(f'{v}f' for v in limb['pivot'])
        lines.append(f'        root.addOrReplaceChild("{limb["name"]}", CubeListBuilder.create().texOffs({limb["uv"][0]}, {limb["uv"][1]})'+('.mirror()' if limb['mirror'] else '')+f'.addBox({box}, deformation), PartPose.offset({pivot}));')
    files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/SkeletonSoldierMesh.java']=('''package techguns.modern.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelSkeletonSoldier; Techguns license and attribution retained. */
public final class SkeletonSoldierMesh {
    public static LayerDefinition create(float inflation) {
        var deformation=new CubeDeformation(inflation);
        var mesh=HumanoidModel.createMesh(deformation,0); var root=mesh.getRoot();
'''+ '\n'.join(lines)+'''
        return LayerDefinition.create(mesh,64,32);
    }
    private SkeletonSoldierMesh() {}
}
''').encode()
    return files


def skeleton_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.skeletonsoldier':source['entity.techguns.SkeletonSoldier.name'],
            'item.techguns.skeletonsoldier_spawn_egg':'Яйцо призыва скелета-солдата' if lang=='ru_ru' else 'Skeleton Soldier Spawn Egg'}
