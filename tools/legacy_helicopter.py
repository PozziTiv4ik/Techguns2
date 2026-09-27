"""AttackHelicopter's active AI, dedicated projectile parameters, loot and three source OBJ parts."""
import hashlib
import json
import re
from decimal import Decimal
from legacy_models import strip_comments
from legacy_npcs import LEGACY,RESOURCES,npc_loot

SOUNDS=('npcs.apachehit','npcs.apacherotor','npcs.apacheburst','npcs.apachedie','npcs.apacheexplode')


def helicopter_definition():
    source=strip_comments((LEGACY/'java/techguns/entities/npcs/AttackHelicopter.java').read_text())
    flight=strip_comments((LEGACY/'java/techguns/entities/npcs/GenericFlyingMob.java').read_text())
    renderer=strip_comments((LEGACY/'java/techguns/client/render/entities/npcs/RenderAttackHelicopter.java').read_text())
    health=float(re.search(r'MAX_HEALTH\).setBaseValue\(([^D]+)D\)',source)[1])
    attrs={name.lower():float(value) for name,value in re.findall(r'SharedMonsterAttributes\.(FOLLOW_RANGE|ARMOR|ARMOR_TOUGHNESS)\).setBaseValue\(([\d.]+)',source)}
    bullet=re.search(r'new GenericProjectile\(this.parentEntity.world, this.parentEntity,([^;]+)\);',source)[1].strip()
    rocket=re.search(r'new RocketProjectile\(this.parentEntity.world, this.parentEntity,([^;]+)\);',source)[1].strip()
    assert bullet=='12.0f, 1.0f, 100, 0.05f, 30, 40, 8.0f, 0.25f,false,EnumBulletFirePos.CENTER'
    assert rocket=='12.0f, 1.0f, 100, 0.05f, 30, 40, 8.0f, 0.25f,false,this.parentEntity.rand.nextBoolean()?EnumBulletFirePos.LEFT:EnumBulletFirePos.RIGHT, 4.0f, 0.0f'
    assert 'attackTimer >= 14' in source and 'attackTimer <24' in source and 'attackTimer % 2 == 0' in source and 'attackTimer == 35' in source and 'this.attackTimer=-30' in source
    assert 'new EntityAIFindEntityNearestPlayer(this)' in source and 'groundPoint' in flight and 'nextInt(5) + 2' in flight
    assert 'this.setPosition(this.posX, this.posY-yoffset, this.posZ)' not in source
    stats=[v.strip().removesuffix('f') for v in bullet.split(',')[:8]]
    mesh=[]
    for i in range(3):
        path=LEGACY/f'resources/assets/techguns/models/item/npc/helicopter{i}.obj';raw=path.read_bytes().replace(b'\r\n',b'\n');lines=raw.decode().splitlines()
        positions=[list(map(float,s.split()[1:])) for s in lines if s.startswith('v ')]
        mesh.append({'part':i,'vertices':len(positions),'faces':sum(s.startswith('f ') for s in lines),'uvs':sum(s.startswith('vt ') for s in lines),
                     'source_sha256':hashlib.sha256(raw).hexdigest(),'bounds':[[min(v[a] for v in positions) for a in range(3)],[max(v[a] for v in positions) for a in range(3)]]})
    return {'source':'legacy/1.12.2/src/main/java/techguns/entities/npcs/AttackHelicopter.java','health':health,**attrs,
            'size':[4,4],'eye_height':.5,'xp':5,'fire_immune':True,'faction':'not GenericNPC / ITGNpcTeam','natural_spawn_entry':False,
            'spawn_check_chance':1/20,'max_spawn_group':1,'original_egg_colors':[0x373d23,0x8ec0d7],'tracking_blocks':200,'update_interval':3,
            'flight':{'height_above_current_column':24,'random_xz':16,'waypoint_min_squared':1,'waypoint_max_squared':3600,'course_delay':[2,6],'acceleration':.1,
                      'nearest_player_vertical_inflate':4,'look_range':96,'sneaking_factor':.800000011920929,'invisibility_factor':.7,'minimum_armor_coverage':.1},
            'attack':{'range_squared':4096,'bullets':[14,16,18,20,22],'rocket_tick':35,'rest_timer':-30,'attacking_after':10,'clock_requires_sight':True,
                      'damage':float(stats[0]),'speed':float(stats[1]),'lifetime':int(stats[2]),'spread':float(stats[3]),'drop_start':int(stats[4]),'drop_end':int(stats[5]),'minimum_damage':float(stats[6]),'penetration':float(stats[7]),
                      'block_damage':False,'npc_difficulty_penalty':False,'rocket_blast_radii':[30,40],'unused_rocket_radius_argument':4,'rocket_gravity':0,'muzzle_side':2.16,'muzzle_height':-.1},
            'death':{'ticks':100,'loot_at_death':True,'xp_at_finish':True,'visual_y_offset':-4,'visual_yaw_degrees':1440,'fx_y_offset':-8,'damaging_explosion':False},
            'model':{'scale':2.5,'yaw_offset':-90,'gun_pivot_x':1.2,'rotor_degrees_per_tick':24,'dying_rotor_degrees_per_tick':12,'parts':mesh},'sounds':list(SOUNDS)}


