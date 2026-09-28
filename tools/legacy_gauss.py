"""GaussProjectile / AdvancedBulletProjectile contracts and the original Gauss OBJ."""
import json
import re
from legacy_items import LEGACY, arguments
from legacy_models import strip_comments, numeric, display_transforms

RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def gauss_parameters():
    generic = strip_comments((LEGACY / 'java/techguns/entities/projectiles/GenericProjectile.java').read_text())
    source = strip_comments((LEGACY / 'java/techguns/entities/projectiles/GaussProjectile.java').read_text())
    advanced = strip_comments((LEGACY / 'java/techguns/entities/projectiles/AdvancedBulletProjectile.java').read_text())
    if 'extends AdvancedBulletProjectile' not in source or 'extends GenericProjectile' not in advanced:
        raise ValueError('Review changed Gauss inheritance')
    for text in (source, advanced):
        if re.search(r'void (onUpdate|onHit)\(|getProjectileDamageSource\(', text):
            raise ValueError('Review changed Gauss flight/damage override')
    factory = arguments(re.search(r'return new GaussProjectile\(([^;]+)\)', source)[1])
    if factory != ['world', 'p', 'damage', 'speed', 'TTL', 'spread', 'dmgDropStart', 'dmgDropEnd', 'dmgMin', 'penetration', 'blockdamage', 'firePos']:
        raise ValueError('Review changed Gauss factory')
    guns = strip_comments((LEGACY / 'java/techguns/TGuns.java').read_text())
    statement = re.search(r'gaussrifle\s*=([^;]+);', guns)[1]
    sound_field = re.search(r'setRechamberSound\(TGSounds\.(\w+)\)', statement)[1]
    sounds = (LEGACY / 'java/techguns/TGSounds.java').read_text()
    return {'air_drag': numeric(re.search(r'float f1 = (0\.99F);', generic)[1]),
            'water_drag': numeric(re.search(r'f1 = (0\.85F);', generic)[1]),
            'gravity': 0, 'rechamber_sound': re.search(sound_field + r' = createSoundEvent\("([^"]+)"', sounds)[1]}


def gauss_model():
    return {'loader': 'neoforge:obj', 'model': 'techguns:models/item/gaussrifle.obj',
            'automatic_culling': False, 'flip_v': True, 'emissive_ambient': False,
            'textures': {'skin': 'techguns:item/gaussrifle', 'particle': 'techguns:item/gaussrifle'},
            'display': display_transforms('+x')}


def generate_gauss_content():
    files = {}; params = gauss_parameters()
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    source = LEGACY / 'resources/assets/techguns/models/item/gaussrifle.obj'
    raw = source.read_text().splitlines()
    vertices = [list(map(float, line.split()[1:])) for line in raw if line.startswith('v ')]
    low = [min(v[i] for v in vertices) for i in range(3)]
    high = [max(v[i] for v in vertices) for i in range(3)]
    center = [(a + b) / 2 for a, b in zip(low, high)]
    scale = min(.5, 2.5 / max(b - a for a, b in zip(low, high)))
    lines = []
    # Source muzzle points -X; rotate 180 around Y to use the shared +X transforms.
    # Original OBJ is already in block units. Preserve all UVs, faces and object names.
    for line in raw:
        if line.startswith('v '):
            line = 'v ' + ' '.join(f'{.5 + (float(v) - center[i]) * scale * (-1 if i != 1 else 1):.9f}' for i, v in enumerate(line.split()[1:]))
        elif line.startswith('vn '):
            line = 'vn ' + ' '.join(str(float(v) * (-1 if i != 1 else 1)) for i, v in enumerate(line.split()[1:]))
        lines.append(line.rstrip())
    files[RESOURCES + 'assets/techguns/models/item/gaussrifle.obj'] = ('\n'.join(lines) + '\n').encode()
    files[RESOURCES + 'assets/techguns/models/item/gaussrifle.mtl'] = b'newmtl tex\nKd 1.00 1.00 1.00\nmap_Kd #skin\n'
    data('content/gauss-projectile.json', {
        'source': 'GaussProjectile -> AdvancedBulletProjectile -> GenericProjectile', **params,
        'damage': 'PROJECTILE, weapon penetration, bypass cooldown, ordinary 0.01 PHYSICAL impulse then no-knockback main hit',
        'flight': 'real projectile; 1.5 * configured speed with legacy spread; stops at first accepted entity/block impact; TTL after movement',
        'model': {'source': source.relative_to(LEGACY.parents[3]).as_posix(), 'vertices': len(vertices),
                  'faces': sum(l.startswith('f ') for l in raw), 'parts': sum(l.startswith('o ') for l in raw),
                  'scale': scale, 'rotation_y': 180, 'flip_v': True},
        'pending': ['Original Gauss trail, muzzle/impact FX, dynamic light, scope/recoil animation and GPU acceptance']})
    data(RESOURCES + 'data/techguns/damage_type/gauss.json', {'message_id': 'techguns.gauss', 'scaling': 'when_caused_by_living_non_player', 'exhaustion': .1})
    for tag in ('is_projectile', 'bypasses_cooldown', 'no_knockback'):
        data(RESOURCES + f'data/minecraft/tags/damage_type/{tag}.json', {'replace': False, 'values': ['techguns:gauss']})
    files['core/src/main/java/techguns/core/GaussRules.java'] = ('''package techguns.core;

/** Generated from GaussProjectile, AdvancedBulletProjectile and GenericProjectile. */
public final class GaussRules {
    public static final float AIR_DRAG = ''' + str(params['air_drag']) + '''f;
    public static final float WATER_DRAG = ''' + str(params['water_drag']) + '''f;
    public static final String RECHAMBER_SOUND = "''' + params['rechamber_sound'] + '''";
    private GaussRules() {}
}
''').encode()
    return files


def gauss_translations(lang):
    ru = lang == 'ru_ru'
    result = {'entity.techguns.gauss': 'Снаряд Гаусса' if ru else 'Gauss slug',
              'tooltip.techguns.compound_ammo': 'Для перезарядки: %s + %s' if ru else 'Reload requires: %s + %s'}
    for suffix in ('', '.player'):
        result['death.attack.techguns.gauss' + suffix] = '%1$s застрелен игроком %2$s' if ru else '%1$s was shot by %2$s'
    result['death.attack.techguns.gauss.item'] = '%1$s застрелен игроком %2$s с помощью %3$s' if ru else '%1$s was shot by %2$s using %3$s'
    return result
