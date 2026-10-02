"""AdvancedBulletProjectile's active GenericProjectile flight/damage and material impact sounds."""
import json
import re
from legacy_items import LEGACY, arguments
from legacy_models import strip_comments, numeric

RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def advanced_parameters():
    advanced = strip_comments((LEGACY / 'java/techguns/entities/projectiles/AdvancedBulletProjectile.java').read_text())
    generic = strip_comments((LEGACY / 'java/techguns/entities/projectiles/GenericProjectile.java').read_text())
    if 'extends GenericProjectile' not in advanced or re.search(r'void (onUpdate|onHit|hitBlock)\(|getProjectileDamageSource\(', advanced):
        raise ValueError('Review changed Advanced projectile inheritance')
    if arguments(re.search(r'return new AdvancedBulletProjectile\(([^;]+)\)', advanced)[1]) != [
            'world', 'p', 'damage', 'speed', 'TTL', 'spread', 'dmgDropStart', 'dmgDropEnd', 'dmgMin', 'penetration', 'blockdamage', 'firePos']:
        raise ValueError('Review changed Advanced factory')
    for contract in ('causeBulletDamage(this, this.shooter, DeathType.GORE)', 'src.setNoKnockback();', 'return start.distanceTo(end);'):
        if contract not in generic: raise ValueError('Review changed generic projectile contract: ' + contract)
    if not generic.index('this.initStartPos();') < generic.index('this.onHit(raytraceresult);') < generic.index('this.posX += this.motionX;'):
        raise ValueError('Review changed impact/displacement ordering')
    fields = list(dict.fromkeys(re.findall(r'TGSounds\.(BULLET_IMPACT_\w+)', advanced)))
    sounds = strip_comments((LEGACY / 'java/techguns/TGSounds.java').read_text())
    events = {key:re.search(key+r'\s*=\s*createSoundEvent\("([^"]+)"', sounds)[1].lower() for key in fields}
    renderer = strip_comments((LEGACY / 'java/techguns/client/render/entities/projectiles/RenderAdvancedBulletProjectile.java').read_text())
    scale = numeric(re.search(r'float f10 = ([\d.]+F);', renderer)[1])
    render = {'texture':re.search(r'textureLoc = new ResourceLocation\(Techguns.MODID,"([^"]+)"', renderer)[1],
              'half_length':numeric(re.search(r'double length = ([\d.]+D);', renderer)[1])*scale,
              'half_width':numeric(re.search(r'double width = ([\d.]+D);', renderer)[1])*scale,
              'delay_factor':numeric(re.search(r'float delay = ([\d.]+f) /', renderer)[1])}
    return {'air_drag': numeric(re.search(r'float f1 = ([\d.]+F);', generic)[1]),
            'water_drag': numeric(re.search(r'f1 = ([\d.]+F);', generic.split('protected float inWaterUpdateBehaviour',1)[1])[1]),
            'gravity':0, 'impact_sounds':events, 'render':render,
            'damage':'PROJECTILE with penetration; ordinary 0.01 PHYSICAL impulse, main hit bypasses cooldown and knockback',
            'falloff':'displacement from first-tick origin, evaluated before movement on the impact tick',
            'block_impact':'material sound and blue FX only, no explosion, ignition or destruction'}


def generate_advanced_content():
    p = advanced_parameters(); files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode()
    data('content/advanced-projectile.json', {'source':'AdvancedBulletProjectile -> GenericProjectile', **p,
         'pending':['Complete original impact/muzzle FX, poses/recoil animation, dynamic light and GPU acceptance']})
    data(RESOURCES+'data/techguns/damage_type/advanced_bullet.json', {'message_id':'techguns.advanced_bullet','scaling':'when_caused_by_living_non_player','exhaustion':.1})
    for tag in ('is_projectile','bypasses_cooldown','no_knockback'):
        data(RESOURCES+f'data/minecraft/tags/damage_type/{tag}.json', {'replace':False,'values':['techguns:advanced_bullet']})
    files[RESOURCES+'assets/techguns/'+p['render']['texture']] = (LEGACY/'resources/assets/techguns'/p['render']['texture']).read_bytes()
    files['core/src/main/java/techguns/core/AdvancedBulletRules.java'] = ('''package techguns.core;

/** Generated from AdvancedBulletProjectile and GenericProjectile. */
public final class AdvancedBulletRules {
    public static final float AIR_DRAG = '''+str(p['air_drag'])+'''f, WATER_DRAG = '''+str(p['water_drag'])+'''f;
''' + ''.join('    public static final String '+k+' = "'+v+'";\n' for k,v in p['impact_sounds'].items()) + ''.join(
        '    public static final double '+key.upper()+' = '+str(value)+';\n' for key,value in p['render'].items() if key != 'texture') + '''    private AdvancedBulletRules() {}
}
''').encode()
    return files


def advanced_translations(lang):
    ru = lang == 'ru_ru'
    return {'entity.techguns.advanced_bullet':'Усовершенствованная пуля' if ru else 'Advanced bullet',
            'tooltip.techguns.gun.camo':'Камуфляж: %s' if ru else 'Camouflage: %s',
            **{'death.attack.techguns.advanced_bullet'+s:('%1$s застрелен игроком %2$s' if ru else '%1$s was shot by %2$s') for s in ('','.player')},
            'death.attack.techguns.advanced_bullet.item':'%1$s застрелен игроком %2$s с помощью %3$s' if ru else '%1$s was shot by %2$s using %3$s'}
