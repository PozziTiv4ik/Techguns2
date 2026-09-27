"""Castle's six data-only scans and reproducible original maze algorithm.

Serialization is decoded without loading or executing legacy Java classes. The
runtime only consumes explicit native palettes/cells, never Java serialization.
"""
import hashlib
import json
import re
from legacy_npcs import LEGACY, RESOURCES
from legacy_models import strip_comments
from legacy_java_serialization import read_java, java_list, java_map, custom_utf
from legacy_military_camp import method, nested_loot

CORE = 'core/src/main/java/techguns/core/castle/'
FAMILIES = ('ncdung1', 'nclower1', 'ncmid1', 'ncupper1', 'nctop1', 'ncroof1')


def native_state(name, meta):
    assert name.startswith('minecraft:')
    name = name.split(':')[1]
    props = {}
    if name.endswith('_stairs'):
        name = {'stone_stairs': 'cobblestone_stairs'}.get(name, name)
        props = {'facing': ['east','west','south','north'][meta & 3], 'half': 'top' if meta & 4 else 'bottom', 'shape': 'straight', 'waterlogged': 'false'}
        assert 0 <= meta < 8
    elif name in ('stone_slab', 'double_stone_slab', 'wooden_slab', 'double_wooden_slab'):
        wood = 'wooden' in name
        base = ({0:'oak', 1:'spruce'} if wood else {0:'smooth_stone',3:'cobblestone',4:'brick',5:'stone_brick'})[meta & 7]
        props = {'type':'double' if name.startswith('double_') else 'top' if meta & 8 else 'bottom', 'waterlogged':'false'}
        name = base + '_slab'
    elif name == 'stonebrick':
        name = {0:'stone_bricks',3:'chiseled_stone_bricks'}[meta]
    elif name == 'log':
        name = {0:'oak_log',1:'spruce_log'}[meta & 3]
        props = {'axis':{0:'y',4:'x',8:'z'}[meta & 12]}
    elif name == 'planks':
        assert meta == 1
        name = 'spruce_planks'
    elif name == 'torch':
        name = 'torch' if meta == 5 else 'wall_torch'
        if meta != 5: props = {'facing':{1:'east',2:'west',3:'south',4:'north'}[meta]}
    elif name in ('chest','furnace'):
        props = {'facing':{2:'north',3:'south',4:'west',5:'east'}[meta]}
        props.update({'type':'single','waterlogged':'false'} if name=='chest' else {'lit':'false'})
    elif name == 'spruce_door':
        # Complete both halves from their partner cell after reading the whole scan.
        props = {'facing':['east','south','west','north'][meta & 3] if meta < 8 else 'north',
                 'half':'upper' if meta & 8 else 'lower', 'hinge':'right' if meta & 8 and meta & 1 else 'left',
                 'open':str(bool(meta & 4) if meta < 8 else False).lower(), 'powered':str(bool(meta & 2) if meta & 8 else False).lower()}
    elif name == 'skull':
        assert meta == 1
        name = 'skeleton_skull'; props = {'rotation':'0','powered':'false'}
    elif name == 'anvil':
        assert meta == 10
        name = 'damaged_anvil'; props = {'facing':'north'}
    elif name == 'carpet':
        assert meta == 14
        name = 'red_carpet'
    elif name == 'quartz_block':
        assert meta in (0,2)
        if meta == 2: name = 'quartz_pillar'; props = {'axis':'y'}
    else:
        assert name in ('air','stone','cobblestone','iron_bars','gravel','crafting_table','bookshelf','glowstone') and meta == 0, (name,meta)
    return {'Name':'minecraft:'+name, **({'Properties':props} if props else {})}


