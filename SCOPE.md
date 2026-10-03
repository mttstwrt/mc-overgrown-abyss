# Overgrown Abyss: Project Scope

Working title: **Overgrown Abyss** (mod id and data namespace `overgrown_abyss`, package `dev.syrval.overgrownabyss`). The spec [`03-abyss.md`](03-abyss.md) still says "Abyss" and `abyss:` for the namespace; read those as `overgrown_abyss`. Common rules: [`../README.md`](../README.md) and
[`../AGENTS.md`](../AGENTS.md).

Goal: a giant "Made in Abyss" style ravine whose walls are lush and varied, opening onto a flat-floored cavern
holding a large ruined jungle city (iteration 1: a reskin of the vanilla Ancient City).

## 1. Scope changes against the existing spec

The spec and README assume NeoForge only. The new requirement is **NeoForge and Fabric, Minecraft 1.21.1 first,
newer versions later**. This changes:

| Spec statement | Change |
|---|---|
| "Target: 1.21.1 on NeoForge" | NeoForge + Fabric from day one. Both must pass every phase gate. |
| "Features added with a NeoForge biome modifier" | Fabric has no datapack biome modifier. Feature injection goes through a thin `BiomeInjector` interface over Architectury's `BiomeModifications` (or Lithostitched JSON if adopted), referencing the same placed-feature IDs on both loaders. |
| "`ravine-core` mixin hooks" | Mixins into vanilla live in `common` (Mixin works on both loaders). Fabric and NeoForge modules contain no mixins unless unavoidable. |
| "No dependencies on other mods" | Relaxed by the owner: common libraries/APIs are allowed. Still forbidden as hard dependencies: terrain/biome/content mods (Larion, biome packs) and sibling projects. Larion remains a test target only. |
| "Jungle" theme | The spec reskins to stone bricks/mossy variants and a vines/azalea/moss palette, which fits a jungle ruin. Still vanilla blocks only. Add jungle-specific block choices (mossy stone brick, vines, jungle leaves, bamboo, glow lichen) to the processor table. |

Note: the README says four projects, "none depends on any third-party mod". Overgrown Abyss and Rift share `ravine-core` by
source inclusion. With two loaders that module must follow the same `common/fabric/neoforge` split, or be a
`common`-only module with loader-free code. That affects Rift too; decide before either starts.

## 2. Architecture

```
mc-abyss-ruins/
  common/      codecs, ravine cells, carve density function types, structure types, processors, mixins
               + data/overgrown_abyss/** (worldgen JSON: pools, processors, placed/configured features, loot, tags)
  fabric/      entrypoint, BiomeInjector (BiomeModifications), registry glue
  neoforge/    entrypoint, BiomeInjector (biome_modifier JSON), registry glue
  ravine-core/ shared source module (shape + fluid + cells); see below
  docs/PINS.md exact versions
```

Layers, mapped to code:

| Layer | What it is | Where it lives | Portable? |
|---|---|---|---|
| Shape (carve, plateau, cells) | Density-function types + `min(original, carve)` wrap hook | `ravine-core` in `common` | Yes (shared mixin) |
| Fluid override | Mixin on fluid-picker creation | `common/…/compat` (version-sensitive) | Yes, but breaks per MC version |
| Wall dressing | Vanilla placed features gated by noise zones | JSON in `common` + `BiomeInjector` per loader | JSON yes; injection per loader |
| Bottom city | Custom `overgrown_abyss:city` jigsaw structure, vanilla pool copies, rule processors, `append_loot` | JSON + one structure type in `common` | Yes |
| City placement | Custom structure-placement type at the ravine cell centre | `ravine-core` in `common` | Yes |
| Wall ruins (later) | Jigsaw structures anchored on ledges | JSON in `common` | Yes |

Custom Java is limited to: density-function type(s), a structure-placement type, the fluid-override mixin, the
density-wrap mixin, and the `BiomeInjector` glue. Everything else is data.

## 3. Libraries

Constraint (owner, updated): common libraries and APIs are fine. **No hard dependency on terrain or biome mods
(Larion, biome packs) or on sibling projects.**

### Recommended (build-time only)

