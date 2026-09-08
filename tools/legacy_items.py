"""Shared legacy metadata, ingredient tags and Java argument parsing."""
from pathlib import Path
import re
from legacy_models import strip_comments
ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "legacy/1.12.2/src/main"

# Modern tag, and the original shared material that contributes to it (if any).
ORE_TAGS = {
    'STONE': ('c:stones', None), 'COBBLESTONE': ('c:cobblestones', None),
    'LOGWOOD': ('minecraft:logs', None), 'BLOCKWOOL': ('minecraft:wool', None),
    'BLOCKGLASS': ('c:glass_blocks', None), 'PANEGLASS': ('c:glass_panes', None),
    'HARDENEDGLASSORGLASS': ('c:glass_blocks/hardened', None),
    'DUSTREDSTONE': ('c:dusts/redstone', None), 'GEMDIAMOND': ('c:gems/diamond', None),
    'INGOTIRON': ('c:ingots/iron', None), 'NUGGETIRON': ('c:nuggets/iron', None),
    'INGOTCOPPER': ('c:ingots/copper', None),
    'INGOTGOLD': ('c:ingots/gold', None), 'NUGGETCOPPER': ('c:nuggets/copper', None),
    'INGOTLEAD': ('c:ingots/lead', 'ingotlead'), 'NUGGETLEAD': ('c:nuggets/lead', 'nuggetlead'),
    'INGOTSTEEL': ('c:ingots/steel', 'ingotsteel'), 'NUGGETSTEEL': ('c:nuggets/steel', 'nuggetsteel'),
    'INGOTOBSIDIANSTEEL': ('c:ingots/obsidian_steel', 'ingotobsidiansteel'),
    'PLATEIRON': ('c:plates/iron', 'plateiron'), 'PLATESTEEL': ('c:plates/steel', 'platesteel'),
    'PLATEOBSIDIANSTEEL': ('c:plates/obsidian_steel', 'plateobsidiansteel'),
    'SHEETPLASTIC': ('c:sheets/plastic', 'plasticsheet'),
    'WIRECOPPER': ('c:wires/copper', 'copperwire'),
    'PLATECOPPER': ('c:plates/copper', 'platecopper'),
    'DYEGREEN': ('c:dyes/green', None),
    'BLOCKIRON': ('c:storage_blocks/iron', None),
    'INGOTTIN': ('c:ingots/tin', 'ingottin'), 'INGOTBRONZE': ('c:ingots/bronze', 'ingotbronze'),
    'INGOTTITANIUM': ('c:ingots/titanium', 'ingottitanium'),
    'PLATETIN': ('c:plates/tin', 'platetin'), 'PLATEBRONZE': ('c:plates/bronze', 'platebronze'),
    'PLATELEAD': ('c:plates/lead', 'platelead'), 'PLATECARBON': ('c:plates/carbon', 'platecarbon'),
    'PLATETITANIUM': ('c:plates/titanium', 'platetitanium'),
    'FIBERCARBON': ('c:fibers/carbon', 'carbonfibers'), 'GEMQUARTZ': ('c:gems/quartz', None),
}


def shared_items():
    source = strip_comments((LEGACY / 'java/techguns/TGItems.java').read_text())
    # GenericItemShared assigns sharedItems.size() even to disabled optional entries.
    return dict(enumerate(re.findall(r'SHARED_ITEM\.addsharedVariant(?:Optional)?\("([^"]+)"', source)))


def shared_fields():
    source = strip_comments((LEGACY / 'java/techguns/TGItems.java').read_text())
    return dict(re.findall(r'(\w+)\s*=\s*SHARED_ITEM\.addsharedVariant(?:Optional)?\("([^"]+)"', source))


def ammo_slot_items():
    source = strip_comments((LEGACY / 'java/techguns/TGItems.java').read_text())
    return {name for name, args in re.findall(r'SHARED_ITEM\.addsharedVariant\("([^"]+)"([^;]+);', source) if 'TGSlotType.AMMOSLOT' in args}


def arguments(text):
    parts, start, depth, quoted = [], 0, 0, False
    for i, char in enumerate(text):
        if char == '"': quoted = not quoted
        if not quoted:
            if char == '(': depth += 1
            elif char == ')': depth -= 1
            elif char == ',' and depth == 0:
                parts.append(text[start:i].strip())
                start = i+1
    return parts + [text[start:].strip()]


def parse_stack(expression):
    fields = shared_fields()
    match = re.fullmatch(r'TGItems\.newStack\(TGItems\.(\w+),\s*(\d+)\)', expression)
    if match: return {'id': 'techguns:'+fields[match[1]], 'count': int(match[2])}
    match = re.fullmatch(r'TGItems\.(\w+)', expression)
    if match: return {'id': 'techguns:'+fields[match[1]], 'count': 1}
    match = re.fullmatch(r'new ItemStack\((Items|Blocks)\.(\w+)(?:,\s*(\d+))?(?:,\s*(\d+))?\)', expression)
    if match:
        name, count, metadata = match[2].lower(), int(match[3] or 1), int(match[4] or 0)
        if name == 'coal' and metadata == 1: name, metadata = 'charcoal', 0
        if name == 'stonebrick' and metadata == 0: name = 'stone_bricks'
        if metadata != 0: raise ValueError(f'Unconverted vanilla metadata: {expression}')
        return {'id': 'minecraft:'+name, 'count': count}
    raise ValueError(f'Unconverted machine stack: {expression}')
