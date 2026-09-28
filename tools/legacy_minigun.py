"""Original Minigun multipart geometry and the non-resetting GenericGun recoil clock."""
import json
import re
from legacy_items import LEGACY
from legacy_models import strip_comments, extract_shapes, shape_vertices, convert_mesh

RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def minigun_parts():
    source = (LEGACY / 'java/techguns/client/models/guns/ModelMinigun.java').read_text()
    _, _, shapes = extract_shapes(source, 'ModelMinigun')
    render = strip_comments(source).split('if(part==0)', 1)[1]
    body, rotor = [re.findall(r'(\w+)\.render\(scale\)', block) for block in render.split('}else', 1)]
    if set(body) & set(rotor) or set(body + rotor) != {s['name'] for s in shapes}:
        raise ValueError('Review Minigun multipart geometry')
    points = [p for s in shapes for p in shape_vertices(s)]
    center = [(min(p[i] for p in points) + max(p[i] for p in points)) / 2 for i in range(3)]
    return source, body, rotor, center


def generate_minigun_content():
    source, body, rotor, center = minigun_parts()
    generic = strip_comments((LEGACY / 'java/techguns/items/guns/GenericGun.java').read_text())
    guns = strip_comments((LEGACY / 'java/techguns/TGuns.java').read_text())
    definition = re.search(r'\bminigun\s*=\s*new GenericGun\([^;]+;', guns)[0]
    if '.setCheckRecoil()' not in definition or '.setRecoiltime(' in definition:
        raise ValueError('Review Minigun recoil override')
    ticks = int(re.search(r'\bint recoiltime\s*=\s*(\d+)', generic)[1])
    files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    # Keep both parts in the same coordinate frame as the complete mesh.
    def point(p): return [.5 + (v-c) * (-1 if i == 1 else 1) / 32 for i, (v,c) in enumerate(zip(p,center))]
    for name, skipped in [('minigun_body', rotor), ('minigun_rotor', body)]:
        model, obj, mtl = convert_mesh(source, 'ModelMinigun', name, 'techguns:item/minigun', '+x',
                                       skip_parts=skipped, coordinate_transform=point)
        model['display'] = {}
        data(RESOURCES + f'assets/techguns/models/item/{name}.json', model)
        files[RESOURCES + f'assets/techguns/models/item/{name}.obj'] = obj.encode()
        files[RESOURCES + f'assets/techguns/models/item/{name}.mtl'] = mtl.encode()
    pivot_y, pivot_z = center[1] / 32, -center[2] / 32
    data('content/minigun-behavior.json', {
        'sources': ['legacy/1.12.2/src/main/java/techguns/TGuns.java',
                    'legacy/1.12.2/src/main/java/techguns/items/guns/GenericGun.java',
                    'legacy/1.12.2/src/main/java/techguns/events/TGTickHandler.java',
                    'legacy/1.12.2/src/main/java/techguns/packets/PacketShootGun.java',
                    'legacy/1.12.2/src/main/java/techguns/client/models/guns/ModelMinigun.java'],
        'held_fire': 'one request at PlayerTickEvent.START; minFiretime remains zero',
        'server_guard': 'shared firearms cooldown, minimum one player tick; repeated packets cannot multiply shots',
        'reload': 'R or empty trigger, 100 ticks; consumption and remainders at completion',
        'creative': 'requires a loaded drum; accepted shots do not consume it; empty trigger can reload without items',
        'body_parts': body, 'rotor_parts': rotor, 'spin_ticks': ticks,
        'rotor_pivot': [0, pivot_y, pivot_z], 'rotor_degrees': -360,
        'audio': 'original finite fire/reload samples; the commented-out start/loop is not enabled',
        'pending': ['GPU acceptance, original muzzle FX, hand placement and whole-weapon recoil/reload animations']} )
    files['core/src/main/java/techguns/core/MinigunAnimation.java'] = ('''package techguns.core;

/** Generated from ModelMinigun and GenericGun: full rotation per non-resetting recoil cycle. */
public final class MinigunAnimation {
    public static final int SPIN_TICKS = %s;
    public static final double PIVOT_Y = %s;
    public static final double PIVOT_Z = %s;
    public static boolean startsSpin(long last, long now) { return last < 0 || now < last || now - last >= SPIN_TICKS; }
    public static float degrees(long start, long now, float partial) {
        double elapsed = (double) now - start + partial;
        // Mesh conversion reflects Y, reversing the source X rotation.
        return start < 0 || elapsed < 0 || elapsed >= SPIN_TICKS || !Float.isFinite(partial) ? 0
                : (float) (-360 * elapsed / SPIN_TICKS);
    }
    private MinigunAnimation() {}
}
''' % (ticks, pivot_y, pivot_z)).encode()
    return files
