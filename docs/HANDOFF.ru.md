# Продолжение работы в новом чате

Обновлено 2026-09-28 после локальной проверки взрывных патронов AS50.
Linux CI нового source commit ожидается. Прочитать
[порядок работы](DEVELOPMENT_WORKFLOW.ru.md) и [AGENTS.md](../AGENTS.md),
проверить Git. Поиск — [карта кода](CODE_MAP.ru.md), [каталог систем](README.ru.md)
и [карта генераторов](../tools/README.ru.md).

## Репозиторий и текущая точка

| Поле | Значение |
|---|---|
| Рабочая папка | `D:/Project/Techguns` |
| Продолжаемая ветка | `port/26.2-neoforge` |
| Writable fork / origin | `PozziTiv4ik/Techguns2` |
| Оригинал / upstream, только чтение | `pWn3d1337/Techguns2` |
| Платформа | Minecraft 26.2 / NeoForge 26.2.0.81 / Java 25 |
| Последний source SHA, подтверждённый Linux | `61164e71c2ada9ed1afc294463e8958e30908bad` — предыдущий срез Minigun |
| Linux CI нового кода AS50 | Ожидается после source commit/push; текущий SHA получить из Git |

Работают третий магазин AS50, все три варианта крафтовой смены, R за 80 тиков,
возврат пустого магазина/целых пачек, сохранение и HUD. Прямое попадание
EXPLOSION ×1,15 отделено от исходного **vanilla Explosion 1,5**: TGExplosion
в оригинале закомментирован. Проверены настоящий взрыв, броня/cooldown,
стены до разрушения, события, B/права, NPC, вода и TTL. Магазин использует
исходные OBJ/PNG; рецепты пачек Metal Press/Grinder уже были подключены.
Подробности — [AS50_EXPLOSIVE.ru.md](AS50_EXPLOSIVE.ru.md).

Локально прошли **250 JUnit, 426 Python и 2319 GameTests (2239 + 52 + 16 + 6 + 6)**. Все пять серверов
завершились с кодом 0 и без ошибок загрузки данных; основной — 2238
Techguns + один встроенный Minecraft. Python — единый discover за 721.376 с.
Генератор проверяет 2485 файлов; 2363 ресурса и 696 классов JAR
сверены побайтово. Лицензия/legacy/исключение тестовых паков проверены.
Журналы — `.tools/as50-*`, доказательства —
[протокол](VERIFICATION.ru.md#as50-explosive-2026-09-28).

Сначала дождаться CI точного source SHA и записать documentation-only checkpoint
с `[skip ci]`. Не выдавать CI предыдущего Minigun за проверку AS50.
После подтверждения следующий законченный срез приведён ниже.

<a id="next-milestone"></a>

## Следующий законченный срез — Gauss Rifle

Перенести `gaussrifle` с составным исходным боеприпасом: `GAUSSRIFLE_SLUGS`
и `ENERGY_CELL`, возврат `ENERGY_CELL_EMPTY`, остатки и транзакционная R
без расхода одного входа при нехватке другого. Снаряды уже производит
Metal Press; обычная зарядная станция/энергоячейка также работают.

Сначала восстановить `TGuns.gaussrifle`, `AmmoTypes.AMMO_GAUSS_RIFLE`,
составные ветки `AmmoType`/`GenericGun`, `GaussProjectile` и
`AdvancedBulletProjectile`. Отдельно сверить фабрику, реальный полёт,
урон/пробитие/события и сохранение. Не сводить массив входов к одному
магазину и не объявлять незавершённые FX готовыми. Исходные рецепты —
`gaussrifle`, `gaussrifle_alt`, ствол Gauss и рецепт Grinder.

Современные точки — `GunItem`, `ReloadSessions`, `AmmoSpec`, `Magazine`,
`LegacyShot`, `ShotDamage`, `ArmorDamage`, примеры BallisticAmmo/RocketAmmo.
Вход оружия — `content/weapon-ports.json`; владельцы —
`generate_weapon_content.py`, `legacy_crafting.py`, `legacy_items.py`,
`legacy_grinder.py` и карта генераторов. Новый converter/subsystem добавить
в карты и пройти полный workflow. Прежние AS50/Minigun заново не переносить.

## Существенные ограничения

AS50 MiningChargeBlockExplosion/световой импульс и GPU-приёмка остаются.
У Minigun остаются полные muzzle FX, движения рук, отдача корпуса и анимация R.
Обычная баллистика использует общие правила порта; полного сравнения полёта
с 1.12.2 нет. M4/M5 и полный порт не объявлены завершёнными.

Все 21 активных природных кандидата и сетки реализованы, но полный M10
и матрица seed/высот остаются. Locate перебирает текущую сетку: сохранённый
старт вне неё не удаляется, но может не находиться этим поиском.
Закомментированные City/Submarine/NetherDungeon и CampFlagTileEnt не включать
в активную генерацию. Интерактивный клиент/рабочий стол не трогать;
бинарные релизы не публиковать.
