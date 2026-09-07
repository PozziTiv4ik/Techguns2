"""Generate the current port's weapon parameters, models and assets from the untouched 1.12.2 source."""
from pathlib import Path
import argparse
import json
import re
from legacy_models import strip_comments, numeric, convert_model

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = Path('platforms/neoforge-26.2/src/main/resources')
SELECTION = json.loads((ROOT / 'content/weapon-ports.json').read_text())
AMMO = {
    'handcannon': ('stonebullets', '', '', 0),
    'revolver': ('pistolrounds', '', '', 0),
    'goldenrevolver': ('pistolrounds', '', '', 0),
    'sawedoff': ('shotgunrounds', '', '', 0),
    'combatshotgun': ('shotgunrounds', '', '', 0),
    'boltaction': ('riflerounds', '', '', 0),
    'thompson': ('smgmagazine', 'smgmagazineempty', 'pistolrounds', 2),
}
MATERIALS = ('stonebarrel', 'woodstock')
RECIPE_PORTS = {
    'handcannon': 'handcannon.json',
    'stonebullets': 'itemshared_0_stonebullets.json',
    'stonebarrel': 'itemshared_37_stonebarrel.json',
    'woodstock': 'itemshared_42_woodstock.json',
}
SHARED_IDS = {0: 'stonebullets', 37: 'stonebarrel', 42: 'woodstock'}
ORE_TAGS = {'STONE': '#c:stones', 'COBBLESTONE': '#c:cobblestones', 'LOGWOOD': '#minecraft:logs'}


def convert_recipe(legacy):
    def item(value):
        identifier = value['item']
        if identifier.startswith('#'):
            return ORE_TAGS[identifier[1:]]
        if identifier == 'techguns:itemshared':
            return 'techguns:' + SHARED_IDS[value['data']]
        if value.get('data', 0) not in (0, 32767) and identifier not in ('techguns:handcannon',):
            raise ValueError(f'Unconverted metadata ingredient: {value}')
        return identifier
    kind = legacy['type'].replace('forge:ore_', 'minecraft:crafting_')
    if kind not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
        raise ValueError(f'Unsupported recipe: {kind}')
    result = {'type': kind, 'category': 'misc', 'result': {'id': item(legacy['result']), 'count': legacy['result'].get('count', 1)}}
    if kind.endswith('_shaped'):
        result['pattern'] = legacy['pattern']
        result['key'] = {key: item(value) for key, value in legacy['key'].items()}
    else:
        result['ingredients'] = [item(value) for value in legacy['ingredients']]
    return result


def arguments(text):
    parts, start, depth, quoted = [], 0, 0, False
    for i, char in enumerate(text):
        if char == '"': quoted = not quoted
        if not quoted:
            if char == '(': depth += 1
            elif char == ')': depth -= 1
            elif char == ',' and depth == 0:
                parts.append(text[start:i].strip())
                start = i+1
    return parts + [text[start:].strip()]


