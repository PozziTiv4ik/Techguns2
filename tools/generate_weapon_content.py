"""Generate the current port's weapon parameters, models and assets from the untouched 1.12.2 source."""
from pathlib import Path
import argparse
import json
import re
from legacy_models import strip_comments, numeric, convert_model, convert_mesh, extract_shapes
from legacy_crafting import plan_crafting
from legacy_machines import generate_machine_content, machine_translations
from legacy_ores import generate_ore_content, ore_translations
from legacy_fluids import generate_fluid_content, fluid_translations
from legacy_chemistry import generate_chemical_content, chemical_translations
from legacy_reactions import generate_reaction_content, reaction_translations
from legacy_radiation import generate_radiation_content, radiation_translations
from legacy_fabricator import generate_fabricator_content, fabricator_translations
from legacy_charging import generate_charging_content, charging_translations
from legacy_rockets import generate_rocket_content, rocket_item_model, rocket_translations
from legacy_npcs import generate_npc_content, npc_translations, SOUNDS as NPC_SOUNDS
from legacy_cyber import generate_cyber_content, cyber_translations
from legacy_armors import generate_armor_content, armor_translations
from legacy_repair import generate_repair_content, repair_translations
from legacy_camo import generate_camo_content, camo_translations
from legacy_items import arguments

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = Path('platforms/neoforge-26.2/src/main/resources')
SELECTION = json.loads((ROOT / 'content/weapon-ports.json').read_text())
GUI_HIDDEN_PARTS = {'m4_infiltrator': ('LaserBeam', 'LaserBeam01')}
MISSING_SOURCE_SOUNDS = {'guns.cyberdemonblasterreload'}  # Registered in TGSounds, absent from original sounds.json.


