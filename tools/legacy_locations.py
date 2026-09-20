"""Original Nether Metal and small Nether locations, preserving scanned cells and encounter/loot NBT."""
import gzip
import hashlib
import json
import re
import struct
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES
from legacy_items import shared_items


def metal_definitions():
    source=strip_comments((LEGACY/'java/techguns/blocks/EnumNetherMetalType.java').read_text())
    constants=source.split('{',1)[1].split(';',1)[0]
    return [{'id':'nethermetal_'+name.lower(),'metadata':i,'light':int(light or 0)}
            for i,(name,light) in enumerate(re.findall(r'([A-Z_][A-Z_0-9]*)(?:\((\d+)\))?',constants))]


def altar_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/nether_altar_small').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode().splitlines(); cells=[list(map(int,line.split(','))) for line in lines[1:] if line]
    assert len(cells)==int(lines[0]) and len({tuple(c[:3]) for c in cells})==len(cells)
    source=strip_comments((LEGACY/'java/techguns/world/structures/NetherAltarSmall.java').read_text())
    entries=re.findall(r'blockList.add\((.*)\);',source)
    metals=metal_definitions(); palette=[]; spawners={}
    for index,entry in enumerate(entries):
        metal=re.fullmatch(r'new MBlock\(TGBlocks.NETHER_METAL, (\d+)\)',entry)
        vanilla=re.fullmatch(r'new MBlock\(Blocks.(\w+), (\d+)\)',entry)
        spawner=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(CyberDemon.class, (\d+)\)',entry)
        if metal: state={'Name':'techguns:'+metals[int(metal[1])]['id']}
        elif vanilla:
            block,meta=vanilla[1],int(vanilla[2])
            names={'AIR':'air','NETHER_BRICK_STAIRS':'nether_brick_stairs','NETHER_BRICK_FENCE':'nether_brick_fence'}
            state={'Name':'minecraft:'+names[block]}
            if block=='NETHER_BRICK_STAIRS': state['Properties']={'facing':['east','west','south','north'][meta&3],'half':'top' if meta&4 else 'bottom','shape':'straight','waterlogged':'false'}
        elif spawner:
            left,active,delay,radius,weight=map(int,spawner.groups()); state={'Name':'techguns:tg_spawner'}
            spawners[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,
                             'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:cyberdemon','weight':weight}]}
        else: raise ValueError('Unmapped altar palette entry: '+entry)
        palette.append(state)
    assert all(0<=c[3]<len(palette) for c in cells)
    size=[max(c[i] for c in cells)+1 for i in range(3)]
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/NetherAltarSmall.java',
            'scan_sha256':hashlib.sha256(raw).hexdigest(),'size':size,'declared_size':[11,10,11],
            'height_offset':-2,'worldgen_floor_offset':-1,'foundation_depth':16,'foundation_stop_after_solids':2,
            'palette':palette,'spawners':spawners,'cells':cells,
            'generation':{'dimension':'minecraft:the_nether','small_grid':16,'medium_grid':32,'big_grid':64,
                          'min_y':20,'max_y':100,'clearance':10,'corner_height_spread':10,
                          'candidates':[{'id':name,'weight':10,'implemented':name in ('nether_altar_small','nether_loot_01')} for name in
                                        ('nether_altar_small','nether_soul_platform','nether_loot_01','nether_acid_hole','nether_ore_cluster_small')],
                          'ore_cluster_candidate_conditional':True,'native_rng':'Minecraft 26.2 structure seed; not identical to 1.12.2 population RNG'}}