def parse_weapons():
    source = strip_comments((LEGACY / 'java/techguns/TGuns.java').read_text())
    sounds = dict(re.findall(r'(\w+)\s*=\s*createSoundEvent\("([^"]+)"\)',
                            strip_comments((LEGACY / 'java/techguns/TGSounds.java').read_text())))
    constants = {k: numeric(v) for k, v in re.findall(r'(?:float|int)\s+(\w+)\s*=\s*([^;]+);', source)
                 if re.fullmatch(r'\s*-?[\d.]+[fF]?\s*', v)}

    def num(token):
        return constants[token] if token in constants else numeric(token)

    result = []
    for identifier, model in SELECTION.items():
        match = re.search(r'\b' + identifier + r'\s*=\s*new GenericGun\(', source)
        if not match: raise ValueError(f'No GenericGun definition: {identifier}')
        statement = source[match.end():source.index(';', match.end())]
        depth, end, quoted = 1, 0, False
        while depth:
            char = statement[end]
            if char == '"': quoted = not quoted
            if not quoted:
                if char == '(': depth += 1
                if char == ')': depth -= 1
            end += 1
        args = arguments(statement[:end-1])
        if len(args) != 11: raise ValueError(f'Unexpected gun constructor: {identifier}')
        calls = {}
        for name, values in re.findall(r'\.([\w]+)\(([^()]*)\)', statement[end:]):
            calls[name] = arguments(values)
        drop = calls.get('setDamageDrop', [args[9], args[9], args[6]])
        shotgun = calls.get('setShotgunSpread', ['0', '0', 'false'])
        if shotgun[2] != 'false': raise ValueError(f'{identifier}: burst behavior not ported yet')
        ammo_item, empty, loose, bundles = AMMO[identifier]
        texture = (calls.get('setTexture') or calls['setTextures'])[0].strip('"')
        result.append({'id': identifier, 'capacity': int(num(args[4])), 'fire_delay': int(num(args[3])),
            'reload_ticks': int(num(args[5])), 'damage': num(args[6]), 'minimum_damage': num(drop[2]),
            'drop_start': num(drop[0]), 'drop_end': num(drop[1]),
            'speed': num(calls.get('setBulletSpeed', ['2'])[0]), 'lifetime': int(num(args[9])),
            'accuracy': num(args[10]), 'automatic': args[2] == 'false',
            'extra_pellets': int(num(shotgun[0])), 'pellet_spread': num(shotgun[1]),
            'gravity': num(calls.get('setGravity', ['0'])[0]),
            'penetration': num(calls.get('setPenetration', ['0'])[0]),
            'zoom': num(calls.get('setZoom', ['1'])[0]),
            'ammo': {'item': ammo_item, 'empty_item': empty, 'loose_item': loose,
                     'bundles_per_magazine': bundles, 'individual': int(num(calls.get('setAmmoCount', ['1'])[0])) > 1},
            'fire_sound': sounds[args[7].split('.')[-1]], 'reload_sound': sounds[args[8].split('.')[-1]],
            'model': model, 'texture': texture,
            'source': 'legacy/1.12.2/src/main/java/techguns/TGuns.java'})
    return result


def resolve_asset(path):
    exact = LEGACY / 'resources/assets/techguns' / path
    if exact.is_file(): return exact
    matches = [p for p in exact.parent.iterdir() if p.name.lower() == exact.name.lower()]
    if len(matches) != 1: raise ValueError(f'Missing or ambiguous legacy resource: {path}')
    return matches[0]


