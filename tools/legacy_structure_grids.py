"""Shared owner of native grid placements; defaults/ranges come from the immutable source config."""
import json
import re
from legacy_items import LEGACY
from legacy_models import strip_comments


def grid_placement(size):
    if size not in ('small','medium','big'): raise ValueError(size)
    return {'type':'techguns:structure_grid','size':size,'salt':1337262}


def grid_definition():
    config=strip_comments((LEGACY/'java/techguns/TGConfig.java').read_text(encoding='utf-8'))
    spawn=strip_comments((LEGACY/'java/techguns/world/WorldGenTGStructureSpawn.java').read_text(encoding='utf-8'))
    register=strip_comments((LEGACY/'java/techguns/world/TGStructureSpawnRegister.java').read_text(encoding='utf-8'))
    sizes={}
    for size in ('small','medium','big'):
        key='StructureSpawnWeight'+size.title()
        default,minimum,maximum=map(int,re.search(r'config.getInt\("'+key+r'", WORLDGEN, (\d+), (\d+), (\d+),',config).groups())
        assert spawn.count(f'cx % SPAWNWEIGHT_{size.upper()} == 0')==2
        sizes[size]={'setting':key,'default':default,'minimum':minimum,'maximum':maximum,
                     'candidates':re.findall(r'spawns_'+size+r'\.add\(new TGStructureSpawn\(new (\w+)\(',register)}
    return {'source':'legacy/1.12.2/src/main/java/techguns/world/WorldGenTGStructureSpawn.java',
            'sizes':sizes,'priority':['big','medium','small'],'native_type':'techguns:structure_grid',
            'restart':'world/server','existing_starts':'preserved; locate searches the currently configured grid',
            'nether_big':'reserved even though there is no active big Nether candidate',
            'random_offsets':False,'frequency_is_not_a_probability':True}


def generate_grid_content():
    return {'content/structure-grids.json':(json.dumps(grid_definition(),ensure_ascii=False,indent=2)+'\n').encode()}
