"""Neonlights' five original enum variants, opaque cube models and Camo Bench order."""
import json
import re
from legacy_items import LEGACY
from legacy_repair import RESOURCES
from legacy_models import strip_comments

RECIPES = ('neonlights_0',)


def neon_definition():
    enum = strip_comments((LEGACY/'java/techguns/blocks/EnumLightblockType.java').read_text(encoding='utf-8'))
    names = [s.strip().lower() for s in re.search(r'implements IStringSerializable\s*\{(.*?);', enum, re.S)[1].split(',') if s.strip()]
    source = strip_comments((LEGACY/'java/techguns/TGBlocks.java').read_text(encoding='utf-8'))
    assignment = re.search(r'NEONLIGHT_BLOCK\s*=.*?;', source, re.S)[0]
    assert all(value in assignment for value in ('GenericBlockMetaEnumCamoChangeable', 'Material.GLASS', 'MapColor.YELLOW', 'SoundType.GLASS'))
    hardness = float(re.search(r'setHardness\(([\d.]+)f\)', assignment)[1])
    light = float(re.search(r'setLightLevel\(([\d.]+)f\)', assignment)[1])
    states = json.loads((LEGACY/'resources/assets/techguns/blockstates/neonlights.json').read_text(encoding='utf-8'))['variants']
    variants = [{'id': name, 'metadata': i, 'source_model': states['type='+name]['model'].split(':')[1]} for i, name in enumerate(names)]
    register = strip_comments((LEGACY/'java/techguns/world/structures/MBlockRegister.java').read_text(encoding='utf-8'))
    helipad = int(re.search(r'HELIPAD_GLOWBLOCK\s*=\s*new MBlock\(TGBlocks.NEONLIGHT_BLOCK,\s*(\d+)\)', register)[1])
    return {'source': 'legacy/1.12.2/src/main/java/techguns/TGBlocks.java', 'family': 'neonlights', 'variants': variants,
            'hardness': hardness, 'explosion_resistance': hardness, 'light_level': int(light*15),
            'full_cube': True, 'opaque_cube': True, 'requires_tool': False, 'redstone_conductor': False,
            'note_instrument': 'hat', 'rotation': 'identity; rotated variants use distinct source textures',
            'camo_order': names, 'recipes': list(RECIPES), 'helipad_metadata': helipad,
            'chisel_integration': 'not ported', 'client_acceptance': 'pending'}


def neon_id(metadata):
    variants = neon_definition()['variants']
    if not 0 <= metadata < len(variants): raise ValueError(f'Unsupported neonlights metadata: {metadata}')
    return variants[metadata]['id']


def generate_neon_content():
    files = {}; definition = neon_definition(); assets = LEGACY/'resources/assets/techguns'
    def data(path, value): files[path] = (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode('utf-8')
    data('content/neonlights.json', definition)
    for variant in definition['variants']:
        name = variant['id']; source = json.loads((assets/f'models/block/{variant["source_model"]}.json').read_text(encoding='utf-8'))
        assert source['parent'] == 'block/cube_all'
        for atlas in ('block', 'item'):
            model = {'parent': 'minecraft:'+source['parent'], 'textures': {k: v.replace(':blocks/', ':'+atlas+'/') for k,v in source['textures'].items()}}
            data(RESOURCES+f'assets/techguns/models/{atlas}/{name}.json', model)
            for texture in source['textures'].values():
                source_name = texture.split(':blocks/')[1]
                files[RESOURCES+f'assets/techguns/textures/{atlas}/{source_name}.png'] = (assets/f'textures/blocks/{source_name}.png').read_bytes()
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json', {'variants': {'': {'model': 'techguns:block/'+name}}})
        data(RESOURCES+f'assets/techguns/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': 'techguns:item/'+name}})
        data(RESOURCES+f'data/techguns/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
             'entries': [{'type': 'minecraft:item', 'name': 'techguns:'+name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    entries = ',\n'.join(f'        new Variant("{v["id"]}", {v["metadata"]})' for v in definition['variants'])
    files['core/src/main/java/techguns/core/NeonLights.java'] = ('''package techguns.core;

import java.util.List;
import java.util.Optional;

/** Generated original Neonlights enum order; rotated styles are textures, not directional states. */
public final class NeonLights {
    public record Variant(String id,int metadata) {}
    public static final List<Variant> ALL=List.of(
'''+entries+'''
    );
    public static final CamoPalette PALETTE=new CamoPalette("neonlights",ALL.stream().map(v->"techguns:"+v.id()).toList());
    public static Optional<CamoPalette> palette(String item) { return PALETTE.index(item)>=0?Optional.of(PALETTE):Optional.empty(); }
    public static Variant byMetadata(int metadata) {
        if(metadata<0 || metadata>=ALL.size()) throw new IllegalArgumentException("Invalid neonlights metadata: "+metadata);
        return ALL.get(metadata);
    }
    private NeonLights() {}
}
''').encode('utf-8')
    return files


def neon_translations(lang):
    names = dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    return {kind+'.techguns.'+v['id']: names[f'tile.techguns.neonlights.{v["metadata"]}.name'] for v in neon_definition()['variants'] for kind in ('block','item')}
