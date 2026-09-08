"""Check the modern item resource graph without launching a game or opening windows."""
from pathlib import Path
import json
import re
import math

ROOT = Path(__file__).resolve().parents[1] / 'platforms/neoforge-26.2/src/main/resources'
ASSETS = ROOT / 'assets/techguns'


def local_path(identifier, kind, suffix):
    namespace, value = identifier.split(':', 1)
    if namespace != 'techguns':
        return None
    if not re.fullmatch(r'[a-z0-9_./-]+', value) or '..' in value.split('/'):
        raise ValueError(f'Invalid resource identifier: {identifier}')
    return ASSETS / kind / (value + suffix)


def require(path):
    if path is not None and not path.is_file():
        raise ValueError(f'Missing resource: {path.relative_to(ROOT)}')


def check_item_model(definition):
    if definition['type'] == 'minecraft:model':
        require(local_path(definition['model'], 'models', '.json'))
    elif definition['type'] == 'minecraft:select' and definition['property'] == 'minecraft:display_context':
        check_item_model(definition['fallback'])
        for case in definition['cases']: check_item_model(case['model'])
    else:
        raise ValueError(f'Add validation for item model type {definition["type"]}')


def check_mesh(path):
    require(path)
    vertices, texcoords, faces = 0, 0, []
    for line in path.read_text(encoding='utf-8').splitlines():
        parts = line.split()
        if not parts: continue
        if parts[0] in ('v', 'vt'):
            if not all(math.isfinite(float(v)) for v in parts[1:]): raise ValueError(f'Invalid mesh coordinate: {path}')
            if parts[0] == 'v': vertices += 1
            else: texcoords += 1
        elif parts[0] == 'mtllib':
            if Path(parts[1]).name != parts[1]: raise ValueError('Material library must stay with mesh')
            require(path.parent / parts[1])
        elif parts[0] == 'f': faces.append(parts[1:])
    if not faces: raise ValueError(f'Mesh has no faces: {path}')
    for face in faces:
        if len(face) != 4: raise ValueError(f'Expected a quad: {path}')
        for pair in face:
            vertex, uv = map(int, pair.split('/'))
            if not (1 <= vertex <= vertices and 1 <= uv <= texcoords): raise ValueError(f'Invalid face indices: {path}')


def main():
    for path in ROOT.rglob('*.json'):
        json.loads(path.read_text(encoding='utf-8'))
    names = []
    for path in (ASSETS / 'blockstates').glob('*.json'):
        for variant in json.loads(path.read_text(encoding='utf-8'))['variants'].values():
            require(local_path(variant['model'], 'models', '.json'))
    for path in (ASSETS / 'items').glob('*.json'):
        definition = json.loads(path.read_text(encoding='utf-8'))['model']
        check_item_model(definition)
        names.append(path.stem)
    texture_count = 0
    for path in (ASSETS / 'models').rglob('*.json'):
        model = json.loads(path.read_text(encoding='utf-8'))
        if model.get('loader') == 'neoforge:obj': check_mesh(local_path(model['model'], '', ''))
        for texture in model.get('textures', {}).values():
            if texture.startswith('#'):
                continue
            require(local_path(texture, 'textures', '.png'))
            # Default 26.2 item atlas includes textures/item/, not the legacy items/ or guns/.
            atlas_path = 'techguns:block/' if path.parent.name == 'block' else 'techguns:item/'
            if texture.startswith('techguns:') and not texture.startswith(atlas_path):
                raise ValueError(f'Texture outside the item atlas: {texture}; add an explicit atlas source first')
            texture_count += 1
    for lang in ('en_us', 'ru_ru'):
        messages = json.loads((ASSETS / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
        for name in names:
            if not messages.get(f'item.techguns.{name}'):
                raise ValueError(f'Missing {lang} item name: {name}')
        if not messages.get('key.techguns.reload'):
            raise ValueError(f'Missing {lang} reload key name')
        for key in ('hud.techguns.ammo', 'hud.techguns.reloading'):
            if not messages.get(key): raise ValueError(f'Missing {lang} HUD text: {key}')
    sounds = json.loads((ASSETS / 'sounds.json').read_text(encoding='utf-8'))
    for event in sounds.values():
        for sound in event['sounds']:
            require(local_path(sound if isinstance(sound, str) else sound['name'], 'sounds', '.ogg'))
    def check_ingredient(value):
        if isinstance(value, str):
            if value.startswith('techguns:'): require(ASSETS / 'items' / (value.split(':')[1] + '.json'))
        elif value.get('neoforge:ingredient_type') == 'techguns:tag_fallback':
            if not value.get('preferred') or not value.get('fallback'): raise ValueError('Incomplete fallback ingredient')
        else: raise ValueError(f'Unsupported recipe ingredient: {value}')
    recipes = list((ROOT / 'data/techguns/recipe').rglob('*.json'))
    for path in recipes:
        recipe = json.loads(path.read_text(encoding='utf-8'))
        check_ingredient(recipe['result']['id'])
        for value in list(recipe.get('key', {}).values()) + recipe.get('ingredients', []): check_ingredient(value)
        if recipe['type'] == 'techguns:ammo_press':
            for key in ('metal1', 'metal2', 'powder'): check_ingredient(recipe[key])
        if recipe['type'] in ('techguns:metal_press', 'techguns:blast_furnace'):
            for key in ('first', 'second'): check_ingredient(recipe[key])
    for path in (ROOT / 'data/c/tags/item').rglob('*.json'):
        for value in json.loads(path.read_text(encoding='utf-8'))['values']: check_ingredient(value)
    print(f'Validated {len(names)} item definitions, {texture_count} texture references, {len(sounds)} sound events and {len(recipes)} recipes')


if __name__ == '__main__':
    main()
