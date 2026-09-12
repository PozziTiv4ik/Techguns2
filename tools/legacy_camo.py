"""Original Camo Bench palettes, textures and control contract."""
import json
import re
from legacy_items import LEGACY
from legacy_models import strip_comments
from legacy_repair import RESOURCES

COLORS = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
FAMILIES = {'WOOL':'wool', 'CONCRETE':'concrete', 'CONCRETE_POWDER':'concrete_powder', 'STAINED_HARDENED_CLAY':'terracotta',
            'STAINED_GLASS':'stained_glass', 'STAINED_GLASS_PANE':'stained_glass_pane', 'STANDING_BANNER':'banner', 'CARPET':'carpet'}


def camo_data():
    source = strip_comments((LEGACY / 'java/techguns/TGMachineRecipes.java').read_text())
    blocks = re.findall(r'CamoBenchRecipes\.addRecipe\(new CamoBenchRecipe\(Blocks\.(\w+)\)\)', source)
    if set(blocks) != set(FAMILIES): raise ValueError('Review changed original Camo Bench families')
    # Mojang's 1.12.2 ItemBanner stores EnumDyeColor.getDyeDamage, which is 15 - block metadata.
    palettes = [{'legacy_block': block, 'id': FAMILIES[block],
                 'items': ['minecraft:' + color + '_' + FAMILIES[block] for color in (list(reversed(COLORS)) if block == 'STANDING_BANNER' else COLORS)]} for block in blocks]
    return {'source': 'legacy/1.12.2/src/main/java/techguns/TGMachineRecipes.java', 'legacy_item': 'techguns:simplemachine@8',
            'palettes': palettes, 'input_slots': 1, 'energy': 0, 'recolor_cost': 0, 'armor': ['T2_COMBAT','T2_HAZMAT','T1_MINER'],
            'minecraft_reference': {'version':'1.12.2', 'client_sha1':'0f275bc1547d01fa5f56ba34bdc87d981ee12daf',
                'manifest':'https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json'},
            'pending': ['Other Techguns armor, masks, backpacks and decorative blocks', 'Client acceptance and real two-client verification']}


def generate_camo_content():
    files = {}; catalog = camo_data(); assets = LEGACY / 'resources/assets/techguns'
    def data(path, value): files[RESOURCES + path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    source = json.loads((assets / 'models/block/camo_bench.json').read_text())
    for atlas in ('block','item'):
        data(f'assets/techguns/models/{atlas}/camo_bench.json', {'parent':'minecraft:block/cube',
            'textures':{key:value.replace(':blocks/', ':' + atlas + '/') for key,value in source['textures'].items()}})
        for texture in set(source['textures'].values()):
            name = texture.split('/')[-1]
            files[RESOURCES + f'assets/techguns/textures/{atlas}/{name}.png'] = (assets / f'textures/blocks/{name}.png').read_bytes()
    data('assets/techguns/items/camo_bench.json', {'model':{'type':'minecraft:model','model':'techguns:item/camo_bench'}})
    data('assets/techguns/blockstates/camo_bench.json', {'variants':{'facing=' + direction:{'model':'techguns:block/camo_bench','y':angle}
        for direction,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    data('data/techguns/loot_table/blocks/camo_bench.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'techguns:camo_bench'}],
        'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    data('data/minecraft/tags/block/mineable/pickaxe.json', {'replace':False,'values':['techguns:camo_bench']})
    files[RESOURCES + 'assets/techguns/textures/gui/camo_bench.png'] = (assets / 'textures/gui/camo_bench_gui.png').read_bytes()
    files['content/camo-bench.json'] = (json.dumps(catalog, ensure_ascii=False, indent=2) + '\n').encode()
    lines = ['        new CamoPalette("' + p['id'] + '", List.of(' + ', '.join('"' + item + '"' for item in p['items']) + '))' for p in catalog['palettes']]
    files['core/src/main/java/techguns/core/CamoPalettes.java'] = ('''package techguns.core;

import java.util.List;
import java.util.Optional;

/** Generated from TGMachineRecipes with the original block/banner metadata order. */
public final class CamoPalettes {
    public static final List<CamoPalette> ALL = List.of(
''' + ',\n'.join(lines) + '''
    );
    public static Optional<CamoPalette> forItem(String item) { return ALL.stream().filter(p -> p.index(item) >= 0).findFirst(); }
    private CamoPalettes() {}
}
''').encode()
    return files


def camo_translations(lang):
    ru = lang == 'ru_ru'; name = 'Камуфляжный станок' if ru else 'Camo Bench'
    result = {'block.techguns.camo_bench':name, 'item.techguns.camo_bench':name,
        'gui.techguns.camo.unsupported':'Нет вариантов' if ru else 'No variants',
        'gui.techguns.camo.variant':'Вариант %s/%s: %s' if ru else 'Variant %s/%s: %s',
        'gui.techguns.camo.forward':'Следующий вариант' if ru else 'Next variant',
        'gui.techguns.camo.back':'Предыдущий вариант' if ru else 'Previous variant'}
    for color, russian in zip(COLORS, ['Белый','Оранжевый','Пурпурный','Голубой','Жёлтый','Лаймовый','Розовый','Серый','Светло-серый','Бирюзовый','Фиолетовый','Синий','Коричневый','Зелёный','Красный','Чёрный']):
        result['gui.techguns.camo.color.' + color] = russian if ru else color.replace('_',' ').title()
    for i, english, russian in [(0,'Item','Предмет'),(1,'Helmet','Шлем'),(2,'Chestplate','Нагрудник'),(3,'Leggings','Поножи'),(4,'Boots','Ботинки')]:
        result['gui.techguns.camo.target_' + str(i)] = russian if ru else english
    return result
