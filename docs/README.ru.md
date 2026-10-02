# Документация по системам

Начало работы: [handoff](HANDOFF.ru.md) → [порядок работы](DEVELOPMENT_WORKFLOW.ru.md)
→ нужный раздел [карты кода](CODE_MAP.ru.md). Этот каталог помогает выбрать
описание поведения; пути реализации и тестов находятся в карте, владельцы
генерации — в [tools/README.ru.md](../tools/README.ru.md).

| Что нужно узнать | Где читать |
|---|---|
| Подтверждённый код, незавершённая работа, следующая задача | [HANDOFF.ru.md](HANDOFF.ru.md) |
| Проверки, коммиты и CI | [DEVELOPMENT_WORKFLOW.ru.md](DEVELOPMENT_WORKFLOW.ru.md) |
| Готовность систем и ограничения | [STATUS.ru.md](STATUS.ru.md) |
| Полная цель и критерии этапов | [PORTING_PLAN.ru.md](PORTING_PLAN.ru.md) |
| Результаты конкретного прогона | [VERIFICATION.ru.md](VERIFICATION.ru.md) — история; актуальный прогон указан в handoff |
| Реализация, общие классы, старые и новые имена | [CODE_MAP.ru.md](CODE_MAP.ru.md) |
| Какой генератор менять и какой Python-тест открыть | [tools/README.ru.md](../tools/README.ru.md) |

