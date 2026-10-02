# Продолжение работы в новом чате

Обновлено 2026-10-02 после CI checkpoint PDW. Локальные проверки и Linux CI #85 успешны.
Прочитать [порядок работы](DEVELOPMENT_WORKFLOW.ru.md) и [AGENTS.md](../AGENTS.md),
затем проверить Git. Поиск — [карта кода](CODE_MAP.ru.md),
[каталог систем](README.ru.md) и [карта генераторов](../tools/README.ru.md).

## Репозиторий и текущая точка

| Поле | Значение |
|---|---|
| Рабочая папка | `D:/Project/Techguns` |
| Продолжаемая ветка | `port/26.2-neoforge` |
| Writable fork / origin | `PozziTiv4ik/Techguns2` |
| Оригинал / upstream, только чтение | `pWn3d1337/Techguns2` |
| Платформа | Minecraft 26.2 / NeoForge 26.2.0.81 / Java 25 |
| Последний source, подтверждённый Linux | `71ab97670725551ec84f402506da5f484eb88eb7` — PDW |
| Linux CI PDW | [Linux CI #85](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36993942213), success |

PDW: 40 зарядов/R 40 тиков, автоматический темп 1, один Advanced-снаряд,
PROJECTILE 5→3 на 18–25 блоках, пробитие 1 и TTL 20. Восстановлены
смещение до движения в тик попадания, air/water drag, PHYSICAL-импульс,
события, сохранение и очередь NPC. Работают оба крафта, цепочка Metal Press
→ магазин → R → попадание, округление трёх пачек и исходный Grinder.
Модель сохраняет 41 деталь, UV repeat, три текстуры/расцветки в Camo Bench,
исходные звуки и bullet_blue с исходными размерами/задержкой видимости.

Локально прошли **269 JUnit, 459 Python и 2508 GameTests (2427 + 52 + 17 + 6 + 6)**. В JUnit нет failures/errors/skipped.
Python выполнен четырьмя независимыми группами 117/114/114/114; их test ID
точно совпали с полным discover без пропусков и повторов. Окно прогонов —
240.242 с. Все пять серверов завершились с кодом 0, без ошибок
загрузки данных. Основной сервер — 2426 Techguns и один встроенный Minecraft.
Генератор проверил 2605 файлов; 2468 ресурсов и 717 классов JAR
сверены побайтово. Лицензия, отсутствие legacy/восьми файлов тестовых паков
и неизменность всей legacy проверены. Журналы — `.tools/pdw-*`.

Адресно прошли 44 GameTests PDW и 400 повторов NPC-сценария
с четырьмя поворотами. Проверка Grinder входит в основной сервер.
Подробности — [PDW](PDW.ru.md), доказательства —
[протокол](VERIFICATION.ru.md#pdw-2026-10-02).

[Linux CI #85](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36993942213) подтвердил source `71ab97670725551ec84f402506da5f484eb88eb7`:
459 Python за 413.322 с, сборку/ядро и все 2508 GameTests
(2427 + 52 + 17 + 6 + 6). Все 15 шагов успешны; завершение —
2026-10-02T12:30:26+02:00 (Europe/Zurich), `2026-10-02T10:30:26+00:00`.

После этого source меняется только документация с `[skip ci]`.
Незавершённых действий и известных красных проверок этого серверного среза нет.
Текущий HEAD получать из Git; прошедшие проверки без новых изменений не повторять.

<a id="next-milestone"></a>

## Следующий законченный срез — Pulse Rifle / исходный burst

Перенести pulserifle на AdvancedBulletProjectile. TGuns: темп 7, магазин
12/R 45, урон 10→8 на 30–45 блоках, скорость 3,25, TTL ceil(75/3,25)=24,
пробитие 1, zoom 0,35 и точность в прицеле ×0,5, три текстуры.
Используется тот же ADVANCED_MAGAZINE; проверить исходные три пачки,
частичную R, оба рецепта/Grinder и Camo Bench.

Активная setShotgunSpread(2,0.015,true) сейчас явно отклоняется парсером.
В GenericGun.shootGun burst создаёт основной и два дополнительных снаряда
в одном вызове, с forward offset speed/bulletcount и дальнейшим
shiftForward(offset/speed). Это пространственный сдвиг, а не очередь
по тикам; не подменять его NPC burst. Tooltip показывает ammo×3 и clip×3,
хотя расходуется одна из 12 единиц. Сверить initStartPos/shiftForward,
основную точность 0,024 против 0,015 дополнительных снарядов и отдельный
NPC range 24 / interval 30 / без AI-очереди. Не включать комментарии PDW.

Точки входа: TGuns, GenericGun (shootGun/spawnProjectile/tooltip),
GenericProjectile.shiftForward/initStartPos, AmmoTypes, TGMachineRecipes,
ModelPulseRifle/ClientProxy; современные GunItem, AdvancedBulletProjectile,
LegacyShot, NpcCombat/NpcAttackCycle, WeaponDefinition, HUD и Camo Bench.
Владельцы — generate_weapon_content.py, legacy_advanced.py,
legacy_gun_camos.py, legacy_models.py, legacy_crafting.py, legacy_grinder.py.
Проверить игрока/NPC, сдвиги/стены, расход, R, броню, сохранение, интерфейсные
числа и полную регрессию. Реализацию Pulse Rifle ещё не начинали.

## Существенные ограничения

PDW: полные FX/свет, исходные позиции рук, отдача/R, прицельная сетка
и GPU-приёмка. Три расцветки и модель перенесены; клиент не запускался.

Alien Blaster: полный FX, свет, позиции рук, breechReload/отдача,
прицельная сетка и GPU-приёмка остаются. Используются общие современные
положения модели и native-частицы. Рецептов в оригинале нет.
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
