"""AlienBlasterProjectile's shared behavior and the registered Ghastling's independent factory."""
import json
import re
from legacy_items import LEGACY, arguments
from legacy_models import strip_comments, numeric


def alien_parameters():
    source = strip_comments((LEGACY / 'java/techguns/entities/projectiles/AlienBlasterProjectile.java').read_text())
    generic = strip_comments((LEGACY / 'java/techguns/entities/projectiles/GenericProjectile.java').read_text())
    if 'extends GenericProjectile' not in source or re.search(r'void onUpdate\(|inWaterUpdateBehaviour\(', source):
        raise ValueError('Review changed Alien flight inheritance')
    factory = arguments(re.search(r'return new AlienBlasterProjectile\(([^;]+)\)', source)[1])
    if factory != ['world', 'p', 'damage', 'speed', 'TTL', 'spread', 'dmgDropStart', 'dmgDropEnd', 'dmgMin', 'penetration', 'blockdamage', 'firePos']:
        raise ValueError('Review changed Alien weapon factory')
    for contract in ('causeFireDamage(this, this.shooter, DeathType.LASER)', 'src.armorPenetration = this.penetration;',
                     'ent.setFire(ENTITY_IGNITE_TIME);', 'return DamageType.FIRE;', 'super.hitBlock(raytraceResultIn);'):
        if contract not in source: raise ValueError('Review changed Alien impact: ' + contract)
    if 'setNoKnockback' in source or 'newExplosion' in source or 'createExplosion' in source:
        raise ValueError('Review changed Alien knockback or explosion')
    if 'if (Math.random() <= chanceToIgnite)' not in generic or 'hit.offset(mop.sideHit)' not in generic:
        raise ValueError('Review changed GenericProjectile burnBlocks')
    chance = numeric(re.search(r'if\s*\(this.blockdamage\)\s*\{\s*burnBlocks\(world, raytraceResultIn, ([\d.]+)\);', source)[1])
    return {'ignite_seconds': int(re.search(r'ENTITY_IGNITE_TIME\s*=\s*(\d+)', source)[1]),
            'block_ignite_chance': chance, 'air_drag': numeric(re.search(r'float f1 = ([\d.]+F);', generic)[1]),
            'water_drag': numeric(re.search(r'f1 = ([\d.]+F);', generic.split('protected float inWaterUpdateBehaviour', 1)[1])[1]),
            'gravity': 0, 'damage': 'FIRE / magic, ordinary knockback, no separate dummy impulse',
            'water': 'GenericProjectile water drag and bubbles; extinguish projectile when wet, keep flying',
            'block_impact': 'AlienExplosion FX only; burn adjacent air when blockdamage is allowed; no explosion'}


def ghastling_projectile():
    source = strip_comments((LEGACY / 'java/techguns/entities/npcs/Ghastling.java').read_text())
    if 'new AIFireballAttack(this)' not in source: raise ValueError('Review changed registered Ghastling goal')
    attack = source.split('protected static class AIFireballAttack', 1)[1]
    args = arguments(re.search(r'new AlienBlasterProjectile\(([^;]+)\);', attack)[1])
    if len(args) != 12 or args[:2] != ['this.parentEntity.world', 'parentEntity'] or args[-1] != 'EnumBulletFirePos.CENTER':
        raise ValueError('Review changed Ghastling projectile factory')
    damage, speed, lifetime, spread, drop_start, drop_end, minimum, penetration = map(numeric, args[2:10])
    if damage != minimum or penetration != 0 or args[10] != 'false': raise ValueError('Review changed Ghastling damage / block profile')
    return {'damage': damage, 'speed': speed, 'spread': spread, 'lifetime': int(lifetime),
            'ignite_seconds': alien_parameters()['ignite_seconds'], 'block_damage': False, 'kind': 'FIRE'}


def generate_alien_content():
    params = alien_parameters(); ghast = ghastling_projectile()
    catalog = {'sources': ['legacy/1.12.2/src/main/java/techguns/entities/projectiles/AlienBlasterProjectile.java',
                           'legacy/1.12.2/src/main/java/techguns/entities/projectiles/GenericProjectile.java',
                           'legacy/1.12.2/src/main/java/techguns/entities/npcs/Ghastling.java'],
               **params, 'ghastling_projectile': ghast,
               'weapon_profile': 'TGuns / GenericGun: see alien-blaster-weapon.json; TTL is ceil(range / speed)',
               'recipes': 'No source workbench or Grinder recipe for alienblaster',
               'pending': ['Original AlienBlasterTrail/AlienExplosion, muzzle FX, dynamic light, recoil/crosshair and GPU acceptance']}
    java = '''package techguns.core;

/** Generated from AlienBlasterProjectile, GenericProjectile and the registered Ghastling factory. */
public final class AlienBlasterRules {
    public static final int IGNITE_SECONDS = %d;
    public static final double BLOCK_IGNITE_CHANCE = %s;
    public static final float AIR_DRAG = %sf, WATER_DRAG = %sf;
    public static final float GHASTLING_DAMAGE = %sf;
    public static final double GHASTLING_SPEED = %s, GHASTLING_SPREAD = %s;
    public static final int GHASTLING_LIFETIME = %d;
    public static boolean ignites(double roll) { return roll >= 0 && roll <= BLOCK_IGNITE_CHANCE; }
    private AlienBlasterRules() {}
}
''' % (params['ignite_seconds'], params['block_ignite_chance'], params['air_drag'], params['water_drag'],
       ghast['damage'], ghast['speed'], ghast['spread'], ghast['lifetime'])
    return {'content/alien-blaster-behavior.json': (json.dumps(catalog, ensure_ascii=False, indent=2) + '\n').encode(),
            'core/src/main/java/techguns/core/AlienBlasterRules.java': java.encode()}
