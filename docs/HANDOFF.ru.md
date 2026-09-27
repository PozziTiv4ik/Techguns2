# Продолжение работы в новом чате

Обновлено 2026-09-27 после AttackHelicopter и успешного Linux CI #66.
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
| Последний проверенный код | `9b0046f8f28a2136e908b9537ef570157a3f5b57` — AttackHelicopter |
| Linux CI кода | [Linux CI #66](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36319408687), success; 2026-09-27 14:45 Europe/Zurich |

AttackHelicopter перенесён: полёт и обнаружение игрока, пять пуль/ракета,
исходная броня, четыре пула лута, смерть за 100 тиков и опыт, сохранение
полёта/снарядов/погибающей сущности, конечный военный пост 1/1/200/0.
Три OBJ, Apache-текстура и звуки подключены. Доступен через яйцо, команду
и настроенный пост; MilitaryCamp ещё не генерируется.
См. [ATTACK_HELICOPTER.ru.md](ATTACK_HELICOPTER.ru.md).

Локально прошли **205 JUnit, 360 Python и 1871 GameTest**: 1809 основных
(1808 Techguns + 1 встроенный), 46 worldgen/выборочных и 16 условных.
Python-пакеты 180+180 точно покрывают полный discover без повторов/пропусков.
Генератор: 2212 файлов; 2122 ресурсные записи JAR совпали побайтово.
Лицензия, неизменность legacy и исключение тестовых паков проверены.
Подробности: последний раздел [VERIFICATION.ru.md](VERIFICATION.ru.md).
Локальные `.tools/heli-*` помогают восстановить результаты, но их отсутствие
не блокирует новый клон. Не повторять проверки без изменений или сомнений.

Первый source `10533ba` / Linux CI #65 получил ошибку соседнего AI-теста.
Исправлена изоляция настоящего тестового игрока и учёт объединённых сфер XP;
повторные сборка и все 1809 основных тестов прошли. Игровое поведение NPC
не изменялось. Успехом считать только CI последующего исправляющего SHA.

Linux CI #66 подтвердил именно указанный source SHA: 360 Python единым
запуском, сборку/ядро и все 1871 GameTest. После него изменена только
документация. Текущий HEAD получать из Git, не принимать source SHA за
последний коммит ветки. Незавершённой реализации и известных красных
проверок этого среза нет.

## Следующая задача: Neonlights для MilitaryCamp

Перенести **пять вариантов Neonlights**: NEONTUBES2, NEONTUBES2_ROTATED,
NEONTUBES4, NEONTUBES4_ROTATED, NEONSQUARE_WHITE. Это обязательная зависимость
Helipad: он использует metadata 4. В modern блоки пока отсутствуют.
После них — военные ящики с настоящим лутом, затем MilitaryBaseStructure/
MilitaryCamp. Активные CampProps/Bunker используют все девять типов ящиков;
декоративная замена не закрывает зависимость.

Начать с исходников и активных вызовов:

```text
legacy/1.12.2/src/main/java/techguns/blocks/EnumLightblockType.java
legacy/1.12.2/src/main/java/techguns/blocks/GenericBlockMetaEnumCamoChangeable.java
legacy/1.12.2/src/main/java/techguns/TGBlocks.java
legacy/1.12.2/src/main/resources/assets/techguns/blockstates/neonlights.json
legacy/1.12.2/src/main/resources/assets/techguns/recipes/neonlights_0.json
legacy/1.12.2/src/main/java/techguns/world/structures/Helipad.java
```

Уже установлено: Material.GLASS, звук стекла, свет 1,0, твёрдость 4,0;
исходный рецепт даёт 16 блоков из шести железных самородков, двух стеклянных
панелей и светопыли. Проверить геометрию/повороты, дроп, исходную смену
оформления и интеграцию Camo Bench, не переносить внешний Chisel как уже
поддержанную интеграцию. Современные точки входа: `tools/legacy_building.py`,
`tools/legacy_fortifications.py`, `modern/world/BuildingContent.java`,
`FortificationContent.java` и Camo Bench.

Военные ящики: BlockMilitaryCrate/EnumMilitaryCrateType, шесть loot tables,
активная замена дропа при добыче без Silk Touch в TGEventHandler. Перед их
реализацией проверить все предметы лута. Большой лагерь имеет 13 внутренних,
семь граничных шаблонов и два вида башен. Сохранить исходный билет CastleStructure
в большой LAND-таблице; AircraftCarrier относится к WATER.

## Границы результата

У вертолёта нет исходной записи обычного биомного спавна. Он не относится
к HOSTILE-фракции GenericNPC. Взрыв боевой ракеты имеет фактические исходные
радиусы 30/40 и не ломает блоки; взрыв смерти только визуальный. Не менять
эти особенности по комментариям или неиспользуемым аргументам конструктора.

Клиентская/визуальная приёмка остаётся; частицы смерти адаптированы к ванильным,
полная FX-система не перенесена. Агенту нельзя запускать интерактивный Minecraft
или управлять рабочим столом. Остальные структуры, оружие, броня, машины,
анимации и интеграции остаются в [плане](PORTING_PLAN.ru.md) и [статусе](STATUS.ru.md).
Полный порт ещё не завершён.
