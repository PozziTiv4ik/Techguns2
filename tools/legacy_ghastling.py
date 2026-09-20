"""Registered Ghastling AI (not the unused AIGhastlingAttack), vanilla textures and original model."""
import json
import re
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES, npc_loot


def ghastling_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/Ghastling.java').read_text())
    model=strip_comments((LEGACY/'java/techguns/client/models/npcs/ModelGhastling.java').read_text())
    assert 'new AIFireballAttack(this)' in source and 'new AIGhastlingAttack(this)' not in source
    seed=int(re.search(r'new Random\((\d+)L\)',model)[1]); state=(seed^0x5DEECE66D)&((1<<48)-1)
    lengths=[]
    for i in range(9):
        while True:
            state=(state*0x5DEECE66D+0xB)&((1<<48)-1); bits=state>>17; value=bits%7
            if bits-value+6<1<<31: break
        lengths.append(value+8)
    body={'name':'body','uv':[0,0],'box':[-8,-8,-8,16,16,16],'pivot':[0,8,0]}
    parts=[body]+[{'name':'tentacle'+str(i),'uv':[0,0],'box':[-1,0,-1,2,n,2],
                  'pivot':[((i%3-(i//3%2)*.5+.25)/2*2-1)*5,15,(i//3/2*2-1)*5]} for i,n in enumerate(lengths)]
    attack=source.split('protected static class AIFireballAttack',1)[1]
    shot=re.search(r'new AlienBlasterProjectile\(this.parentEntity.world, parentEntity, ([^;]+)\);',attack)[1]
    assert shot=='6, 1.5f, 200, 0.05f, 200, 200, 6, 0, false, EnumBulletFirePos.CENTER'
    return {'source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/Ghastling.java',
            'base':'EntityMob; ground navigation, no flying or slow-fall behavior','health':20,'movement_speed':.7,
            'attack_damage':2,'follow_range':64,'armor_attribute':10,'typed_armor':0,'toughness':0,
            'size':[1,2.1],'eye_height':1.5,'xp':10,'fire_immune':True,'undead':False,'natural_spawn_entry':False,
            'faction':'not GenericNPC / ITGNpcTeam','original_egg_colors':[0xaeaeae,0xce81ff],
            'registered_goal':'AIFireballAttack','warmup':30,'burst_shots':3,'burst_interval':6,'rest':50,'melee_interval':20,
            'line_of_sight_in_attack_goal':False,'projectile':{'damage':6,'speed':1.5,'spread':.05,'lifetime':200,'ignite_seconds':3,'block_damage':False,'kind':'FIRE'},
            'model':{'seed':seed,'texture_size':[64,32],'translation':[0,-.6,0],'parts':parts,'tentacle_animation':'.2*sin(age*.3+index)+.4'},
            'textures':['minecraft:textures/entity/ghast/ghast.png','minecraft:textures/entity/ghast/ghast_shooting.png']}


def generate_ghastling_content():
    files={}; d=ghastling_definition()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/ghastling.json',d)
    data(RESOURCES+'assets/techguns/items/ghastling_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':d['original_egg_colors'][0]}]}})
    data(RESOURCES+'data/techguns/loot_table/entities/ghastling.json',npc_loot('ghastling'))
    data(RESOURCES+'data/techguns/damage_type/alien_blast.json',{'message_id':'techguns.alien_blast','scaling':'when_caused_by_living_non_player','exhaustion':.1})
    for namespace,tag in (('minecraft','bypasses_cooldown'),('minecraft','witch_resistant_to'),('neoforge','is_magic')):
        data(RESOURCES+f'data/{namespace}/tags/damage_type/{tag}.json',{'replace':False,'values':['techguns:alien_blast']})
    lines=[]
    for part in d['model']['parts']:
        box=', '.join(str(float(n))+'f' for n in part['box']); x,y,z=part['pivot']; y=round(y-9.6,6)
        lines.append(f'        root.addOrReplaceChild("{part["name"]}",CubeListBuilder.create().texOffs(0,0).addBox({box}),PartPose.offset({float(x)}f,{y}f,{float(z)}f));')
    files['platforms/neoforge-26.2/src/main/java/techguns/modern/client/GhastlingMesh.java']=('''package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelGhastling; -0.6 model translation is folded into every pivot. Techguns Mod License. */
public final class GhastlingMesh {
    public static LayerDefinition create() {
        var mesh=new MeshDefinition(); var root=mesh.getRoot();
'''+ '\n'.join(lines)+'''
        return LayerDefinition.create(mesh,64,32);
    }
    private GhastlingMesh() {}
}
''').encode()
    return files


def ghastling_translations(lang):
    names=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {'entity.techguns.ghastling':names['entity.techguns.Ghastling.name'],
            'item.techguns.ghastling_spawn_egg':'Яйцо призыва гастёныша' if lang=='ru_ru' else 'Ghastling Spawn Egg',
            'entity.techguns.alien_blast':'Зажигательный заряд гастёныша' if lang=='ru_ru' else 'Ghastling Incendiary Charge',
            **{'death.attack.techguns.alien_blast'+suffix:('%1$s испепелён зарядом %2$s' if lang=='ru_ru' else '%1$s was incinerated by %2$s') for suffix in ('','.player','.item')}}