| Need | Choice | Notes |
|---|---|---|
| Multi-loader build | **Architectury Loom + Architectury Plugin** (what `Architects_Toolbox` already uses), or the MultiLoader-Template (plain Fabric Loom + NeoGradle/ModDevGradle) | Both give `common/fabric/neoforge`. Using the same as Architect's Toolbox keeps one mental model. Using Architectury *Loom* does not force the Architectury *API* mod at runtime. |
| Mappings | Mojang official | Required by the portability rules. |
| Mixin | Mixin (bundled by both loaders) | MixinExtras is bundled in NeoForge and Fabric Loader for 1.21.1, but verify before relying on it; if used, prefer it only for `@WrapOperation`-style hooks to reduce conflicts. |
| Testing | JUnit 5 | Pure-logic tests for cells, carve math, codecs. |
| Multi-version (later) | **Stonecutter** (or Stonecraft, a wrapper around Stonecutter + Architectury) | Adopt only when a second MC version is supported. Until then keep version-sensitive code in a `compat` package. |

### Runtime libraries considered

| Library | Does | Verdict |
|---|---|---|
| **Architectury API** (13.0.11 exists for 1.21.1 on both loaders) | Cross-loader registries, events, `BiomeModifications` | **Recommended.** Same stack as Architect's Toolbox. Covers registration and biome feature injection, so no custom `BiomeInjector` per loader is needed; keep a thin project interface over it so it can be swapped. Verify its worldgen coverage on the pinned version. |
| **Lithostitched** | Cross-loader worldgen modifiers (biome modifiers, template-pool injectors, surface rule tweaks) as datapack JSON | **Optional / alternative.** Would make feature injection pure datapack JSON on both loaders, and its pool injectors may help the wall-ruin phase. A worldgen helper, not a terrain mod, so allowed. Adopt only if Architectury's `BiomeModifications` proves insufficient, to avoid two overlapping libraries. |
| **Fabric API** | Required on Fabric by nearly all mods | Treat as a given on the Fabric side only (it is the Fabric platform, not an optional extra). Needed for `BiomeModifications`. Document it as a required dependency in `fabric.mod.json`. |
| **TerraBlender** | Adds biomes/regions | Not needed. Spec uses noise-gated style zones, not real biomes. Cavern-biome ambience is deferred. |
| **Cloth Config / owo-lib** | Config screens | Not needed. Tunables live in datapack JSON. |
| **Moonlight Lib, Balm, Forgix** | Cross-loader helpers / jar merging | Not needed. |

**Decision:** Architectury API for loader glue. Lithostitched held in reserve. Neither is a terrain/biome mod.

## 4. Phases (updated for two loaders)

| Phase | Work | Gate |
|---|---|---|
| 0 | Pin versions in `docs/PINS.md`; `common/fabric/neoforge` build; fixed-seed test world | Both loaders boot on a dedicated server; `common` has no loader imports |
| 1 | **Shape spike:** density wrap hook + carve + fluid override; plain stone | Terrain outside the footprint unchanged under Larion; screenshots from rim, mid-air, floor look striking; floor is lava-free; **same result on Fabric and NeoForge** |
| 2 | 3 wall styles; ledges; waterfalls; `BiomeInjector` | Walls look varied from three viewpoints on both loaders |
| 3 | Cavern, flat floor, vanilla city placement unmodified | City generates intact on the plateau |
| 4 | Reskin processors (jungle palette); loot | Does not read as the Deep Dark; chests give correct loot |
| 5 | Wall ruins | Visible from the rim; loot works |

Stop and review after Phase 1. If the silhouette is not striking, nothing later fixes it.

Larion exists on both loaders only if you test with its Fabric or NeoForge build respectively; confirm availability
for 1.21.1 on Fabric before Phase 1.

## 5. Risks (additions to the spec)

1. **Fabric lacks Larion parity or a datapack feature injector**: mitigated by `BiomeInjector` and testing Fabric with a terrain-mod stand-in if Larion is unavailable.
2. **Fluid override mixin target differs in names/mappings per MC version**: isolate in `compat`; add a startup self-check that logs loudly if the mixin target is missing.
3. **Two loaders double the test matrix**: automate one dedicated-server smoke test per loader (boot, generate fixed-seed area, check no exceptions).
4. **Sharing `ravine-core` with Rift** now has a two-loader shape; agree on its layout first.
5. **Other mods overriding vanilla Ancient City templates.** Our pools and processor lists are copies in the `overgrown_abyss` namespace, so overrides of vanilla *pool* or *processor* JSON do not affect us. Vanilla *template* files (NBT) are referenced by ID, so a pack or mod that replaces one (same path) wins by pack priority and Overgrown Abyss would use its version. **[recall; verify in 1.21.1 source that template lookup follows pack priority.]** Effects: (a) the look changes and our reskin table may not map the other mod's blocks, so Deep Dark blocks could leak through; (b) different jigsaw blocks can break the connections our pool wiring expects, leaving gaps or generation errors. Responses:
   - *Iteration 1:* accept as a known limit and document it. Add a startup check that compares each referenced template's jigsaw layout (or hash) with the expected vanilla one and logs a loud warning on mismatch.
   - *Later:* each piece we author ourselves under the `overgrown_abyss` namespace removes the exposure for that piece. Full isolation arrives when all pieces are our own.
   - *Open decision (owner):* whether Overgrown Abyss should deliberately honour mod-added or overridden Ancient City pieces. Not decided. Default until decided: do not promise support, and do not block it. If wanted later, the cheap route is Lithostitched-style pool injection or documenting how packs can extend our pools (see library notes).
