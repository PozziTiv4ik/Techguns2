"""Convert legacy ModelRenderer cuboids to the 26.2 cuboid format, preserving vertex UVs.

Only constructor geometry is converted. Procedural animations are a separate porting task.
ModelRenderer uses Y-down and Rz*Ry*Rx; JSON uses Y-up and supports Euler rotations in 26.2.
"""
import math
import re


def strip_comments(source):
    return re.sub(r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')|//[^\n]*|/\*.*?\*/',
                  lambda m: m[1] if m[1] else '', source, flags=re.S)


def numeric(text):
    token = text.strip().rstrip('fFdD')
    if not re.fullmatch(r'-?(?:\d+(?:\.\d*)?|\.\d+)', token):
        raise ValueError(f'Unsupported numeric expression: {text}')
    return float(token)


def extract_shapes(source, class_name):
    source = strip_comments(source)
    constructor = re.search(r'public\s+' + re.escape(class_name) + r'\s*\(\s*\)\s*\{', source)
    if not constructor:
        raise ValueError(f'No default constructor: {class_name}')
    start = constructor.end()
    depth, end = 1, start
    while depth:
        if source[end] == '{': depth += 1
        if source[end] == '}': depth -= 1
        end += 1
    body = source[start:end - 1]
    if '.addChild(' in body:
        raise ValueError(f'{class_name}: hierarchical model requires hierarchy conversion')
    width = int(re.search(r'textureWidth\s*=\s*(\d+)', body)[1])
    height = int(re.search(r'textureHeight\s*=\s*(\d+)', body)[1])
    shapes = []
    matches = list(re.finditer(r'(\w+)\s*=\s*new ModelRenderer\(this,\s*(\d+),\s*(\d+)\);', body))
    for i, match in enumerate(matches):
        name, u, v = match.groups()
        chunk = body[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(body)]
        boxes = list(re.finditer(re.escape(name) + r'\.addBox\(([^)]+)\)', chunk))
        if len(boxes) != 1:
            raise ValueError(f'{class_name}.{name}: expected one box, got {len(boxes)}')
        if re.search(re.escape(name) + r'\.mirror\s*=\s*true', chunk[:boxes[0].start()]):
            raise ValueError(f'{class_name}.{name}: mirrored constructor box requires UV mirroring')
        values = [numeric(v) for v in boxes[0][1].split(',')]
        if len(values) != 6:
            raise ValueError(f'{class_name}.{name}: unsupported addBox overload')
        pivot_match = re.search(re.escape(name) + r'\.setRotationPoint\(([^)]+)\)', chunk)
        rotation_match = re.search(r'setRotation\(' + re.escape(name) + r',\s*([^)]+)\)', chunk)
        if not pivot_match or not rotation_match:
            raise ValueError(f'{class_name}.{name}: incomplete transform')
        shapes.append({'name': name, 'uv': [int(u), int(v)], 'box': values,
                       'pivot': [numeric(v) for v in pivot_match[1].split(',')],
                       'rotation': [numeric(v) for v in rotation_match[1].split(',')]})
    if len(shapes) != len(re.findall(r'\.addBox\(', body)):
        raise ValueError(f'{class_name}: unparsed constructor geometry')
    return width, height, shapes


def texture_faces(u, v, dx, dy, dz, width, height):
    # Derive UVs at vertices after reflection in Y, matching vanilla FaceInfo vertex order.
    # Side faces reverse U; top and bottom change places and reverse V.
    old = {
        'east': [u+dz+dx, v+dz, u+dz+dx+dz, v+dz+dy],
        'west': [u, v+dz, u+dz, v+dz+dy],
        'north': [u+dz, v+dz, u+dz+dx, v+dz+dy],
        'south': [u+dz+dx+dz, v+dz, u+dz+dx+dz+dx, v+dz+dy],
    }
    faces = {face: [c, b, a, d] for face, (a, b, c, d) in old.items()}
    faces['up'] = [u+dz, v+dz, u+dz+dx, v]
    faces['down'] = [u+dz+dx, v, u+dz+2*dx, v+dz]
    return {face: {'uv': [round(a*16/width, 7), round(b*16/height, 7),
                         round(c*16/width, 7), round(d*16/height, 7)], 'texture': '#gun'}
            for face, (a, b, c, d) in faces.items()}


def display_transforms():
    # Model muzzle is +X. First-person camera forward is -Z. ItemTransform.apply
    # already negates Y/Z rotations for the left hand: account for that exactly once.
    return {
        'gui': {'rotation': [15, -20, 0], 'scale': [.75, .75, .75]},
        'firstperson_righthand': {'rotation': [0, 90, 0], 'translation': [1, 1, -1], 'scale': [1, 1, 1]},
        'firstperson_lefthand': {'rotation': [0, -90, 0], 'translation': [1, 1, -1], 'scale': [1, 1, 1]},
        'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 2, 1], 'scale': [.8, .8, .8]},
        'thirdperson_lefthand': {'rotation': [0, -90, 0], 'translation': [0, 2, 1], 'scale': [.8, .8, .8]},
        'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [.75, .75, .75]},
    }


def convert_model(source, class_name, texture):
    width, height, shapes = extract_shapes(source, class_name)
    lo = [min(s['pivot'][i] + s['box'][i] for s in shapes) for i in range(3)]
    hi = [max(s['pivot'][i] + s['box'][i] + s['box'][i+3] for s in shapes) for i in range(3)]
    center = [(a+b)/2 for a, b in zip(lo, hi)]
    factor = min(.5, 40/max(b-a for a, b in zip(lo, hi)))

    def point(p):
        return [round(8 + (value-mid)*factor*(1 if i != 1 else -1), 6)
                for i, (value, mid) in enumerate(zip(p, center))]

    elements = []
    for shape in shapes:
        x, y, z, dx, dy, dz = shape['box']
        px, py, pz = shape['pivot']
        element = {'name': shape['name'], 'from': point([px+x, py+y+dy, pz+z]),
                   'to': point([px+x+dx, py+y, pz+z+dz]),
                   'faces': texture_faces(*shape['uv'], dx, dy, dz, width, height)}
        if any(abs(v) > 1e-8 for v in shape['rotation']):
            rx, ry, rz = [math.degrees(v) for v in shape['rotation']]
            element['rotation'] = {'origin': point(shape['pivot']), 'x': round(-rx, 7),
                                   'y': round(ry, 7), 'z': round(-rz, 7)}
        elements.append(element)
    return {'credit': f'Converted from original Techguns {class_name}; Techguns Mod License',
            'textures': {'gun': texture, 'particle': texture}, 'elements': elements,
            'display': display_transforms()}