def scans():
    result = {}
    for family in FAMILIES:
        data = (LEGACY/f'resources/assets/techguns/dungeons/{family}.ser').read_bytes()
        root = read_java(data)
        assert root['class'] == 'techguns.world.dungeon.DungeonTemplate'
        fields = root['fields']; assert (fields['sizeXZ'],fields['sizeY']) == (5,5)
        segments = {}
        for key, value in java_map(fields['segments']):
            kind = key['enum']; assert value['fields']['type']['enum'] == kind
            assert custom_utf(value,'techguns.world.dungeon.DungeonSegment') == family
            owner = value['fields']['structure']; structure = owner['fields']
            palette = [(custom_utf(b,'techguns.util.MBlock'),b['fields']['meta']) for b in java_list(structure['blocks'])]
            cells = []
            for layer, entries in sorted(java_map(structure['blockEntries']),key=lambda pair:pair[0]['fields']['value']):
                assert -1 <= layer['fields']['value'] <= 2
                for entry in java_list(entries):
                    f = entry['fields']; assert f['this$0'] is owner
                    cells.append([f['x'],f['y'],f['z'],f['blockIndex']])
            size = [structure[k] for k in ('sizeX','sizeY','sizeZ')]
            assert size == [5,10 if kind == 'RAMP' else 5,5]
            assert len(cells) == size[0]*size[1]*size[2] == len({tuple(c[:3]) for c in cells})
            assert all(all(0 <= c[i] < size[i] for i in range(3)) and 0 <= c[3] < len(palette) for c in cells)
            native = [native_state(*p) for p in palette]
            by_pos = {tuple(c[:3]):c for c in cells}
            fixed = []
            for x,y,z,index in cells:
                if palette[index][0] == 'minecraft:spruce_door':
                    meta = palette[index][1]
                    other = by_pos[(x,y+(-1 if meta & 8 else 1),z)]
                    name, partner = palette[other[3]]; assert name == 'minecraft:spruce_door' and bool(meta & 8) != bool(partner & 8)
                    upper, lower = (meta,partner) if meta & 8 else (partner,meta)
                    state = native_state('minecraft:spruce_door',lower)
                    state['Properties'].update(half='upper' if meta & 8 else 'lower',hinge='right' if upper & 1 else 'left',powered=str(bool(upper & 2)).lower())
                    if state not in native: native.append(state)
                    index = native.index(state)
                fixed.append([x,y,z,index])
            segments[kind] = {'size':size,'source_palette':palette,'source_cells':cells,'palette':native,'cells':fixed}
        assert len(segments) == 16
        result[family] = {'sha256':hashlib.sha256(data).hexdigest(),'segments':dict(sorted(segments.items()))}
    return result