6. Existing spec risks stand (fluid override fragility, still reads as Deep Dark, footprint/cavern alignment).

## 6. Decisions made while building Phases 3 and 4

- **Floor follows the world bottom.** `floor` and `top` in `ravine/carve.json` are vanilla `VerticalAnchor`s. The floor is
  `above_bottom: 24`, so it is y=-40 in vanilla (min_y -64) and y=-104 with Larion 4.3.0 (min_y -128), and follows any
  other mod that changes the world height. Verified on both.
- **Cavern is radius 160, height 80** (was 128 / 48), so the city's 116-block reach plus terrain adaptation stays
  inside the dome with about 55 blocks of headroom at its edge. Tunable in the same JSON.
- **City placement** is `overgrown_abyss:ravine_centre`, one start chunk per ravine cell. It extends vanilla's
  random-spread placement because `/locate` special-cases that class. Cells without a ravine, and levels that were not
  carved, produce no start.
- **Pool wiring uses `pool_aliases`.** The vanilla templates hard-code `minecraft:ancient_city/*` pool names in their
  jigsaw blocks, so copies of the pools only take effect through aliases on the structure. Template locations still
  point at the vanilla NBT files.
- **A custom swap processor replaces the spec's vanilla rule processors** for the reskin. Vanilla's rule processor
  resets the whole block state, which would lose stair facing, slab type, wall sides and log axis (about 150 distinct
  states in the templates). `swap_blocks` keeps shared properties; `processor_list` nests the reskin table so it is
  written once. Chest loot still uses vanilla `append_loot` rules.
- **Risk 5 update:** template lookup still follows pack priority, so a pack replacing vanilla ancient-city NBT changes
  our city too. Only pool and processor JSON are isolated. Not checked in-game.

## 7. Status and open issues (2026-10-03)

Run on a fixed seed (20261003) in dev dedicated servers, NeoForge 21.1.252 and Fabric 0.19.5: both boot without errors,
`/locate structure overgrown_abyss:city` finds the city at the ravine centre chunk, and force-loading the area generates 77
pieces with no errors. Identical results on both loaders. With Larion 4.3.0 (NeoForge only) the floor resolves to y=-104
and the city stands on it. The generated chunks contain no deepslate bricks or tiles, sculk or soul blocks, and all 24
chests carry `overgrown_abyss:chests/city`.

Not verified:
- **Nothing was looked at.** No screenshots from the rim, mid-air or floor; the Phase 1 visual gate is still open.
- **Fabric with Larion:** not run. Only the NeoForge Larion jar was available.
- **"Every door opens to a path":** not checked.
- **Lava in the cavern is not solved.** In the vanilla-terrain run, 299 lava blocks sit at or above the floor (y>=-40) inside
  the cavern radius: 73 at floor level, the rest in columns running down the walls from y 24 to 72. With Larion there are
  425 blocks between y -75 and 49, none at floor level. This is lava from surface features or springs flowing in, which
  the aquifer override does not cover (it only handles noise-fill fluids). Needs a fix before the Phase 1 gate can pass.
- **Terrain outside the footprint matching the same seed without the mod:** not compared.
- Loot tables are vanilla plus one jungle pool; the vanilla part still has Deep Dark items (echo shards, disc fragments).
- Sculk patches are removed outright; nothing replaces them yet.

## 8. Next steps

1. Review the rim, mid-air and floor views; tune carve and city numbers.
2. Stop lava flowing into the cavern.
3. Wall styles (Phase 2) and `BiomeInjector`.
4. Extract `ravine-core` into a shared source module when Rift starts.