def generate():
    files = {}
    def output(path, text): files[Path(path).as_posix()] = text.encode('utf-8')
    def data(path, value): output(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    def resource(path, value): data(RESOURCES / path, value)
    weapons = parse_weapons()
    data('content/ballistic-weapons.json', weapons)
    definitions = []
    ammo_items = set()
    sounds_data = json.loads(resolve_asset('sounds.json').read_text())
    selected_sounds = {}
    languages = {}
    for lang in ('en_us', 'ru_ru'):
        lines = resolve_asset(f'lang/{lang}.lang').read_text(encoding='utf-8-sig').splitlines()
        languages[lang] = dict(line.split('=', 1) for line in lines if '=' in line and not line.startswith('#'))
    translated = {lang: {'itemGroup.techguns': 'Techguns', 'key.techguns.reload': 'Reload weapon' if lang == 'en_us' else 'Перезарядить оружие'} for lang in languages}
    for gun in weapons:
        identifier = gun['id']
        ammo = gun['ammo']
        for field in ('item', 'empty_item', 'loose_item'):
            if ammo[field]: ammo_items.add(ammo[field])
        java_stats = ', '.join([f'"{identifier}"', str(gun['capacity']), str(gun['fire_delay']), str(gun['reload_ticks']),
            f"{gun['damage']}f", f"{gun['minimum_damage']}f", str(gun['drop_start']), str(gun['drop_end']),
            str(gun['speed']), str(gun['lifetime']), str(gun['accuracy'])])
        ammo_java = f'new AmmoSpec("{ammo["item"]}", "{ammo["empty_item"]}", "{ammo["loose_item"]}", {ammo["bundles_per_magazine"]}, {str(ammo["individual"]).lower()})'
        definitions.append(f'        new WeaponDefinition(new WeaponSpec({java_stats}), {ammo_java}, {str(gun["automatic"]).lower()}, {gun["extra_pellets"]}, {gun["pellet_spread"]}, {gun["gravity"]}, {gun["penetration"]}, {gun["zoom"]}f, "{gun["fire_sound"]}", "{gun["reload_sound"]}")')
        source = (LEGACY / f'java/techguns/client/models/guns/{gun["model"]}.java').read_text()
        resource(f'assets/techguns/models/item/{identifier}.json', convert_model(source, gun['model'], f'techguns:item/{identifier}'))
        files[(RESOURCES / f'assets/techguns/textures/item/{identifier}.png').as_posix()] = resolve_asset(gun['texture'] + '.png').read_bytes()
        for key in ('fire_sound', 'reload_sound'):
            sound = gun[key]
            selected_sounds[sound] = {'sounds': sounds_data[sound]['sounds']}
    for identifier in sorted(ammo_items | set(MATERIALS)):
        resource(f'assets/techguns/models/item/{identifier}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'techguns:item/{identifier}'}})
        files[(RESOURCES / f'assets/techguns/textures/item/{identifier}.png').as_posix()] = resolve_asset(f'textures/items/{identifier}.png').read_bytes()
    for identifier in list(SELECTION) + sorted(ammo_items | set(MATERIALS)):
        resource(f'assets/techguns/items/{identifier}.json', {'model': {'type': 'minecraft:model', 'model': f'techguns:item/{identifier}'}})
        for lang in languages:
            key = f'item.techguns.{identifier}.name'
            translated[lang][f'item.techguns.{identifier}'] = languages[lang].get(key, languages['en_us'].get(key, identifier))
    for sound, value in selected_sounds.items():
        subtitle = f'subtitles.techguns.{sound}'
        value['subtitle'] = subtitle
        for lang in languages:
            reloading = 'reload' in sound
            translated[lang][subtitle] = ('Weapon reloads' if reloading else 'Gunshot') if lang == 'en_us' else ('Перезарядка оружия' if reloading else 'Выстрел')
        for entry in value['sounds']:
            name = entry if isinstance(entry, str) else entry['name']
            path = f'sounds/{name.split(":")[-1]}.ogg'
            files[(RESOURCES / 'assets/techguns' / path).as_posix()] = resolve_asset(path).read_bytes()
    for lang, values in translated.items():
        values['entity.techguns.bullet'] = 'Bullet' if lang == 'en_us' else 'Пуля'
        values['death.attack.techguns.bullet'] = '%1$s was shot by %2$s' if lang == 'en_us' else '%1$s застрелен игроком %2$s'
        values['death.attack.techguns.bullet.player'] = values['death.attack.techguns.bullet']
        values['death.attack.techguns.bullet.item'] = '%1$s was shot by %2$s using %3$s' if lang == 'en_us' else '%1$s застрелен игроком %2$s с помощью %3$s'
        resource(f'assets/techguns/lang/{lang}.json', values)
    resource('assets/techguns/sounds.json', selected_sounds)
    for identifier, filename in RECIPE_PORTS.items():
        recipe = json.loads(resolve_asset(f'recipes/{filename}').read_text())
        resource(f'data/techguns/recipe/{identifier}.json', convert_recipe(recipe))
    source = '''package techguns.core;

import java.util.List;

/** Generated from legacy TGuns.java by tools/generate_weapon_content.py. */
public final class Weapons {
    public static final List<WeaponDefinition> ALL = List.of(
''' + ',\n'.join(definitions) + ''');
    public static WeaponDefinition definition(String id) {
        return ALL.stream().filter(gun -> gun.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown weapon: " + id));
    }
    public static final WeaponSpec REVOLVER = definition("revolver").stats();
    private Weapons() {}
}
'''
    output('core/src/main/java/techguns/core/Weapons.java', source)
    return files


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    files = generate()
    for name, value in files.items():
        path = ROOT / name
        if args.check:
            # Text comparisons ignore platform checkout newlines; assets remain byte-exact.
            actual = path.read_bytes() if path.exists() else b''
            if path.suffix in ('.json', '.java'): actual = actual.replace(b'\r\n', b'\n')
            if actual != value: raise SystemExit(f'Stale generated file: {name}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value)
    print(f'{len(SELECTION)} weapon definitions, {len(files)} generated files: ' + ('verified' if args.check else 'written'))
