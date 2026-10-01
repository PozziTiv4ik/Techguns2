# Карта кода и связей

Карта отвечает на вопрос «где менять и чем проверять». Текущая задача — в
[handoff](HANDOFF.ru.md), описание поведения — в [каталоге документов](README.ru.md).
Выберите нужный раздел; читать весь код или журнал проверок для поиска не требуется.

[Слои](#layers) · [Точки входа](#entrypoints) · [Структуры и старые имена](#worldgen) ·
[Тесты](#tests) · [Поиск](#search).

<a id="layers"></a>

## Слои и источник изменений

```text
legacy/1.12.2/ + content/weapon-ports.json
        ↓ tools/audit_legacy.py, tools/generate_weapon_content.py, tools/legacy_*.py
content/*.json + часть core/*.java + выбранные платформенные ресурсы
        ↓
core/ (правила без Minecraft) ← platforms/neoforge-26.2/ (NeoForge, мир, клиент)
```

- [settings.gradle](../settings.gradle) включает только core и neoforge-26.2;
  зависимость платформы от ядра задана в [Gradle-файле платформы](../platforms/neoforge-26.2/build.gradle).
- [legacy/1.12.2/](../legacy/1.12.2/) — неизменяемый оригинал. Современный код
  не компилирует его; исходное поведение читают генераторы и разработчик.
- [core/](../core/src/main/java/techguns/core/) содержит и написанные вручную
  правила, и сгенерированные таблицы/классы. Наличие Java-файла здесь не означает,
  что его можно править напрямую: например, Weapons, Armors, NpcWeapons,
  CastleMaze и CastleSegments создаются генераторами.
- [content/weapon-ports.json](../content/weapon-ports.json) — входной список
  оружия и моделей. Остальные каталоги content — результаты аудита/преобразования.
  Список выходов `generate()` и [карта генераторов](../tools/README.ru.md)
  помогают найти владельца; исправлять нужно преобразование и обновлять его результат.
- [ресурсы платформы](../platforms/neoforge-26.2/src/main/resources/) включают
  генерируемые assets/data и ручную конфигурацию, например
  [neoforge.mods.toml](../platforms/neoforge-26.2/src/main/resources/META-INF/neoforge.mods.toml)
  и [techguns.mixins.json](../platforms/neoforge-26.2/src/main/resources/techguns.mixins.json).
- [tests/](../tests/) — данные выделенных серверов. `.tools/` и `build/` —
  локальные отчёты/кэш, а не обязательные файлы для следующего клона.

<a id="entrypoints"></a>

## Современные точки входа

Java-ссылки ведут прямо к классам адаптера и связанного с ними ядра.
Общие классы полезно прочитать до добавления ещё одной частной реализации.

| Задача | Открыть сначала | Связь с соседними частями |
|---|---|---|
| Scatterbeam Rifle / Blaster Rifle | [BlasterProjectile](../platforms/neoforge-26.2/src/main/java/techguns/modern/BlasterProjectile.java), [BlasterRenderer](../platforms/neoforge-26.2/src/main/java/techguns/modern/client/BlasterRenderer.java) | Число снарядов — GunItem/NpcCombat; ENERGY и импульс — ShotDamage/ArmorDamage; падение урона — WeaponSpec.damageAt и сохранённый origin; R — ReloadSessions. Проверки: [ScatterbeamGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/ScatterbeamGameTests.java), [BlasterRifleGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/BlasterRifleGameTests.java), [ScatterbeamTest](../core/src/test/java/techguns/core/ScatterbeamTest.java), [BlasterRifleTest](../core/src/test/java/techguns/core/BlasterRifleTest.java). Владелец — legacy_scatterbeam.py; масштабирование TTL Gauss/Blaster — scaled_projectile_lifetime. |
| Gauss Rifle и составная перезарядка | [GaussProjectile](../platforms/neoforge-26.2/src/main/java/techguns/modern/GaussProjectile.java), [AmmoSpec](../core/src/main/java/techguns/core/AmmoSpec.java), [GunItem](../platforms/neoforge-26.2/src/main/java/techguns/modern/GunItem.java) | Массивы входов/возвратов — AmmoSpec.components, время — ReloadSessions, арифметика — Magazine. Полёт — LegacyShot/GaussRules; урон — ShotDamage/ArmorDamage. Проверки: [GaussGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/GaussGameTests.java), [GaussTest](../core/src/test/java/techguns/core/GaussTest.java), общие Arsenal/Grinder. Владелец параметров/OBJ — legacy_gauss.py. |
| Регистрация и запуск | [Techguns](../platforms/neoforge-26.2/src/main/java/techguns/modern/Techguns.java), [TGContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/TGContent.java) | Системы подключаются в конструкторе Techguns; отдельные Content-классы владеют своими регистрациями. |
| Выстрел, боезапас, R, прицел и B | [GunItem](../platforms/neoforge-26.2/src/main/java/techguns/modern/GunItem.java), [ReloadSessions](../platforms/neoforge-26.2/src/main/java/techguns/modern/ReloadSessions.java), [AimSessions](../platforms/neoforge-26.2/src/main/java/techguns/modern/AimSessions.java), [SafeMode](../platforms/neoforge-26.2/src/main/java/techguns/modern/SafeMode.java) | Сетевые действия: [GunNetwork](../platforms/neoforge-26.2/src/main/java/techguns/modern/network/GunNetwork.java), [GunActionPayload](../platforms/neoforge-26.2/src/main/java/techguns/modern/network/GunActionPayload.java), [AimPayload](../platforms/neoforge-26.2/src/main/java/techguns/modern/network/AimPayload.java), [SafeModePayload](../platforms/neoforge-26.2/src/main/java/techguns/modern/network/SafeModePayload.java). |
| Minigun: пустой спуск, Creative, вращение | [MinigunItem](../platforms/neoforge-26.2/src/main/java/techguns/modern/MinigunItem.java), [MinigunModel](../platforms/neoforge-26.2/src/main/java/techguns/modern/client/MinigunModel.java) | Темп — WeaponSpec.firingInterval / общий GunItem; вращение — [MinigunAnimation](../core/src/main/java/techguns/core/MinigunAnimation.java), генерируется legacy_minigun.py. Проверки: [MinigunGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/MinigunGameTests.java), общие Incendiary/Arsenal/Grinder. |
| Взрывные патроны AS50 | [ExplosiveBullet](../platforms/neoforge-26.2/src/main/java/techguns/modern/ExplosiveBullet.java), [ExplosiveBulletExplosion](../platforms/neoforge-26.2/src/main/java/techguns/modern/ExplosiveBulletExplosion.java) | Выбор — [BallisticAmmo](../platforms/neoforge-26.2/src/main/java/techguns/modern/BallisticAmmo.java), [BallisticVariant](../core/src/main/java/techguns/core/BallisticVariant.java); генерируемые параметры — [ExplosiveAmmo](../core/src/main/java/techguns/core/ExplosiveAmmo.java). Native vanilla-взрыв отделён от RocketExplosion/TGExplosion. Проверки: [ExplosiveAmmoGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/ExplosiveAmmoGameTests.java), [ExplosiveAmmoTest](../core/src/test/java/techguns/core/ExplosiveAmmoTest.java). |
| Снаряды и урон | [Bullet](../platforms/neoforge-26.2/src/main/java/techguns/modern/Bullet.java), [LaserBeam](../platforms/neoforge-26.2/src/main/java/techguns/modern/LaserBeam.java), [RocketProjectile](../platforms/neoforge-26.2/src/main/java/techguns/modern/RocketProjectile.java), [GrenadeProjectile](../platforms/neoforge-26.2/src/main/java/techguns/modern/GrenadeProjectile.java), [Grenade40mmProjectile](../platforms/neoforge-26.2/src/main/java/techguns/modern/Grenade40mmProjectile.java), [FlameProjectile](../platforms/neoforge-26.2/src/main/java/techguns/modern/FlameProjectile.java), [ShotDamage](../platforms/neoforge-26.2/src/main/java/techguns/modern/ShotDamage.java), [ArmorDamage](../platforms/neoforge-26.2/src/main/java/techguns/modern/ArmorDamage.java) | Специальные атаки: [ChainsawAttack](../platforms/neoforge-26.2/src/main/java/techguns/modern/ChainsawAttack.java), [FlameFiring](../platforms/neoforge-26.2/src/main/java/techguns/modern/FlameFiring.java), [RocketExplosion](../platforms/neoforge-26.2/src/main/java/techguns/modern/RocketExplosion.java), [GrenadeExplosion](../platforms/neoforge-26.2/src/main/java/techguns/modern/GrenadeExplosion.java). |
| Общие станки | [TGMachineContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/TGMachineContent.java), [ProcessingMachineBlockEntity](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/ProcessingMachineBlockEntity.java), [ProcessingMachineMenu](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/ProcessingMachineMenu.java), [MachineFluidStorage](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/MachineFluidStorage.java) | Одиночные реализации находятся рядом; многоблочные связи — [machine/multiblock/](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/multiblock). |
| Специализированные станки | [ReactionContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/reaction/ReactionContent.java), [FabricatorContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/fabricator/FabricatorContent.java), [OreDrillContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/drill/OreDrillContent.java), [ChargingStationContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/charging/ChargingStationContent.java), [RepairBenchContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/repair/RepairBenchContent.java), [CamoBenchContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/camo/CamoBenchContent.java), [GrinderContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/grinder/GrinderContent.java) | От регистрации идти к BlockEntity и Menu в том же пакете; классы Recipe есть у станков с рецептами. |
| Крафт и жидкости | [TGCrafting](../platforms/neoforge-26.2/src/main/java/techguns/modern/crafting/TGCrafting.java), [TGFluids](../platforms/neoforge-26.2/src/main/java/techguns/modern/fluid/TGFluids.java), [ChemicalRules](../platforms/neoforge-26.2/src/main/java/techguns/modern/machine/ChemicalRules.java) | Рецепты и теги создаются генераторами; ChemicalRules хранит правила выбора внешних жидкостей. |
| Броня и радиация | [ArmorContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/armor/ArmorContent.java), [TGArmorItem](../platforms/neoforge-26.2/src/main/java/techguns/modern/armor/TGArmorItem.java), [TGArmorSystem](../platforms/neoforge-26.2/src/main/java/techguns/modern/armor/TGArmorSystem.java), [RadiationSystem](../platforms/neoforge-26.2/src/main/java/techguns/modern/radiation/RadiationSystem.java) | Параметры Armors генерируются; независимая математика — [ArmorMath](../core/src/main/java/techguns/core/ArmorMath.java). |
| NPC и естественный спавн | [NpcContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/NpcContent.java), [HostileNpc](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/HostileNpc.java), [ArmedNpc](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/ArmedNpc.java), [NpcCombat](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/NpcCombat.java), [OverworldSpawns](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/OverworldSpawns.java), [NetherSpawns](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/NetherSpawns.java) | Селекция пулов: [OverworldSpawnSelector](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/OverworldSpawnSelector.java), [NetherSpawnSelector](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/NetherSpawnSelector.java); отдельные сущности лежат в npc/. |
| Конечные посты NPC | [NpcSpawnerContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/spawner/NpcSpawnerContent.java), [NpcSpawnerBlockEntity](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/spawner/NpcSpawnerBlockEntity.java), [SpawnerLifecycle](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/spawner/SpawnerLifecycle.java), [SpawnerMailbox](../platforms/neoforge-26.2/src/main/java/techguns/modern/npc/spawner/SpawnerMailbox.java) | Лимиты — [SpawnerRules](../core/src/main/java/techguns/core/SpawnerRules.java); смерть, UUID и отложенные уведомления связаны через npc/spawner/. |
| Руды и строительные блоки | [TGOreContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/TGOreContent.java), [OreClusterContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/OreClusterContent.java), [BuildingContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/BuildingContent.java), [FortificationContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/FortificationContent.java), [NeonContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/NeonContent.java), [CamouflageNetContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/CamouflageNetContent.java), [MilitaryCrateContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/MilitaryCrateContent.java) | Рудный кластер и обычная руда — разные блоки; бур живёт в machine/drill/. |
| Структуры | [LocationContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/LocationContent.java), [LocationConfig](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/LocationConfig.java) | Старт/рельеф — *Structure; сохранение и размещение — *Piece. Соответствия имён приведены ниже. |
| Клиент: клавиши, HUD, экраны, рендер | [TechgunsClient](../platforms/neoforge-26.2/src/main/java/techguns/modern/client/TechgunsClient.java) | Рендереры и экраны — [client/](../platforms/neoforge-26.2/src/main/java/techguns/modern/client); серверные пакеты не должны зависеть от client/. |

<a id="worldgen"></a>

## Структуры: от оригинала к размещению и проверке

Реестр — LocationContent; исходные пулы и небольшие NBT-шаблоны —
[legacy_locations.py](../tools/legacy_locations.py). Сетки, пересечения и native locate:
[StructureGrid](../core/src/main/java/techguns/core/StructureGrid.java),
[LocationConfig](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/LocationConfig.java),
[StructureGridPlacement](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/StructureGridPlacement.java).
Общий выбор кандидатов и поиск рельефа:
[SmallNetherStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/SmallNetherStructure.java), [MediumNetherStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/MediumNetherStructure.java), [SmallOverworldStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/SmallOverworldStructure.java).
Большой LAND-выбор связывает MilitaryCamp и Castle; правила выбора нельзя
менять только в одном кандидате. WATER-пул содержит AircraftCarrier и рассматривается отдельно.
[PlannedBlocks](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/PlannedBlocks.java)
вычисляет соединения блоков по полному плану для Castle и AircraftCarrier.
Ступени палитры регистрирует [MetalStairContent](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/MetalStairContent.java).

| Имя оригинала / поиск | Современное размещение | Извлечение данных | GameTests |
|---|---|---|---|
| [WorldGenTGStructureSpawn](../legacy/1.12.2/src/main/java/techguns/world/WorldGenTGStructureSpawn.java), TGConfig | StructureGrid / StructureGridPlacement; интервалы задаёт LocationConfig | [legacy_structure_grids.py](../tools/legacy_structure_grids.py) | [StructureGridGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/StructureGridGameTests.java) |
| [NetherAltarSmall](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherAltarSmall.java) | [NetherAltarStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherAltarStructure.java), [NetherAltarPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherAltarPiece.java) | [legacy_locations.py](../tools/legacy_locations.py) | [LocationGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/LocationGameTests.java) |
| [NetherLoot01](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherLoot01.java) | [NetherLootStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherLootStructure.java), [NetherLootPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherLootPiece.java) | [legacy_locations.py](../tools/legacy_locations.py) | [NetherLootGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/NetherLootGameTests.java) |
| [NetherAcidHole](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherAcidHole.java) | [NetherAcidStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherAcidStructure.java), [NetherAcidPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherAcidPiece.java) | [legacy_locations.py](../tools/legacy_locations.py) | [NetherAcidGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/NetherAcidGameTests.java) |
| [NetherSoulPlatform](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherSoulPlatform.java) | [NetherSoulStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherSoulStructure.java), [NetherSoulPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherSoulPiece.java) | [legacy_locations.py](../tools/legacy_locations.py) | [NetherSoulGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/NetherSoulGameTests.java) |
| [NetherOreClusterSmall](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherOreClusterSmall.java) | [NetherClusterStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherClusterStructure.java), [NetherClusterPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherClusterPiece.java) | [legacy_locations.py](../tools/legacy_locations.py) | [OreClusterGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/OreClusterGameTests.java) |
| [OreClusterSpike](../legacy/1.12.2/src/main/java/techguns/world/structures/OreClusterSpike.java) | [OreSpikeStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/OreSpikeStructure.java), [OreSpikePiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/OreSpikePiece.java) | [legacy_spike.py](../tools/legacy_spike.py) | [OreSpikeGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/OreSpikeGameTests.java) |
| [OreClusterMeteorBasis](../legacy/1.12.2/src/main/java/techguns/world/structures/OreClusterMeteorBasis.java) | [MeteorStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/MeteorStructure.java), [MeteorPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/MeteorPiece.java) | [legacy_meteor.py](../tools/legacy_meteor.py) | [MeteorGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/MeteorGameTests.java) |
| [AlienBugNestStructure](../legacy/1.12.2/src/main/java/techguns/world/structures/AlienBugNestStructure.java) | [BugNestStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/BugNestStructure.java), [BugNestPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/BugNestPiece.java) | [legacy_bugnests.py](../tools/legacy_bugnests.py) | [BugNestGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/BugNestGameTests.java) |
| [NetherOreClusterCastle](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherOreClusterCastle.java) | [NetherCastleStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherCastleStructure.java), [NetherCastlePiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherCastlePiece.java) | [legacy_nether_castle.py](../tools/legacy_nether_castle.py) | [NetherCastleGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/NetherCastleGameTests.java) |
| [NetherAltarMedium](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherAltarMedium.java) | [NetherMediumAltarStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherMediumAltarStructure.java), [NetherMediumAltarPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherMediumAltarPiece.java) | [legacy_medium_altar.py](../tools/legacy_medium_altar.py) | [MediumAltarGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/MediumAltarGameTests.java) |
| [NetherGhastSpawner](../legacy/1.12.2/src/main/java/techguns/world/structures/NetherGhastSpawner.java) | [NetherGhastStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherGhastStructure.java), [NetherGhastPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/NetherGhastPiece.java) | [legacy_ghast_spawner.py](../tools/legacy_ghast_spawner.py) | [NetherGhastGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/NetherGhastGameTests.java) |
| [PoliceStation](../legacy/1.12.2/src/main/java/techguns/world/structures/PoliceStation.java) | [PoliceStationStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/PoliceStationStructure.java), [PoliceStationPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/PoliceStationPiece.java) | [legacy_police_station.py](../tools/legacy_police_station.py) | [PoliceStationGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/PoliceStationGameTests.java) |
| [SurvivorHideout](../legacy/1.12.2/src/main/java/techguns/world/structures/SurvivorHideout.java) | [SurvivorHideoutStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/SurvivorHideoutStructure.java), [SurvivorHideoutPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/SurvivorHideoutPiece.java) | [legacy_survivor_hideout.py](../tools/legacy_survivor_hideout.py) | [SurvivorHideoutGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/SurvivorHideoutGameTests.java) |
| [DesertOilCluster](../legacy/1.12.2/src/main/java/techguns/world/structures/DesertOilCluster.java) | [DesertOilStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/DesertOilStructure.java), [DesertOilPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/DesertOilPiece.java) | [legacy_desert_oil.py](../tools/legacy_desert_oil.py) | [DesertOilGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/DesertOilGameTests.java) |
| [GasStation](../legacy/1.12.2/src/main/java/techguns/world/structures/GasStation.java) | [GasStationStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/GasStationStructure.java), [GasStationPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/GasStationPiece.java) | [legacy_gas_station.py](../tools/legacy_gas_station.py) | [GasStationGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/GasStationGameTests.java) |
| [SmallTrainstation](../legacy/1.12.2/src/main/java/techguns/world/structures/SmallTrainstation.java) | [TrainStationStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/TrainStationStructure.java), [TrainStationPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/TrainStationPiece.java) | [legacy_train_station.py](../tools/legacy_train_station.py) | [TrainStationGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/TrainStationGameTests.java) |
| [FactoryHouseSmall](../legacy/1.12.2/src/main/java/techguns/world/structures/FactoryHouseSmall.java) | [FactoryHouseStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/FactoryHouseStructure.java), [FactoryHousePiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/FactoryHousePiece.java) | [legacy_factory_house.py](../tools/legacy_factory_house.py) | [FactoryHouseGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/FactoryHouseGameTests.java) |
| [SmallMine](../legacy/1.12.2/src/main/java/techguns/world/structures/SmallMine.java) | [SmallMineStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/SmallMineStructure.java), [SmallMinePiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/SmallMinePiece.java) | [legacy_small_mine.py](../tools/legacy_small_mine.py) | [SmallMineGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/SmallMineGameTests.java) |
| [MilitaryBaseStructure](../legacy/1.12.2/src/main/java/techguns/world/structures/MilitaryBaseStructure.java) | [MilitaryCampStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/MilitaryCampStructure.java), [MilitaryCampPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/MilitaryCampPiece.java) | [legacy_military_camp.py](../tools/legacy_military_camp.py) | [MilitaryCampGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/MilitaryCampGameTests.java) |
| [CastleStructure](../legacy/1.12.2/src/main/java/techguns/world/structures/CastleStructure.java) | [CastleStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/CastleStructure.java), [CastlePiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/CastlePiece.java) | [legacy_castle.py](../tools/legacy_castle.py) | [CastleGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/CastleGameTests.java) |
| [AircraftCarrier](../legacy/1.12.2/src/main/java/techguns/world/structures/AircraftCarrier.java) | [AircraftCarrierStructure](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/AircraftCarrierStructure.java), [AircraftCarrierPiece](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/AircraftCarrierPiece.java), [AircraftCarrierPlan](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/AircraftCarrierPlan.java) | [legacy_aircraft_carrier.py](../tools/legacy_aircraft_carrier.py) | [AircraftCarrierGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/AircraftCarrierGameTests.java) |

Процедурные планы имеют отдельные точки входа: [CampLayout](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/camp/CampLayout.java), [CastlePlan](../platforms/neoforge-26.2/src/main/java/techguns/modern/world/structure/CastlePlan.java), [BugNestLayout](../core/src/main/java/techguns/core/BugNestLayout.java), [CastleLayout](../core/src/main/java/techguns/core/castle/CastleLayout.java).
Если современной реализации нет в карте, сверить [статус](STATUS.ru.md),
[handoff](HANDOFF.ru.md#next-milestone) и исходники. Наличие исходного класса
в legacy не означает наличие современной структуры.

Сохраняйте весь план и RNG до размещения по чанкам. Соседняя декорация зависит
также от [mixin/](../platforms/neoforge-26.2/src/main/java/techguns/modern/mixin/):
CastleOreProtectionMixin защищает исходные клетки Castle и SmallMine от соседних
рудных жил; HideoutDecorationProtectionMixin и GasStationTreeProtectionMixin — от
растительности; NetherDeltaProtectionMixin и NetherBasaltProtectionMixin — от
декораций Незера. Их регистрация —
в techguns.mixins.json; проверки последствий входят в тесты соответствующих локаций.

<a id="tests"></a>

## Как найти проверку

| Уровень | Что проверяет / точка входа |
|---|---|
| Python | [tools/tests/](../tools/tests/) — соответствие исходникам, палитры, данные и модели. Конкретные модули сопоставлены в [карте генераторов](../tools/README.ru.md#domains). |
| JUnit | [core/src/test/](../core/src/test/java/techguns/core/) — чистые правила. Например, [CastleLayoutTest](../core/src/test/java/techguns/core/CastleLayoutTest.java) для CastleLayout и [WeaponTest](../core/src/test/java/techguns/core/WeaponTest.java) для боезапаса Magazine. |
| GameTests | [test/](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/) — игра, сохранение, размещение, настоящие события. Для удержания области полёта вертолётной фикстуры — FlightChunks в [HelicopterGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/HelicopterGameTests.java): собственные непостоянные билеты, независимые от FORCED следующей группы. Классы подключаются через [WeaponGameTests](../platforms/neoforge-26.2/src/main/java/techguns/modern/test/WeaponGameTests.java), несмотря на общее имя оружия. |
| Природная генерация | [worldgen-packs/](../tests/worldgen-packs/) и задача runOreWorldTestServer — фильтр `techguns:*_natural_chunks`. |
| Настраиваемые сетки и дополнительные seed | Тот же worldgen pack; runGridWorldTestServer и runGridAlternateTestServer — фильтр `techguns:structure_grid_*_natural_chunks`. [WorldgenTestOptionsMixin](../platforms/neoforge-26.2/src/main/java/techguns/modern/mixin/WorldgenTestOptionsMixin.java) задаёт seed только тестовым серверам. |
| Условные рецепты | [chemistry-packs/](../tests/chemistry-packs/) и задача runChemistryTestServer — фильтр `techguns:chem_optional_*`. |

Параметры запуска и opt-in свойства — в [Gradle-файле платформы](../platforms/neoforge-26.2/build.gradle).
Полный набор перед коммитом кода, команды и требования к CI — только в
[порядке работы](DEVELOPMENT_WORKFLOW.ru.md#implementation-checks).
Адресный тест помогает отладке, но не заменяет обязательную проверку среза.
Текущие числа тестов здесь не дублируются.

<a id="search"></a>

## Поиск без чтения всего репозитория

Команды из корня; замените пример на имя, ID или класс своей задачи:

```powershell
# Сначала имена файлов: учитывает Castle / castle / CASTLE.
rg --files core platforms/neoforge-26.2/src tools content docs | rg -i 'castle'
# Владелец генерации и описание извлечённых данных.
rg -n 'castle|Castle' tools/legacy_castle.py content/castle.json
# Регистрация, вызовы и тесты современного класса.
rg -n 'CastlePiece|CastleLayout' core/src platforms/neoforge-26.2/src/main/java
# Исходное поведение: искать в нужной подсистеме, а не во всём legacy.
rg -n 'CastleStructure|PresetCastle' legacy/1.12.2/src/main/java/techguns/world
# Выбрать историческую запись, затем читать только её раздел.
rg -n '^## |^### ' docs/VERIFICATION.ru.md
```

Карта — список точек входа, а не полный каталог классов. При перемещении общего
класса, изменении владельца генерации или добавлении подсистемы обновите нужную
строку здесь и в tools/README.ru.md; детали поведения остаются в тематическом документе.