def loot_location_definition():
    raw=(LEGACY/'resources/assets/techguns/structures/nether_loot_01').read_bytes().replace(b'\r\n',b'\n')
    lines=raw.decode().splitlines(); cells=[list(map(int,line.split(','))) for line in lines[1:] if line]
    assert len(cells)==int(lines[0]) and len({tuple(c[:3]) for c in cells})==len(cells)
    source=strip_comments((LEGACY/'java/techguns/world/structures/NetherLoot01.java').read_text())
    palette=[]; entities={}
    for index,entry in enumerate(re.findall(r'blockList.add\((.*)\);',source)):
        metal=re.fullmatch(r'new MBlock\(TGBlocks.NETHER_METAL, (\d+)\)',entry)
        vanilla=re.fullmatch(r'new MBlock\(Blocks.(\w+), (\d+)\)',entry)
        chest=re.fullmatch(r'new MBlockChestLoottable\(Blocks.CHEST, (\d+), CHEST_LOOT\)',entry)
        spawner=re.fullmatch(r'new MBlockTGSpawner\(EnumMonsterSpawnerType.HOLE,(\d+),(\d+),(\d+),(\d+)\).addMobType\(ZombiePigmanSoldier.class, (\d+)\)',entry)
        if entry=='MBlockRegister.NETHERRACK_ROCKY': state={'Name':'minecraft:netherrack'}
        elif entry=='MBlockRegister.AIR': state={'Name':'minecraft:air'}
        elif metal: state={'Name':'techguns:'+metal_definitions()[int(metal[1])]['id']}
        elif vanilla:
            block,meta=vanilla[1],int(vanilla[2])
            state={'Name':'minecraft:'+{'NETHERRACK':'netherrack','NETHER_BRICK_FENCE':'nether_brick_fence','SKULL':'skeleton_skull'}[block]}
            # Block metadata 1 means UP; type/yaw live in TileEntitySkull and default to zero.
            if block=='SKULL':
                assert meta==1
                state['Properties']={'rotation':'0','powered':'false'}
            else: assert meta==0
        elif chest:
            assert int(chest[1])==5
            state={'Name':'minecraft:chest','Properties':{'facing':'east','type':'single','waterlogged':'false'}}
            table=re.search(r'CHEST_LOOT = new ResourceLocation\(Techguns.MODID,"([^"]+)"\)',source)[1]
            entities[index]={'id':'minecraft:chest','LootTable':'techguns:'+table}
        elif spawner:
            left,active,delay,radius,weight=map(int,spawner.groups()); state={'Name':'techguns:tg_spawner'}
            entities[index]={'id':'techguns:tg_spawner','mobsLeft':left,'maxActive':active,'spawnDelay':delay,'delay':200,
                             'spawnRange':float(radius),'spawnHeightOffset':0,'mobtypes':[{'id':'techguns:zombiepigmansoldier','weight':weight}]}
        else: raise ValueError('Unmapped NetherLoot01 palette entry: '+entry)
        palette.append(state)
    assert all(0<=c[3]<len(palette) for c in cells)
    declared=list(map(int,re.search(r'super\((\d+),(\d+),(\d+),',source).groups()))
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/structures/NetherLoot01.java',
            'scan_sha256':hashlib.sha256(raw).hexdigest(),'size':[max(c[i] for c in cells)+1 for i in range(3)],
            'declared_size':declared,'height_offset':int(re.search(r'int hoffset = (-?\d+);',source)[1]),
            'worldgen_floor_offset':-1,'foundation_cells':sum(c[1]==0 for c in cells),
            'palette':palette,'block_entities':entities,'cells':cells,'generation':altar_definition()['generation'],
            'loot_table_source':'legacy/1.12.2/src/main/resources/assets/techguns/loot_tables/chests/factory_building.json'}


