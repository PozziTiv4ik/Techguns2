# Продолжение работы в новом чате

Обновлено 2026-09-28 после CI checkpoint Gauss Rifle.
Локальные проверки и Linux CI #80 успешны. Прочитать [порядок работы](DEVELOPMENT_WORKFLOW.ru.md)
и [AGENTS.md](../AGENTS.md), проверить Git. Поиск — [карта кода](CODE_MAP.ru.md),
[каталог систем](README.ru.md) и [карта генераторов](../tools/README.ru.md).

## Репозиторий и текущая точка

| Поле | Значение |
|---|---|
| Рабочая папка | `D:/Project/Techguns` |
| Продолжаемая ветка | `port/26.2-neoforge` |
| Writable fork / origin | `PozziTiv4ik/Techguns2` |
| Оригинал / upstream, только чтение | `pWn3d1337/Techguns2` |
| Платформа | Minecraft 26.2 / NeoForge 26.2.0.81 / Java 25 |
| Последний source, подтверждённый Linux | `985f7a1be85effe2b73bce014169a86b2b6fd63c` — Gauss Rifle |
| Linux CI Gauss Rifle | [Linux CI #80](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36478221956), success |

Работает Gauss Rifle: восемь зарядов, оба входа в одной транзакционной R,
возврат пустой ячейки, реальный PROJECTILE с пробитием 2,0, события/броня,
вода/TTL, NPC, сохранение, рецепты/Grinder, исходный OBJ и звуки.
Описание — [GAUSS_RIFLE.ru.md](GAUSS_RIFLE.ru.md).

Локально прошли **255 JUnit, 433 Python и 2356 GameTests (2276 + 52 + 16 + 6 + 6)**. Python — четыре независимые группы за 334.591 с
общего времени; сохранённые test ID точно покрывают полный discover без
пропусков и повторов. JUnit failures/errors/skipped = 0. Все пять серверов завершились
с кодом 0, без ошибок загрузки данных. Основной сервер — 2275 Techguns
и один встроенный Minecraft. Генератор проверяет 2520 файлов; 2395 ресурсов
и 701 класс JAR сверены побайтово. Лицензия, неизменность legacy и отсутствие
семи уникальных файлов тестовых паков проверены. Журналы — `.tools/gauss-*`.
[Linux CI #80](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36478221956) подтвердил source `985f7a1be85effe2b73bce014169a86b2b6fd63c`:
433 Python за 370.510 с, сборку/ядро и все 2356 GameTests
(2276 + 52 + 16 + 6 + 6). Все 15 шагов успешны; завершение —
2026-09-28 22:37:54 +0200 (Europe/Zurich), `2026-09-28T20:37:54Z`.

Доказательства — [протокол](VERIFICATION.ru.md#gauss-rifle-2026-09-28).
После указанного source SHA меняется только документация с `[skip ci]`.
Незавершённой реализации и известных красных проверок этого среза нет.
Текущий HEAD получать из Git;
не повторять уже прошедшие проверки без изменений или конкретного сомнения.


CI #79 нового Gauss source выявил сбой прежней фикстуры вертолёта.
Добавлены ожидание native entity tracking, удержание чанков и проверка
реального урона от своей пули; игровой AI не менялся. После исправления
прошли сборка, 32 адресных повторения и полный основной сервер 2276/2276.
Исправленный source подтверждён [Linux CI #80](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36478221956); история —
[протокол](VERIFICATION.ru.md#gauss-rifle-2026-09-28).

<a id="next-milestone"></a>

## Следующий законченный срез — Scatterbeam Rifle

Перенести `scatterbeamrifle` и `BlasterProjectile.Factory`. В `TGuns` две
последовательные setBulletSpeed: действующее значение 2,0. Проверить пять
энергетических снарядов на один расход заряда оружия, реальный полёт, прямой урон,
броню/события/сохранение и NPC. Боеприпас — уже работающая ENERGY_CELL,
возврат ENERGY_CELL_EMPTY, исходная ёмкость 40 и R 45 тиков.
Не заменять фабрику обычной пулей/мгновенным лазером. Восстановить активные
ветки самого BlasterProjectile до реализации; полный FX не объявлять готовым.

Исходные точки — TGuns, AmmoTypes, GenericGun, BlasterProjectile,
рецепты scatterbeamrifle/alt, TGMachineRecipes и ClientProxy/ModelLasergun2.
Современные — GunItem, ReloadSessions, AmmoSpec, LegacyShot, ShotDamage,
ArmorDamage, NpcCombat и GaussProjectile как пример интеграции.
Вход выбора — content/weapon-ports.json, владельцы — generate_weapon_content.py,
legacy_crafting.py, legacy_items.py и legacy_grinder.py. Новый subsystem/converter
добавить в карты и пройти полный workflow. Прежние AS50/Minigun/Gauss заново не переносить.

## Существенные ограничения

Gauss: исходные GaussFireFX/GaussProjectileTrail/impact FX, свет, прицельная
сетка/отдача и GPU-приёмка остаются; голубой след — native-адаптация.
AS50: MiningChargeBlockExplosion/световой импульс; Minigun: полный muzzle FX,
движения рук, отдача корпуса и анимация R. Обычная баллистика использует общие
правила порта без полного сравнения полёта с 1.12.2. M4/M5 и весь порт не завершены.

Все 21 активных природных кандидата и сетки реализованы, но полный M10
и матрица seed/высот остаются. Locate перебирает текущую сетку: сохранённый
старт вне неё не удаляется, но может не находиться этим поиском.
Закомментированные City/Submarine/NetherDungeon и CampFlagTileEnt не включать
в активную генерацию. Интерактивный клиент/рабочий стол не трогать;
бинарные релизы не публиковать.
