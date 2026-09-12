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
    elif definition['type'] == 'minecraft:select' and definition['property'] in ('minecraft:display_context', 'minecraft:component'):
        check_item_model(definition['fallback'])
        for case in definition['cases']: check_item_model(case['model'])
    elif definition['type'] == 'minecraft:composite':
        for model in definition['models']: check_item_model(model)
    elif definition['type'] == 'minecraft:condition' and definition['property'] == 'techguns:rocket_loaded':
        check_item_model(definition['on_true']); check_item_model(definition['on_false'])
    elif definition['type'] == 'minecraft:empty': pass
    elif definition['type'] == 'neoforge:fluid_container':
        if not definition.get('fluid') or not definition.get('textures'): raise ValueError('Incomplete fluid container model')
        for texture in definition['textures'].values(): require(local_path(texture,'textures','.png'))
    else:
        raise ValueError(f'Add validation for item model type {definition["type"]}')


def check_mesh(path, textures=None):
    require(path)
    vertices, texcoords, normals, faces = 0, 0, 0, []
    for line in path.read_text(encoding='utf-8').splitlines():
        parts = line.split()
        if not parts: continue
        if parts[0] in ('v', 'vt', 'vn'):
            if not all(math.isfinite(float(v)) for v in parts[1:]): raise ValueError(f'Invalid mesh coordinate: {path}')
            if parts[0] == 'v': vertices += 1
            elif parts[0] == 'vt':
                texcoords += 1
                # OBJ's optional third texture coordinate is not part of the 2D sprite UV.
                if any(float(v) < -1e-7 or float(v) > 1+1e-7 for v in parts[1:3]):
                    raise ValueError(f'OBJ UV outside atlas sprite: {path}')
            else: normals += 1
        elif parts[0] == 'mtllib':
            if Path(parts[1]).name != parts[1]: raise ValueError('Material library must stay with mesh')
            require(path.parent / parts[1])
            for material in (path.parent / parts[1]).read_text(encoding='utf-8').splitlines():
                if material.startswith('map_Kd '):
                    texture=material.split()[1]
                    if not texture.startswith('#'):
                        raise ValueError(f'26.2 OBJ texture must reference a JSON texture slot: {path}: {texture}')
                    if textures is not None and texture[1:] not in textures:
                        raise ValueError(f'Missing OBJ texture slot: {path}: {texture}')
        elif parts[0] == 'f': faces.append(parts[1:])
    if not faces: raise ValueError(f'Mesh has no faces: {path}')
    for face in faces:
        if len(face) not in (3,4): raise ValueError(f'Expected a triangle or quad: {path}')
        for pair in face:
            indices=list(map(int,pair.split('/')))
            if len(indices) not in (2,3): raise ValueError(f'Missing mesh texture index: {path}')
            vertex, uv = indices[:2]
            if not (1 <= vertex <= vertices and 1 <= uv <= texcoords): raise ValueError(f'Invalid face indices: {path}')
            if len(indices)==3 and not 1<=indices[2]<=normals: raise ValueError(f'Invalid normal index: {path}')


def main():
    for path in ROOT.rglob('*.json'):
        json.loads(path.read_text(encoding='utf-8'))
    for path in (ASSETS/'equipment').glob('*.json'):
        for layer, entries in json.loads(path.read_text(encoding='utf-8'))['layers'].items():
            if layer not in ('humanoid','humanoid_leggings'): raise ValueError(f'Unvalidated equipment layer: {path}: {layer}')
            for entry in entries:
                require(local_path(entry['texture'],f'textures/entity/equipment/{layer}','.png'))
    for name in ('laser3', 'laser3_start'):
        require(local_path(f'techguns:fx/{name}', 'textures', '.png'))
    fluid_catalog=json.loads((Path(__file__).resolve().parents[1]/'content/fluids.json').read_text(encoding='utf-8'))
    for fluid in fluid_catalog['fluids']:
        for key in ('still_texture','flow_texture'):
            texture=local_path(fluid[key],'textures','.png'); require(texture); require(Path(str(texture)+'.mcmeta'))
            json.loads(Path(str(texture)+'.mcmeta').read_text(encoding='utf-8'))
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
        if 'render_type' in model and model.get('loader') in (None,'neoforge:obj'):
            raise ValueError(f'Obsolete render_type hint in 26.2 model: {path}; transparency comes from the material and sprite alpha')
        if model.get('loader') == 'neoforge:obj': check_mesh(local_path(model['model'], '', ''), model.get('textures', {}))
        for element in model.get('elements', []):
            for face in element['faces'].values():
                if any(not math.isfinite(v) or v < 0 or v > 16 for v in face.get('uv', [])):
                    raise ValueError(f'Cuboid UV outside atlas sprite: {path}: {element.get("name", "unnamed")}')
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
        if 'result' in recipe: check_ingredient(recipe['result']['id'])
        for value in list(recipe.get('key', {}).values()) + recipe.get('ingredients', []): check_ingredient(value)
        if 'ingredient' in recipe: check_ingredient(recipe['ingredient'])
        if recipe['type'] == 'techguns:ammo_press':
            for key in ('metal1', 'metal2', 'powder'): check_ingredient(recipe[key])
        if recipe['type'] in ('techguns:metal_press', 'techguns:blast_furnace'):
            for key in ('first', 'second'): check_ingredient(recipe[key])
        if recipe['type']=='techguns:chem_lab':
            for key in ('first','second','bottle'):
                if key in recipe: check_ingredient(recipe[key]['ingredient'])
        if recipe['type']=='techguns:reaction_chamber':
            for key in ('input','focus'): check_ingredient(recipe[key])
            for result in recipe['results']: check_ingredient(result['id'])
        if recipe['type']=='techguns:fabricator':
            for key in ('input','wire','powder','plate'): check_ingredient(recipe[key]['ingredient'])
        if recipe['type']=='techguns:charging_station':
            check_ingredient(recipe['input']['ingredient'])
            if not 1 <= recipe['input']['count'] <= 64 or not 1 <= recipe['charge_amount'] <= 57600000:
                raise ValueError('Invalid Charging Station recipe amounts')
        if recipe['type']=='techguns:grinder':
            check_ingredient(recipe['input'])
            if (not recipe.get('armor') and not recipe['outputs']) or len(recipe['outputs']) > 9:
                raise ValueError('Invalid Grinder output slots')
            for output in recipe['outputs']:
                check_ingredient(output['result']['id'])
                if not 0 <= output.get('factor',1) <= 64 or output['result'].get('count',1) < 1:
                    raise ValueError('Invalid Grinder output factor/count')
                if 'preferred_tag' in output and not isinstance(output['preferred_tag'],str):
                    raise ValueError('Invalid Grinder preferred material tag')
    for path in (ROOT / 'data/c/tags/item').rglob('*.json'):
        for value in json.loads(path.read_text(encoding='utf-8'))['values']: check_ingredient(value)
    print(f'Validated {len(names)} item definitions, {texture_count} texture references, {len(sounds)} sound events and {len(recipes)} recipes')


if __name__ == '__main__':
    main()
