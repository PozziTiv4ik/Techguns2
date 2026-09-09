"""Rocket Launcher multipart geometry, original projectile meshes and damage tags."""
from pathlib import Path
import json
import re
from legacy_models import extract_shapes, shape_vertices, convert_mesh, strip_comments, numeric

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'
VARIANTS = {'default': 'rocket', 'nuke': 'rocket_nuke', 'high_velocity': 'rocket_high_velocity'}


def parse_variants():
    result = []
    for variant, ammo in VARIANTS.items():
        name = {'default': 'RocketProjectile', 'nuke': 'RocketProjectileNuke', 'high_velocity': 'RocketProjectileHV'}[variant]
        path = 'java/techguns/entities/projectiles/' + name + '.java'
        source = strip_comments((LEGACY / path).read_text())
        modifiers = {}
        for key, value, additive in re.findall(r'\.set(Dmg|Radius|Range|Velocity)\(([^,]+),\s*([^\)]+)\)', source):
            if numeric(additive) != 0: raise ValueError('Unported additive rocket modifier: ' + name)
            modifiers[key] = numeric(value)
        factory = re.search(r'return new ' + name + r'\(world,\s*p,([^;]+);', source)[1]
        result.append({'id': variant, 'ammo': ammo, 'damage_multiplier': modifiers.get('Dmg', 1),
                       'blast_radius_multiplier': modifiers.get('Range', 1),
                       'velocity_multiplier': modifiers.get('Velocity', 1) if 'mod.getVelocity(speed)' in factory else 1,
                       'lifetime_multiplier': modifiers.get('Range', 1) if 'mod.getTTL(TTL)' in factory else 1,
                       'source': 'legacy/1.12.2/src/main/' + path})
    return result


def rocket_item_model():
    def model(name): return {'type': 'minecraft:model', 'model': 'techguns:item/' + name}
    return {'type': 'minecraft:composite', 'models': [model('rocketlauncher'), {
        'type': 'minecraft:condition', 'property': 'techguns:rocket_loaded',
        'on_false': {'type': 'minecraft:empty'},
        'on_true': {'type': 'minecraft:select', 'property': 'minecraft:component', 'component': 'techguns:rocket_variant',
                    'fallback': model('rocketlauncher_default'),
                    'cases': [{'when': variant, 'model': model('rocketlauncher_' + variant)} for variant in VARIANTS]}}]}


def generate_rocket_content():
    files = {}
    files['content/rocket-variants.json'] = (json.dumps(parse_variants(), indent=2) + '\n').encode()
    def data(path, value): files[RESOURCES + path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    launcher = (LEGACY / 'java/techguns/client/models/guns/ModelRocketLauncher.java').read_text()
    _, _, shapes = extract_shapes(launcher, 'ModelRocketLauncher')
    points = [p for shape in shapes for p in shape_vertices(shape)]
    center = [(min(p[i] for p in points) + max(p[i] for p in points)) / 2 for i in range(3)]
    # Both gun parts must use the body origin. Centering the rocket independently detaches it from the barrel.
    def launcher_point(p): return [(8 + (v-c) * .5 * (-1 if i == 1 else 1)) / 16 for i, (v,c) in enumerate(zip(p,center))]
    flying = (LEGACY / 'java/techguns/client/models/projectiles/ModelRocket.java').read_text()
    for variant, ammo in VARIANTS.items():
        for name, source, transform, winding in (
            ('rocketlauncher_' + variant, launcher, launcher_point, True),
            ('rocket_projectile_' + variant, flying, lambda p: [.5 + v / 16 for v in p], False)):
            model, obj, mtl = convert_mesh(source, 'ModelRocket', name, 'techguns:item/' + ammo, '+x',
                                           coordinate_transform=transform, reverse_winding=winding)
            if name.startswith('rocket_projectile_'):
                model['display'] = {}
                data('assets/techguns/items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'techguns:item/' + name}})
            data('assets/techguns/models/item/' + name + '.json', model)
            files[RESOURCES + 'assets/techguns/models/item/' + name + '.obj'] = obj.encode()
            files[RESOURCES + 'assets/techguns/models/item/' + name + '.mtl'] = mtl.encode()
    data('data/techguns/damage_type/rocket.json', {'message_id': 'techguns.rocket', 'scaling': 'when_caused_by_living_non_player', 'exhaustion': .1})
    data('data/minecraft/tags/damage_type/is_explosion.json', {'replace': False, 'values': ['techguns:rocket']})
    return files


def rocket_translations(lang):
    ru = lang == 'ru_ru'
    values = {
        'key.techguns.safemode': 'Переключить безопасный режим' if ru else 'Toggle safe mode',
        'hud.techguns.safe': 'Блоки: защищены' if ru else 'Blocks: protected',
        'hud.techguns.unsafe': 'Блоки: разрушаются' if ru else 'Blocks: destructible',
        'hud.techguns.unsafe_denied': 'Сервер запретил разрушение блоков оружием' if ru else 'Weapon block destruction is restricted by the server',
        'entity.techguns.rocket': 'Ракета' if ru else 'Rocket',
        'entity.techguns.radiation_zone': 'Радиационная зона' if ru else 'Radiation zone',
        'death.attack.techguns.rocket': '%1$s взорван игроком %2$s' if ru else '%1$s was blown up by %2$s',
    }
    values['death.attack.techguns.rocket.player'] = values['death.attack.techguns.rocket']
    values['death.attack.techguns.rocket.item'] = '%1$s взорван игроком %2$s с помощью %3$s' if ru else '%1$s was blown up by %2$s using %3$s'
    for variant in VARIANTS:
        label = {'default': ('Обычная ракета', 'Rocket'), 'nuke': ('Ядерная ракета', 'Nuclear rocket'), 'high_velocity': ('Скоростная ракета', 'High-velocity rocket')}[variant][not ru]
        values['hud.techguns.rocket.' + variant] = label
        values['item.techguns.rocket_projectile_' + variant] = label
    return values