def factory_chest_loot():
    source=json.loads((LEGACY/'resources/assets/techguns/loot_tables/chests/factory_building.json').read_text())
    pools=[]
    for pool in source['pools']:
        assert set(pool)<= {'name','rolls','entries'}
        entries=[]
        for entry in pool['entries']:
            assert entry['type']=='item' and set(entry)<={'type','weight','name','entryName','functions'}
            name=entry['name']; functions=[]
            for function in entry.get('functions',[]):
                if function['function']=='set_data':
                    assert name=='techguns:itemshared'
                    name='techguns:'+shared_items()[function['data']]
                elif function['function']=='set_count':
                    functions.append({'function':'minecraft:set_count','count':{'type':'minecraft:uniform',**function['count']}})
                else: raise ValueError('Unported chest loot function: '+function['function'])
            modern={'type':'minecraft:item','name':name,'weight':entry['weight']}
            if functions: modern['functions']=functions
            entries.append(modern)
        pools.append({'rolls':{'type':'minecraft:uniform',**pool['rolls']},'entries':entries})
    return {'type':'minecraft:chest','pools':pools}


def nbt_payload(value):
    if isinstance(value,str):
        encoded=value.encode('utf-8'); return 8,struct.pack('>H',len(encoded))+encoded
    if isinstance(value,int): return 3,struct.pack('>i',value)
    if isinstance(value,float): return 6,struct.pack('>d',value)
    if isinstance(value,list):
        parts=[nbt_payload(v) for v in value]; kind=parts[0][0] if parts else 10
        assert all(t==kind for t,_ in parts)
        return 9,bytes([kind])+struct.pack('>i',len(parts))+b''.join(p for _,p in parts)
    if isinstance(value,dict):
        payload=b''
        for key,v in value.items():
            kind,data=nbt_payload(v); payload+=bytes([kind])+nbt_payload(key)[1]+data
        return 10,payload+b'\0'
    raise TypeError(type(value))


def altar_nbt():
    return location_nbt(altar_definition())


def location_nbt(d):
    blocks=[]; entities=d.get('block_entities',d.get('spawners',{}))
    for x,y,z,state in d['cells']:
        block={'pos':[x,y,z],'state':state}
        if state in entities: block['nbt']=entities[state]
        blocks.append(block)
    # Pinned Minecraft 26.2 SharedConstants.WORLD_VERSION. All palette names/properties are modern.
    tree={'DataVersion':4903,'size':d['size'],'palette':d['palette'],'blocks':blocks,'entities':[]}
    kind,data=nbt_payload(tree); compressed=bytearray(gzip.compress(bytes([kind])+b'\0\0'+data,mtime=0)); compressed[9]=255
    return bytes(compressed)


