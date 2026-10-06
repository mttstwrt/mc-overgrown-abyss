# Pinned versions

Source of truth is `gradle.properties`. Update both together. Pinned 2026-10-03.

| Component | Version | Notes |
|---|---|---|
| Minecraft | 1.21.1 | First target |
| Mod id | overgrown_abyss | Also the data namespace |
| Java | 21 | Gradle 8.14 does not run on JDK 27; build with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` |
| Gradle | 8.14 | Wrapper copied from Architect's Toolbox |
| Architectury Loom | 1.13-SNAPSHOT | Snapshot as in Architect's Toolbox; pin a release once one covers it |
| Architectury Plugin | 3.4-SNAPSHOT | |
| Mappings | Mojang official | |
| NeoForge | 21.1.252 | Latest 21.1.x found on maven.neoforged.net |
| Fabric Loader | 0.19.5 | `fabric.mod.json` allows `>=0.16.0` |
| Fabric API | 0.116.17+1.21.1 | |
| Architectury API | 13.0.11 | Latest 13.x; 21.x versions belong to newer Minecraft |

## Libraries

| Library | Purpose | Why not hand-written |
|---|---|---|
| Architectury API | Cross-loader registries and biome feature injection | Registration timing differs per loader; same stack as Architect's Toolbox |
| JUnit 5 (5.11.4) | Unit tests for pure logic | Build-time only |
| MixinExtras (bundled: 0.5.5 in Fabric Loader 0.19.5, 0.5.3 in NeoForge 21.1.252) | `@WrapOperation` and `@Local` in the density hook | Not a dependency we ship; both loaders bundle it. Wrapping instead of redirecting stays compatible with other mods hooking the same call |

Considered and held in reserve: Lithostitched (datapack biome modifiers / pool injectors).

## Version-specific files

Re-check these on every Minecraft bump:

| File | Depends on |
|---|---|
| `common/.../mixin/ChunkMapMixin.java` | `ChunkMap` constructor calling `RandomState.create(NoiseGeneratorSettings, HolderGetter, long)` |
| `common/.../mixin/NoiseChunkMixin.java` | `NoiseChunk` constructor signature and its `aquifer` field being read lazily |
| `common/.../mixin/RandomStateMixin.java` | `RandomState` class |
| `common/.../compat/RavineDensityHook.java` | `NoiseRouter` and `NoiseGeneratorSettings` record components |
| `common/.../compat/FootprintAquifer.java` | `Aquifer` interface |
| `common/.../mixin/NoiseBasedChunkGeneratorMixin.java` | Private `NoiseBasedChunkGenerator.doCreateBiomes` and its call to `ChunkAccess.fillBiomesFromNoise(BiomeResolver, Climate.Sampler)` |
| `common/.../mixin/NoiseBasedChunkGeneratorMixin.java` (disc materials) | `NoiseBasedChunkGenerator.applyCarvers` (7 parameters in 1.21.1, called once per chunk with `GenerationStep.Carving.AIR`), and the chunk status order: a chunk's features require its neighbours' carvers (`ChunkPyramid`), which is why painting at the end of carving never covers a feature's blocks |
| `common/.../mixin/ChunkGeneratorMixin.java` | `ChunkGenerator.applyBiomeDecoration(WorldGenLevel, ChunkAccess, StructureManager)` as the one entry point for decoration, and its keeping only biomes the biome source can produce (`retainAll(possibleBiomes)`), which is why a disc biome's own feature list never runs and themes place their growth themselves |
| `common/.../compat/DiscGrower.java` | `ConfiguredFeature.place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`, `Registries.CONFIGURED_FEATURE`, `BlockState.isFaceSturdy`, `ServerChunkCache.randomState()`. For inherited features it runs a placed feature itself, so it also depends on `PlacedFeature.placement()` and `feature()`, `PlacementModifier.getPositions(PlacementContext, RandomSource, BlockPos)`, `BiomeFilter` being the rule that asks whether the biome lists the feature, `BiomeGenerationSettings.features()` being one `HolderSet` per `GenerationStep.Decoration` in ordinal order, and `WorldgenRandom.setFeatureSeed`. `TreeFeature` placing a tree with no check of the ground under it (vanilla checks in the placed feature), which is why a growth on `top` is only started over a firm block and one in `water` only on the bed |
| `common/.../compat/DiscPlacementContext.java` | `PlacementContext` being an open class whose `getHeight(Heightmap.Types, int, int)` is what `HeightmapPlacement`, `SurfaceWaterDepthFilter` and `SurfaceRelativeThresholdFilter` ask, and `HeightmapPlacement` dropping a place whose height is not above `getMinBuildHeight()`. `Heightmap.Types.isOpaque()` as the test each heightmap applies to a block (which of them see through water) |
| `common/.../compat/InheritedSpawns.java` | `MobSpawnSettings`: `getMobs(MobCategory)`, `getMobSpawnCost(EntityType)` (one mob at a time, hence the walk over `BuiltInRegistries.ENTITY_TYPE`), `getCreatureProbability()`, and its `Builder` (`addSpawn`, `addMobCharge(type, charge, energyBudget)`, `creatureGenerationProbability`); `SpawnerData`'s public `type`, `minCount`, `maxCount` |
| `common/.../mixin/BiomeMixin.java` | `Biome.getMobSettings()` being the one accessor all spawning reads. NeoForge patches it (and `getGenerationSettings()`) to return the biome as changed by biome modifiers, which is how a parent's mod-added spawns and features are seen; Fabric API changes the biome's settings in place |
| `common/.../compat/DiscPainter.java` | `ChunkAccess.setBlockState(BlockPos, BlockState, boolean)` updating worldgen heightmaps during carving (`ProtoChunk`), `getMinBuildHeight`/`getMaxBuildHeight`, `WorldgenRandom.setDecorationSeed`, `BlockStateProvider.getState(RandomSource, BlockPos)`. For water: `Blocks.WATER`, `BlockState.isAir()`, `ChunkAccess.isOutsideBuildHeight(int)`; water written here is never scheduled to flow, which is why `DiscWater` only puts it where it is held in |
| `common/.../compat/FootprintBiomeResolver.java`, `RavineDensityHook.java` (land check) | `BiomeResolver`, `BiomeSource.getNoiseBiome`, `RandomState.sampler()`, quart coordinates |
| `common/.../compat/RavineCentrePlacement.java` | `RandomSpreadStructurePlacement` (`placementCodec`, `getPotentialStructureChunk`, `spacing`) and `StructurePlacement.ExclusionZone` (deprecated). Extends the random-spread class only because `/locate` special-cases it |
| `common/.../compat/RavineCityStructure.java` | `Structure.findGenerationPoint`, `JigsawPlacement.addPieces` signature (11 parameters in 1.21.1), `PoolAliasBinding`, `JigsawStructure` default constants. `JigsawStructure` is final, so this wraps `addPieces` instead of extending it |
| `common/.../compat/SwapBlocksProcessor.java`, `NestedProcessorListProcessor.java` | `StructureProcessor.processBlock` signature, `StructureBlockInfo` record, `StructureProcessorType.LIST_CODEC` |
| `data/overgrown_abyss/worldgen/density_function/**` | Density-function JSON format; `InclusiveRange` field names (`min_inclusive`, `max_inclusive`); in a theme's `palette`, vanilla's block-state provider format (`simple_state_provider`, `weighted_state_provider`, `noise_threshold_provider`, ...) and block-state format (`Name`, `Properties`) |
| `data/overgrown_abyss/worldgen/biome/**` | Biome JSON format (`has_precipitation`, `temperature`, `downfall`, `effects` with `grass_color` and `foliage_color`, `spawners`, `spawn_costs`, `carvers`, `features`). The spawn lists and sounds are copied from vanilla's jungle, lush caves and mangrove swamp |
| `data/minecraft/tags/worldgen/biome/**`, `data/c/tags/worldgen/biome/**` | The vanilla and convention biome tags that `lush_caves`, `jungle` and `mangrove_swamp` are in; our disc biomes are added to the same ones (not the structure tags) |
| `data/overgrown_abyss/worldgen/configured_feature/**` | `simple_block` feature config; block-state properties of `amethyst_cluster`. The jungle and mangrove files are copies of 1.21.1's `jungle_tree`, `mega_jungle_tree`, `jungle_bush`, `mangrove`, `tall_mangrove` and `cave_vine` and follow their formats (`mangrove_giant` is `tall_mangrove` with other numbers; its and `mangrove_tall`'s `max_root_length` rest on `MangroveRootPlacer` giving the whole tree up when a root arm needs more steps than that to reach the ground and on its width counting the arm's 3D Manhattan distance from the trunk's base, both `[recall; verify]` against the jar): the `tree` config (`two_layers_feature_size` with `min_clipped_height`, the `leave_vine` and `attached_to_leaves` decorators, `mangrove_root_placer`), `block_column`, `random_patch` with an inline placed feature, `simple_random_selector`, the `random_offset` placement (spreads limited to 16), and the block predicates `matching_blocks`, `solid`, `has_sturdy_face` and `all_of`. Block-state properties of `vine`, `jungle_leaves`, `mangrove_roots`, `hanging_roots`, `cave_vines` |
| the mangrove theme's palette in `ravine/carve.json` | The tag `#minecraft:mangrove_roots_can_grow_through` holding mud: a mangrove's roots pass through mud and stop at the next block, so the platform has packed mud under its mud. All mud, the roots leave through the underside and `MangroveRootPlacer` gives the tree up |
| `disc_themes` in `density_function/ravine/carve.json` | The ids of the biomes inherited from (`lush_caves`, `jungle`, `mangrove_swamp`) and the names of decoration stages (`vegetal_decoration`, ...). In `dev-datapacks/tuned-themes`, the ids of the vanilla configured features the hand-tuned themes grow |
| `data/overgrown_abyss/tags/worldgen/noise_settings/**`, `tags/worldgen/biome/**` | Tag folder layout |
| `data/overgrown_abyss/worldgen/structure/**`, `structure_set/**` | Jigsaw settings fields, `pool_aliases` format, placement codec fields |
| `data/overgrown_abyss/worldgen/template_pool/city/**` | Pool element format; template locations are vanilla `minecraft:ancient_city/**` IDs |
| `data/overgrown_abyss/worldgen/processor_list/city/**` | `rule` processor, `append_loot` modifier, `blockstate_match` predicate, `protected_blocks` |
| `data/overgrown_abyss/loot_table/**` | Folder name (`loot_table`, singular, since 1.21) |
