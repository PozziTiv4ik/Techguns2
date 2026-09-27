# Продолжение работы в новом чате

Обновлено 2026-09-27 после локальной проверки Neonlights.
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
| Текущий локально проверенный срез | Neonlights; source SHA получить из Git после первого коммита |
| Linux CI нового кода | Ожидается; прежний успешный CI не подтверждает новый код |

Все пять Neonlights перенесены: полные непрозрачные кубы, постоянный свет 15,
исходные текстуры/названия, добыча рукой, рецепт на 16 штук, Camo Bench.
Квадратный вариант metadata 4 готов как материал Helipad. Проверен цикл
крафта, выбора оформления и установки семи светящихся клеток буквы H.
См. [NEONLIGHTS.ru.md](NEONLIGHTS.ru.md).

Локально прошли **205 JUnit, 366 Python и 1885 GameTests**: 1823 основных
(1822 Techguns + 1 встроенный), 46 worldgen/выборочных и 16 условных.
Python выполнен единым discover. Генератор: 2250 файлов;
2158 ресурсных записей JAR совпали побайтово. Лицензия, неизменность legacy
и исключение тестовых паков проверены. Подробности:
последний раздел [VERIFICATION.ru.md](VERIFICATION.ru.md).
Локальные `.tools/neon-*` помогают, но их отсутствие не блокирует новый клон.

До закрытия среза: отправить проверенный source commit в origin, дождаться
именно его Linux CI, записать фактический результат и отправить документационный
checkpoint с `[skip ci]`. Новую функцию до завершения CI не начинать.

## Следующая задача: Stielgranate и FragGrenade

Перенести **две ручные гранаты** как следующую зависимость полного лута
военных ящиков. Аудит шести таблиц выявил четыре отсутствующих боевых предмета:
`stielgranate`, `fraggrenade`, `grenadelauncher`, `flamethrower`. Остальные
перечисленные Techguns-предметы имеют современные определения; перед переносом
ящиков всё равно сверить компоненты, функции лута, диапазоны и реальные применения.
Нельзя удалять отсутствующие награды или отдавать их вес готовым предметам.

Начать с активного кода и вызываемых методов:

```text
legacy/1.12.2/src/main/java/techguns/TGuns.java
legacy/1.12.2/src/main/java/techguns/items/guns/GenericGrenade.java
legacy/1.12.2/src/main/java/techguns/items/guns/IGrenadeProjectileFactory.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/GrenadeProjectile.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/FragGrenadeProjectile.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/GenericProjectile.java
legacy/1.12.2/src/main/resources/assets/techguns/loot_tables/blocks/military_crate_explosives.json
```

В TGuns обе гранаты — GenericGrenade со стаком 16 и maxUseDur 72000;
обычный бросок начинается при отпускании использования. GenericGrenade задаёт
fullChargeTime 30, TTL 200, speed 0,75, spread 0,1 и расчёт gravity 0,015/charge.
Сверить действительное применение charge в фабриках, нулевой заряд,
руки/ведущую руку, отскоки и момент взрыва: FragGrenadeProjectile.init()
назначает bounces=2, тогда как базовое значение GenericGrenade равно 3.
Не трактовать названия radiusMin/radiusMax без проверки TGExplosion.

Современные точки входа: `modern/RocketProjectile.java`, `LegacyShot.java`,
`ShotDamage.java`, серверные права B и `core/ExplosionMath.java`.
Нужны настоящие use/release, физика/сохранение/взрыв, рецепты и исходные модели/
звуки. Проверить границы срока жизни, отскоки, стены, отмену событий и права
разрушения блоков. Не заменять новый вид оружия инертным предметом добычи.

После гранат — GrenadeLauncher и Flamethrower, затем девять вариантов
BlockMilitaryCrate с шестью таблицами. Взрывной пул: веса 2/2/2/2/1/1 для
rocket/40mmgrenade/Stielgranate/FragGrenade/RocketLauncher/GrenadeLauncher;
оружейный пул содержит Flamethrower. TGEventHandler заменяет дроп только
при добыче игроком без Silk Touch и передаёт Fortune как luck. Остальные
случаи сохраняют исходный блок. Проверить эти ветки и не путать ящики с сундуками.

Далее MilitaryBaseStructure/MilitaryCamp. AttackHelicopter, ArmySoldier,
военный пост и Neonlights уже есть. Сохранить билет CastleStructure в большой
LAND-таблице; AircraftCarrier относится к WATER.

## Границы результата

MilitaryCamp пока не генерируется. Визуальная приёмка и внешний Chisel
остаются; агенту нельзя запускать интерактивный Minecraft или управлять
рабочим столом. Повернутые Neonlights — отдельные текстуры, не facing.
Полный порт, остальные оружие/машины/броня/структуры и интеграции остаются
в [плане](PORTING_PLAN.ru.md) и [статусе](STATUS.ru.md).
