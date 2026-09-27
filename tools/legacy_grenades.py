"""GenericGrenade's two active items, meshes and inherited projectile rules."""
from pathlib import Path
import json
import re
from legacy_models import strip_comments, numeric, convert_mesh, extract_shapes, shape_vertices

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'
RECIPES = ('stielgranate', 'fraggrenade')


def grenade_definitions():
    guns = strip_comments((LEGACY / 'java/techguns/TGuns.java').read_text())
    generic = strip_comments((LEGACY / 'java/techguns/items/guns/GenericGrenade.java').read_text())
    defaults = {name: numeric(value) for name, value in re.findall(r'public (?:int|float) (\w+)\s*=\s*([^;]+);', generic)}
    result = []
    for name in RECIPES:
        expression = re.search(name + r' = new GenericGrenade\(([^;]+);', guns)[1]
        stack, duration, factory = re.search(r'"\w+",\s*(\d+),\s*(\d+),\s*new (\w+)\.Factory', expression).groups()
        damage, inner, minimum, outer = map(numeric, re.search(r'setDamageAndRadius\(([^)]+)\)', expression)[1].split(','))
        projectile = strip_comments((LEGACY / f'java/techguns/entities/projectiles/{factory}.java').read_text())
        override = re.search(r'this.bounces\s*=\s*(\d+)', projectile)
        result.append(dict(id=name, stack_size=int(stack), use_duration=int(duration), damage=damage,
                           inner_radius=inner, minimum_damage=minimum, outer_radius=outer,
                           bounces=int(override[1]) if override else int(defaults['maxBounces']),
                           charge_ticks=int(defaults['fullChargeTime']), lifetime=int(defaults['ticksToLive']),
                           speed=defaults['speed'], spread=defaults['spread'], penetration=defaults['penetration'],
                           start_sound='guns.grenade_pin' if 'GRENADE_PIN' in expression else '',
                           projectile=factory))
    return result


