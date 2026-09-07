"""Check the modern item resource graph without launching a game or opening windows."""
from pathlib import Path
import json
import re

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


def main():
    for path in ROOT.rglob('*.json'):
        json.loads(path.read_text(encoding='utf-8'))
    names = []
    for path in (ASSETS / 'items').glob('*.json'):
        definition = json.loads(path.read_text(encoding='utf-8'))['model']
        if definition['type'] != 'minecraft:model':
            raise ValueError(f'Add validation for item model type in {path.name}')
        require(local_path(definition['model'], 'models', '.json'))
        names.append(path.stem)
    texture_count = 0
    for path in (ASSETS / 'models').rglob('*.json'):
        model = json.loads(path.read_text(encoding='utf-8'))
        for texture in model.get('textures', {}).values():
            if texture.startswith('#'):
                continue
            require(local_path(texture, 'textures', '.png'))
            # Default 26.2 item atlas includes textures/item/, not the legacy items/ or guns/.
            if texture.startswith('techguns:') and not texture.startswith('techguns:item/'):
                raise ValueError(f'Texture outside the item atlas: {texture}; add an explicit atlas source first')
            texture_count += 1
    for lang in ('en_us', 'ru_ru'):
        messages = json.loads((ASSETS / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
        for name in names:
            if not messages.get(f'item.techguns.{name}'):
                raise ValueError(f'Missing {lang} item name: {name}')
        if not messages.get('key.techguns.reload'):
            raise ValueError(f'Missing {lang} reload key name')
    sounds = json.loads((ASSETS / 'sounds.json').read_text(encoding='utf-8'))
    for event in sounds.values():
        for sound in event['sounds']:
            require(local_path(sound if isinstance(sound, str) else sound['name'], 'sounds', '.ogg'))
    print(f'Validated {len(names)} item definitions, {texture_count} texture references and {len(sounds)} sound events')


if __name__ == '__main__':
    main()
