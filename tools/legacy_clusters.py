"""Original unbreakable cluster blocks and their actual TGConfig defaults (not obsolete enum fields)."""
import json
import re
from legacy_models import strip_comments
from legacy_npcs import LEGACY, RESOURCES


def cluster_definitions():
    source=strip_comments((LEGACY/'java/techguns/blocks/EnumOreClusterType.java').read_text())
    names=re.findall(r'([A-Z_]+)\(',source.split('{',1)[1].split(';',1)[0])
    config=strip_comments((LEGACY/'java/techguns/TGConfig.java').read_text())
    result=[]
    for metadata,name in enumerate(names):
        key=name.lower(); entry={'id':'ore_cluster_'+key,'type':key,'metadata':metadata}
        for field,prefix in [('mining_level','mininglevel'),('ore_multiplier','oremult'),('power_multiplier','powermult')]:
            match=re.search(r'\b'+prefix+'_'+key+r'\s*=\s*config.get(?:Int|Float)\("[^"]+",\s*ORE_DRILLS\s*,\s*([\d.]+)f?',config)
            assert match, (key,field)
            entry[field]=int(match[1]) if field=='mining_level' else float(match[1])
        result.append(entry)
    assert len(result)==9
    return result


def generate_cluster_content():
    files={}; assets=LEGACY/'resources/assets/techguns'; variants=cluster_definitions()
    def data(path,value): files[path]=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    data('content/ore-clusters.json',{'source':'legacy/1.12.2/src/main/java/techguns/blocks/BlockOreCluster.java',
         'defaults_source':'legacy/1.12.2/src/main/java/techguns/TGConfig.java','hardness':-1,
         'legacy_resistance_argument':6000000,'blast_resistance':3600000,'drops':[],
         'depletion':False,'ore_drill':'not ported; no resource extraction yet','variants':variants})
    for variant in variants:
        name=variant['id']; model=json.loads((assets/f'models/block/{name}.json').read_text())
        model['textures']={k:v.replace(':blocks/',':block/') for k,v in model['textures'].items()}
        data(RESOURCES+f'assets/techguns/models/block/{name}.json',model)
        data(RESOURCES+f'assets/techguns/models/item/{name}.json',{**model,'textures':{k:v.replace(':block/',':item/') for k,v in model['textures'].items()}})
        data(RESOURCES+f'assets/techguns/items/{name}.json',{'model':{'type':'minecraft:model','model':'techguns:item/'+name}})
        data(RESOURCES+f'assets/techguns/blockstates/{name}.json',{'variants':{'':{'model':'techguns:block/'+name}}})
        for atlas in ('block','item'):
            for suffix in ('.png','.png.mcmeta'):
                files[RESOURCES+f'assets/techguns/textures/{atlas}/{name}{suffix}']=(assets/f'textures/blocks/{name}{suffix}').read_bytes()
    entries=',\n'.join('        new Variant("{id}", "{type}", {metadata}, {mining_level}, {ore_multiplier}, {power_multiplier})'.format(**v) for v in variants)
    files['core/src/main/java/techguns/core/OreClusters.java']=('''package techguns.core;
import java.util.List;
/** Generated source metadata and TGConfig defaults; enum constructor values were unused in 1.12.2. */
public final class OreClusters {
    public record Variant(String id, String type, int metadata, int miningLevel, double oreMultiplier, double powerMultiplier) {}
    public static final List<Variant> ALL=List.of(
'''+entries+'''
    );
    private OreClusters() {}
}
''').encode()
    return files


def cluster_translations(lang):
    source=dict(line.split('=',1) for line in (LEGACY/f'resources/assets/techguns/lang/{lang}.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
    result={kind+'.techguns.'+v['id']:source[f'tile.techguns.orecluster.{v["metadata"]}.name'] for v in cluster_definitions() for kind in ('block','item')}
    for key in ('mininglevel','powermult','amountmult'): result['techguns.orecluster.'+key]=source['techguns.orecluster.'+key]
    return result
