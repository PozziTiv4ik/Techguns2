"""Active FlamethrowerProjectile / GenericGun rules; no optional Albedo dependency."""
import json
import re
from legacy_items import LEGACY
from legacy_models import strip_comments, numeric
from legacy_repair import RESOURCES


def flame_parameters():
    projectile = strip_comments((LEGACY / 'java/techguns/entities/projectiles/FlamethrowerProjectile.java').read_text())
    guns = strip_comments((LEGACY / 'java/techguns/TGuns.java').read_text())
    gun = re.search(r'flamethrower\s*=\s*new GenericGun\([^;]+;', guns)[0]
    integer = lambda name: int(re.search(r'\.' + name + r'\((\d+)\)', gun)[1])
    return {'ignition_chance': numeric(re.search(r'chanceToIgnite\s*=\s*([^;]+)', projectile)[1]),
            'burn_seconds': int(re.search(r'entityIgniteTime\s*=\s*(\d+)', projectile)[1]),
            'loop_delay': integer('setMaxLoopDelay'), 'recoil_ticks': integer('setRecoiltime'),
            'muzzle_ticks': integer('setMuzzleFlashTime'),
            'start_sound': 'guns.flamethrowerstart',
            'recoil_translation': .025, 'recoil_degrees': 2.5,
            'muzzle_offset': [-.15, -.05, .5]}


def generate_flame_content():
    params = flame_parameters(); files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    data('content/flamethrower-behavior.json', {
        'sources': ['legacy/1.12.2/src/main/java/techguns/entities/projectiles/FlamethrowerProjectile.java',
                    'legacy/1.12.2/src/main/java/techguns/items/guns/GenericGun.java',
                    'legacy/1.12.2/src/main/java/techguns/client/ClientProxy.java'], **params,
        'damage': 'FIRE with magic flag, not vanilla is_fire; 0.01 PHYSICAL preliminary hit; main hit bypasses cooldown and knockback',
        'flight': 'inherited motion, then air/water drag, gravity and wet removal; collision precedes wet removal',
        'block_fire': 'roll <= 0.5, air cell on hit face; unsafe permission snapshot at firing',
        'audio': 'one non-repeating start/shot sample per accepted player shot; restart after >=10 silent ticks; NPC always uses fire sample',
        'effects': 'native flame/bubble particles; source ten-tick sine recoil, no reset while active',
        'pending': ['Full legacy FX system, idle pilot flame and GPU acceptance', 'Optional Albedo lighting integration']})
    data(RESOURCES + 'data/techguns/damage_type/flame.json',
         {'message_id': 'techguns.flame', 'scaling': 'when_caused_by_living_non_player', 'exhaustion': .1})
    for namespace, tag in (('minecraft', 'bypasses_cooldown'), ('minecraft', 'no_knockback'),
                           ('minecraft', 'witch_resistant_to'), ('neoforge', 'is_magic')):
        data(RESOURCES + f'data/{namespace}/tags/damage_type/{tag}.json', {'replace': False, 'values': ['techguns:flame']})
    files['core/src/main/java/techguns/core/FlameRules.java'] = ('''package techguns.core;

/** Generated from FlamethrowerProjectile, TGuns and the source sine recoil animation. */
public final class FlameRules {
    public static final int BURN_SECONDS = %(burn_seconds)s;
    public static final int LOOP_DELAY = %(loop_delay)s;
    public static final int RECOIL_TICKS = %(recoil_ticks)s;
    public static final int MUZZLE_TICKS = %(muzzle_ticks)s;
    public static final double IGNITION_CHANCE = %(ignition_chance)s;
    public static final float RECOIL_TRANSLATION = %(recoil_translation)sf;
    public static final float RECOIL_DEGREES = %(recoil_degrees)sf;
    public static boolean ignites(double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Invalid ignition roll");
        return roll <= IGNITION_CHANCE;
    }
    public static boolean startsSound(long last, long now) { return last < 0 || now < last || now - last >= LOOP_DELAY; }
    public static boolean startsRecoil(long last, long now) { return last < 0 || now < last || now - last >= RECOIL_TICKS; }
    public static float recoil(long start, long now, float partial) {
        double elapsed = now - start + partial;
        return start < 0 || elapsed < 0 || elapsed >= RECOIL_TICKS || !Float.isFinite(partial) ? 0
                : (float)Math.sin(2 * Math.PI * elapsed / RECOIL_TICKS);
    }
    private FlameRules() {}
}
''' % params).encode('utf-8')
    return files


def flame_translations(lang):
    ru = lang == 'ru_ru'
    result = {'entity.techguns.flame': 'Струя огнемёта' if ru else 'Flamethrower flame'}
    for suffix in ('', '.player', '.item'):
        result['death.attack.techguns.flame' + suffix] = '%1$s сожжён игроком %2$s' if ru else '%1$s was burned by %2$s'
    return result
