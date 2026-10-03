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

Considered and held in reserve: Lithostitched (datapack biome modifiers / pool injectors).

## Version-specific files

None yet.
