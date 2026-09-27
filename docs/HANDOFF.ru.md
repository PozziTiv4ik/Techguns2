# Продолжение работы в новом чате

Обновлено 2026-09-27 после MilitaryCamp и успешного Linux CI #72.
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
| Последний проверенный код | `2fb998f08ae48145de23175a8f280277cd158330` — MilitaryCamp |
| Linux CI кода | [Linux CI #72](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36338938613), success; 2026-09-27 20:13 Europe/Zurich |

MilitaryCamp перенесён как процедурная структура: списки компонентов
13/7/2, исходные размеры/повторы, рельеф, дороги/ограда, все постройки,
военные ящики, две вложенные таблицы сундуков и конечные посты солдат/вертолёта.
План сохраняется до размещения по чанкам. Природная генерация включена;
билет пока не перенесённого Castle сохранён. См. [MILITARY_CAMP.ru.md](MILITARY_CAMP.ru.md).

Локально прошли **222 JUnit, 396 Python и 2109 GameTests (2045 + 48 + 16)**; основной сервер включает
2044 Techguns и один встроенный тест. Python — единый discover без пропусков.
Генератор: 2410 файлов; 2297 ресурсных записей JAR совпали
побайтово. Лицензия, legacy и исключение тестовых паков проверены.
Подробности — последний раздел [VERIFICATION.ru.md](VERIFICATION.ru.md).
Локальные `.tools/camp-*` помогают, но их отсутствие не блокирует новый клон.

Linux CI #72 подтвердил именно указанный source SHA: 396 Python единым
discover, сборку/ядро и все 2109 GameTests. Все 13 шагов успешны.
После source SHA меняется только документация с `[skip ci]`. Текущий HEAD
получать из Git, не принимать source SHA за последний коммит ветки.
Незавершённой реализации и известных красных проверок этого среза нет.
Не повторять проверки без изменений или сомнений.

## Следующий законченный срез — CastleStructure / PresetCastle

Перенести второй большой LAND-кандидат: настоящий процедурный замок с
исходным графом подземелья, шестью наборами сегментов, снабжением и охраной.
Не заменять его одной декоративной постройкой и не перераспределять вес
пока не перенесённого AircraftCarrier из отдельной WATER-таблицы.

Исходники под `legacy/1.12.2/src/main/java/techguns/`:

- `world/structures/CastleStructure.java`, `WorldgenStructure.java`;
- `world/dungeon/Dungeon.java`, `DungeonTemplate.java`, `DungeonSegment.java`,
  `MazeDungeonPath.java`, `TemplateSegment.java`, `IDungeonPath.java`;
- `world/dungeon/presets/PresetCastle.java`, `IDungeonPreset.java`;
- палитры и specialblocks, на которые фактически ссылаются сегменты.

Castle выбирает X/Z независимо 32..47, Y 24..39; heightdiffLimit=10,
наследуемый шаг проверки 4. Сверить исходные поворот, origin shift,
выбор поверхности и смещение `posY+1-PRESET_CASTLE.getSizeY()`.
Dungeon допускает пять попыток; восстановить активные ветки MazeDungeonPath,
ограничения объёма, уровни, лестницы, развилки, комнаты, опоры и крышу.
PresetCastle: startHeightLevel=1, chanceStraight=.8, chanceRamp=.5,
chanceRoom=.25, chanceFork=.4, chanceUp=.65; foundations/pillars/roof включены.

Все шесть исходных файлов находятся в
`legacy/1.12.2/src/main/resources/assets/techguns/dungeons/`:
`ncdung1.ser`, `nclower1.ser`, `ncmid1.ser`, `ncupper1.ser`, `nctop1.ser`,
`ncroof1.ser`. Это сериализованные шаблоны: сначала разобрать их формат,
все клетки/палитры/метаданные и реальные зависимости. Недостающие блоки
переносить по исходнику, не подменять содержимое молча.

Охрана PresetCastle: 2 смерти / 2 активных / 200 тиков / радиус 2,
ZombieSoldier:SkeletonSoldier = 1:1. Оба NPC уже работают.
`loot_tables/chests/castle.json`: gun 1..2, generic 3..6,
ammo/explosives/armor 3..6 с весами 3:1:2; это вложенные loot tables.
Все шесть целевых таблиц `techguns:blocks/military_crate_*` уже готовы.

Большой LAND-пул должен остаться MilitaryCamp:Castle = 1:1. При добавлении
отдельного native ID использовать тот же seed кандидата, как у существующих
малых/средних структур; оба объекта не должны занимать одну точку.
Проверить целый сохранённый план, все сегменты, границы чанков и повороты,
настоящий лут/конечные смерти и положительные/отрицательные природные точки.

## Полезные современные точки

`tools/legacy_military_camp.py`, `content/military-camp.json`,
`core/src/main/java/techguns/core/MilitaryCampRules.java` и пакет
`modern/world/structure/camp/` содержат воспроизводимые алгоритмы и адаптеры.
Все классы modern находятся в `platforms/neoforge-26.2/src/main/java/techguns/modern/`.
`MilitaryCampStructure` выполняет выбор/планирование; `MilitaryCampPiece`
сохраняет весь план, seeds, chest/post данные и уже размещённые чанки.

Снимок шумовых колонок и окончательная поверхность отличаются. Соединения
блоков используют полный план плюс реальный нетронутый грунт; учитывают
предстоящую очистку соседа и штатную финальную обработку ProtoChunk.
SimpleBlockFeature/TreeFeature защищают поверхность только во время worldgen;
игрок и последующее выращивание не блокируются. Не расширять защиту на весь
подземный объём высокого bounding box. Военные ящики не контейнеры;
`blocks/military_crate_*` — награды, `*_self` — обычные block drops.

Графическая приёмка, внешний Chisel, анимации и полная legacy FX-система
остаются. Интерактивный клиент/рабочий стол не трогать. Полный порт остаётся
в [плане](PORTING_PLAN.ru.md) и [статусе](STATUS.ru.md); бинарные релизы не публикуются.
