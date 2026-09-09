"""Convert the two fluids owned by legacy Techguns, retaining their animated textures."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / 'legacy/1.12.2/src/main'
RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'
FLUIDS = [('creeper_acid','acid',100),('milk','milk',1000)]


def generate_fluid_content():
    files = {}
    def resource(path, value): files[RESOURCES+path] = (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    for identifier, texture, density in FLUIDS:
        for state in ('still','flow'):
            for suffix in ('.png','.png.mcmeta'):
                files[RESOURCES+f'assets/techguns/textures/block/{texture}_{state}{suffix}'] = (LEGACY/f'resources/assets/techguns/textures/blocks/{texture}_{state}{suffix}').read_bytes()
        resource(f'assets/techguns/items/{identifier}_bucket.json', {'model': {'type':'neoforge:fluid_container',
            'fluid':'techguns:'+identifier, 'textures': {'base':'minecraft:item/bucket','fluid':'neoforge:item/mask/bucket_fluid'}}})
        resource(f'assets/techguns/blockstates/block_{identifier}.json', {'variants': {'': {'model':'minecraft:block/water'}}})
        values=['techguns:'+identifier,'techguns:flowing_'+identifier]
        if identifier=='milk': values += [{'id':'minecraft:milk','required':False},{'id':'minecraft:flowing_milk','required':False}]
        resource(f'data/c/tags/fluid/{"acids/creeper" if identifier=="creeper_acid" else "milk"}.json',{'replace':False,'values':values})
        resource(f'data/techguns/tags/fluid/{identifier}.json', {'values':['techguns:'+identifier,'techguns:flowing_'+identifier]})
    resource('data/techguns/damage_type/acid.json', {'exhaustion':.1,'message_id':'techguns.acid','scaling':'never'})
    resource('data/minecraft/tags/damage_type/no_knockback.json', {'replace':False,'values':['techguns:acid']})
    files['content/fluids.json'] = (json.dumps({'source':'legacy/1.12.2/src/main/java/techguns/TGFluids.java',
        'fluids':[{'id':'techguns:'+identifier,'block':'techguns:block_'+identifier,'bucket':'techguns:'+identifier+'_bucket',
                   'density':density,'viscosity':1000,'tick_rate':5,'infinite_sources':False,
                   'still_texture':'techguns:block/'+texture+'_still','flow_texture':'techguns:block/'+texture+'_flow'} for identifier,texture,density in FLUIDS],
        'milk_collision_fix':'Milk no longer uses the original BlockFluidAcid poison collision handler',
        'pending':'Other fluid machines, world-spawn pools, complete client visual acceptance and integrations'},ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    return files


def fluid_translations(lang):
    old=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines()
             if '=' in line and not line.startswith('#'))
    result={}
    for identifier,_,_ in FLUIDS:
        name=old[f'tile.techguns.block_{identifier}.name']
        result['block.techguns.block_'+identifier]=name
        result['fluid_type.techguns.'+identifier]=name
        result['item.techguns.'+identifier+'_bucket']=name+' Bucket' if lang=='en_us' else 'Ведро: '+name
    result['death.attack.techguns.acid']='%1$s dissolved in creeper acid' if lang=='en_us' else '%1$s растворился в кислоте крипера'
    result['death.attack.techguns.acid.player']='%1$s dissolved in acid while escaping %2$s' if lang=='en_us' else '%1$s растворился в кислоте, спасаясь от %2$s'
    return result
