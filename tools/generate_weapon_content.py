"""Generate the current port's weapon parameters, models and assets from the untouched 1.12.2 source."""
from pathlib import Path
import argparse
import json
import re
from legacy_models import strip_comments, numeric, convert_model, convert_mesh, extract_shapes
from legacy_crafting import plan_crafting

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = Path('platforms/neoforge-26.2/src/main/resources')
SELECTION = json.loads((ROOT / 'content/weapon-ports.json').read_text())
GUI_HIDDEN_PARTS = {'m4_infiltrator': ('LaserBeam', 'LaserBeam01')}


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
    ammo_source = strip_comments((LEGACY / 'java/techguns/items/guns/ammo/AmmoTypes.java').read_text())
    item_source = strip_comments((LEGACY / 'java/techguns/TGItems.java').read_text())
    item_ids = dict(re.findall(r'(\w+)\s*=\s*SHARED_ITEM\.addsharedVariant\("([^"]+)"', item_source))
    selectors = dict(re.findall(r'(\w+)\s*=\s*new ProjectileSelector(?:<[^;]+?>)?\(AmmoTypes\.(\w+)', source))
    render_source = strip_comments((LEGACY / 'java/techguns/client/ClientProxy.java').read_text())
    renderers = {identifier: (renderer, model) for identifier, renderer, model in re.findall(
        r'registerItemRenderer\(TGuns\.(\w+),\s*new (RenderGunBase90|RenderGunBase)\(new (\w+)\(', render_source)}
    sounds = dict(re.findall(r'(\w+)\s*=\s*createSoundEvent\("([^"]+)"\)',
                            strip_comments((LEGACY / 'java/techguns/TGSounds.java').read_text())))
    # 1.12 ResourceLocation normalizes paths to lowercase; 26.2 rejects uppercase paths.
    sounds = {key: value.lower() for key, value in sounds.items()}
    constants = {k: numeric(v) for k, v in re.findall(r'(?:float|int)\s+(\w+)\s*=\s*([^;]+);', source)
                 if re.fullmatch(r'\s*-?[\d.]+[fF]?\s*', v)}

    def num(token):
        return constants[token] if token in constants else numeric(token)

    def ammo_for(selector):
        inline = re.search(r'AmmoTypes\.(\w+)', selector)
        ammo_type = inline[1] if inline else selectors[selector]
        match = re.search(r'\b' + ammo_type + r'\s*=\s*new AmmoType\(([^;]+)\);', ammo_source)
        if not match: raise ValueError(f'Cannot resolve legacy ammo type: {ammo_type}')
        values = arguments(match[1])
        def item(value):
            if value == 'ItemStack.EMPTY': return ''
            if not value.startswith('TGItems.'): raise ValueError(f'Compound ammo requires a dedicated port: {value}')
            return item_ids[value.split('.')[1]]
        if len(values) == 1: return item(values[0]), '', '', 0
        if len(values) == 4: return item(values[0]), item(values[1]), item(values[2]), int(num(values[3]))
        raise ValueError(f'Unsupported ammo constructor for {ammo_type}')

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
        ammo_item, empty, loose, bundles = ammo_for(args[1])
        renderer, bound_model = renderers[identifier]
        if bound_model != model: raise ValueError(f'Model does not match original renderer for {identifier}')
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
            'zoom_toggle': calls.get('setZoom', ['1', 'false'])[1] == 'true',
            'zoom_accuracy': num(calls.get('setZoom', ['1', 'false', '1'])[2]),
            'zoom_centered': calls.get('setZoom', ['1', 'false', '1', 'false'])[3] == 'true',
            'ammo': {'item': ammo_item, 'empty_item': empty, 'loose_item': loose,
                     'bundles_per_magazine': bundles, 'individual': int(num(calls.get('setAmmoCount', ['1'])[0])) > 1},
            'fire_sound': sounds[args[7].split('.')[-1]], 'reload_sound': sounds[args[8].split('.')[-1]],
            'model': model, 'texture': texture, 'forward_axis': '+x' if renderer == 'RenderGunBase90' else '-z',
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
    crafting = plan_crafting(weapons)
    materials = set(crafting['materials'])
    data('content/crafting-content.json', crafting['catalog'])
    data('content/ballistic-weapons.json', weapons)
    definitions = []
    ammo_items = set(crafting['extra_ammo'])
    sounds_data = json.loads(resolve_asset('sounds.json').read_text())
    selected_sounds = {}
    render_source = strip_comments((LEGACY / 'java/techguns/client/ClientProxy.java').read_text())
    custom_items = {identifier: (model, empty == 'true', texture) for identifier, model, empty, texture in re.findall(
        r'addRenderForType\("([^"]+)",\s*new \w+\(new (\w+)\((true|false)\),\s*new ResourceLocation\(Techguns.MODID,\s*"([^"]+)"\)', render_source)}
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
        definitions.append(f'        new WeaponDefinition(new WeaponSpec({java_stats}), {ammo_java}, {str(gun["automatic"]).lower()}, {gun["extra_pellets"]}, {gun["pellet_spread"]}, {gun["gravity"]}, {gun["penetration"]}, new AimSpec({gun["zoom"]}f, {str(gun["zoom_toggle"]).lower()}, {gun["zoom_accuracy"]}f, {str(gun["zoom_centered"]).lower()}), "{gun["fire_sound"]}", "{gun["reload_sound"]}")')
        source = (LEGACY / f'java/techguns/client/models/guns/{gun["model"]}.java').read_text()
        _, _, shapes = extract_shapes(source, gun['model'])
        gui_hidden = GUI_HIDDEN_PARTS.get(identifier, ())
        mesh = gui_hidden or any(s['inflate'] != 0 or s['render_scale'] != [1, 1, 1] for s in shapes)
        if mesh:
            model, obj, material = convert_mesh(source, gun['model'], identifier, f'techguns:item/{identifier}', gun['forward_axis'], gui_hidden)
            output(RESOURCES / f'assets/techguns/models/item/{identifier}.obj', obj)
            output(RESOURCES / f'assets/techguns/models/item/{identifier}.mtl', material)
        else:
            model = convert_model(source, gun['model'], f'techguns:item/{identifier}', gun['forward_axis'])
        resource(f'assets/techguns/models/item/{identifier}.json', model)
        if gui_hidden:
            resource(f'assets/techguns/models/item/{identifier}_gui.json', {**model, 'visibility': {part: False for part in gui_hidden}})
        files[(RESOURCES / f'assets/techguns/textures/item/{identifier}.png').as_posix()] = resolve_asset(gun['texture'] + '.png').read_bytes()
        for key in ('fire_sound', 'reload_sound'):
            sound = gun[key]
            selected_sounds[sound] = {'sounds': sounds_data[sound]['sounds']}
    for identifier in sorted(ammo_items | materials):
        if identifier in custom_items:
            class_name, empty, texture = custom_items[identifier]
            source = (LEGACY / f'java/techguns/client/models/items/{class_name}.java').read_text()
            model, obj, material = convert_mesh(source, class_name, identifier, f'techguns:item/{identifier}', '-z', constructor_values={'empty': empty})
            model['display']['gui'] = {'rotation': [15, -35, 0], 'scale': [1.4,1.4,1.4]}
            resource(f'assets/techguns/models/item/{identifier}.json', model)
            output(RESOURCES / f'assets/techguns/models/item/{identifier}.obj', obj)
            output(RESOURCES / f'assets/techguns/models/item/{identifier}.mtl', material)
            files[(RESOURCES / f'assets/techguns/textures/item/{identifier}.png').as_posix()] = resolve_asset(texture).read_bytes()
        else:
            resource(f'assets/techguns/models/item/{identifier}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'techguns:item/{identifier}'}})
            files[(RESOURCES / f'assets/techguns/textures/item/{identifier}.png').as_posix()] = resolve_asset(f'textures/items/{identifier}.png').read_bytes()
    for identifier in list(SELECTION) + sorted(ammo_items | materials):
        model = {'type': 'minecraft:model', 'model': f'techguns:item/{identifier}'}
        if identifier in GUI_HIDDEN_PARTS:
            model = {'type': 'minecraft:select', 'property': 'minecraft:display_context', 'fallback': model,
                     'cases': [{'when': ['gui'], 'model': {'type': 'minecraft:model', 'model': f'techguns:item/{identifier}_gui'}}]}
        resource(f'assets/techguns/items/{identifier}.json', {'model': model})
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
        values['hud.techguns.ammo'] = '%s / %s'
        values['hud.techguns.reloading'] = 'Reloading' if lang == 'en_us' else 'Перезарядка'
        values['entity.techguns.bullet'] = 'Bullet' if lang == 'en_us' else 'Пуля'
        values['death.attack.techguns.bullet'] = '%1$s was shot by %2$s' if lang == 'en_us' else '%1$s застрелен игроком %2$s'
        values['death.attack.techguns.bullet.player'] = values['death.attack.techguns.bullet']
        values['death.attack.techguns.bullet.item'] = '%1$s was shot by %2$s using %3$s' if lang == 'en_us' else '%1$s застрелен игроком %2$s с помощью %3$s'
        resource(f'assets/techguns/lang/{lang}.json', values)
    resource('assets/techguns/sounds.json', selected_sounds)
    for identifier, recipe in crafting['recipes'].items():
        resource(f'data/techguns/recipe/{identifier}.json', recipe)
    for identifier, values in crafting['tags'].items():
        namespace, name = identifier.split(':')
        resource(f'data/{namespace}/tags/item/{name}.json', {'replace': False, 'values': values})
    output('core/src/main/java/techguns/core/CraftingContent.java', '''package techguns.core;

import java.util.List;

/** Generated from the selected original workbench recipe graph. */
public final class CraftingContent {
    public static final List<String> MATERIALS = List.of(''' + ', '.join(json.dumps(s) for s in crafting['materials']) + ''');
    public static final List<String> EXTRA_AMMO = List.of(''' + ', '.join(json.dumps(s) for s in crafting['extra_ammo']) + ''');
    public static final List<String> RECIPE_IDS = List.of(''' + ', '.join(json.dumps(s) for s in crafting['recipes']) + ''');
    private CraftingContent() {}
}
''')
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
            if path.suffix in ('.json', '.java', '.obj', '.mtl'): actual = actual.replace(b'\r\n', b'\n')
            if actual != value: raise SystemExit(f'Stale generated file: {name}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value)
    print(f'{len(SELECTION)} weapon definitions, {len(files)} generated files: ' + ('verified' if args.check else 'written'))
