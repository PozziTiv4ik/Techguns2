# Продолжение работы в новом чате

Обновлено 2026-09-27 после локальной проверки Flamethrower.
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
| Локально проверенный срез | Flamethrower; Linux CI нового source commit ожидается |
| Предыдущий проверенный код | `fada63e61161be734fe16ff893328c7dfcc8cf81`, [Linux CI #69](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36327092043), GrenadeLauncher |

Flamethrower работает: 100 зарядов топлива, автоматический огонь каждые
2 тика, R 45 с возвратом пустого бака, оба рецепта и Grinder. FIRE/броня,
поджог, B/права, гравитация/TTL, вода/дождь, сохранение, NPC и исходные
start/fire/reload проверены. Перенесены 36 деталей, исходная PNG/семь OGG
и отдача от первого лица. См. [FLAMETHROWER.ru.md](FLAMETHROWER.ru.md).

Локально прошли **214 JUnit, 383 Python и 1999 GameTests (1937 + 46 + 16)**: основной сервер включает
1936 Techguns + один встроенный тест. Python выполнен единым discover.
Генератор: 2319 файлов; 2220 ресурсных записей JAR совпали
побайтово. Лицензия, legacy и исключение тестовых паков проверены.
Подробности — последний раздел [VERIFICATION.ru.md](VERIFICATION.ru.md).
Локальные `.tools/flame-*` помогают, но их отсутствие не блокирует новый клон.

Сначала завершить exact-SHA Linux CI этого source commit и записать
документационный checkpoint с `[skip ci]`, если это ещё не сделано по Git.

## Следующий законченный срез — военные ящики

Все четыре боевые зависимости готовы: Stielgranate, FragGrenade,
GrenadeLauncher и Flamethrower. Перенести девять BlockMilitaryCrate с шестью
полными таблицами добычи. Не удалять награды и не перераспределять веса.
Это добыча при разрушении, а не контейнер для хранения.

Исходные точки:

- `legacy/1.12.2/src/main/java/techguns/blocks/BlockMilitaryCrate.java`;
- `EnumMilitaryCrateType.java` и `GenericBlockMetaEnum.java` в той же папке;
- `legacy/1.12.2/src/main/java/techguns/TGBlocks.java`;
- `legacy/1.12.2/src/main/java/techguns/events/TGEventHandler.java`, MilitaryCrateDrops;
- шесть `legacy/1.12.2/src/main/resources/assets/techguns/loot_tables/blocks/military_crate_*.json`;
- исходные blockstates/models/textures/recipes для military_crate; восстановить активную регистрацию.

Порядок metadata: AMMO, GUN, ARMOR, MEDICAL, EXPLOSIVE, GENERIC_OAK,
GENERIC_JUNGLE, GENERIC_BIRCH, GENERIC_SPRUCE. Последние четыре используют
общую generic-таблицу. Взрывной пул 2/2/2/2/1/1: rocket, 40mmgrenade,
Stielgranate, FragGrenade, RocketLauncher, GrenadeLauncher; Flamethrower
присутствует в оружейном пуле. Сверить все количества, веса, функции и
компоненты современного боезапаса/брони, включая правила повреждения предметов.

MilitaryCrateDrops работает на HarvestDropsEvent HIGH: только сервер,
ящик, игрок-добытчик и отсутствие Silk Touch. Очищает обычный дроп,
строит LootContext с Fortune как luck и withPlayer. Не подменять Fortune
обычной удачей игрока. Silk Touch и разрушение без игрока сохраняют исходный
дроп ящика. Проверить реально исполняемые события новой платформы.

Форма: x/z 0,03125..0,96875, y 0..1; неполный, не является сплошным
непрозрачным кубом. Верх/низ CENTER_BIG. Проверить материал,
звук по фактической цепочке конструкторов и опору прикрепляемых блоков.
После ящиков — MilitaryBaseStructure/MilitaryCamp. AttackHelicopter,
ArmySoldier, конечный военный пост и Neonlights готовы. Сохранить исходный
билет CastleStructure в большой LAND-таблице; AircraftCarrier относится к WATER.

## Полезные современные точки

`BurningProjectile` — общие полёт/урон/поджог/сохранение; IncendiaryBullet
и FlameProjectile явно задают различия фабрик. Не возвращать множитель 1,1
или нулевую гравитацию зажигательных пуль в огнемёт. `FlameFiring` реализует
конечные звуковые образцы с per-hand паузой 10 тиков; у NPC start не используется.
`FlamethrowerModel` — синусоида только первого лица. Детали и ограничения
сохранены в отдельном описании. Grinder имеет 58 рецептов.
Для ящиков полезны существующие `tools/legacy_survivor_hideout.py`,
`world/structure/NetherLootPiece.java` и `SurvivorHideoutPiece.java` в
`platforms/neoforge-26.2/src/main/java/techguns/modern/`.

## Границы результата

MilitaryCamp пока не генерируется. GPU-приёмка, внешний Chisel, остальные
анимации и полная legacy FX-система остаются. Интерактивный клиент и рабочий
стол не трогать. Полный порт остаётся в [плане](PORTING_PLAN.ru.md)
и [статусе](STATUS.ru.md); бинарные релизы не публикуются.
