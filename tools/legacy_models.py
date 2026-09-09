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


def boolean_blocks(source, values):
    for name, value in values.items():
        pattern = re.compile(r'if\s*\(\s*(!?)' + re.escape(name) + r'\s*\)\s*\{')
        while match := pattern.search(source):
            depth, end = 1, match.end()
            while depth:
                if source[end] == '{': depth += 1
                elif source[end] == '}': depth -= 1
                end += 1
            keep = bool(value) != bool(match[1])
            source = source[:match.start()] + (source[match.end():end-1] if keep else '') + source[end:]
    return source


def extract_shapes(source, class_name, constructor_values=None):
    source = strip_comments(source)
    constructor = re.search(r'public\s+' + re.escape(class_name) + r'\s*\(([^)]*)\)\s*\{', source)
    if not constructor:
        raise ValueError(f'No constructor: {class_name}')
    parameters = re.findall(r'boolean\s+(\w+)', constructor[1])
    if constructor[1].strip() and set(parameters) != set(constructor_values or {}):
        raise ValueError(f'{class_name}: supply explicit constructor parameters')
    start = constructor.end()
    depth, end = 1, start
    while depth:
        if source[end] == '{': depth += 1
        if source[end] == '}': depth -= 1
        end += 1
    body = boolean_blocks(source[start:end - 1], constructor_values or {})
    if '.addChild(' in body:
        raise ValueError(f'{class_name}: hierarchical model requires hierarchy conversion')
    width = int(re.search(r'textureWidth\s*=\s*(\d+)', body)[1])
    height = int(re.search(r'textureHeight\s*=\s*(\d+)', body)[1])
    shapes = []
    matches = list(re.finditer(r'(\w+)\s*=\s*new ModelRenderer\(this,\s*(-?\d+),\s*(-?\d+)\);', body))
    for i, match in enumerate(matches):
        name, u, v = match.groups()
        chunk = body[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(body)]
        boxes = list(re.finditer(re.escape(name) + r'\.addBox\(([^)]+)\)', chunk))
        if len(boxes) != 1:
            raise ValueError(f'{class_name}.{name}: expected one box, got {len(boxes)}')
        mirror_assignments = re.findall(re.escape(name) + r'\.mirror\s*=\s*(true|false)', chunk[:boxes[0].start()])
        mirror = bool(mirror_assignments and mirror_assignments[-1] == 'true')
        values = [numeric(v) for v in boxes[0][1].split(',')]
        if len(values) not in (6, 7):
            raise ValueError(f'{class_name}.{name}: unsupported addBox overload')
        pivot_match = re.search(re.escape(name) + r'\.setRotationPoint\(([^)]+)\)', chunk)
        rotation_match = re.search(r'setRotation\(' + re.escape(name) + r',\s*([^)]+)\)', chunk)
        if not pivot_match:
            raise ValueError(f'{class_name}.{name}: incomplete transform')
        shapes.append({'name': name, 'uv': [int(u), int(v)], 'box': values[:6],
                       'mirror': mirror,
                       'inflate': values[6] if len(values) == 7 else 0,
                       'pivot': [numeric(v) for v in pivot_match[1].split(',')],
                       'rotation': [numeric(v) for v in rotation_match[1].split(',')] if rotation_match else [0, 0, 0]})
    if len(shapes) != len(re.findall(r'\.addBox\(', body)):
        raise ValueError(f'{class_name}: unparsed constructor geometry')
    # Some Tabula exports scale a part around its pivot immediately before rendering.
    # Bake those static transforms in OBJ rather than losing them when replacing OpenGL.
    static_scales = {}
    for section in re.findall(r'GlStateManager\.pushMatrix\(\);(.*?)GlStateManager\.popMatrix\(\);', source[end:], re.S):
        scale_calls = re.findall(r'GlStateManager\.scale\(([^)]+)\)', section)
        render_calls = re.findall(r'(\w+)\.render\(', section)
        if scale_calls:
            if len(scale_calls) != 1 or len(render_calls) != 1:
                raise ValueError(f'{class_name}: unsupported static render transform')
            static_scales[render_calls[0]] = [numeric(v) for v in scale_calls[0].split(',')]
    for shape in shapes:
        shape['render_scale'] = static_scales.get(shape['name'], [1, 1, 1])
    return width, height, shapes


