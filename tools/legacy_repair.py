"""Repair Bench geometry, original pixels and inventory contract from the 1.12.2 source."""
import json
from legacy_items import LEGACY

RESOURCES = 'platforms/neoforge-26.2/src/main/resources/'


def generate_repair_content():
    files = {}
    assets = LEGACY / 'resources/assets/techguns'
    def data(path, value): files[RESOURCES + path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    source = json.loads((assets / 'models/block/repair_bench.json').read_text())
    for atlas in ('block', 'item'):
        textures = {key: value.replace(':blocks/', ':' + atlas + '/') for key, value in source['textures'].items()}
        data(f'assets/techguns/models/{atlas}/repair_bench.json', {'parent': 'minecraft:block/cube', 'textures': textures})
        for texture in set(source['textures'].values()):
            name = texture.split('/')[-1]
            files[RESOURCES + f'assets/techguns/textures/{atlas}/{name}.png'] = (assets / f'textures/blocks/{name}.png').read_bytes()
    data('assets/techguns/items/repair_bench.json', {'model': {'type': 'minecraft:model', 'model': 'techguns:item/repair_bench'}})
    data('assets/techguns/blockstates/repair_bench.json', {'variants': {
        'facing=' + direction: {'model': 'techguns:block/repair_bench', 'y': angle}
        for direction, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    data('data/techguns/loot_table/blocks/repair_bench.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': 'techguns:repair_bench'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    data('data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': ['techguns:repair_bench']})
    files[RESOURCES + 'assets/techguns/textures/gui/repair_bench.png'] = (assets / 'textures/gui/repair_bench_gui.png').read_bytes()
    files['content/repair-bench.json'] = (json.dumps({
        'source': 'legacy/1.12.2/src/main/java/techguns/tileentities/RepairBenchTileEnt.java',
        'legacy_item': 'techguns:simplemachine@9', 'id': 'techguns:repair_bench',
        'material_slots': list(range(9)), 'repair_slot': 9, 'energy': 0,
        'targets': ['HEAD', 'CHEST', 'LEGS', 'FEET', 'OFFHAND', 'BENCH'],
        'repair': 'All required materials from slots 0..8, then reset damage to zero, retaining other components',
        'implemented_equipment': ['T2_COMBAT','T2_HAZMAT','T1_COMBAT','T1_MINER'], 'pending_equipment': 'Other original armors and shields',
        'automation': 'All faces and unsided; insert/extract all ten slots, repair slot accepts supported armor',
        'access': 'Public or owner only; team integration pending'
    }, ensure_ascii=False, indent=2) + '\n').encode()
    return files


def repair_translations(lang):
    ru = lang == 'ru_ru'
    name = 'Ремонтный станок' if ru else 'Repair Bench'
    result = {'block.techguns.repair_bench': name, 'item.techguns.repair_bench': name}
    for key, english, russian in [
        ('required', 'Required materials:', 'Нужные материалы:'),
        ('full', 'Already fully repaired', 'Полная прочность'),
        ('unsupported', 'This item cannot be repaired here', 'Этот предмет здесь не ремонтируется'),
        ('empty', 'Place or equip armor to repair', 'Положите или наденьте броню'),
        ('cost', '%s × %s', '%s × %s')]:
        result['gui.techguns.repair.' + key] = russian if ru else english
    for index, english, russian in [(1, 'Repair helmet', 'Починить шлем'), (2, 'Repair chestplate', 'Починить нагрудник'),
            (3, 'Repair leggings', 'Починить поножи'), (4, 'Repair boots', 'Починить ботинки'),
            (5, 'Repair offhand item', 'Починить предмет во второй руке'), (6, 'Repair bench item', 'Починить предмет в станке')]:
        result['gui.techguns.repair.target_' + str(index)] = russian if ru else english
    return result
