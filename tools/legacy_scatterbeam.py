"""Active BlasterProjectile overrides and GenericGun's launch-time TTL scaling."""
import json
import math
import re
from legacy_items import LEGACY, arguments
from legacy_models import strip_comments, numeric

RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def scaled_projectile_lifetime(lifetime, speed):
    gun = strip_comments((LEGACY / 'java/techguns/items/guns/GenericGun.java').read_text())
    if not re.search(r'getScaledTTL\(\)\s*\{\s*return \(int\) Math.ceil\(this.ticksToLive/this.speed\);', gun):
        raise ValueError('Review changed GenericGun lifetime scaling')
    if 'speed, this.getScaledTTL(), spread' not in gun:
        raise ValueError('Review changed projectile factory TTL argument')
    return math.ceil(lifetime / speed)


def blaster_parameters():
    source = strip_comments((LEGACY / 'java/techguns/entities/projectiles/BlasterProjectile.java').read_text())
    generic = strip_comments((LEGACY / 'java/techguns/entities/projectiles/GenericProjectile.java').read_text())
    if 'extends GenericProjectile' not in source or re.search(r'void (onUpdate|onHit|onHitEffect)\(', source):
        raise ValueError('Review changed Blaster flight/impact inheritance')
    factory = arguments(re.search(r'return new BlasterProjectile\(([^;]+)\)', source)[1])
    if factory != ['world', 'p', 'damage', 'speed', 'TTL', 'spread', 'dmgDropStart', 'dmgDropEnd', 'dmgMin', 'penetration', 'blockdamage', 'firePos']:
        raise ValueError('Review changed Blaster factory')
    water = re.search(r'inWaterUpdateBehaviour\(float f1\)\s*\{(.*?)return f1;', source, re.S)[1]
    if re.sub(r'\s+', '', water) != 'if(this.isWet()){this.extinguish();}':
        raise ValueError('Review changed Blaster water behavior')
    for contract in ('causeEnergyDamage(this, this.shooter, DeathType.LASER)', 'src.armorPenetration = this.penetration;', 'src.setNoKnockback();', 'return DamageType.ENERGY;'):
        if contract not in source: raise ValueError('Review changed Blaster damage source: ' + contract)
    block = re.search(r'void hitBlock\(RayTraceResult rayTraceResult\)\s*\{(.*?)\}', source, re.S)[1]
    if re.sub(r'\s+', '', block) != 'Techguns.proxy.createFX("LaserGunImpact",world,rayTraceResult.hitVec.x,rayTraceResult.hitVec.y,rayTraceResult.hitVec.z,0,0,0);':
        raise ValueError('Review changed Blaster block behavior')
    return {'drag': numeric(re.search(r'float f1 = (0\.99F);', generic)[1]), 'gravity': 0,
            'water': 'same drag as air; extinguish when wet; no bubbles',
            'damage': 'ENERGY / magic, armor retained, separate 0.01 PHYSICAL impulse; main hit bypasses cooldown and knockback',
            'block_impact': 'visual LaserGunImpact only; no explosion, ignition or block destruction'}


def generate_scatterbeam_content():
    params = blaster_parameters(); files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    data('content/scatterbeam-behavior.json', {
        'sources': ['legacy/1.12.2/src/main/java/techguns/entities/projectiles/BlasterProjectile.java',
                    'legacy/1.12.2/src/main/java/techguns/entities/projectiles/GenericProjectile.java',
                    'legacy/1.12.2/src/main/java/techguns/items/guns/GenericGun.java'], **params,
        'flight': 'LegacyShot initial 1.5 * speed; swept collision, movement, drag, then TTL expiration',
        'lifetime': 'ceil(TGuns ticksToLive / final setBulletSpeed), 30 / 2 = 15 for Scatterbeam',
        'recipes': 'No source workbench or Grinder recipe for scatterbeamrifle; blasterrifle is a separate weapon',
        'pending': ['Original muzzle/impact FX, dynamic light, recoil/crosshair and GPU acceptance']})
    data(RESOURCES + 'data/techguns/damage_type/blaster.json',
         {'message_id': 'techguns.blaster', 'scaling': 'when_caused_by_living_non_player', 'exhaustion': .1})
    for namespace, tag in (('minecraft', 'bypasses_cooldown'), ('minecraft', 'no_knockback'),
                           ('minecraft', 'witch_resistant_to'), ('neoforge', 'is_magic')):
        data(RESOURCES + f'data/{namespace}/tags/damage_type/{tag}.json', {'replace': False, 'values': ['techguns:blaster']})
    files['core/src/main/java/techguns/core/BlasterRules.java'] = ('''package techguns.core;

/** Generated from BlasterProjectile / GenericProjectile; water preserves ordinary air drag. */
public final class BlasterRules {
    public static final float DRAG = ''' + str(params['drag']) + '''f;
    private BlasterRules() {}
}
''').encode()
    return files


def blaster_translations(lang):
    ru = lang == 'ru_ru'
    result = {'entity.techguns.blaster': 'Энергетический снаряд' if ru else 'Blaster projectile'}
    for suffix in ('', '.player'):
        result['death.attack.techguns.blaster' + suffix] = '%1$s убит бластером игрока %2$s' if ru else '%1$s was blasted by %2$s'
    result['death.attack.techguns.blaster.item'] = '%1$s убит игроком %2$s с помощью %3$s' if ru else '%1$s was blasted by %2$s using %3$s'
    return result
