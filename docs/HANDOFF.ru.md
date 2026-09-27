# Продолжение работы в новом чате

Обновлено 2026-09-27 после локального переноса GrenadeLauncher.
Linux CI нового source commit ожидается.
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
| Последний проверенный код | GrenadeLauncher локально; точный source SHA будет записан после CI |
| Linux CI кода | Ожидается для нового source commit; прежний CI #68 подтверждает только ручные гранаты |

GrenadeLauncher работает: шесть 40-мм зарядов, одиночный огонь, R 100 тиков,
поштучное заполнение, два отскока, TTL 160 без взрыва по истечении,
прямой/взрывной урон, броня/стены/события, сохранение, NPC, крафт и Grinder.
Три исходных OBJ, обе PNG и четыре OGG перенесены; барабан вращается на 60° за пять тиков
вокруг исходной оси. Блоки сохраняются независимо от B.
См. [GRENADE_LAUNCHER.ru.md](GRENADE_LAUNCHER.ru.md).

Локально прошли **210 JUnit, 378 Python и 1956 GameTests**: 1894 основных
(1893 Techguns + 1 встроенный), 46 worldgen/выборочных и 16 условных.
Python: 371 + 7 успешных test ID без пересечений; манифест доказывает
равенство полному discover. Генератор: 2302 файла;
2206 ресурсных записей JAR совпали побайтово. Лицензия, legacy и исключение
тестовых паков проверены. Подробности — последний раздел
[VERIFICATION.ru.md](VERIFICATION.ru.md). Локальные `.tools/launcher-*`
помогают, но их отсутствие не блокирует новый клон.

До следующей реализации завершить CI именно нового source SHA и сделать
документационный checkpoint `[skip ci]`. Старый зелёный CI не подтверждает
гранатомёт. Незавершённой локальной реализации и красных локальных проверок нет.

## Следующая задача: Flamethrower

Перенести **огнемёт Flamethrower** — последнюю отсутствующую боевую награду
шести loot tables военных ящиков. Stielgranate, FragGrenade и GrenadeLauncher
уже работают. Не сокращать награды и не перераспределять исходные веса.

Точки входа оригинала:

```text
legacy/1.12.2/src/main/java/techguns/TGuns.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/FlamethrowerProjectile.java
legacy/1.12.2/src/main/java/techguns/entities/projectiles/GenericProjectile.java
legacy/1.12.2/src/main/java/techguns/items/guns/GenericGun.java
legacy/1.12.2/src/main/java/techguns/items/guns/ammo/AmmoTypes.java
legacy/1.12.2/src/main/java/techguns/client/render/item/RenderGunFlamethrower.java
legacy/1.12.2/src/main/java/techguns/client/models/guns/ModelFlamethrower.java
legacy/1.12.2/src/main/resources/assets/techguns/recipes/flamethrower.json
legacy/1.12.2/src/main/resources/assets/techguns/recipes/flamethrower_alt.json
```

TGuns: автоматический огонь (semiAuto=false), задержка 2, ёмкость 100,
перезарядка 45, урон 5, TTL 16, spread 0,05, speed 0,5, gravity 0,01,
damageDrop 4/16/2 и forwardOffset 0,35. Бак FUEL_TANK уже используется
бензопилой; не переносить на огнемёт её ёмкость или особое поведение Creative.
Проверить исходные R/возврат пустого бака и оба рецепта.

FlamethrowerProjectile наследует GenericProjectile, переопределяет FIRE-урон
без основного отбрасывания, поджигает живую цель на три секунды только по
успешному попаданию. Активный burnBlocks имеет вероятность 0,5 и зависит
от blockdamage/B; сверить RNG и все грани. Вода даёт 0,85 сопротивления,
а isWet удаляет снаряд: проверить воду, дождь и порядок попадания/удаления.
Поле piercing=false само по себе не означает реализованное пробивание.
Фабрика передаёт gravity и шлёт FlamethrowerFireFX; не копировать поведение
фабрики зажигательных пуль, которая гравитацию игнорирует.

Нужны исходные start/loop/fire/reload-звуки и прекращение цикла:
setFiresoundStart, maxLoopDelay=10, recoil=10, muzzleFlash=10.
Проверить реальный путь GenericGun и forwardOffset для игрока/NPC.
Опциональный Albedo и полная legacy FX-система не должны блокировать
серверный порт; границы адаптированного рендера описать честно.

Современные точки: `GunItem.java`, `ReloadSessions.java`, `ShotDamage.java`,
`IncendiaryBullet.java`, `SafeMode.java`, `ArmorDamage.java`, `NpcCombat.java`,
`client/TechgunsClient.java`, `tools/generate_weapon_content.py` и
`tools/legacy_incendiary.py`. У гранатомёта новая ProjectileKind.GRENADE_40MM,
общая физика GrenadeProjectile и отдельный профиль/рендер 40mm.
Grinder теперь имеет 57 записей; при добавлении исходной переработки
огнемёта обновить независимый сценарий и проверку полного набора рецептов.

После огнемёта — девять BlockMilitaryCrate и шесть полных loot tables.
Взрывной пул: 2/2/2/2/1/1 для rocket/40mmgrenade/Stielgranate/FragGrenade/
RocketLauncher/GrenadeLauncher. TGEventHandler заменяет дроп только при
добыче игроком без Silk Touch и передаёт Fortune как luck; другие случаи
сохраняют ящик. Сверить компоненты/функции всех наград и реальное применение.
Далее MilitaryBaseStructure/MilitaryCamp. AttackHelicopter, ArmySoldier,
военный пост и Neonlights готовы. Сохранить билет CastleStructure в большой
LAND-таблице; AircraftCarrier относится к WATER.

## Границы результата

MilitaryCamp пока не генерируется. GPU-приёмка, внешний Chisel и полная
исходная FX-система остаются. Интерактивный клиент и рабочий стол не трогать.
Полный порт и остальные системы остаются в [плане](PORTING_PLAN.ru.md)
и [статусе](STATUS.ru.md).
