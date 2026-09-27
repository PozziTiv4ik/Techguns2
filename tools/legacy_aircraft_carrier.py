"""AircraftCarrier's complete ordered scan, active fallback palette and nested supplies."""
import copy
import hashlib
import json
import re
from legacy_building import building_id, building_definitions
from legacy_castle import native_state
from legacy_military_camp import nested_loot
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def carrier_definition():
    raw = (LEGACY/'resources/assets/techguns/structures/aircraft_carrier').read_bytes().replace(b'\r\n', b'\n')
    lines = raw.decode().splitlines()
    cells = [list(map(int, line.split(','))) for line in lines[1:] if line]
    assert len(cells) == int(lines[0]) == 3967
    assert len({tuple(c[:3]) for c in cells}) == 3929
    source = strip_comments((LEGACY/'java/techguns/world/structures/AircraftCarrier.java').read_text())
    register = strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text())
    entries = re.findall(r'blockList.add\((.*)\);', source)
    assert len(entries) == 41
    palette = []; aliases = {}; door_meta = {}
    for index, entry in enumerate(entries):
        props = {}; kind = 'block'; layer = 0
        if entry.startswith('MBlockRegister.'):
            alias = entry.split('.')[1]
            definition = re.search(r'\b'+alias+r'\s*=\s*(.*?);', register, re.S)[1]
            aliases[alias] = definition
            if alias.startswith('SUPPLY_CRATES'):
                weights = list(map(int, re.findall(r'new int\[\] \{([^}]+)\}', definition)[1].split(',')))
                assert weights == ([1]*9 + ([9] if alias.endswith('_CHANCE') else []))
                kind = 'supply_chance' if alias.endswith('_CHANCE') else 'supply'; name = 'minecraft:air'
            elif alias.startswith('ALUMINIUM_STAIRS_'):
                facing = re.search(r'FACING, EnumFacing.(\w+)', definition)[1].lower()
                assert 'EnumHalf.BOTTOM' in definition
                name = 'techguns:stairs_metal'; props = {'facing':facing, 'half':'bottom', 'shape':'straight', 'waterlogged':'false'}
            elif 'Blocks.HARDENED_CLAY' in definition:
                name = 'minecraft:terracotta'  # This block ignores dye metadata.
            elif definition.startswith('new MBlock(Blocks.CONCRETE'):
                color = re.search(r'EnumDyeColor.(\w+).ordinal', definition)[1].lower()
                name = 'minecraft:'+{'silver':'light_gray'}.get(color, color)+'_concrete'
            elif definition == 'new MBlock(Blocks.IRON_BLOCK,0)': name = 'minecraft:iron_block'
            elif 'TGBlocks.' in definition:
                block, meta = re.fullmatch(r'new MBlock\(TGBlocks.(\w+),(.+)\)', definition).groups()
                family = {'CONCRETE':'concrete','METAL_PANEL':'metalpanel'}[block]
                if meta.isdigit(): name = 'techguns:'+building_id(family, int(meta))
                else:
                    variant = re.fullmatch(r'(?:TGMetalPanelType|EnumConcreteType).(\w+).ordinal\(\)', meta)[1].lower()
                    name = 'techguns:'+next(v['id'] for v in building_definitions() if v['family']==family and v['model']==variant)
            else: raise AssertionError(definition)
        elif entry.startswith('new MBlockChestLoottable'):
            meta = int(re.fullmatch(r'new MBlockChestLoottable\(Blocks.CHEST,(\d+),LOOT_TABLE\)', entry)[1])
            state = native_state('minecraft:chest', meta); name = state['Name']; props = state['Properties']; kind = 'chest'
        elif entry.startswith('new MBlockTGSpawner'):
            if index == 38:
                assert entry == 'new MBlockTGSpawner(EnumMonsterSpawnerType.HOLE,6,2,150,2).addMobType(ArmySoldier.class, 1).addMobType(Commando.class, 1)'
                name = 'techguns:tg_spawner'; kind = 'guard'
            else:
                assert index == 39 and entry == 'new MBlockTGSpawner(EnumMonsterSpawnerType.SOLDIER_SPAWN,1,1,200,0).addMobType(AttackHelicopter.class, 1)'
                name = 'techguns:soldier_spawn'; kind = 'helicopter'
        elif 'TGBlocks.LAMP_0' in entry:
            name = 'techguns:lamp_yellow'; props = {'facing':re.search(r'EnumFacing.(\w+).ordinal', entry)[1].lower()}; layer = 1
        elif 'TGBlocks.BUNKER_DOOR' in entry:
            meta = int(re.fullmatch(r'new MBlock\(TGBlocks.BUNKER_DOOR, (\d+)\)', entry)[1]); door_meta[index] = meta
            name = 'techguns:bunkerdoor'; props = native_state('minecraft:spruce_door', meta)['Properties']
        elif 'TGBlocks.LADDER_0' in entry:
            assert entry == 'new MBlock(TGBlocks.LADDER_0, 0)'
            name = 'techguns:'+building_id('ladder0', 0); props = {'facing':'south'}
        else:
            block = re.fullmatch(r'new MBlock\(Blocks.(\w+),0\)', entry)[1]
            name = 'minecraft:'+{'CRAFTING_TABLE':'crafting_table','AIR':'air','GLASS_PANE':'glass_pane','GLASS':'glass','IRON_BARS':'iron_bars'}[block]
        palette.append({'state':{'Name':name, **({'Properties':props} if props else {})}, 'kind':kind, 'pass':layer})
    original_palette = copy.deepcopy(palette)
    # Preserve duplicates and source order inside each of the two BlockData passes.
    ordered = sorted(cells, key=lambda c:palette[c[3]]['pass'])
    by_pos = {tuple(c[:3]):c for c in ordered}
    fixed = []
    for x,y,z,index in ordered:
        entry = copy.deepcopy(palette[index]); state = entry['state']
        if index in door_meta:
            meta = door_meta[index]; partner = door_meta[by_pos[(x,y+(-1 if meta & 8 else 1),z)][3]]
            assert bool(meta & 8) != bool(partner & 8)
            upper, lower = (meta,partner) if meta & 8 else (partner,meta)
            state['Properties'] = native_state('minecraft:spruce_door', lower)['Properties']
            state['Properties'].update(half='upper' if meta & 8 else 'lower', hinge='right' if upper & 1 else 'left')
        elif entry['kind'] == 'chest':
            facing = state['Properties']['facing']
            clockwise = {'north':(1,0),'east':(0,1),'south':(-1,0),'west':(0,-1)}[facing]
            for sign in (1,-1):
                other = by_pos.get((x+sign*clockwise[0],y,z+sign*clockwise[1]))
                if other is not None and palette[other[3]]['kind']=='chest' and palette[other[3]]['state']['Properties']['facing']==facing:
                    state['Properties']['type'] = 'left' if sign==1 else 'right'; break
        if entry not in palette: palette.append(entry)
        fixed.append([x,y,z,palette.index(entry)])
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/AircraftCarrier.java',
            'scan_sha256':hashlib.sha256(raw).hexdigest(), 'size':[54,24,21], 'pivot':[27,0,10],
            'source_entries':entries, 'aliases':aliases, 'source_cells':cells, 'source_palette':original_palette,
            'palette':palette, 'cells':fixed,
            'water':{'samples':[[0,0],[54,0],[0,21],[54,21]], 'difference_from_first_exclusive':2, 'height':'mean of middle sorted pair', 'floor_offset':-3},
            'selection':{'dimension':'minecraft:overworld','grid':64,'biome':'#minecraft:is_ocean','water_weight':1,'land_weights':{'MilitaryBaseStructure':1,'CastleStructure':1}},
            'supply':{'guaranteed':24,'chance':24,'inclusive_rng_bounds':[10,19],'weights_first_variant':[2,2],'chance_air_tickets':9},
            'posts':{'guard':{'count':9,'remaining':6,'active':2,'interval':150,'range':2,'height_offset':0,'mobs':{'armysoldier':1,'commando':1}},
                     'helicopter':{'count':1,'remaining':1,'active':1,'interval':200,'range':0,'height_offset':0,'mobs':{'attackhelicopter':1}}}}


def generate_carrier_content():
    d = carrier_definition(); files = {}
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode()
    data('content/aircraft-carrier.json', d)
    data(RESOURCES+'data/techguns/aircraft_carrier/scan.json', {k:d[k] for k in ('palette','cells')})
    data(RESOURCES+'data/techguns/loot_table/chests/aircraftcarrier.json', nested_loot('aircraftcarrier'))
    data(RESOURCES+'data/techguns/worldgen/structure/aircraft_carrier.json', {'type':'techguns:aircraft_carrier','biomes':'#minecraft:is_ocean','step':'top_layer_modification','spawn_overrides':{},'terrain_adaptation':'none'})
    data(RESOURCES+'data/techguns/worldgen/structure_set/aircraft_carrier.json', {'structures':[{'structure':'techguns:aircraft_carrier','weight':1}], 'placement':{'type':'minecraft:random_spread','spacing':64,'separation':63,'salt':1337262}})
    return files