def generated_maze():
    source = lambda name: strip_comments((LEGACY/f'java/techguns/world/dungeon/{name}.java').read_text())
    maze = source('MazeDungeonPath')
    maze = maze.replace(' implements IDungeonPath','').replace('@Override','')
    maze = maze.replace(method(maze,'public void generateNPCSpawners('), '''public void generateNPCSpawners(CastleWorld world, int posX, int posY, int posZ, CastlePreset preset) {
        for (PathSegment seg : getSpawnPositions(preset.getSpawnDensity()))
            world.post(posX+seg.x*5+2, posY+seg.y*5+1, posZ+seg.z*5+2);
    }''')
    maze = maze.replace('@Deprecated\n','').replace(method(maze,'private PathSegment addRoomSegment('),'')
    maze = maze.replace(method(maze,'private int countFreeSpaceInDir('),'')
    maze = maze.replace('System.out.println("What is this shit?");','throw new IllegalStateException("Room exceeds dungeon bounds");').replace('System.out.println(min.toString());','').replace('System.out.println(max.toString());','')
    maze = maze.replace('System.out.println("shit");','throw new IllegalStateException("Missing room entrance");')
    maze = maze.replace('System.out.println("couldn\'t match segment " + segment.toString());\n\t\t\t\t\t\t\t\tsegment.printPattern();','throw new IllegalStateException("Unmatched dungeon connection pattern");')
    maze = maze.replace(method(maze,'public void printPattern('),'')
    maze = maze.rstrip()[:-1] + '''
    public record Node(int x,int y,int z,boolean entrance,boolean ramp,int elevation,int rotation,int room,int pattern) {}
    public List<Node> nodes() {
        var result=new ArrayList<Node>();
        for(var s:dungeonList) { int bits=0; for(int i=0;i<8;i++) if(s.pattern[i]) bits|=1<<i;
            result.add(new Node(s.x,s.y,s.z,s.isEntrance,s.isRamp,s.elevation,s.rampRotation,s.roomID,bits)); }
        return List.copyOf(result);
    }
}
'''
    templates = source('TemplateSegment')
    templates = templates.replace(method(templates,'public static Vec3i getMaxExtents('),'')
    # All match patterns are unique up to their allowed rotations; deterministic order aids audits.
    templates = templates.replace('new HashMap<>()','new java.util.LinkedHashMap<>()')
    files = {}
    for name,text in [('CastleMaze',maze),('CastleSegments',templates)]:
        text = re.sub(r'^(?:package|import) .*?;\s*','',text,flags=re.M)
        for old,new in {'MazeDungeonPath':'CastleMaze','TemplateSegment':'CastleSegments','IDungeonPreset':'CastlePreset','EnumFacing':'CastleFacing','BlockPos':'CastlePos','Vec3i':'CastlePos','World':'CastleWorld'}.items():
            text = re.sub(r'\b'+old+r'\b',new,text)
        text = re.sub(r'\n[ \t]*\n(?:[ \t]*\n)+','\n\n',text)
        text = '\n'.join(line.rstrip().expandtabs(4) for line in text.strip().splitlines())+'\n'
        assert not re.search(r'net\.minecraft|TGBlocks|TileEntity|IDungeonPath|DungeonScanner',text)
        files[CORE+name+'.java'] = ('// Generated from the attributed 1.12.2 source by tools/legacy_castle.py.\npackage techguns.core.castle;\n\nimport java.util.*;\nimport techguns.core.castle.CastleSegments.SegmentType;\n\n'+text).encode()
    return files


def generate_castle_content():
    files = generated_maze()
    def data(path,value):
        text=json.dumps(value,ensure_ascii=False,indent=2)
        # Keep each numeric cell on one reviewable line instead of six lines.
        text=re.sub(r'\[\s*(-?\d+(?:,\s*-?\d+)*)\s*\]',lambda m:'['+', '.join(re.findall(r'-?\d+',m[1]))+']',text)
        files[path]=(text+'\n').encode()
    templates = scans()
    data('content/castle.json',{'families':{k:{'sha256':v['sha256'],'segments':len(v['segments']),'cells':sum(len(s['cells']) for s in v['segments'].values())} for k,v in templates.items()},
        'dimensions':{'x':[32,47],'z':[32,47],'y':[24,39],'segment':[5,5,5]},'attempts':5,
        'selection':{'land':{'MilitaryBaseStructure':1,'CastleStructure':1},'water':{'AircraftCarrier':1},'grid':64},
        'terrain':{'step':4,'height_difference':10,'dungeon_y_offset':-5,'direction_changes_sampling_only':True},
        'guard':{'block':'techguns:tg_spawner','deaths':2,'active':2,'interval':200,'range':2,'density':0.1,'retry_limit':5,'mobs':{'zombiesoldier':1,'skeletonsoldier':1}}})
    # Preserve source cells/palettes for independent parity checks; runtime reads only native fields.
    data(RESOURCES+'data/techguns/castle/templates.json',templates)
    data(RESOURCES+'data/techguns/loot_table/chests/castle.json',nested_loot('castle'))
    data(RESOURCES+'data/techguns/worldgen/structure/castle.json',{'type':'techguns:castle','biomes':'#minecraft:is_overworld','step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none'})
    data(RESOURCES+'data/techguns/worldgen/structure_set/castle.json',{'structures':[{'structure':'techguns:castle','weight':1}],'placement':{'type':'minecraft:random_spread','spacing':64,'separation':63,'salt':1337262}})
    return files