def repeat_uv_interval(first, last, period):
    """Move a legacy GL_REPEAT interval into one atlas sprite without changing its span/direction."""
    if min(first, last) >= 0 and max(first, last) <= period: return first, last
    offset = math.floor(min(first, last) / period) * period
    wrapped = first - offset, last - offset
    if max(wrapped) > period:
        raise ValueError('UV interval crosses a repeat seam; split the face before atlas conversion')
    return wrapped


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
    result = {}
    for face, (a, b, c, d) in faces.items():
        a, c = repeat_uv_interval(a, c, width)
        b, d = repeat_uv_interval(b, d, height)
        result[face] = {'uv': [round(a*16/width, 7), round(b*16/height, 7),
                              round(c*16/width, 7), round(d*16/height, 7)], 'texture': '#gun'}
    return result


def display_transforms(forward='+x'):
    # Model muzzle is +X or -Z. First-person camera forward is -Z. ItemTransform.apply
    # already negates Y/Z rotations for the left hand: account for that exactly once.
    if forward not in ('+x', '-z'): raise ValueError(f'Unsupported muzzle axis: {forward}')
    angle = 90 if forward == '+x' else 0
    return {
        'gui': {'rotation': [15, angle - 110, 0], 'scale': [.75, .75, .75]},
        'firstperson_righthand': {'rotation': [0, angle, 0], 'translation': [1, 1, -1], 'scale': [1, 1, 1]},
        'firstperson_lefthand': {'rotation': [0, -angle, 0], 'translation': [1, 1, -1], 'scale': [1, 1, 1]},
        'thirdperson_righthand': {'rotation': [0, angle, 0], 'translation': [0, 2, 1], 'scale': [.8, .8, .8]},
        'thirdperson_lefthand': {'rotation': [0, -angle, 0], 'translation': [0, 2, 1], 'scale': [.8, .8, .8]},
        'ground': {'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [.75, .75, .75]},
    }


def convert_model(source, class_name, texture, forward='+x', constructor_values=None):
    width, height, shapes = extract_shapes(source, class_name, constructor_values)
    lo = [min(s['pivot'][i] + s['box'][i] for s in shapes) for i in range(3)]
    hi = [max(s['pivot'][i] + s['box'][i] + s['box'][i+3] for s in shapes) for i in range(3)]
    center = [(a+b)/2 for a, b in zip(lo, hi)]
    factor = min(.5, 40/max(b-a for a, b in zip(lo, hi)))

    def point(p):
        return [round(8 + (value-mid)*factor*(1 if i != 1 else -1), 6)
                for i, (value, mid) in enumerate(zip(p, center))]

    elements = []
    for shape in shapes:
        if shape['inflate'] != 0 or shape['render_scale'] != [1, 1, 1] or shape['mirror']:
            raise ValueError(f'{class_name}: use mesh conversion for grown, scaled or mirrored geometry')
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
            'display': display_transforms(forward)}


# Original ModelBox vertices and quad winding. Y reflection reverses the winding.
QUADS = {
    'east': [5, 1, 2, 6], 'west': [0, 4, 7, 3],
    'down': [5, 4, 0, 1], 'up': [2, 3, 7, 6],
    'north': [1, 0, 3, 2], 'south': [4, 5, 6, 7],
}


def rotated(point, angles):
    x, y, z = point
    for axis, a in enumerate(angles):
        c, s = math.cos(a), math.sin(a)
        if axis == 0: y, z = c*y-s*z, s*y+c*z
        elif axis == 1: x, z = c*x+s*z, -s*x+c*z
        else: x, y = c*x-s*y, s*x+c*y
    return [x, y, z]