Разделы: [оружие](#weapons) · [броня](#armor) · [станки](#machines) ·
[NPC](#npcs) · [блоки](#blocks) · [структуры](#structures).

Документы ниже описывают отдельные срезы, а не завершённость всего порта.
Результаты и фразы «следующий этап» внутри старых записей относятся к их дате.

<a id="weapons"></a>

## Оружие и боеприпасы

- [Базовый арсенал и управление](../README.md#что-можно-попробовать); [параметры баллистики](../content/ballistic-weapons.json), [выбор переносимого оружия](../content/weapon-ports.json).
- [Два лазера: попадания, батареи, модели и ограничения](LASERS.ru.md)
- [Ракетница: три боеприпаса, взрывы, безопасный режим и радиация](ROCKET_LAUNCHER.ru.md)
- [Зажигательные патроны: магазины, смена боеприпаса, броня и поджог](INCENDIARY_AMMO.ru.md)
- [Взрывные патроны AS50: прямое попадание, vanilla-взрыв и права B](AS50_EXPLOSIVE.ru.md)
- [Бензопила: топливо, рубка и улучшения лезвий](CHAINSAW.ru.md)
- [Stielgranate и FragGrenade: бросок, отскоки, взрыв и крафт](HAND_GRENADES.ru.md)
- [GrenadeLauncher: шесть 40-мм гранат, отскоки и вращение барабана](GRENADE_LAUNCHER.ru.md)
- [Flamethrower: топливо, автоматический огонь и поджог](FLAMETHROWER.ru.md)
- [Minigun: нулевая задержка, барабаны, перезарядка и вращение стволов](MINIGUN.ru.md)
- [Gauss Rifle: составной боеприпас, транзакционная R и летящий снаряд](GAUSS_RIFLE.ru.md)
- [Scatterbeam Rifle: пять энергетических снарядов, вода, R и отсутствие исходных рецептов](SCATTERBEAM_RIFLE.ru.md)
- [Blaster Rifle: одиночный энергетический снаряд, падение урона, R, крафт и Grinder](BLASTER_RIFLE.ru.md)
- [Alien Blaster: FIRE-снаряд, ячейка/R, поджог с правами B и профиль Ghastling](ALIEN_BLASTER.ru.md)
- [PDW: Advanced-пуля, магазин/R, производство и три расцветки](PDW.ru.md)

<a id="armor"></a>

## Броня и защита

- [Боевая броня T2, камуфляж, ремонт и зомби-пигман](T2_ARMOR_PIGMAN.ru.md)
- [Hazmat: защитный костюм, радиация и волокно](HAZMAT.ru.md)
- [T1 Combat: солдатская броня из железа и ткани](T1_COMBAT.ru.md)
- [T1 Miner: шахтёрская броня, добыча и четыре цвета](T1_MINER.ru.md)
- [T1 Scout: бандитская броня, скорость и прыжок](T1_SCOUT.ru.md)
- [Commando, броня T2 Commando и подводные бонусы](COMMANDO.ru.md)
- Берет ArmySoldier описан вместе с [военным NPC](ARMY_SOLDIER_BERET.ru.md); радиация — вместе с [Reaction Chamber](REACTION_CHAMBER.ru.md).

<a id="machines"></a>

## Станки и производственные цепочки

- [Ammo Press: производство, питание и автоматизация](AMMO_PRESS.ru.md)
- [Metal Press: пластины, детали и авторазделение](METAL_PRESS.ru.md)
- [Blast Furnace: выплавка стали и сплавов](BLAST_FURNACE.ru.md)
- [Химическая лаборатория, кислота, молоко и производственные цепочки](CHEM_LAB.ru.md)
- [Реакционная камера, переработка титана/урана, радиация и лекарства](REACTION_CHAMBER.ru.md)
- [Fabricator: кибернетические детали, ячейки и продвинутые компоненты](FABRICATOR.ru.md)
- [Зарядная станция: батареи и энергетические предметы](CHARGING_STATION.ru.md)
- [Repair Bench: ремонт надетой брони за материалы](REPAIR_BENCH.ru.md)
- [Camo Bench: камуфляж T2 и перекраска блоков](CAMO_BENCH.ru.md)
- [Grinder: переработка оружия, патронов и брони](GRINDER.ru.md)
- [Ore Drill: добыча из кластеров, размеры, головки и питание](ORE_DRILL.ru.md)

<a id="npcs"></a>

## NPC, бой и спавнеры

- [SuperMutantBasic: первый NPC, бой, броня и лут](SUPER_MUTANT.ru.md)
- [CyberDemon, Nether Blaster и естественная добыча кибердеталей](CYBER_DEMON.ru.md)
- [ZombieSoldier: оружие, лут и первый срез спавна Верхнего мира](ZOMBIE_SOLDIER.ru.md)
- [ZombieFarmer и ZombieMiner: экипировка, лут и спавн](RURAL_ZOMBIES.ru.md)
- [SkeletonSoldier: скелет-солдат с оружием и бронёй](SKELETON_SOLDIER.ru.md)
- [Bandit: шесть пушек, Scout и добыча патронов](BANDIT.ru.md)
- [PsychoSteve: бой, редкий спавн, пилы и топливо](PSYCHO_STEVE.ru.md)
- [ArmySoldier, берет T2 и военная точка появления](ARMY_SOLDIER_BERET.ru.md)
- [NPC-спавнер: HOLE / SOLDIER_SPAWN, лимиты и сохранение связей](NPC_SPAWNER.ru.md)
- [AttackHelicopter: полёт, пулемёт/ракеты, лут и конечный пост](ATTACK_HELICOPTER.ru.md)
- Ещё NPC описаны вместе с их системами: [Commando](COMMANDO.ru.md), [ZombiePigmanSoldier](T2_ARMOR_PIGMAN.ru.md), [AlienBug](ORE_SPIKE_ALIENBUG.ru.md), [Ghastling](GHASTLING_SOUL_PLATFORM.ru.md), [ZombiePoliceman](POLICE_STATION.ru.md).

<a id="blocks"></a>

## Блоки, руды и снабжение

- [Руды: добыча, генерация и 19 рецептов обычной печи](ORES.ru.md)
- [Рудные кластеры и пятая малая локация Незера](ORE_CLUSTERS.ru.md)
- [Панели, армированный бетон и металлические лестницы](BUILDING_BLOCKS.ru.md)
- [Мешки с песком, лампы и бункерная дверь](FORTIFICATIONS.ru.md)
- [Neonlights: пять светящихся блоков, крафт и Camo Bench](NEONLIGHTS.ru.md)
- [Камуфляжные сети: соединения, навесы, крафт и перекраска](CAMOUFLAGE_NETS.ru.md)
- [Военные ящики: девять вариантов и полный исходный лут](MILITARY_CRATES.ru.md)

<a id="structures"></a>

## Генерация мира и локации

- [Настраиваемые сетки структур, пересечения, locate и существующие миры](STRUCTURE_GRIDS.ru.md)
- [Малый адский алтарь и десять блоков Nether Metal](NETHER_ALTAR.ru.md)
- [NetherLoot01: сундук с исходными ресурсами и охрана](NETHER_LOOT.ru.md)
- [NetherAcidHole: природная кислота и её использование](NETHER_ACID.ru.md)
- [Ghastling: зажигательная очередь и NetherSoulPlatform](GHASTLING_SOUL_PLATFORM.ru.md)
- [Рудный выступ Верхнего мира, AlienBug и слизистые блоки](ORE_SPIKE_ALIENBUG.ru.md)
- [Метеоритная база и её генерация](METEOR_BASE.ru.md)
- [Процедурное гнездо AlienBug и закалённый песок](ALIENBUG_NEST.ru.md)
- [Адская крепость с рудным кластером и охраной](NETHER_CLUSTER_CASTLE.ru.md)
- [Средний адский алтарь и четыре встречи с CyberDemon](NETHER_MEDIUM_ALTAR.ru.md)
- [Клетка Ghastling с обычным спавнером и сундуком](NETHER_GHAST_SPAWNER.ru.md)
- [ZombiePoliceman, охрана и полицейский участок](POLICE_STATION.ru.md)
- [SurvivorHideout: убежище, охрана, полный лут и природная генерация](SURVIVOR_HIDEOUT.ru.md)
- [DesertOilCluster: нефтяное месторождение, охрана и добыча буром](DESERT_OIL_CLUSTER.ru.md)
- [GasStation: заправка, сундуки с топливом и конечная встреча](GAS_STATION.ru.md)
- [SmallTrainstation: станция, случайные разрушения, шахтёры и добыча](SMALL_TRAIN_STATION.ru.md)
- [FactoryHouseSmall: заводской дом, сундук, освещение и охрана](FACTORY_HOUSE_SMALL.ru.md)
- [SmallMine: малая шахта, рудные смеси, охрана и добыча буром](SMALL_MINE.ru.md)
- [Процедурный военный лагерь MilitaryCamp](MILITARY_CAMP.ru.md)
- [Процедурный замок Castle](CASTLE.ru.md)
- [AircraftCarrier: авианосец, снабжение и конечные встречи](AIRCRAFT_CARRIER.ru.md)

## Исходный каталог

- [Аудит оригинала](../content/legacy-inventory.json): пакеты, Java-файлы и оружие.
- [Metadata, рецепты и материалы](../content/crafting-content.json).
- [Оригинальный README](../legacy/1.12.2/README.md) и [лицензия](../LICENSE.txt).

При добавлении документа о новой системе включить его в соответствующий раздел.
Не переносить сюда текущий source SHA, числа тестов и очередь работ из handoff.
