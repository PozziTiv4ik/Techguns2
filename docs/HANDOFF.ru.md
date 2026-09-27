# Продолжение работы в новом чате

Обновлено 2026-09-27 после военных ящиков и успешного Linux CI #71.
Перед разработкой прочитать [порядок работы](DEVELOPMENT_WORKFLOW.ru.md),
затем проверить Git. [AGENTS.md](../AGENTS.md) обязателен для всего проекта.

## Репозиторий и текущая точка

| Поле | Значение |
|---|---|
| Рабочая папка | `D:\Project\Techguns` |
| Продолжаемая ветка | `port/26.2-neoforge` |
| Writable fork / origin | `PozziTiv4ik/Techguns2` |
| Оригинал / upstream, только чтение | `pWn3d1337/Techguns2` |
| Платформа | Minecraft 26.2 / NeoForge 26.2.0.81 / Java 25 |
| Последний проверенный код | `75b40502971460dd1a4f6854867dcd8030b6dcf4` — военные ящики |
| Linux CI кода | [Linux CI #71](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36333299515), success; 2026-09-27 18:40 Europe/Zurich |

Девять военных ящиков работают: шесть полных таблиц / 60 наград, исходные
веса/диапазоны, Fortune, Silk Touch, события и реальные добыча/взрыв/подбор.
Найденные оружие и лекарства действуют. Рецептов и хранения нет, как в оригинале.
Модели и десять PNG перенесены. См. [MILITARY_CRATES.ru.md](MILITARY_CRATES.ru.md).

Локально прошли **214 JUnit, 390 Python и 2069 GameTests (2007 + 46 + 16)**; основной сервер включает
2006 Techguns и один встроенный тест. Python — единый discover без пропусков.
Генератор: 2393 файла; 2292 ресурсные записи JAR совпали
побайтово. Лицензия, legacy и исключение тестовых паков проверены.
Подробности — последний раздел [VERIFICATION.ru.md](VERIFICATION.ru.md).
Локальные `.tools/crate-*` помогают, но их отсутствие не блокирует новый клон.

Linux CI #71 подтвердил именно указанный source SHA: 390 Python единым
discover, сборку/ядро и все 2069 GameTests. Все 13 шагов успешны.
После source SHA меняется только документация с `[skip ci]`. Текущий HEAD
получать из Git, не принимать source SHA за последний коммит ветки.
Незавершённой реализации и известных красных проверок этого среза нет.
Не повторять проверки без изменений или сомнений.

## Следующий законченный срез — MilitaryBaseStructure / MilitaryCamp

Перенести процедурный военный лагерь и подключить его к исходному выбору
больших локаций Верхнего мира. AttackHelicopter, ArmySoldier, конечный военный
пост, Neonlights, все боевые награды и военные ящики теперь готовы.
Не заменять лагерь одной декоративной заготовкой и не сокращать его компоненты.

Исходные точки в `legacy/1.12.2/src/main/java/techguns/`:

- `world/structures/MilitaryBaseStructure.java` и `MilitaryCamp.java`;
- `world/TGStructureSpawnRegister.java`, общий выбор больших локаций;
- `world/structures/MBlockRegister.java`, MultiMBlock и раскладки снабжения;
- компоненты Tent, Containers, CampProps, Bunker, Barracks, Helipad, Tanks,
  WatchTowerSmall и их базовый WorldgenStructure;
- `util/BlockUtils.java`: поиск высоты, очистка, flattenArea, FILTER_GAUSSIAN_5x5;
- исходные `loot_tables/chests/militarybase_bunker.json` и `militarybase_barracks.json`
  в `legacy/1.12.2/src/main/resources/assets/techguns/`.

MilitaryBaseStructure берёт независимые X/Z **32+rnd.nextInt(48)** (32..79),
Y=8. Проверяет getValidSpawnYArea с допуском 5 и шагом 8. При неудаче,
если обе стороны >=32, повторяет попытку со сторонами на 16 меньше.
При успехе очищает область с ободком 1 и создаёт MilitaryCamp(4,rnd).
У лагеря активны очистка/выравнивание/гауссово сглаживание, разделение
на сегменты, дороги, ограда, ворота, цвет биома и точки встреч.
Сверить активные ветки RNG и сохранение результата до размещения по чанкам.

Внутренний список содержит 13 записей компонентов, граничный — 7,
угловой — 2. Сохранять повторяющиеся варианты, отбор по размерам и swapXZ;
это не равномерный выбор уникальных классов. Проверить генерацию всех
веток, реальный лут, освещение Helipad, посты, развороты и естественные точки.
initCampFlagEntity содержит закомментированный CampFlagTileEnt; не переносить
комментарий как работающую механику, прочитать остальную активную ветку.

Таблицы бункера/казарм содержат **вложенные loot_table**, а не предметы
с ID blocks/military_crate_*. Шесть целевых таблиц уже зарегистрированы.
Бункер: gun 1..2, generic 1..3, ammo/explosives 3..6 с весами 3:1.
Казарма: medical 2..6 и armor 1..4. Сохранить полные ссылки/контекст/бонусы.

В TGStructureSpawnRegister большие LAND-кандидаты — MilitaryBaseStructure
и CastleStructure, веса 1:1. Сохранить билет пока не перенесённого Castle;
AircraftCarrier — отдельный WATER-кандидат, не переносить его вес на сушу.
Сверить сетку, резервирование, высоту, биомы и конфиги через существующие
`world/structure/Location*` и `core/*Location*` (найти точные классы через rg).

## Полезные современные точки

`tools/legacy_military_crates.py`, `content/military-crates.json`,
`core/src/main/java/techguns/core/MilitaryCrates.java`, `modern/world/MilitaryCrateContent.java`:
девять ID и точное сопоставление metadata, fromMetadata для палитры лагеря.
Военные ящики не контейнеры. Событие MilitaryCrateDrops HIGH обрабатывает
только игрока без Silk Touch; прямой Block.getDrops возвращает ящик.
Loot-таблицы blocks/military_crate_* хранят содержимое; *_self — обычный блок.
Не менять их роли при подключении вложенного лута или структуры.

Все классы modern находятся в `platforms/neoforge-26.2/src/main/java/techguns/modern/`.
Исходная генерация лагеря ещё не включена. GPU-приёмка, внешний Chisel,
анимации и полная legacy FX-система остаются. Интерактивный клиент/рабочий
стол не трогать. Полный порт остаётся в [плане](PORTING_PLAN.ru.md)
и [статусе](STATUS.ru.md); бинарные релизы не публикуются.
