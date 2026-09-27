# Продолжение работы в новом чате

Обновлено 2026-09-27 после FactoryHouseSmall. Это текущая точка входа для
запроса «продолжай». Перед разработкой прочитать
[порядок работы](DEVELOPMENT_WORKFLOW.ru.md) и проверить состояние Git.
Инструкции в [AGENTS.md](../AGENTS.md) обязательны для всего проекта.

## Репозиторий и проверенная точка

| Поле | Значение |
|---|---|
| Рабочая папка на этом компьютере | `D:\Project\Techguns` |
| Продолжаемая ветка | `port/26.2-neoforge` |
| Writable fork / origin | `PozziTiv4ik/Techguns2` |
| Оригинал / upstream, только чтение | `pWn3d1337/Techguns2` |
| Платформа | Minecraft 26.2 / NeoForge 26.2.0.81 / Java 25 |
| Последний проверенный код | `d50b3c69ba3251832226eecb0eb35941b631a847` — FactoryHouseSmall |
| Документальная запись этого CI | `0b2ffeb6d1e8a3bd567241cfd518df4066ee8f6c` |
| Linux CI кода | [№63, success](https://github.com/PozziTiv4ik/Techguns2/actions/runs/36310548871), 2026-09-27 12:00 Europe/Zurich |

Коммиты с инструкциями могут быть новее этой точки. Текущий HEAD получать из
Git, а не принимать эту таблицу за latest HEAD. Если после указанного source
commit появился другой код, проверить его и обновить эту запись.

На проверенной точке прошли **194 JUnit, 348 Python и 1797 GameTests**:
1738 основных (1737 Techguns + 1 встроенный), 43 worldgen/выборочных и 16
условных. Генератор: 2185 файлов; 2098 ресурсных записей JAR сверены побайтово.
Полные результаты: [VERIFICATION.ru.md](VERIFICATION.ru.md), последние разделы
FactoryHouseSmall и контрольная запись CI №63. Это прошлый проверенный срез,
а не обещание неизменного количества тестов в следующем.

После FactoryHouseSmall нет незавершённой реализации или известных красных
проверок; это нужно перепроверить при старте. Сам порт ещё не завершён.
Локальные журналы `.tools/factory-*` могут помочь на этом компьютере, но они
не входят в Git. Для нового клона достаточно исходников, tracked docs и CI;
не делать отсутствие этих локальных файлов блокером.

## Следующая задача: SmallMine

Перенести **SmallMine**, последнего кандидата малой LAND-таблицы Верхнего мира.
FactoryHouseSmall, SmallTrainstation и GasStation уже реализованы. За шахтой
сохранены десять билетов `20..29` из общего пула 40; их нельзя раздать другим
структурам. Если пользователь не изменил приоритет, начинать с этой задачи.

В исходнике уже установлено:

- 972 уникальные позиции; скан 17×11×11, зарегистрированный размер 17×11.
- Шаблон размещается на пять блоков ниже поверхностной точки; очистка четырёх
  слоёв вызывается отдельно от заглублённого размещения. Фундамент в исходнике
  закомментирован. Не копировать параметры основания заводского дома.
- Семь типов кластера с исходными весами `10,10,10,5,2,2,2`; выбор использует
  включительную границу. Сохранить фактические вероятности и общий тип кластера.
- Биомное покрытие, случайные пропуски, обычные руды/камень, шесть смешанных
  кластерных клеток и один гарантированный кластер требуют сохранения раскладки.
- Два HOLE-поста ZombieMiner `2/1/200/1`; проверять настоящие четыре смерти
  суммарно, активные лимиты, сохранение и связь мобов с постами.

Начать с этих файлов, затем проверить вызываемые ими исходные методы:

```text
legacy/1.12.2/src/main/java/techguns/world/structures/SmallMine.java
legacy/1.12.2/src/main/resources/assets/techguns/structures/small_mine
legacy/1.12.2/src/main/java/techguns/world/TGStructureSpawnRegister.java
legacy/1.12.2/src/main/java/techguns/util/BlockUtils.java
legacy/1.12.2/src/main/java/techguns/util/MBlockOreClusterTypeOre.java
legacy/1.12.2/src/main/java/techguns/util/MBlockOreclusterType.java
legacy/1.12.2/src/main/java/techguns/util/MultiMMBlock.java
```

Современные точки входа:

```text
core/src/main/java/techguns/core/SmallOverworldRules.java
platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/SmallOverworldStructure.java
platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/LocationContent.java
platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/FactoryHousePiece.java
platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/TrainStationPiece.java
tools/legacy_locations.py
tools/legacy_train_station.py
tools/legacy_clusters.py
tools/generate_weapon_content.py
```

Критерий готовности среза: исходный шаблон и вероятности, повороты и рельеф,
сохранение/порядок чанков, рельсы/свет/опоры, конечная охрана, работа настоящего
Ore Drill с полученным кластером, естественное появление и locate, общий
выбор всех четырёх малых кандидатов без наложений. Добавить осмысленные unit,
Python и GameTests, затем пройти цикл локальных проверок → source push → CI →
документальный checkpoint. Если обнаружится обязательная отсутствующая
зависимость, включить её в срез либо явно обновить следующий шаг.

## Границы текущего результата

FactoryHouseSmall проверен во всех четырёх поворотах и двух природных точках
seed 0; выбор малых локаций — на seed 0/1/42. Это не полная проверка нескольких
природных миров. Клиентская/визуальная приёмка остаётся, интерактивный клиент
на этом компьютере агенту запускать нельзя.

Остальные структуры, оружие, броня, машины, анимации и интеграции остаются
в общем [плане](PORTING_PLAN.ru.md) и [статусе](STATUS.ru.md). После SmallMine
выбрать следующий незавершённый срез по зависимостям и заменить эту текущую
запись; не возвращаться автоматически к уже перенесённому заводскому дому.
