# Продолжение работы в новом чате

Обновлено 2026-10-01 после локальных проверок Blaster Rifle.
Linux CI нового source ожидается. Прочитать [порядок работы](DEVELOPMENT_WORKFLOW.ru.md)
и [AGENTS.md](../AGENTS.md), затем проверить Git. Поиск — [карта кода](CODE_MAP.ru.md),
[каталог систем](README.ru.md) и [карта генераторов](../tools/README.ru.md).

## Репозиторий и текущая точка

| Поле | Значение |
|---|---|
| Рабочая папка | `D:/Project/Techguns` |
| Продолжаемая ветка | `port/26.2-neoforge` |
| Writable fork / origin | `PozziTiv4ik/Techguns2` |
| Оригинал / upstream, только чтение | `pWn3d1337/Techguns2` |
| Платформа | Minecraft 26.2 / NeoForge 26.2.0.81 / Java 25 |
| Последний source, подтверждённый Linux | `35ac9169f04bab79eda1ed1b39373fd1621070e6` — Scatterbeam Rifle |
| Linux CI Blaster Rifle | Ожидается; прежний [CI #81](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36894452571) новый код не подтверждает |

Работает Blaster Rifle: один летящий ENERGY-снаряд, 50 зарядов/R 45 тиков,
темп 5, падение 10→8 на 25–35 блоках, TTL 30 и сохранённый origin.
Проверены броня/события/вода/NPC/сохранение, оба крафта, Grinder с выбором
материалов и модель из 23 деталей. Старый Scatterbeam без origin совместим;
его постоянный урон и отсутствие исходных рецептов сохранены.
[Описание Blaster Rifle](BLASTER_RIFLE.ru.md).

Локально прошли **261 JUnit, 445 Python и 2423 GameTests (2342 + 52 + 17 + 6 + 6)**.
Python — четыре независимые группы за 255.676 с общего времени;
сохранённые test ID точно покрывают полный discover без пропусков и повторов.
JUnit failures/errors/skipped = 0. Все пять серверов завершились с кодом 0,
без ошибок загрузки данных. Основной сервер — 2341 Techguns и один
встроенный Minecraft. Генератор проверяет 2539 файлов; 2410 ресурсов и
708 классов JAR сверены побайтово. Лицензия, неизменность legacy и отсутствие
8 уникальных файлов тестовых паков проверены. Журналы — `.tools/blaster-*`.
Linux CI нового source пока ожидается.

Доказательства — [протокол](VERIFICATION.ru.md#blaster-rifle-2026-10-01).
Сначала дождаться CI точного source, устранить ошибки, записать результат,
сделать documentation-only checkpoint `[skip ci]` и push. Текущий HEAD
получать из Git; старый зелёный CI не подтверждает новый source.

<a id="next-milestone"></a>

## Следующий законченный срез — Alien Blaster

Перенести `alienblaster` и его фабрику. В TGuns: автоматический темп 8 тиков,
10 зарядов ENERGY_CELL/R 35 тиков, урон 16 FIRE, скорость 1, TTL
`ceil(40/1)=40`, пробитие 1; NPC range 24 / interval 40 без очереди.
Сверить GenericGun/GenericProjectile и активные ветки AlienBlasterProjectile:
поджог сущности на 3 секунды, обычное отбрасывание, air/water drag,
burnBlocks с вероятностью 0,35 только при разрешении blockdamage.
AlienExplosion — название FX; не добавлять отсутствующий взрыв.

Современная AlienBlasterProjectile сейчас обслуживает только Ghastling:
фиксированные 6 урона, TTL 200, скорость 1,5, разброс 0,05 и центрированный
запуск; NPC-профиль и старые сохранения должны сохраниться при обобщении.
Проверить игрока/NPC, R/ячейки, воду, броню, события, права B/поджог и saved
flight, затем полную регрессию Ghastling. У оружия в оригинале нет рецепта
верстака и Grinder: не придумывать survival-рецепт; повторно сверить источники.
Перенести ModelAlienBlaster, ClientProxy, исходные текстуру и звуки.

Точки входа — TGuns, AmmoTypes, GenericGun, GenericProjectile,
AlienBlasterProjectile, TGMachineRecipes, Ghastling/его AI и ClientProxy;
современные — AlienBlasterProjectile, GunItem, ReloadSessions, LegacyShot,
ShotDamage, ArmorDamage, NpcCombat, SafeMode, GhastlingGameTests.
Вход — content/weapon-ports.json; владельцы — generate_weapon_content.py,
legacy_ghastling.py, legacy_models.py, legacy_crafting.py и legacy_grinder.py.
Разделить профиль оружия и Ghastling в генераторе/каталоге без дублирования
исходных констант. Пройти полный workflow; новую реализацию ещё не начинали.

## Существенные ограничения

Blaster Rifle/Scatterbeam: полный muzzle/impact FX, свет, отдача/оптика
и GPU-приёмка остаются. У Scatterbeam нет исходных рецептов.
Gauss: исходные FX, свет, прицел/отдача и GPU-приёмка; голубой след —
native-адаптация. AS50: MiningChargeBlockExplosion/световой импульс;
Minigun: полный muzzle FX, движения рук, отдача корпуса и анимация R.
Общая баллистика и другие старые семейства требуют полного сравнения
полёта/TTL с 1.12.2. M4/M5 и весь порт не завершены.

Все 21 активных природных кандидата и сетки реализованы, но полный M10
и матрица seed/высот остаются. Locate перебирает текущую сетку: сохранённый
старт вне неё не удаляется, но может не находиться этим поиском.
Закомментированные City/Submarine/NetherDungeon и CampFlagTileEnt не включать
в активную генерацию. Интерактивный клиент/рабочий стол не трогать;
бинарные релизы не публиковать.
