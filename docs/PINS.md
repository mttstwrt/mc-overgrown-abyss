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
| `common/.../compat/RavineCentrePlacement.java` | `RandomSpreadStructurePlacement` (`placementCodec`, `getPotentialStructureChunk`, `spacing`) and `StructurePlacement.ExclusionZone` (deprecated). Extends the random-spread class only because `/locate` special-cases it |
| `common/.../compat/RavineCityStructure.java` | `Structure.findGenerationPoint`, `JigsawPlacement.addPieces` signature (11 parameters in 1.21.1), `PoolAliasBinding`, `JigsawStructure` default constants. `JigsawStructure` is final, so this wraps `addPieces` instead of extending it |
| `common/.../compat/SwapBlocksProcessor.java`, `NestedProcessorListProcessor.java` | `StructureProcessor.processBlock` signature, `StructureBlockInfo` record, `StructureProcessorType.LIST_CODEC` |
| `data/overgrown_abyss/worldgen/density_function/**` | Density-function JSON format; `InclusiveRange` field names (`min_inclusive`, `max_inclusive`) |
| `data/overgrown_abyss/tags/worldgen/noise_settings/**` | Tag folder layout |
| `data/overgrown_abyss/worldgen/structure/**`, `structure_set/**` | Jigsaw settings fields, `pool_aliases` format, placement codec fields |
| `data/overgrown_abyss/worldgen/template_pool/city/**` | Pool element format; template locations are vanilla `minecraft:ancient_city/**` IDs |
| `data/overgrown_abyss/worldgen/processor_list/city/**` | `rule` processor, `append_loot` modifier, `blockstate_match` predicate, `protected_blocks` |
| `data/overgrown_abyss/loot_table/**` | Folder name (`loot_table`, singular, since 1.21) |