def generate_helicopter_content():
    files={};d=helicopter_definition()
    def data(path,value):files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/attackhelicopter.json',d)
    data(RESOURCES+'data/techguns/loot_table/entities/attackhelicopter.json',npc_loot('attackhelicopter'))
    data(RESOURCES+'assets/techguns/items/attackhelicopter_spawn_egg.json',{'model':{'type':'minecraft:model','model':'minecraft:item/egg','tints':[{'type':'minecraft:constant','value':d['original_egg_colors'][0]}]}})
    texture=(LEGACY/'resources/assets/techguns/textures/entity/apache.png').read_bytes()
    files[RESOURCES+'assets/techguns/textures/item/apache.png']=texture
    files[RESOURCES+'assets/techguns/textures/entity/apache.png']=texture
    for i in range(3):
        name=f'helicopter{i}';raw=(LEGACY/f'resources/assets/techguns/models/item/npc/{name}.obj').read_text();lines=[]
        for line in raw.splitlines():
            # ItemStackRenderState centers models by -.5: offset all three parts equally to retain the original origin.
            if line.startswith('v '):line='v '+' '.join(str(Decimal(v)+Decimal('.5')) for v in line.split()[1:])
            if line.startswith('mtllib '):line='mtllib helicopter.mtl'
            lines.append(line.rstrip())
        files[RESOURCES+f'assets/techguns/models/item/{name}.obj']=('\n'.join(lines)+'\n').encode()
        data(RESOURCES+f'assets/techguns/models/item/{name}.json',{'loader':'neoforge:obj','model':f'techguns:models/item/{name}.obj','automatic_culling':False,'flip_v':True,'emissive_ambient':False,'textures':{'skin':'techguns:item/apache','particle':'techguns:item/apache'},'display':{}})
        data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':f'techguns:item/{name}'}})
    files[RESOURCES+'assets/techguns/models/item/helicopter.mtl']=b'newmtl tex\nKd 1.00 1.00 1.00\nmap_Kd #skin\n'
    a=d['attack']
    files['core/src/main/java/techguns/core/HelicopterWeapons.java']=('''package techguns.core;

/** Generated from AttackHelicopter's two constructors; these are NPC profiles, not obtainable guns. */
public final class HelicopterWeapons {
    public static final WeaponDefinition BULLET=profile("attackhelicopter_bullet",ProjectileKind.BALLISTIC);
    public static final WeaponDefinition ROCKET=profile("attackhelicopter_rocket",ProjectileKind.ROCKET);
    private static WeaponDefinition profile(String id,ProjectileKind kind) {
        return new WeaponDefinition(new WeaponSpec(id,1,1,1,'''+f'{a["damage"]}f,{a["minimum_damage"]}f,{a["drop_start"]},{a["drop_end"]},{a["speed"]},{a["lifetime"]},{a["spread"]}'+'''),
                new AmmoSpec("riflerounds","","",0,true),kind,false,0,0,0,'''+str(a['penetration'])+''',new AimSpec(1,false,1,true),"","");
    }
    public static WeaponDefinition resolve(String id) {
        if(id.equals(BULLET.id())) return BULLET;
        if(id.equals(ROCKET.id())) return ROCKET;
        return Weapons.definition(id);
    }
    private HelicopterWeapons() {}
}
''').encode()
    return files


def helicopter_translations(lang):
    names=dict(s.split('=',1) for s in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in s)
    return {'entity.techguns.attackhelicopter':names['entity.techguns.AttackHelicopter.name'],
            'item.techguns.attackhelicopter_spawn_egg':'Яйцо призыва боевого вертолёта' if lang=='ru_ru' else 'Attack Helicopter Spawn Egg',
            **{f'item.techguns.helicopter{i}':ru if lang=='ru_ru' else en for i,(ru,en) in enumerate((
                ('Корпус боевого вертолёта','Attack Helicopter Body'),('Ротор боевого вертолёта','Attack Helicopter Rotor'),('Пулемёт боевого вертолёта','Attack Helicopter Gun')))}}
