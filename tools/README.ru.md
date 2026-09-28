# Генераторы: где менять источник данных

[Карта Java-кода и тестов](../docs/CODE_MAP.ru.md) ·
[описания систем](../docs/README.ru.md) · [порядок проверок](../docs/DEVELOPMENT_WORKFLOW.ru.md).

## Входы и выходы

| Файл | Роль |
|---|---|
| [audit_legacy.py](audit_legacy.py) | Читает оригинал и создаёт [legacy-inventory.json](../content/legacy-inventory.json). |
| [generate_weapon_content.py](generate_weapon_content.py) | Общий запуск **всего** выбранного контента, несмотря на имя weapon. `generate()` возвращает словарь `путь → bytes`; запись на диск выполняется только CLI без `--check`. |
| [weapon-ports.json](../content/weapon-ports.json) | Редактируемый входной список оружия/моделей. Параметры поведения извлекаются из оригинала, а не задаются в этом списке. |
| [legacy_*.py](.) | Преобразования по областям ниже: каталоги content, Java-таблицы, рецепты, теги, шаблоны, модели, текстуры и звуки. |
| [verify_resources.py](verify_resources.py) | Проверка ресурсных ссылок и целостности результата; не запускает генерацию. |
| [dev.ps1](dev.ps1) | Windows-обёртка Gradle с Java 25; не генератор данных. |

