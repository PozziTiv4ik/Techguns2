# Продолжение работы в новом чате

Обновлено 2026-10-01 после локальных проверок Scatterbeam Rifle.
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
| Последний source, подтверждённый Linux | `985f7a1be85effe2b73bce014169a86b2b6fd63c` — Gauss Rifle до исправления TTL |
| Linux CI Scatterbeam | Ожидается; прежний [CI #80](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36478221956) новый код не подтверждает |

Работает Scatterbeam Rifle: пять летящих ENERGY-снарядов на один заряд,
40 зарядов/R 45 тиков/возврат пустой ячейки, броня/события/вода/NPC/сохранение,
исходные модель и звуки. Последнее setBulletSpeed — 2,0, TTL — 15.
Исправлен Gauss TTL: 18 вместо 90. У Scatterbeam нет исходных рецептов
верстака и Grinder; прежнее задание ошибочно их предполагало. Получение —
Creative/команда; производство ячеек работает. [Описание](SCATTERBEAM_RIFLE.ru.md).

Локально прошли **258 JUnit, 439 Python и 2391 GameTests (2311 + 52 + 16 + 6 + 6)**.
Python — четыре независимые группы, одна повторена после исправления старого
теста; окно успешных групп — 910.329 с. Их сохранённые test ID
совпали с полным discover без повторов и пропусков.
JUnit failures/errors/skipped = 0. Все пять серверов завершились с кодом 0,
без ошибок загрузки данных. Основной сервер — 2310 Techguns и один
встроенный Minecraft. Генератор проверяет 2529 файлов; 2401 ресурс и
707 классов JAR сверены побайтово. Лицензия, неизменность legacy и отсутствие
семи уникальных файлов тестовых паков проверены. Журналы — `.tools/scatterbeam-*`.
Linux CI нового source пока ожидается.

Доказательства — [протокол](VERIFICATION.ru.md#scatterbeam-rifle-2026-10-01).
Сначала дождаться CI точного source, устранить ошибки, записать результат,
сделать documentation-only checkpoint `[skip ci]` и push. Текущий HEAD
получать из Git; старый зелёный CI не подтверждает новый source.

<a id="next-milestone"></a>

## Следующий законченный срез — Blaster Rifle

Перенести `blasterrifle` с исходной BlasterProjectile.Factory, используя
уже перенесённую современную BlasterProjectile.
В этой винтовке один снаряд, 50 зарядов ENERGY_CELL/R 45 тиков,
автоматический темп 5 тиков, урон 10→8 на 25–35 блоках и пробитие 1,0.
BlasterProjectile сейчас использует постоянный урон Scatterbeam: добавить
исходную дистанцию от точки запуска, вычислять падение до движения в тик
попадания и сохранять origin. Проверить saved flight и обе границы падения,
воду/броню/события, NPC и отсутствие регрессии Scatterbeam.
Скорость по умолчанию 2, TTL `ceil(60/2)=30`; проверить по GenericGun.

У Blaster Rifle действительно есть blasterrifle/alt и Grinder. Сохранить
обычный/пустой крафт, fallback hardened glass/gold-or-electrum там, где
он задан оригиналом, исходные звуки и ModelBlasterRifle/ClientProxy.
Не выдавать доступность Scatterbeam через команды за survival-рецепт.

Точки входа — TGuns, AmmoTypes, GenericGun, GenericProjectile,
BlasterProjectile, TGMachineRecipes, ClientProxy/ModelBlasterRifle;
современные — GunItem, ReloadSessions, LegacyShot, ShotDamage,
ArmorDamage, NpcCombat, BlasterRenderer. Вход — content/weapon-ports.json;
владельцы — generate_weapon_content.py, legacy_scatterbeam.py,
legacy_models.py, legacy_crafting.py и legacy_grinder.py. Пройти полный workflow.

## Существенные ограничения

Scatterbeam: нет исходных рецептов; полный muzzle/impact FX, свет,
отдача/прицел и GPU-приёмка остаются. Gauss: исходные FX, свет,
прицел/отдача и GPU-приёмка; голубой след — native-адаптация.
AS50: MiningChargeBlockExplosion/световой импульс; Minigun: полный muzzle FX,
движения рук, отдача корпуса и анимация R. Общая баллистика и другие старые
семейства требуют полного сравнения полёта/TTL с 1.12.2. M4/M5 и весь порт не завершены.

Все 21 активных природных кандидата и сетки реализованы, но полный M10
и матрица seed/высот остаются. Locate перебирает текущую сетку: сохранённый
старт вне неё не удаляется, но может не находиться этим поиском.
Закомментированные City/Submarine/NetherDungeon и CampFlagTileEnt не включать
в активную генерацию. Интерактивный клиент/рабочий стол не трогать;
бинарные релизы не публиковать.