def parse_weapons():
    source = strip_comments((LEGACY / 'java/techguns/TGuns.java').read_text())
    ammo_source = strip_comments((LEGACY / 'java/techguns/items/guns/ammo/AmmoTypes.java').read_text())
    item_source = strip_comments((LEGACY / 'java/techguns/TGItems.java').read_text())
    item_ids = dict(re.findall(r'(\w+)\s*=\s*SHARED_ITEM\.addsharedVariant\("([^"]+)"', item_source))
    selectors = dict(re.findall(r'(\w+)\s*=\s*new ProjectileSelector(?:<[^;]+?>)?\(AmmoTypes\.(\w+)', source))
    projectile_classes = dict(re.findall(r'(\w+)\s*=\s*new ProjectileSelector<([\w]+)>\(', source))
    projectile_classes.update(dict(re.findall(r'(\w+)\s*=\s*new ProjectileSelector\(AmmoTypes\.\w+,\s*new (\w+)\.Factory\(', source)))
    render_source = strip_comments((LEGACY / 'java/techguns/client/ClientProxy.java').read_text())
    renderers = {identifier: (renderer, model) for identifier, renderer, model in re.findall(
        r'registerItemRenderer\(TGuns\.(\w+),\s*new (RenderGunBase90|RenderGunBase|RenderRocketLauncher)\(new (\w+)\(', render_source)}
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
        npc_ai = calls.get('setAIStats', ['15', '60', '0', '0'])
        shotgun = calls.get('setShotgunSpread', ['0', '0', 'false'])
        if shotgun[2] != 'false': raise ValueError(f'{identifier}: burst behavior not ported yet')
        ammo_item, empty, loose, bundles = ammo_for(args[1])
        inline_projectile = re.search(r'new ProjectileSelector<(\w+)>', args[1])
        projectile_class = inline_projectile[1] if inline_projectile else projectile_classes[args[1]]
        projectile = {'GenericProjectile': 'ballistic', 'StoneBulletProjectile': 'ballistic',
                      'LaserProjectile': 'laser', 'RocketProjectile': 'rocket',
                      'CyberdemonBlasterProjectile': 'nether_blaster'}.get(projectile_class)
        if projectile is None: raise ValueError(f'Projectile factory not ported: {projectile_class}')
        lifetime = int(num(args[9]))
        if projectile == 'laser':
            laser = strip_comments((LEGACY / 'java/techguns/entities/projectiles/LaserProjectile.java').read_text())
            lifetime = int(re.search(r'return new LaserProjectile\(world, p, damage, speed, (\d+),', laser)[1])
        renderer, bound_model = renderers[identifier]
        if bound_model != model: raise ValueError(f'Model does not match original renderer for {identifier}')
        texture = (calls.get('setTexture') or calls['setTextures'])[0].strip('"')
        result.append({'id': identifier, 'capacity': int(num(args[4])), 'fire_delay': int(num(args[3])),
            'reload_ticks': int(num(args[5])), 'damage': num(args[6]), 'minimum_damage': num(drop[2]),
            'drop_start': num(drop[0]), 'drop_end': num(drop[1]),
            'speed': num(calls.get('setBulletSpeed', ['2'])[0]), 'lifetime': lifetime,
            'projectile': projectile,
            'npc_ai': {'range':num(npc_ai[0]), 'interval':int(num(npc_ai[1])), 'burst':int(num(npc_ai[2])),
                       'shot_delay':int(num(npc_ai[3])), 'forward_offset':num(calls.get('setForwardOffset', ['0'])[0])},
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
            'model': model, 'texture': texture, 'forward_axis': '+x' if renderer in ('RenderGunBase90', 'RenderRocketLauncher') else '-z',
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
    output('core/src/main/java/techguns/core/NpcWeapons.java', '''package techguns.core;

/** Generated from TGuns.setAIStats and GenericGun.getAIAttack. */
public final class NpcWeapons {
    public static NpcAttackSpec forWeapon(String id) {
        return switch (id) {
''' + ''.join(f'            case "{g["id"]}" -> new NpcAttackSpec({g["npc_ai"]["range"]}, {g["npc_ai"]["interval"]}, {g["npc_ai"]["burst"]}, {g["npc_ai"]["shot_delay"]}, {g["npc_ai"]["forward_offset"]});\n' for g in weapons) + '''            default -> throw new IllegalArgumentException("Unported NPC weapon: " + id);
        };
    }
    private NpcWeapons() {}
}
''')
    crafting = plan_crafting(weapons)
    materials = set(crafting['materials'])
    data('content/crafting-content.json', crafting['catalog'])
    data('content/ballistic-weapons.json', [gun for gun in weapons if gun['projectile'] == 'ballistic'])
    data('content/laser-weapons.json', [gun for gun in weapons if gun['projectile'] == 'laser'])
    data('content/rocket-weapons.json', [gun for gun in weapons if gun['projectile'] == 'rocket'])
    data('content/nether-weapons.json', [gun for gun in weapons if gun['projectile'] == 'nether_blaster'])
    definitions = []
    ammo_items = set(crafting['extra_ammo'])
    sounds_data = json.loads(resolve_asset('sounds.json').read_text())
    selected_sounds = {}
    render_source = strip_comments((LEGACY / 'java/techguns/client/ClientProxy.java').read_text())
    custom_items = {identifier: (model, empty == 'true', texture) for identifier, model, empty, texture in re.findall(
        r'addRenderForType\("([^"]+)",\s*new \w+\(new (\w+)\((true|false)\),\s*new ResourceLocation\(Techguns.MODID,\s*"([^"]+)"\)', render_source)}
    custom_items.update({identifier:(model,None,texture) for identifier,model,texture in re.findall(
        r'addRenderForType\("([^"]+)",\s*new \w+\(new (\w+)\(\),\s*new ResourceLocation\(Techguns.MODID,\s*"([^"]+)"\)',render_source)})
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
        definitions.append(f'        new WeaponDefinition(new WeaponSpec({java_stats}), {ammo_java}, ProjectileKind.{gun["projectile"].upper()}, {str(gun["automatic"]).lower()}, {gun["extra_pellets"]}, {gun["pellet_spread"]}, {gun["gravity"]}, {gun["penetration"]}, new AimSpec({gun["zoom"]}f, {str(gun["zoom_toggle"]).lower()}, {gun["zoom_accuracy"]}f, {str(gun["zoom_centered"]).lower()}), "{gun["fire_sound"]}", "{gun["reload_sound"]}")')
        source = (LEGACY / f'java/techguns/client/models/guns/{gun["model"]}.java').read_text()
        _, _, shapes = extract_shapes(source, gun['model'])
        gui_hidden = GUI_HIDDEN_PARTS.get(identifier, ())
        mesh = gun['projectile'] == 'rocket' or gui_hidden or any(s['inflate'] != 0 or s['render_scale'] != [1, 1, 1] or s['mirror'] for s in shapes)
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
            selected_sounds[sound] = {'sounds': sounds_data[sound]['sounds'] if sound not in MISSING_SOURCE_SOUNDS else []}
    for identifier in sorted(ammo_items | materials):
        if identifier in custom_items:
            class_name, empty, texture = custom_items[identifier]
            candidates=list((LEGACY/'java/techguns/client/models').rglob(class_name+'.java'))
            if len(candidates)!=1: raise ValueError('Ambiguous custom item model: '+class_name)
            source = candidates[0].read_text()
            model, obj, material = convert_mesh(source, class_name, identifier, f'techguns:item/{identifier}', '+x' if class_name=='ModelRocket' else '-z', constructor_values={} if empty is None else {'empty': empty})
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
        if identifier == 'rocketlauncher': model = rocket_item_model()
        if identifier in GUI_HIDDEN_PARTS:
            model = {'type': 'minecraft:select', 'property': 'minecraft:display_context', 'fallback': model,
                     'cases': [{'when': ['gui'], 'model': {'type': 'minecraft:model', 'model': f'techguns:item/{identifier}_gui'}}]}
        resource(f'assets/techguns/items/{identifier}.json', {'model': model})
        for lang in languages:
            key = f'item.techguns.{identifier}.name'
            translated[lang][f'item.techguns.{identifier}'] = languages[lang].get(key, languages['en_us'].get(key, identifier))
    for name in ('machines.ammopresswork1', 'machines.ammopresswork2', 'machines.metalpresswork', 'machines.chemlabwork', 'machines.rc_heatraywork', 'machines.rc_beep', 'machines.rc_warning', 'effects.geiger.low', 'effects.geiger.high', 'machines.fabricatorwork', 'machines.chargingstationwork', 'effects.nukeexplosion'):
        selected_sounds[name] = {'sounds': sounds_data[name]['sounds']}
    for name in NPC_SOUNDS: selected_sounds[name] = {'sounds':sounds_data[name]['sounds']}
    for sound, value in selected_sounds.items():
        subtitle = f'subtitles.techguns.{sound}'
        value['subtitle'] = subtitle
        for lang in languages:
            reloading = 'reload' in sound
            translated[lang][subtitle] = ('Weapon reloads' if reloading else 'Gunshot') if lang == 'en_us' else ('Перезарядка оружия' if reloading else 'Выстрел')
            if sound.startswith('machines.'):
                translated[lang][subtitle] = ('Metal Press works' if lang == 'en_us' else 'Работает металлический пресс') if sound == 'machines.metalpresswork' else ('Ammo Press works' if lang == 'en_us' else 'Работает пресс для патронов')
                if sound=='machines.chemlabwork': translated[lang][subtitle]='Chemical Laboratory works' if lang=='en_us' else 'Работает химическая лаборатория'
                if sound.startswith('machines.rc_'): translated[lang][subtitle]=('Reaction chamber warning' if sound.endswith('warning') else 'Reaction chamber check') if lang=='en_us' else ('Предупреждение реакционной камеры' if sound.endswith('warning') else 'Проверка реакционной камеры')
                if sound=='machines.fabricatorwork': translated[lang][subtitle]='Fabricator works' if lang=='en_us' else 'Работает фабрикатор'
                if sound=='machines.chargingstationwork': translated[lang][subtitle]='Charging Station works' if lang=='en_us' else 'Работает зарядная станция'
            if sound.startswith('effects.geiger.'): translated[lang][subtitle]='Geiger counter clicks' if lang=='en_us' else 'Щёлкает счётчик Гейгера'
            if sound == 'effects.nukeexplosion': translated[lang][subtitle] = 'Nuclear explosion' if lang == 'en_us' else 'Ядерный взрыв'
            if sound in NPC_SOUNDS: translated[lang][subtitle] = 'Cyber Demon or Super Mutant' if lang == 'en_us' else 'Кибердемон или супермутант'
        for entry in value['sounds']:
            name = entry if isinstance(entry, str) else entry['name']
            path = f'sounds/{name.split(":")[-1]}.ogg'
            files[(RESOURCES / 'assets/techguns' / path).as_posix()] = resolve_asset(path).read_bytes()
    for lang, values in translated.items():
        values.update(machine_translations(lang))
        values['hud.techguns.ammo'] = '%s / %s'
        values['hud.techguns.reloading'] = 'Reloading' if lang == 'en_us' else 'Перезарядка'
        values['entity.techguns.bullet'] = 'Bullet' if lang == 'en_us' else 'Пуля'
        values['death.attack.techguns.bullet'] = '%1$s was shot by %2$s' if lang == 'en_us' else '%1$s застрелен игроком %2$s'
        values['death.attack.techguns.bullet.player'] = values['death.attack.techguns.bullet']
        values['death.attack.techguns.bullet.item'] = '%1$s was shot by %2$s using %3$s' if lang == 'en_us' else '%1$s застрелен игроком %2$s с помощью %3$s'
        values.update(ore_translations(lang))
        values.update(fluid_translations(lang))
        values.update(chemical_translations(lang))
        values.update(reaction_translations(lang))
        values.update(radiation_translations(lang))
        values.update(fabricator_translations(lang))
        values.update(charging_translations(lang))
        values.update(repair_translations(lang))
        values.update(camo_translations(lang))
        values.update(rocket_translations(lang))
        values.update(cyber_translations(lang))
        values.update(armor_translations(lang))
        values.update(npc_translations(lang))
        values['entity.techguns.laser_beam'] = 'Laser beam' if lang == 'en_us' else 'Лазерный луч'
        values['death.attack.techguns.laser'] = '%1$s was lasered by %2$s' if lang == 'en_us' else '%1$s убит лазером игрока %2$s'
        values['death.attack.techguns.laser.player'] = values['death.attack.techguns.laser']
        values['death.attack.techguns.laser.item'] = '%1$s was lasered by %2$s using %3$s' if lang == 'en_us' else '%1$s убит игроком %2$s с помощью %3$s'
        resource(f'assets/techguns/lang/{lang}.json', values)
    resource('assets/techguns/sounds.json', selected_sounds)
    # This tag used to be a handwritten resource. Own it here before other damage domains contribute.
    resource('data/minecraft/tags/damage_type/bypasses_cooldown.json', {'replace':False,'values':['techguns:bullet','techguns:laser']})
    resource('data/minecraft/tags/damage_type/no_knockback.json', {'replace':False,'values':['techguns:laser']})
    resource('data/neoforge/tags/damage_type/is_magic.json', {'replace':False,'values':['techguns:laser']})
    resource('data/minecraft/tags/damage_type/witch_resistant_to.json', {'replace':False,'values':['techguns:laser']})
    resource('data/techguns/damage_type/laser.json', {'message_id':'techguns.laser','scaling':'when_caused_by_living_non_player','exhaustion':0.1})
    for texture in ('laser3', 'laser3_start'):
        path = f'textures/fx/{texture}.png'
        files[(RESOURCES / 'assets/techguns' / path).as_posix()] = resolve_asset(path).read_bytes()
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
    files.update(generate_machine_content())
    for path, value in [entry for domain in (generate_ore_content(), generate_fluid_content(), generate_chemical_content(), generate_reaction_content(), generate_radiation_content(), generate_fabricator_content(), generate_charging_content(), generate_rocket_content(), generate_npc_content(), generate_cyber_content(), generate_armor_content(), generate_repair_content(), generate_camo_content()) for entry in domain.items()]:
        if path in files:
            # Several content domains contribute to the same mining/tool and common item tags.
            if '/tags/' not in path:
                if files[path] == value: continue  # Workbenches share the original side texture.
                raise ValueError(f'Colliding generated resource: {path}')
            merged = json.loads(files[path])
            values = {json.dumps(v,sort_keys=True):v for v in merged['values'] + json.loads(value)['values']}
            merged['values'] = [values[key] for key in sorted(values)]
            data(path, merged)
        else: files[path] = value
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