def generate_location_content():
    files={}; assets=LEGACY/'resources/assets/techguns'; metals=metal_definitions()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/nether-metal.json',{'source':'legacy/1.12.2/src/main/java/techguns/blocks/EnumNetherMetalType.java','hardness':8,'blast_resistance':8,'variants':metals,
                                   'camo_order':[m['id'] for m in metals],'ctm_integration':'pending; source base pixels retained'})
    textures=set()
    for metal in metals:
        name=metal['id']; model=json.loads((assets/f'models/block/{name}.json').read_text())
        if model['parent'].startswith('block/'): model['parent']='minecraft:'+model['parent']
        model['textures']={k:v.replace(':blocks/',':block/') for k,v in model['textures'].items()}
        textures.update(v.split(':block/')[1] for v in model['textures'].values() if v.startswith('techguns:block/'))
        data(RESOURCES+f'assets/techguns/models/block/{name}.json',model)
        item_model={**model,'textures':{k:v.replace('techguns:block/','techguns:item/') for k,v in model['textures'].items()}}
        item_model['parent']=item_model['parent'].replace('techguns:block/','techguns:item/')
        data(RESOURCES+f'assets/techguns/models/item/{name}.json',item_model)
        data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+name}})
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json',{'variants':{'':{'model':'techguns:block/'+name}}})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:'+name}], 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for name in textures:
        for atlas in ('block','item'): files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}.png']=(assets/f'textures/blocks/{name}.png').read_bytes()
    for name in ('cube_glow_frame','cube_glow_frame_all'):
        model=json.loads((assets/f'models/block/{name}.json').read_text())
        model.pop('ctm_version',None); model.pop('ctm_overrides',None)
        if model['parent'].startswith('block/'): model['parent']='minecraft:'+model['parent']
        if name=='cube_glow_frame':
            model['elements'][0]['light_emission']=15
            for face in model['elements'][0]['faces'].values(): face.pop('tintindex',None)
        data(RESOURCES+f'assets/techguns/models/block/{name}.json',model)
        data(RESOURCES+f'assets/techguns/models/item/{name}.json',{**model,'parent':model['parent'].replace('techguns:block/','techguns:item/')})
    data(RESOURCES+'assets/minecraft/atlases/items.json',{'sources':[{'type':'minecraft:single','resource':'minecraft:block/lava_still'}]})
    data(RESOURCES+'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['techguns:'+m['id'] for m in metals]})
    data(RESOURCES+'data/c/tags/item/netherracks.json',{'replace':False,'values':['minecraft:netherrack']})
    data('content/nether-altar-small.json',altar_definition())
    files[RESOURCES+'data/techguns/structure/nether_altar_small.nbt']=altar_nbt()
    data(RESOURCES+'data/techguns/tags/worldgen/biome/has_nether_altar_small.json',{'replace':False,'values':['#minecraft:is_nether']})
    data(RESOURCES+'data/techguns/worldgen/structure/nether_altar_small.json',{'type':'techguns:nether_altar_small','biomes':'#techguns:has_nether_altar_small',
         'step':'surface_structures','spawn_overrides':{},'terrain_adaptation':'none','reserved_medium_grid':32,'reserved_big_grid':64})
    # separation=spacing-1 removes the vanilla random offset: exactly the original modulo lattice.
    data(RESOURCES+'data/techguns/worldgen/structure_set/nether_altar_small.json',{'structures':[{'structure':'techguns:nether_altar_small','weight':1}],
         'placement':{'type':'minecraft:random_spread','spacing':16,'separation':15,'salt':1337262}})
    data('content/nether-loot-01.json',loot_location_definition())
    files[RESOURCES+'data/techguns/structure/nether_loot_01.nbt']=location_nbt(loot_location_definition())
    data(RESOURCES+'data/techguns/loot_table/chests/factory_building.json',factory_chest_loot())
    data(RESOURCES+'data/techguns/tags/worldgen/biome/has_nether_loot_01.json',{'replace':False,'values':['#minecraft:is_nether']})
    data(RESOURCES+'data/techguns/worldgen/structure/nether_loot_01.json',{'type':'techguns:nether_loot_01','biomes':'#techguns:has_nether_loot_01',
         'step':'surface_structures','spawn_overrides':{},'terrain_adaptation':'none','reserved_medium_grid':32,'reserved_big_grid':64})
    # Separate native IDs keep /locate useful. Both structures read the same chunk-seeded candidate roll,
    # so their disjoint original tickets can never place both locations on the same site.
    data(RESOURCES+'data/techguns/worldgen/structure_set/nether_loot_01.json',{'structures':[{'structure':'techguns:nether_loot_01','weight':1}],
         'placement':{'type':'minecraft:random_spread','spacing':16,'separation':15,'salt':1337262}})
    entries=',\n'.join(f'        new Variant("{m["id"]}", {m["metadata"]}, {m["light"]})' for m in metals)
    files['core/src/main/java/techguns/core/NetherMetal.java']=('''package techguns.core;
import java.util.List;
/** Generated original Nether Metal enum order, independent of vanilla dye palettes. */
public final class NetherMetal {
    public record Variant(String id, int metadata, int light) {}
    public static final List<Variant> ALL=List.of(
'''+entries+'''
    );
    public static final CamoPalette PALETTE=new CamoPalette("nethermetal",ALL.stream().map(v->"techguns:"+v.id()).toList());
    private NetherMetal() {}
}
''').encode()
    return files


def location_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {kind+'.techguns.'+m['id']:source[f'tile.techguns.nethermetal.{m["metadata"]}.name'] for m in metal_definitions() for kind in ('block','item')}