def shape_vertices(shape):
    x, y, z, dx, dy, dz = shape['box']
    g = shape['inflate']
    x0, y0, z0 = x-g, y-g, z-g
    x1, y1, z1 = x+dx+g, y+dy+g, z+dz+g
    if shape.get('mirror', False): x0, x1 = x1, x0
    corners = [(x0,y0,z0), (x1,y0,z0), (x1,y1,z0), (x0,y1,z0),
               (x0,y0,z1), (x1,y0,z1), (x1,y1,z1), (x0,y1,z1)]
    return [[p + value * scale for p, value, scale in zip(shape['pivot'], rotated(corner, shape['rotation']), shape['render_scale'])]
            for corner in corners]


def convert_mesh(source, class_name, identifier, texture, forward, gui_hidden=(), constructor_values=None,
                 coordinate_transform=None, reverse_winding=True, model_folder='item', skip_parts=()):
    width, height, shapes = extract_shapes(source, class_name, constructor_values)
    shapes = [shape for shape in shapes if shape['name'] not in skip_parts]
    geometry = {shape['name']: shape_vertices(shape) for shape in shapes}
    body = [point for name, points in geometry.items() if name not in gui_hidden for point in points]
    center = [(min(p[i] for p in body) + max(p[i] for p in body)) / 2 for i in range(3)]
    # OBJ positions are in block units. Preserve the nominal half-scale of cuboid conversion.
    def point(p):
        if coordinate_transform: return coordinate_transform(p)
        return [(8 + (v-c) * .5 * (-1 if i == 1 else 1)) / 16 for i, (v,c) in enumerate(zip(p,center))]
    lines = [f'# Original Techguns {class_name}; Techguns Mod License', f'mtllib {identifier}.mtl']
    index = 1
    for shape in shapes:
        lines.extend([f'o {shape["name"]}', 'usemtl gun'])
        u, v = shape['uv']
        _, _, _, dx, dy, dz = shape['box']
        rects = {'east': [u+dz+dx,v+dz,u+dz+dx+dz,v+dz+dy], 'west': [u,v+dz,u+dz,v+dz+dy],
                 'down': [u+dz,v,u+dz+dx,v+dz], 'up': [u+dz+dx,v+dz,u+dz+2*dx,v],
                 'north': [u+dz,v+dz,u+dz+dx,v+dz+dy], 'south': [u+dz+dx+dz,v+dz,u+2*dz+2*dx,v+dz+dy]}
        for face, corners in QUADS.items():
            a,b,c,d = rects[face]
            uvs = [(c/width,b/height),(a/width,b/height),(a/width,d/height),(c/width,d/height)]
            vertices = [point(geometry[shape['name']][corner]) for corner in corners]
            # Zero-thickness planes still have two real faces, but their edge quads have zero area.
            e1 = [vertices[1][i]-vertices[0][i] for i in range(3)]
            e2 = [vertices[2][i]-vertices[0][i] for i in range(3)]
            cross = [e1[1]*e2[2]-e1[2]*e2[1], e1[2]*e2[0]-e1[0]*e2[2], e1[0]*e2[1]-e1[1]*e2[0]]
            if sum(n*n for n in cross) < 1e-18: continue
            for vertex in vertices: lines.append('v ' + ' '.join(f'{value:.9f}' for value in vertex))
            for uv in uvs: lines.append('vt ' + ' '.join(f'{value:.9f}' for value in uv))
            # ModelBox swaps the X endpoints and flips every face for mirror=true.
            flip = reverse_winding != shape['mirror']
            lines.append('f ' + ' '.join(f'{index+i}/{index+i}' for i in ((3,2,1,0) if flip else (0,1,2,3))))
            index += 4
    model = {'loader': 'neoforge:obj', 'model': f'techguns:models/{model_folder}/{identifier}.obj',
             'automatic_culling': False, 'flip_v': False, 'emissive_ambient': False,
             'textures': {'gun': texture, 'particle': texture}, 'display': display_transforms(forward)}
    material = 'newmtl gun\nKa 0 0 0\nKd 1 1 1\nmap_Kd #gun\n'
    return model, '\n'.join(lines) + '\n', material
