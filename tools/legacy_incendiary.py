"""AmmoTypes / GenericProjectileIncendiary, restricted to the current ballistic arsenal."""
import json
import re
from legacy_items import LEGACY, shared_fields, arguments
from legacy_models import strip_comments, numeric
from legacy_repair import RESOURCES


def incendiary_families():
    source = strip_comments((LEGACY / 'java/techguns/items/guns/ammo/AmmoTypes.java').read_text(encoding='utf-8'))
    fields = shared_fields()
    constructors = dict(re.findall(r'(\w+)\s*=\s*new AmmoType\(([^;]+)\);', source))
    result = []
    for family, args in re.findall(r'(\w+)\.addVariant\(TYPE_INCENDIARY,\s*([^;]+)\);', source):
        base = arguments(constructors[family]); variant = arguments(args)
        if len(base) not in (1, 4) or len(variant) not in (1, 2): raise ValueError('Unported compound incendiary ammo')
        item = lambda field: fields[field.strip().removeprefix('TGItems.')]
        result.append({'family': family, 'default': item(base[0]), 'item': item(variant[0]),
                       'empty': item(base[1]) if len(base) == 4 else '',
                       'loose': item(variant[1]) if len(variant) == 2 else '',
                       'bundles': int(base[3]) if len(base) == 4 else 0})
    return result


def incendiary_for(gun):
    if gun['projectile'] != 'ballistic': return None
    return next((f for f in incendiary_families() if f['default'] == gun['ammo']['item']), None)


def incendiary_parameters():
    source = strip_comments((LEGACY / 'java/techguns/entities/projectiles/GenericProjectileIncendiary.java').read_text(encoding='utf-8'))
    modifier = re.search(r'new DamageModifier\(\)\.setDmg\(([^,]+),\s*([^\)]+)\)', source)
    if numeric(modifier[2]) != 0: raise ValueError('Review additive incendiary damage')
    return {'damage_multiplier': numeric(modifier[1]), 'burn_seconds': int(re.search(r'ent.setFire\((\d+)\)', source)[1]),
            'ignition_divisor': numeric(re.search(r'burnBlocks\(world, raytraceResultIn, damage/([^\)]+)\)', source)[1])}


def generate_incendiary_content(weapons):
    files = {}; params = incendiary_parameters()
    selected = [g for g in weapons if incendiary_for(g)]
    families = [f for f in incendiary_families() if any(g['ammo']['item'] == f['default'] for g in selected)]
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    data('content/incendiary-ammo.json', {
        'sources': ['legacy/1.12.2/src/main/java/techguns/items/guns/ammo/AmmoTypes.java',
                    'legacy/1.12.2/src/main/java/techguns/entities/projectiles/GenericProjectileIncendiary.java',
                    'legacy/1.12.2/src/main/java/techguns/recipes/AmmoSwitchRecipeFactory.java'],
        **params, 'families': families, 'weapons': [g['id'] for g in selected],
        'crafting_rounds': '1 for individual shotgun shells, full capacity for bundles/magazines; old rounds consumed',
        'damage_kind': 'FIRE, magic flag, not vanilla is_fire; ordinary hurt cooldown bypassed',
        'knockback': 'inherited 0.01 PHYSICAL preliminary hit; main FIRE hit adds no knockback',
        'flight': {'air_drag': .99, 'water_drag': .85, 'falloff': 'origin displacement at start of impact tick', 'gravity': 0},
        'block_fire': 'air cell on hit face, roll <= modified initial damage / 40, firing-time unsafe policy',
        'pending': ['Original FX engine and visual acceptance', 'Minigun weapon and its incendiary drum',
                    'Explosive AS50 ammunition']})
    for key in ('incendiary', 'incendiary_knockback'):
        data(RESOURCES + f'data/techguns/damage_type/{key}.json',
             {'message_id': 'techguns.' + key, 'scaling': 'when_caused_by_living_non_player', 'exhaustion': .1})
    for namespace, tag in (('minecraft', 'bypasses_cooldown'), ('minecraft', 'no_knockback'),
                            ('minecraft', 'witch_resistant_to'), ('neoforge', 'is_magic')):
        data(RESOURCES + f'data/{namespace}/tags/damage_type/{tag}.json', {'replace': False, 'values': ['techguns:incendiary']})
    entries = ',\n'.join('        new Family("{default}", "{item}", "{empty}", "{loose}", {bundles})'.format(**f) for f in families)
    files['core/src/main/java/techguns/core/IncendiaryAmmo.java'] = ('''package techguns.core;

import java.util.List;

/** Generated from original AmmoTypes and GenericProjectileIncendiary.Factory. */
public final class IncendiaryAmmo {
    public record Family(String normal, String item, String empty, String loose, int bundles) {}
    public static final float DAMAGE_MULTIPLIER = ''' + str(params['damage_multiplier']) + '''f;
    public static final int BURN_SECONDS = ''' + str(params['burn_seconds']) + ''';
    public static final float IGNITION_DIVISOR = ''' + str(params['ignition_divisor']) + '''f;
    public static final List<Family> FAMILIES = List.of(
''' + entries + '''
    );
    public static boolean supported(WeaponDefinition gun) {
        return gun.projectile() == ProjectileKind.BALLISTIC && FAMILIES.stream().anyMatch(f -> f.normal().equals(gun.ammo().item()));
    }
    public static AmmoSpec ammo(WeaponDefinition gun, BallisticVariant variant) {
        if (variant == BallisticVariant.DEFAULT) return gun.ammo();
        if (!supported(gun)) throw new IllegalArgumentException("Weapon has no incendiary variant");
        var family = FAMILIES.stream().filter(f -> f.normal().equals(gun.ammo().item())).findFirst().orElseThrow();
        return new AmmoSpec(family.item(), family.empty(), family.loose(), family.bundles(), gun.ammo().individual());
    }
    public static float damageAt(WeaponSpec gun, double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        float max = gun.damage() * DAMAGE_MULTIPLIER, min = gun.minimumDamage() * DAMAGE_MULTIPLIER;
        if (gun.dropEnd() == 0 || distance <= gun.dropStart()) return max;
        if (distance > gun.dropEnd()) return min;
        float factor = 1f - (float)((distance - gun.dropStart()) / (gun.dropEnd() - gun.dropStart()));
        return min + (max - min) * factor;
    }
    public static boolean ignites(WeaponSpec gun, double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Invalid ignition roll");
        return roll <= gun.damage() * DAMAGE_MULTIPLIER / IGNITION_DIVISOR;
    }
    private IncendiaryAmmo() {}
}
''').encode('utf-8')
    return files


def incendiary_translations(lang):
    ru = lang == 'ru_ru'
    result = {'entity.techguns.incendiary_bullet': 'Зажигательная пуля' if ru else 'Incendiary bullet',
              'hud.techguns.ballistic.default': 'Обычные патроны' if ru else 'Standard ammunition',
              'hud.techguns.ballistic.incendiary': 'Зажигательные патроны' if ru else 'Incendiary ammunition',
              'tooltip.techguns.loaded_ammo': 'Боеприпас: %s' if ru else 'Ammunition: %s'}
    for kind in ('incendiary', 'incendiary_knockback'):
        result['death.attack.techguns.' + kind] = '%1$s застрелен игроком %2$s' if ru else '%1$s was shot by %2$s'
        result['death.attack.techguns.' + kind + '.player'] = result['death.attack.techguns.' + kind]
        result['death.attack.techguns.' + kind + '.item'] = '%1$s застрелен игроком %2$s с помощью %3$s' if ru else '%1$s was shot by %2$s using %3$s'
    return result
