# Продолжение работы в новом чате

Обновлено 2026-09-27 после локально проверенной SmallMine. Linux CI нового
source commit пока ожидается. Перед разработкой прочитать
[порядок работы](DEVELOPMENT_WORKFLOW.ru.md), затем проверить Git.
Инструкции [AGENTS.md](../AGENTS.md) обязательны для всего проекта.

## Репозиторий и текущая точка

| Поле | Значение |
|---|---|
| Рабочая папка | `D:\Project\Techguns` |
| Продолжаемая ветка | `port/26.2-neoforge` |
| Writable fork / origin | `PozziTiv4ik/Techguns2` |
| Оригинал / upstream, только чтение | `pWn3d1337/Techguns2` |
| Платформа | Minecraft 26.2 / NeoForge 26.2.0.81 / Java 25 |
| Текущий срез | SmallMine; source commit включает эту запись |
| Linux CI SmallMine | Ожидается проверка точного source SHA после push |
| Предыдущий проверенный Linux CI | [FactoryHouseSmall №63](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36310548871), source `d50b3c69ba3251832226eecb0eb35941b631a847` |

SmallMine перенесена: 972 позиции, исходные семь типов кластера и смеси,
биомное покрытие, заглубление на пять блоков, одна колонна очистки без
фундамента, два конечных поста ZombieMiner и добыча настоящим буром.
Все четыре малых кандидата Верхнего мира теперь используют исходный пул
40 билетов без наложений. См. [SMALL_MINE.ru.md](SMALL_MINE.ru.md).

Локально прошли **199 JUnit, 354 Python и 1845 GameTests**: 1783 основных
(1782 Techguns + 1 встроенный), 46 worldgen/выборочных и 16 условных.
Генератор: 2190 файлов; 2102 ресурсные записи JAR совпали побайтово.
Подробности и ограничения: последний раздел [VERIFICATION.ru.md](VERIFICATION.ru.md).
Локальные `.tools/mine-*` могут помочь, но отсутствие этих игнорируемых файлов
не является блокером для нового клона.

Сначала закончить цикл публикации текущего среза: source push → CI по точному
SHA → документационный checkpoint `[skip ci]` и push. Не повторять уже
успешные локальные проверки без изменений или конкретного сомнения.

## Следующая задача после CI: AttackHelicopter

Перенести **AttackHelicopter** как обязательную зависимость больших военных
лагерей. В активном `MilitaryCamp` есть SOLDIER_SPAWN-пост вертолёта
1/1/200/0 с поднятой точкой появления. ArmySoldier и военный пост уже
реализованы; вертолёта в modern/core пока нет. После него следующий
крупный срез — MilitaryBaseStructure/MilitaryCamp. Сохранить исходный
билет CastleStructure в большой LAND-таблице; авианосец относится к WATER.

Начать с исходников и вызываемых ими методов:

```text
legacy/1.12.2/src/main/java/techguns/entities/npcs/AttackHelicopter.java
legacy/1.12.2/src/main/java/techguns/entities/npcs/GenericFlyingMob.java
legacy/1.12.2/src/main/java/techguns/client/render/entities/npcs/RenderAttackHelicopter.java
legacy/1.12.2/src/main/resources/assets/techguns/loot_tables/entities/attackhelicopter.json
legacy/1.12.2/src/main/java/techguns/world/structures/MilitaryCamp.java
legacy/1.12.2/src/main/java/techguns/world/structures/MilitaryBaseStructure.java
legacy/1.12.2/src/main/java/techguns/world/TGStructureSpawnRegister.java
```

Уже установлено: размер 4×4, здоровье 100, follow range 128, vanilla armor
16/toughness 5, отдельная типовая броня; целевая высота 24. В бою пять пуль
на тиках 14/16/18/20/22 и ракета на 35, затем таймер -30. Исходную смерть
нужно отличать от мгновенного удаления: цикл 100 тиков, XP, звук и FX.
Не принимать закомментированные ветки за активную механику.

Современные точки входа: `platforms/neoforge-26.2/src/main/java/techguns/modern/npc/`,
`npc/spawner/`, `Bullet.java`, `RocketProjectile.java`, клиентские renderer
в `modern/client/` и `tools/legacy_npcs.py`.
Сначала уточнить зависимости лута, пуль/ракет и исходных моделей helicopter0/1/2,
затем реализовать пригодный к игре NPC с сохранением и настоящей конечной
встречей. Не заменять недостающие AI/лут/боевую механику декорацией.

## Границы результата

SmallMine проверена во всех четырёх поворотах, для всех семи типов ресурса и
биомных вариантов; природные точки — две на seed 0. Выбор четырёх кандидатов
проверен на seed 0/1/42. Это не полная проверка нескольких природных миров.
Защита от соседней травы действует только при первоначальной декорации;
обычное изменение мира после генерации разрешено.

Клиентская/визуальная приёмка остаётся. Агенту нельзя запускать интерактивный
Minecraft или управлять рабочим столом. Остальные структуры, оружие, броня,
машины, анимации и интеграции остаются в [плане](PORTING_PLAN.ru.md) и
[статусе](STATUS.ru.md). Полный порт ещё не завершён.
