"""AS50's active GenericProjectileExplosive branch: vanilla Explosion, not TGExplosion."""
import json
import re
from legacy_items import LEGACY, shared_fields, arguments
from legacy_models import strip_comments, numeric
from legacy_repair import RESOURCES


def explosive_family():
    source = strip_comments((LEGACY / 'java/techguns/items/guns/ammo/AmmoTypes.java').read_text(encoding='utf-8'))
    fields = shared_fields()
    variants = re.findall(r'(\w+)\.addVariant\(TYPE_EXPLOSIVE,\s*([^;]+)\);', source)
    if len(variants) != 1: raise ValueError('Review additional explosive ammunition families')
    family, args = variants[0]
    base = arguments(re.search(r'\b' + family + r'\s*=\s*new AmmoType\(([^;]+)\);', source)[1])
    variant = arguments(args)
    if len(base) != 4 or len(variant) != 2: raise ValueError('Review explosive magazine contract')
    item = lambda field: fields[field.removeprefix('TGItems.')]
    return {'family': family, 'default': item(base[0]), 'item': item(variant[0]),
            'empty': item(base[1]), 'loose': item(variant[1]), 'bundles': int(base[3])}


def explosive_for(gun):
    family = explosive_family()
    return family if gun['projectile'] == 'ballistic' and gun['ammo']['item'] == family['default'] else None


def explosive_parameters():
    source = strip_comments((LEGACY / 'java/techguns/entities/projectiles/GenericProjectileExplosive.java').read_text(encoding='utf-8'))
    modifier = re.search(r'new DamageModifier\(\)\.setDmg\(([^,]+),\s*([^\)]+)\)', source)
    explosion = arguments(re.search(r'new Explosion\(([^;]+)\);', source)[1])
    if numeric(modifier[2]) != 0 or explosion[:5] != ['world', 'this', 'x', 'y', 'z'] or explosion[6:] != ['false', 'this.blockdamage']:
        raise ValueError('Review changed explosive bullet factory')
    if 'new TGExplosion(' in source: raise ValueError('Review newly active TGExplosion branch')
    guns = strip_comments((LEGACY / 'java/techguns/TGuns.java').read_text(encoding='utf-8'))
    if not re.search(r'SNIPER_ROUNDS\s*=\s*\{GENERIC_PROJECTILE,\s*INCENDIARY_ROUNDS,\s*new GenericProjectileExplosive.Factory\(\)\}', guns):
        raise ValueError('Review sniper projectile selector order')
    return {'damage_multiplier': numeric(modifier[1]), 'explosion_power': numeric(explosion[5]),
            'direct_knockback': numeric(re.search(r'src.knockbackMultiplier\s*=\s*([^;]+)', source)[1])}


def generate_explosive_content(weapons):
    files = {}; params = explosive_parameters(); family = explosive_family()
    selected = [g['id'] for g in weapons if explosive_for(g)]
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    data('content/explosive-ammo.json', {
        'sources': ['legacy/1.12.2/src/main/java/techguns/TGuns.java',
                    'legacy/1.12.2/src/main/java/techguns/items/guns/ammo/AmmoTypes.java',
                    'legacy/1.12.2/src/main/java/techguns/entities/projectiles/GenericProjectileExplosive.java'],
        **params, 'family': family, 'weapons': selected,
        'direct': 'EXPLOSION with weapon penetration, ordinary hurt cooldown, no preliminary 0.01 hit',
        'trigger': 'block impact or accepted living-entity damage; non-living impact does not explode',
        'blast': 'vanilla 1.12 integer exposure formula, radius = 2 * power; not TGExplosion',
        'blast_source': 'unattributed vanilla explosion; independent of weapon falloff, penetration and NPC scale',
        'terrain': 'firing-time unsafe policy; no fire; entities hurt before blocks break; original 1/power drop decay',
        'flight': {'air_drag': .99, 'water_drag': .85, 'gravity': 0, 'falloff': 'origin displacement at start of impact tick'},
        'pending': ['Original MiningChargeBlockExplosion FX/light pulse and GPU acceptance',
                    'Modern Minecraft exposure, block hooks and knockback enchantments are native adaptations']})
    data(RESOURCES + 'data/techguns/damage_type/explosive_bullet.json',
         {'message_id': 'techguns.explosive_bullet', 'scaling': 'when_caused_by_living_non_player', 'exhaustion': .1})
    data(RESOURCES + 'data/minecraft/tags/damage_type/is_explosion.json', {'replace': False, 'values': ['techguns:explosive_bullet']})
    files['core/src/main/java/techguns/core/ExplosiveAmmo.java'] = ('''package techguns.core;

/** Generated from the active GenericProjectileExplosive factory and AmmoTypes. */
public final class ExplosiveAmmo {
    public static final float DAMAGE_MULTIPLIER = ''' + str(params['damage_multiplier']) + '''f;
    public static final float EXPLOSION_POWER = ''' + str(params['explosion_power']) + '''f;
    public static final float DIRECT_KNOCKBACK = ''' + str(params['direct_knockback']) + '''f;
    public static boolean supported(WeaponDefinition gun) {
        return gun.projectile() == ProjectileKind.BALLISTIC && gun.ammo().item().equals("''' + family['default'] + '''");
    }
    public static AmmoSpec ammo(WeaponDefinition gun) {
        if (!supported(gun)) throw new IllegalArgumentException("Weapon has no explosive variant");
        return new AmmoSpec("''' + family['item'] + '''", "''' + family['empty'] + '''", "''' + family['loose'] + '''", ''' + str(family['bundles']) + ''', false);
    }
    public static float damageAt(WeaponSpec gun, double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        float max = gun.damage() * DAMAGE_MULTIPLIER, min = gun.minimumDamage() * DAMAGE_MULTIPLIER;
        if (gun.dropEnd() == 0 || distance <= gun.dropStart()) return max;
        if (distance > gun.dropEnd()) return min;
        float factor = 1f - (float)((distance - gun.dropStart()) / (gun.dropEnd() - gun.dropStart()));
        return min + (max - min) * factor;
    }
    /** Vanilla 1.12 floors the result, including its one-damage fully occluded boundary. */
    public static float blastDamage(double distance, double exposure) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(exposure) || exposure < 0 || exposure > 1)
            throw new IllegalArgumentException("Invalid blast geometry");
        double radius = EXPLOSION_POWER * 2;
        if (distance > radius) return 0;
        double impact = (1 - distance / radius) * exposure;
        return (int)((impact * impact + impact) / 2 * 7 * radius + 1);
    }
    private ExplosiveAmmo() {}
}
''').encode('utf-8')
    return files


def explosive_translations(lang):
    ru = lang == 'ru_ru'
    result = {'entity.techguns.explosive_bullet': 'Взрывная пуля' if ru else 'Explosive bullet',
              'hud.techguns.ballistic.explosive': 'Взрывные патроны' if ru else 'Explosive ammunition'}
    for suffix in ('', '.player'):
        result['death.attack.techguns.explosive_bullet' + suffix] = '%1$s взорван игроком %2$s' if ru else '%1$s was blown up by %2$s'
    result['death.attack.techguns.explosive_bullet.item'] = '%1$s взорван игроком %2$s с помощью %3$s' if ru else '%1$s was blown up by %2$s using %3$s'
    return result
