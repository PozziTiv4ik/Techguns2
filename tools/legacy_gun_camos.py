"""Opt-in GenericGun camouflage, preserving ClientProxy texture suffixes and source labels."""
import json
from legacy_items import LEGACY
from legacy_models import strip_comments


def camo_definition(identifier, texture, count):
    generic = strip_comments((LEGACY / 'java/techguns/items/guns/GenericGun.java').read_text())
    proxy = strip_comments((LEGACY / 'java/techguns/client/ClientProxy.java').read_text())
    change = strip_comments((LEGACY / 'java/techguns/items/armors/ICamoChangeable.java').read_text())
    if 'this.camoCount=variations;' not in generic or 'path+(i!=0?("_"+i):"")+".png"' not in proxy:
        raise ValueError('Review changed gun camouflage textures')
    if not all(s in change for s in ('camoID++;', 'camoID--;', 'camoID>=it.getCamoCount()', 'camoID=(byte) (it.getCamoCount()-1);')):
        raise ValueError('Review changed camouflage cycling')
    if count < 2 or count > 128: raise ValueError('Expected a source camouflage family')
    return [{'texture':texture + (f'_{i}' if i else ''),
             'name_key':f'item.techguns.{identifier}.camoname.{i}' if i else 'techguns.item.defaultcamo'} for i in range(count)]


def generate_gun_camos(weapons):
    selected = {g['id']:g['camos'] for g in weapons if g.get('camos')}
    entries = ',\n'.join('        Map.entry("' + key + '", List.of(' + ', '.join('"'+c['name_key']+'"' for c in camos) + '))' for key,camos in selected.items())
    java = '''package techguns.core;

import java.util.List;
import java.util.Map;

/** Generated from opt-in weapon-ports camos and GenericGun / ClientProxy. */
public final class GunCamos {
    private static final Map<String, List<String>> NAMES = Map.ofEntries(
''' + entries + ''');
    public static final int MAX_INDEX = ''' + str(max((len(v)-1 for v in selected.values()), default=0)) + ''';
    public static int count(String id) { return NAMES.getOrDefault(id, List.of()).size(); }
    public static String nameKey(String id, int index) { return NAMES.get(id).get(index); }
    private GunCamos() {}
}
'''
    return {'core/src/main/java/techguns/core/GunCamos.java':java.encode(),
            'content/gun-camos.json':(json.dumps({'source':'GenericGun / ICamoChangeable / ClientProxy', 'weapons':selected}, ensure_ascii=False, indent=2)+'\n').encode()}
