"""Convert the static legacy revolver cuboids, and copy only the assets used by the port.

This handles ModelRevolver's single-axis rotations only. It intentionally fails on an
unrecognised shape rather than silently dropping geometry. Display transforms require
in-game visual QA; this does not port the old procedural renderer/animations.
"""
from pathlib import Path
import argparse
import json
import math
import re

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "legacy/1.12.2/src/main"
OUT = ROOT / "platforms/neoforge-26.2/src/main/resources"


def build_files():
    files = {}

    def json_file(path, value):
        files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()

    def numbers(text):
        return [float(value.strip().rstrip("fFdD")) for value in text.split(",")]

    def point(x, y, z):
        return [round(3.5 + x * .5, 5), round(10.175 - y * .5, 5), round(7.5 + z * .5, 5)]

    source = (LEGACY / "java/techguns/client/models/guns/ModelRevolver.java").read_text()
    shapes = re.findall(r'(Shape\d+) = new ModelRenderer\(this, (\d+), (\d+)\);(.*?)(?=\n\s*Shape\d+ =|\n\s*\})', source, re.S)
    elements = []
    for name, u, v, body in shapes:
        box = re.search(r'\.addBox\(([^)]+)\)', body)
        pivot = re.search(r'\.setRotationPoint\(([^)]+)\)', body)
        rotation = re.search(r'setRotation\(' + name + r', ([^)]+)\)', body)
        if not all((box, pivot, rotation)):
            raise ValueError(f"Incomplete shape {name}")
        x, y, z, dx, dy, dz = numbers(box[1])
        px, py, pz = numbers(pivot[1])
        u, v = int(u), int(v)
        # Legacy ModelBox atlas layout, converted to JSON UV units (16x16).
        uv = {
            'east': [u+dz+dx, v+dz, u+dz+dx+dz, v+dz+dy],
            'west': [u, v+dz, u+dz, v+dz+dy],
            'down': [u+dz, v, u+dz+dx, v+dz],
            'up': [u+dz+dx, v+dz, u+dz+dx+dx, v],
            'north': [u+dz, v+dz, u+dz+dx, v+dz+dy],
            'south': [u+dz+dx+dz, v+dz, u+dz+dx+dz+dx, v+dz+dy],
        }
        element = {'name': name, 'from': point(px+x, py+y+dy, pz+z),
                   'to': point(px+x+dx, py+y, pz+z+dz),
                   'faces': {face: {'uv': [a/4, b/2, c/4, d/2], 'texture': '#gun'}
                             for face, (a, b, c, d) in uv.items()}}
        active = [(axis, angle) for axis, angle in zip('xyz', numbers(rotation[1])) if abs(angle) > .00001]
        if len(active) > 1:
            raise ValueError(f"Multi-axis shape {name} needs a mesh converter")
        if active:
            axis, angle = active[0]
            degrees = round(math.degrees(angle) / 22.5) * 22.5
            if abs(math.degrees(angle) - degrees) > .001:
                raise ValueError(f"Unsupported rotation in {name}")
            element['rotation'] = {'origin': point(px, py, pz), 'axis': axis,
                                   'angle': degrees * (-1 if axis in 'xz' else 1)}
        elements.append(element)
    if len(elements) != 20:
        raise ValueError(f"Expected 20 legacy cuboids, found {len(elements)}")
    json_file('assets/techguns/models/item/revolver.json', {
        'credit': 'Converted from original Techguns ModelRevolver.java; Techguns Mod License',
        'textures': {'gun': 'techguns:item/revolver', 'particle': 'techguns:item/revolver'},
        'elements': elements,
        'display': {
            'gui': {'rotation': [15, -20, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
            'firstperson_righthand': {'rotation': [0, -90, 0], 'translation': [1, 1, -1], 'scale': [1, 1, 1]},
            'firstperson_lefthand': {'rotation': [0, 90, 0], 'translation': [1, 1, -1], 'scale': [1, 1, 1]},
            'thirdperson_righthand': {'rotation': [0, -90, 0], 'translation': [0, 2, 1], 'scale': [.8, .8, .8]},
            'thirdperson_lefthand': {'rotation': [0, 90, 0], 'translation': [0, 2, 1], 'scale': [.8, .8, .8]},
            'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
            'fixed': {'rotation': [0, 180, 0], 'scale': [1, 1, 1]},
        }})
    json_file('assets/techguns/models/item/pistolrounds.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'techguns:item/pistolrounds'}})
    for item in ('revolver', 'pistolrounds'):
        json_file(f'assets/techguns/items/{item}.json', {
            'model': {'type': 'minecraft:model', 'model': f'techguns:item/{item}'}})
    for lang, values in {
        'en_us': {'itemGroup.techguns': 'Techguns · Development', 'item.techguns.revolver': 'Revolver',
                  'item.techguns.pistolrounds': 'Pistol Rounds', 'subtitles.techguns.revolver_fire': 'Revolver fires',
                  'subtitles.techguns.revolver_reload': 'Revolver reloads'},
        'ru_ru': {'itemGroup.techguns': 'Techguns · В разработке', 'item.techguns.revolver': 'Револьвер',
                  'item.techguns.pistolrounds': 'Пистолетные патроны', 'subtitles.techguns.revolver_fire': 'Выстрел револьвера',
                  'subtitles.techguns.revolver_reload': 'Перезарядка револьвера'}
    }.items():
        json_file(f'assets/techguns/lang/{lang}.json', values)
    sound_data = json.loads((LEGACY / 'resources/assets/techguns/sounds.json').read_text())
    sounds = {}
    for event, subtitle in [('guns.revolverfire', 'revolver_fire'), ('guns.revolverreload', 'revolver_reload')]:
        sounds[event] = {'subtitle': f'subtitles.techguns.{subtitle}', 'sounds': sound_data[event]['sounds']}
        for sound in sounds[event]['sounds']:
            path = f"assets/techguns/sounds/{sound.split(':')[1]}.ogg"
            files[path] = (LEGACY / 'resources' / path).read_bytes()
    json_file('assets/techguns/sounds.json', sounds)
    for source, target in [('guns/revolver', 'item/revolver'), ('items/pistolrounds', 'item/pistolrounds')]:
        files[f'assets/techguns/textures/{target}.png'] = (LEGACY / f'resources/assets/techguns/textures/{source}.png').read_bytes()
    return files


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    files = build_files()
    for name, data in files.items():
        path = OUT / name
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                raise SystemExit(f'Stale generated resource: {name}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    print(f'Checked {len(files)} resources' if args.check else f'Generated {len(files)} resources; 20 revolver cuboids')
