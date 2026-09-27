# Продолжение работы в новом чате

Обновлено 2026-09-27 после Stielgranate / FragGrenade и успешного Linux CI #68.
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
| Последний проверенный код | `6b29817b22f449e3975359990223355109d8d70c` — Stielgranate / FragGrenade |
| Linux CI кода | [Linux CI #68](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36324166462), success; 2026-09-27 16:09 Europe/Zurich |

Обе ручные гранаты работают: удержание/отпускание, обе руки и ведущая рука,
заряд для гравитации, 3/2 отскока, TTL 200 без взрыва по истечении,
прямой/взрывной урон, броня, стены/права, сохранение, крафт по 16, модели и звук.
Обе сохраняют блоки независимо от B: исходный коэффициент разрушения равен 0.
Нулевой заряд обработан конечной гравитацией, сдвиг после отскока защищён
от попадания внутрь пола. См. [HAND_GRENADES.ru.md](HAND_GRENADES.ru.md).

Локально прошли **208 JUnit, 373 Python и 1930 GameTests**: 1868 основных
(1867 Techguns + 1 встроенный), 46 worldgen/выборочных и 16 условных.
Python выполнен единым discover за 193.099 с. Генератор: 2280 файлов;
2186 ресурсных записей JAR совпали побайтово. Лицензия, неизменность legacy
и исключение тестовых паков проверены. Подробности:
последний раздел [VERIFICATION.ru.md](VERIFICATION.ru.md).
Локальные `.tools/grenade-*` помогают, но их отсутствие не блокирует новый клон.

Linux CI #68 подтвердил именно указанный source SHA: 373 Python единым
запуском, сборку/ядро и все 1930 GameTests. Все 13 шагов успешны.
После source SHA меняется только документация с `[skip ci]`. Текущий HEAD
получать из Git, не принимать source SHA за последний коммит ветки.
Незавершённой реализации и известных красных проверок этого среза нет.
Не повторять проверки без изменений или сомнений.

## Следующая задача: GrenadeLauncher

Перенести **гранатомёт GrenadeLauncher** как следующую зависимость полного
лута военных ящиков. После двух ручных гранат отсутствуют только
`grenadelauncher` и `flamethrower`. Не удалять награды из исходных loot tables
и не перераспределять их вес на готовые предметы.

Начать с активного кода и вызываемых методов:

```text
legacy/1.12.2/src/main/java/techguns/TGuns.java
legacy/1.12.2/src/main/java/techguns/items/guns/GenericGun.java
legacy/1.12.2/src/main/java/techguns/items/guns/ammo/AmmoTypes.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/Grenade40mmProjectile.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/GrenadeProjectile.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/GenericProjectile.java
legacy/1.12.2/src/main/java/techguns/client/ClientProxy.java
legacy/1.12.2/src/main/java/techguns/client/models/guns/ModelBaseBakedGrenadeLauncher.java
legacy/1.12.2/src/main/resources/assets/techguns/recipes/grenadelauncher.json
```

TGuns задаёт ёмкость 6, задержку 5, перезарядку 100, урон 30, TTL 160,
spread 0,015, speed 0,5, damageDrop 4/8/12, gravity 0,01 и setAmmoCount(6).
Первый boolean конструктора — semiauto: не перепутать его с automatic.
AmmoTypes.GRENADES_40MM содержит TGItems.GRENADE_40MM; современный
`40mmgrenade` уже производится Metal Press. Сверить действительный
поштучный расход/перезарядку и набор исходных рецептов.
Фабрика Grenade40mmProjectile задаёт два отскока, наследует GrenadeProjectile
и не переопределяет explode: проверять прямой пулевой урон, взрыв с фактором
разрушения 0, TTL и копирование состояния по активному пути, а не по имени оружия.

Современные точки входа: `GrenadeProjectile.java`, `GrenadeExplosion.java`,
`GrenadeItem.java`, `LegacyShot.java`, `RocketDamage.java`, `ArmorDamage.java`,
`core/HandGrenade.java`, `core/ExplosionMath.java`, `tools/legacy_grenades.py`,
`tools/generate_weapon_content.py` и `GrenadeGameTests.java`.
Ручные гранаты имеют отдельный каталог и регистрацию, не являются GenericGun.
Профиль гранатомёта следует подключить к штатным оружейным циклам, сохранив
различия заряда руки и постоянной гравитации 40mm. Нужны модель/заряженные
части, исходные звуки, R/сохранение/крафт и настоящая стрельба.
ClientProxy использует RenderGunBaseObj и ModelBaseBakedGrenadeLauncher:
проверить исходные OBJ/blockstates и подвижную часть, а не искать cuboid-модель.

После гранатомёта — Flamethrower, затем девять BlockMilitaryCrate и шесть
loot tables. Взрывной пул: веса 2/2/2/2/1/1 для rocket/40mmgrenade/
Stielgranate/FragGrenade/RocketLauncher/GrenadeLauncher. TGEventHandler заменяет
дроп только при добыче игроком без Silk Touch и передаёт Fortune как luck;
остальные случаи сохраняют исходный ящик. Проверить функции/компоненты
остальных наград и не путать эти блоки с сундуками.

Далее MilitaryBaseStructure/MilitaryCamp. AttackHelicopter, ArmySoldier,
военный пост и Neonlights уже готовы. Сохранить билет CastleStructure
в большой LAND-таблице; AircraftCarrier относится к WATER.

## Границы результата

MilitaryCamp пока не генерируется. GPU-приёмка, внешний Chisel и полная
исходная FX-система остаются. Интерактивный клиент и рабочий стол не трогать.
Полный порт, остальные оружие/машины/броня/структуры и интеграции остаются
в [плане](PORTING_PLAN.ru.md) и [статусе](STATUS.ru.md).