def generate_grenade_content():
    files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    definitions = grenade_definitions()
    data('content/hand-grenades.json', definitions)
    entries = []
    for g in definitions:
        entries.append('    ' + g['id'].upper() + '(' + ', '.join([
            '"'+g['id']+'"', str(g['damage'])+'f', str(g['minimum_damage'])+'f', str(g['inner_radius']),
            str(g['outer_radius']), str(g['bounces']), str(g['stack_size']), str(g['use_duration']),
            str(g['charge_ticks']), str(g['lifetime']), str(g['speed']), str(g['spread'])]) + ')')
    source = '''package techguns.core;

/** Generated from TGuns/GenericGrenade and both projectile constructors. */
public enum HandGrenade {
ENTRIES;
    private final String id;
    public final float damage, minimumDamage;
    public final double innerRadius, outerRadius, speed, spread;
    public final int bounces, stackSize, useDuration, chargeTicks, lifetime;
    HandGrenade(String id, float damage, float minimumDamage, double innerRadius, double outerRadius,
                int bounces, int stackSize, int useDuration, int chargeTicks, int lifetime, double speed, double spread) {
        this.id=id; this.damage=damage; this.minimumDamage=minimumDamage; this.innerRadius=innerRadius;
        this.outerRadius=outerRadius; this.bounces=bounces; this.stackSize=stackSize; this.useDuration=useDuration;
        this.chargeTicks=chargeTicks; this.lifetime=lifetime; this.speed=speed; this.spread=spread;
    }
    public String id() { return id; }
    public static HandGrenade fromId(String id) {
        for (var grenade : values()) if (grenade.id.equals(id)) return grenade;
        throw new IllegalArgumentException("Unknown hand grenade: " + id);
    }
    public float gravity(int heldTicks) {
        // Source divides by zero on an immediate release. Use its shortest finite, one-tick throw.
        return .015f / ((float)Math.clamp(heldTicks, 1, chargeTicks) / chargeTicks);
    }
    public float directDamage(double distance) {
        if (distance <= innerRadius) return damage;
        if (distance > outerRadius) return minimumDamage;
        return minimumDamage + (damage-minimumDamage) * (1-(float)((distance-innerRadius)/(outerRadius-innerRadius)));
    }
    public float blastDamage(double distance) {
        return (float)ExplosionMath.band(distance, innerRadius, outerRadius, damage, minimumDamage);
    }
}
'''.replace('ENTRIES', ',\n'.join(entries))
    files['core/src/main/java/techguns/core/HandGrenade.java'] = source.encode()
    for g in definitions:
        name = g['id']; frag = name == 'fraggrenade'
        clazz = 'ModelFragGrenade' if frag else 'ModelStielgranate'
        text = (LEGACY / f'java/techguns/client/models/guns/{clazz}.java').read_text()
        texture = 'frag_grenade_texture' if frag else name
        _, _, shapes = extract_shapes(text, clazz, {'showRing':False} if frag else None)
        points = [p for shape in shapes for p in shape_vertices(shape)]
        center = [(min(p[i] for p in points)+max(p[i] for p in points))/2 for i in range(3)]
        files[RESOURCES+f'assets/techguns/textures/item/{name}.png'] = (LEGACY / f'resources/assets/techguns/textures/guns/{texture}.png').read_bytes()
        for mode in (('item', 'primed', 'projectile') if frag else ('item', 'projectile')):
            identifier = name if mode == 'item' else name+'_'+mode
            flying = mode == 'projectile'
            # Projectile coordinates stay in the original ModelRenderer frame; renderer applies source transforms.
            def point(p): return [.5 + v/16 for v in p]
            def item_point(p): return [.5+(v-center[i])/32*(-1 if i==1 else 1) for i,v in enumerate(p)]
            model, obj, mtl = convert_mesh(text, clazz, identifier, 'techguns:item/'+name, '+x',
                                           constructor_values={'showRing': mode == 'item'} if frag else None,
                                           coordinate_transform=point if flying else item_point, reverse_winding=not flying)
            if flying: model['display'] = {}
            else:
                # Native hand anchors replace the old RenderItemBase matrix stack.
                model['display'] = {
                    'gui': {'rotation': [20, 40, 0], 'scale': [1.35 if frag else .8]*3},
                    'ground': {'scale': [.5]*3, 'translation': [0, 3, 0]},
                    'fixed': {'rotation': [0, -90, 0], 'scale': [.7]*3},
                    **{f'{view}_{hand}hand': {'rotation': [0, 90 if frag else 180, 0], 'scale': [scale]*3}
                       for view, scale in [('firstperson', 1.25 if frag else 1), ('thirdperson', .875 if frag else .7)]
                       for hand in ('left', 'right')}}
            data(RESOURCES+f'assets/techguns/models/item/{identifier}.json', model)
            files[RESOURCES+f'assets/techguns/models/item/{identifier}.obj'] = obj.encode()
            files[RESOURCES+f'assets/techguns/models/item/{identifier}.mtl'] = mtl.encode()
            if mode != 'primed':
                item_model = {'type':'minecraft:model', 'model':'techguns:item/'+identifier}
                if frag and mode == 'item':
                    item_model = {'type':'minecraft:select', 'property':'minecraft:display_context', 'fallback':item_model,
                                  'cases':[{'when':['firstperson_lefthand','firstperson_righthand'], 'model':{
                                      'type':'minecraft:condition', 'property':'minecraft:using_item',
                                      'on_true':{'type':'minecraft:model', 'model':'techguns:item/fraggrenade_primed'},
                                      'on_false':item_model}}]}
                data(RESOURCES+f'assets/techguns/items/{identifier}.json', {'model':item_model})
    for name in ('grenade', 'grenade_impact'):
        data(RESOURCES+f'data/techguns/damage_type/{name}.json', {'message_id':'techguns.grenade', 'scaling':'when_caused_by_living_non_player', 'exhaustion':.1})
    for tag, name in [('is_explosion','grenade'), ('is_projectile','grenade_impact'),
                      ('bypasses_cooldown','grenade_impact'), ('no_knockback','grenade_impact')]:
        data(RESOURCES+f'data/minecraft/tags/damage_type/{tag}.json', {'replace':False,'values':['techguns:'+name]})
    return files


def grenade_translations(lang):
    source = dict(line.split('=',1) for line in (LEGACY / f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    ru = lang == 'ru_ru'
    values = {f'item.techguns.{name}':source[f'item.techguns.{name}.name'] for name in RECIPES}
    for name in RECIPES: values[f'item.techguns.{name}_projectile'] = values[f'item.techguns.{name}']
    values['entity.techguns.hand_grenade'] = 'Ручная граната' if ru else 'Hand grenade'
    values['subtitles.techguns.guns.grenade_pin'] = 'Выдернута чека гранаты' if ru else 'Grenade pin pulled'
    values['tooltip.techguns.grenade'] = 'Урон: %s; радиус: %s–%s; отскоки: %s' if ru else 'Damage: %s; radius: %s–%s; bounces: %s'
    values['death.attack.techguns.grenade'] = '%1$s взорван гранатой %2$s' if ru else '%1$s was blown up by %2$s\'s grenade'
    values['death.attack.techguns.grenade.player'] = values['death.attack.techguns.grenade']
    values['death.attack.techguns.grenade.item'] = '%1$s взорван игроком %2$s с помощью %3$s' if ru else '%1$s was blown up by %2$s using %3$s'
    return values