Рабочая цепочка: прочитать неизменяемый оригинал → исправить нужное преобразование
→ запустить общий генератор → просмотреть все изменённые выходы → выполнить
проверки по [workflow](../docs/DEVELOPMENT_WORKFLOW.ru.md#implementation-checks).

`legacy_*.py` — импортируемые модули, а не отдельные команды регенерации.
Подключение их результатов, переводов и общих ресурсов находится в
generate_weapon_content.py. При добавлении модуля проверить и импорт, и вызов.
Общие теги могут объединяться; другие несовпадающие коллизии ресурсов должны
оставаться видимой ошибкой, а не скрываться перезаписью.

Не определяйте сгенерированный файл только по каталогу или комментарию:
core содержит и ручной код. Примеры выходов — Weapons.java, Armors.java,
NpcWeapons.java, CastleMaze.java и CastleSegments.java. Для точного списка
используйте `generate().keys()`; пример внизу не пишет файлы.

<a id="domains"></a>

## Преобразование → адресная проверка Python

Это точки входа, а не полный граф импортов. Один генератор может обслуживать
несколько систем; тесты могут дополнительно проверять общие зависимости.
JUnit и GameTests ищите по [карте кода](../docs/CODE_MAP.ru.md#tests).

[Оружие/броня](#weapons) · [станки](#machines) · [NPC](#npcs) ·
[блоки](#blocks) · [структуры](#structures).

<a id="weapons"></a>

### Оружие, броня и модели

| Область | Модуль преобразования | Проверки |
|---|---|---|
| Арсенал, боеприпасы, лазеры | [generate_weapon_content.py](generate_weapon_content.py), [legacy_crafting.py](legacy_crafting.py) | [test_lasers.py](tests/test_lasers.py), [test_crafting.py](tests/test_crafting.py) |
| Ракетница | [legacy_rockets.py](legacy_rockets.py) | [test_rockets.py](tests/test_rockets.py) |
| Бензопила | [legacy_chainsaw.py](legacy_chainsaw.py) | [test_chainsaw.py](tests/test_chainsaw.py) |
| Ручные гранаты | [legacy_grenades.py](legacy_grenades.py) | [test_grenades.py](tests/test_grenades.py) |
| GrenadeLauncher / 40 мм | [legacy_grenade_launcher.py](legacy_grenade_launcher.py) | [test_grenade_launcher.py](tests/test_grenade_launcher.py) |
| Flamethrower | [legacy_flamethrower.py](legacy_flamethrower.py) | [test_flamethrower.py](tests/test_flamethrower.py) |
| Зажигательные боеприпасы | [legacy_incendiary.py](legacy_incendiary.py) | [test_incendiary.py](tests/test_incendiary.py) |
| Броня, включая Pigman; берет и Commando дополняют свои NPC-модули | [legacy_armors.py](legacy_armors.py) | [test_armors.py](tests/test_armors.py), [test_hazmat.py](tests/test_hazmat.py), [test_t1_combat.py](tests/test_t1_combat.py), [test_t1_miner.py](tests/test_t1_miner.py), [test_t1_scout.py](tests/test_t1_scout.py) |
| Общие Java-модели / OBJ и исходные ItemStack | [legacy_models.py](legacy_models.py), [legacy_items.py](legacy_items.py) | [test_models.py](tests/test_models.py), [test_crafting.py](tests/test_crafting.py) |

<a id="machines"></a>

### Станки и жидкости

| Область | Модуль преобразования | Проверки |
|---|---|---|
| Ammo Press, Metal Press, Blast Furnace | [legacy_machines.py](legacy_machines.py) | [test_machines.py](tests/test_machines.py) |
| Жидкости и Chemical Laboratory | [legacy_fluids.py](legacy_fluids.py), [legacy_chemistry.py](legacy_chemistry.py) | [test_chemistry.py](tests/test_chemistry.py) |
| Reaction Chamber и радиация | [legacy_reactions.py](legacy_reactions.py), [legacy_radiation.py](legacy_radiation.py) | [test_reactions.py](tests/test_reactions.py) |
| Fabricator | [legacy_fabricator.py](legacy_fabricator.py) | [test_fabricator.py](tests/test_fabricator.py) |
| Charging Station | [legacy_charging.py](legacy_charging.py) | [test_charging.py](tests/test_charging.py) |
| Repair Bench | [legacy_repair.py](legacy_repair.py) | [test_repair.py](tests/test_repair.py) |
| Camo Bench | [legacy_camo.py](legacy_camo.py) | [test_camo.py](tests/test_camo.py) |
| Grinder | [legacy_grinder.py](legacy_grinder.py) | [test_grinder.py](tests/test_grinder.py) |
| Ore Drill | [legacy_drill.py](legacy_drill.py) | [test_drill.py](tests/test_drill.py) |

<a id="npcs"></a>

### NPC и спавнеры

| Область | Модуль преобразования | Проверки |
|---|---|---|
| SuperMutantBasic и общие данные NPC | [legacy_npcs.py](legacy_npcs.py) | [test_npcs.py](tests/test_npcs.py) |
| CyberDemon / Nether Blaster | [legacy_cyber.py](legacy_cyber.py) | [test_cyber.py](tests/test_cyber.py) |
| ZombieSoldier / Overworld-пул | [legacy_zombie_soldier.py](legacy_zombie_soldier.py) | [test_zombie_soldier.py](tests/test_zombie_soldier.py) |
| ZombieFarmer и ZombieMiner | [legacy_rural_zombies.py](legacy_rural_zombies.py) | [test_rural_zombies.py](tests/test_rural_zombies.py) |
| SkeletonSoldier | [legacy_skeleton.py](legacy_skeleton.py) | [test_skeleton.py](tests/test_skeleton.py) |
| Bandit | [legacy_bandit.py](legacy_bandit.py) | [test_bandit.py](tests/test_bandit.py) |
| PsychoSteve | [legacy_psychosteve.py](legacy_psychosteve.py) | [test_psychosteve.py](tests/test_psychosteve.py) |
| HOLE / SOLDIER_SPAWN | [legacy_spawner.py](legacy_spawner.py) | [test_spawner.py](tests/test_spawner.py) |
| ArmySoldier / берет | [legacy_army.py](legacy_army.py) | [test_army.py](tests/test_army.py) |
| Commando / T2 Commando | [legacy_commando.py](legacy_commando.py) | [test_commando.py](tests/test_commando.py) |
| Ghastling | [legacy_ghastling.py](legacy_ghastling.py) | [test_ghastling.py](tests/test_ghastling.py) |
| AttackHelicopter | [legacy_helicopter.py](legacy_helicopter.py) | [test_helicopter.py](tests/test_helicopter.py) |
| AlienBug | [legacy_alienbug.py](legacy_alienbug.py) | [test_spike.py](tests/test_spike.py) |
| ZombiePoliceman | [legacy_policeman.py](legacy_policeman.py) | [test_police.py](tests/test_police.py) |

<a id="blocks"></a>

### Блоки и ресурсы

| Область | Модуль преобразования | Проверки |
|---|---|---|
| Обычные руды и плавка | [legacy_ores.py](legacy_ores.py) | [test_ores.py](tests/test_ores.py) |
| Рудные кластеры | [legacy_clusters.py](legacy_clusters.py) | [test_clusters.py](tests/test_clusters.py) |
| Панели, бетон и лестницы | [legacy_building.py](legacy_building.py) | [test_building.py](tests/test_building.py) |
| Мешки, лампы и дверь | [legacy_fortifications.py](legacy_fortifications.py) | [test_fortifications.py](tests/test_fortifications.py) |
| Камуфляжные сети | [legacy_camonets.py](legacy_camonets.py) | [test_camonets.py](tests/test_camonets.py) |
| Neonlights | [legacy_neon.py](legacy_neon.py) | [test_neon.py](tests/test_neon.py) |
| Военные ящики | [legacy_military_crates.py](legacy_military_crates.py) | [test_military_crates.py](tests/test_military_crates.py) |
| Металлические ступени, включая зависимость авианосца | [legacy_metal_stairs.py](legacy_metal_stairs.py), рецепты — [legacy_crafting.py](legacy_crafting.py) | [test_aircraft_carrier.py](tests/test_aircraft_carrier.py) |

<a id="structures"></a>

### Структуры и пулы

| Область | Модуль преобразования | Проверки |
|---|---|---|
| Общая схема placement всех размеров, исходные настройки и активные кандидаты; `content/structure-grids.json` | [legacy_structure_grids.py](legacy_structure_grids.py); остальные конвертеры структур вызывают его `grid_placement()` | [test_structure_grids.py](tests/test_structure_grids.py) |
| Nether Metal, малые локации Незера, общие таблицы/сканы | [legacy_locations.py](legacy_locations.py) | [test_locations.py](tests/test_locations.py), [test_acid_location.py](tests/test_acid_location.py), [test_ghastling.py](tests/test_ghastling.py), [test_clusters.py](tests/test_clusters.py) |
| OreClusterSpike | [legacy_spike.py](legacy_spike.py) | [test_spike.py](tests/test_spike.py) |
| OreClusterMeteorBasis | [legacy_meteor.py](legacy_meteor.py) | [test_meteor.py](tests/test_meteor.py) |
| AlienBugNest | [legacy_bugnests.py](legacy_bugnests.py) | [test_bugnests.py](tests/test_bugnests.py) |
| NetherOreClusterCastle | [legacy_nether_castle.py](legacy_nether_castle.py) | [test_nether_castle.py](tests/test_nether_castle.py) |
| NetherAltarMedium | [legacy_medium_altar.py](legacy_medium_altar.py) | [test_medium_altar.py](tests/test_medium_altar.py) |
| NetherGhastSpawner | [legacy_ghast_spawner.py](legacy_ghast_spawner.py) | [test_ghast_spawner.py](tests/test_ghast_spawner.py) |
| PoliceStation | [legacy_police_station.py](legacy_police_station.py) | [test_police.py](tests/test_police.py) |
| SurvivorHideout | [legacy_survivor_hideout.py](legacy_survivor_hideout.py) | [test_survivor_hideout.py](tests/test_survivor_hideout.py) |
| DesertOilCluster | [legacy_desert_oil.py](legacy_desert_oil.py) | [test_desert_oil.py](tests/test_desert_oil.py) |
| GasStation | [legacy_gas_station.py](legacy_gas_station.py) | [test_gas_station.py](tests/test_gas_station.py) |
| SmallTrainstation | [legacy_train_station.py](legacy_train_station.py) | [test_train_station.py](tests/test_train_station.py) |
| FactoryHouseSmall | [legacy_factory_house.py](legacy_factory_house.py) | [test_factory_house.py](tests/test_factory_house.py) |
| SmallMine | [legacy_small_mine.py](legacy_small_mine.py) | [test_small_mine.py](tests/test_small_mine.py) |
| MilitaryBaseStructure / MilitaryCamp | [legacy_military_camp.py](legacy_military_camp.py) | [test_military_camp.py](tests/test_military_camp.py) |
| CastleStructure / PresetCastle, чтение .ser как данных | [legacy_castle.py](legacy_castle.py), [legacy_java_serialization.py](legacy_java_serialization.py) | [test_castle.py](tests/test_castle.py) |
| AircraftCarrier: полный скан, два прохода и WATER-билет | [legacy_aircraft_carrier.py](legacy_aircraft_carrier.py) | [test_aircraft_carrier.py](tests/test_aircraft_carrier.py) |

## Команды поиска и регенерации

Из корня репозитория. Первые две команды записывают результаты; остальные
проверяют или читают. После изменения генератора нужен полный набор из workflow.

```powershell
python tools/audit_legacy.py
python tools/generate_weapon_content.py
python tools/audit_legacy.py --check
python tools/generate_weapon_content.py --check
python -m unittest discover -s tools/tests -p 'test_castle.py' -v
python tools/verify_resources.py
```

Найти выходы общего генератора с `castle` в имени, без записи файлов:

```powershell
python -c "import sys; sys.path.insert(0, 'tools'); from generate_weapon_content import generate; print('\n'.join(p for p in generate() if 'castle' in p.lower()))"
```

Сначала искать владельца проще по строкам пути/ID:

```powershell
rg -n -g '*.py' 'CastleMaze|castle/templates|content/castle' tools
rg -n 'sha256|CastleStructure' content/castle.json
```

Для нового или переименованного преобразования обновите соответствующую строку
этой карты. Не храните здесь копии исходных параметров, чисел тестов и статуса CI.
