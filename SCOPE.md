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
- **Cavern is radius 136, height 56.** First pass 128/48 felt cramped, second pass 160/80 too big (owner review). The
  city spans about 220 blocks, so 136 leaves a margin and the dome is 33 high at the city's edge, taller than every piece
  except the 31-tall centre. Tunable in the same JSON.
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

- **Ravines open on land only (owner review).** Each cell is checked once: five columns along the centre line (both
  ends, midpoints and centre) are sampled at sea level through the level's biome source, and the ravine is dropped if any
  is in `#overgrown_abyss:ravine_forbidden` (ocean, river, beach by default; editable in the datapack). Biome-based so it
  follows whatever biome source the terrain mod brings. Costs ravine count: on seed 20261003 only 3 of 7 cells near spawn
  survive in vanilla terrain and 1 of 7 under Larion, whose spawn area is mostly ocean. Raise `chance` to compensate.
- **The cavern is a lush caves biome (owner review).** `environment.cavern_biome` in `ravine/carve.json`. The biome
  resolver is wrapped where chunks fill their biomes (`NoiseBasedChunkGeneratorMixin`), so the level's biome source is
  untouched and only the dome plus a 4-block margin (floor, walls, roof surfaces) changes. Lush caves features then place
  themselves, because decoration runs for any biome in the source's possible set and vanilla includes lush caves.
  Omit `cavern_biome` to keep the natural biome. If a terrain mod's biome source lacks lush caves, its features are
  skipped but the biome still applies.
- **Natural lava and water may meet the cavern (owner review).** The aquifer override now applies only inside the carved
  volume, found by evaluating the carve, instead of every open block in the ravine's columns. The old behaviour also
  drained oceans and emptied lava lakes beside and below the ravine. Surface water now pours down as waterfalls.
  Nothing smooths these meetings beyond the carve's 6-block edge falloff.

- **Ravine shape variety (owner review: walls too regular, one texture all the way down).** All in
  `ravine/carve.json`, which is now nested (`walls`, `curvature`, `bridges`, `environment`) because the settings codec is
  at its 16-field limit.
  - *Size:* each ravine draws one size from 0 to 1 and takes that fraction of the way from the smallest to the largest,
    so long ravines are also wide. Largest is the old maximum (400 long, 110 wide); smallest is a round hole 24 wide
    directly above the cavern centre (`length.min_inclusive` 0). The cavern and city do not change with size.
  - *Curves:* in plan view the centre line bends (parabola) and wiggles (sine), both scaled by size; with height the whole
    shaft leans and bows sideways, zero at the cavern floor so it always meets the cavern.
  - *Overhangs and texture:* each wall swells and narrows with height at its own rhythm (`width_wobble`), a second broad
    noise with a stretched vertical axis (`overhang_*`) pushes large lumps and undercuts out of the wall, and terrace
    ledge heights wander along the ravine (`terrace_warp`) instead of forming contour lines. Noise may narrow a wall by at
    most 60% of the half-width, so the centre line always stays open.
  - *Bridges:* up to 3 arches per ravine, only on ravines at least 45% of the way to the largest (`bridges.min_size`), so
    small holes stay open. They are carved out of the open volume as rock, flat on top and thicker at the walls. Because
    the carve is combined as `min(original, carve)` it cannot add rock where the terrain itself is open air, so
    `bridges.max_height` (0.75 of the span from cavern roof to rim) must stay below the terrain surface.
  - *Cost:* `maxReach` now includes every curve and the wall wobble (about 485 blocks for the shipped numbers), so cells
    must be at least twice that. The shipped 2048 is fine.

- **Wall form: ledges and strata (owner review: walls too rounded and generated).** Walls were one smooth tube with noise
  on top. Now:
  - *Ledges* (`ledges` in `ravine/carve.json`): flat-topped rock shelves standing out from the walls into the shaft,
    thickest at the wall and thinner at the lip, 14 to 46 long and 6 to 16 deep, about 3.5 per 100 blocks of ravine
    length, each turned up to 30 degrees off the wall so ledges at different heights overlap and cross. A ledge reaches
    24 blocks back into the rock so it always joins the wall. Whatever its size it is shrunk so it never reaches more than
    45% of the way across, so the shaft stays open. Small ravines (under 15% of the way to full size) get none.
  - *Bridges land on ledges:* every bridge gets a ledge on each wall at exactly its top height and position, so a bridge
    connects two level, walkable shelves. Bridges and ledges share one height function (`RavineShape.featureTop`).
  - *Strata* (`walls.strata_*`): the wall is cut into 7-block layers, each pushed in or out by its own amount that
    varies along the ravine, which gives vertical faces joined by flat steps of varying width. Layer boundaries drift with
    the old terrace warp so they do not line up along the ravine.
  - *Calmed the round shapes:* broad noise 24 to 10 blocks, width wobble 0.22 to 0.12, fine noise 8 to 3 blocks, and the
    evenly spaced terraces are off (`terrace_step` 0; set 12 to bring them back).
  - Like bridges, ledges can only add rock where the terrain is solid, so `ledges.max_height` (0.75) must stay below the
    terrain surface.

## 7. Status and open issues (2026-10-03)

Owner reviewed the first build in game: the chasm and the city look good. This round (land-only, smaller cavern, lush
caves, natural fluids) was checked in dev dedicated servers on a fixed seed (20261003) after a full `./gradlew build`.

| Check | NeoForge 21.1.252 | Fabric 0.19.5 | NeoForge + Larion 4.3.0 |
|---|---|---|---|
| Boots, no errors | yes | yes | yes |
| Ravines kept near spawn (of 7 hashed) | 3 | 3 | 1 |
| `/locate structure overgrown_abyss:city` | finds it | finds it | finds it |
| City generates, pieces | 77 | 77 | 78 |
| Floor y | -40 | -40 | -104 |
| Chests with our loot table | 28 | 28 | 26 + 1 ice box |
| Deepslate bricks, sculk | none | none | none |
| Lush caves biome present, vegetation present | yes | yes | yes |

Biome probes on the vanilla NeoForge ravine: lush caves from the floor to the roof and 130 blocks out, not 40 blocks above
the dome, not 8 blocks under the floor. The six cells rejected under Larion are all ocean (probed at y 63 and 100).
In the Larion cavern, water and lava are 2.3% of the cavity: floor pools plus thin waterfalls from the surface, not a
flooded cavern.

Not verified:
- **Nothing was looked at this round.** No screenshots; the Phase 1 visual gate is still open.
- **Fabric with Larion:** not run (only the NeoForge Larion jar was available).
- **"Every door opens to a path":** not checked.
- **A user's ice ocean seed:** not reproduced. Ocean rejection was checked on generated seeds, not on that world.
- **Terrain outside the footprint matching the same seed without the mod:** not compared.
- **River-centred ravines:** two of the vanilla rejections were centred on rivers. If you would rather keep those, remove
  `#minecraft:is_river` from the tag.
- Loot tables are vanilla plus one jungle pool; the vanilla part still has Deep Dark items (echo shards, disc fragments).
- Sculk patches are removed outright; nothing replaces them yet.

### Shape variety round

Checked after a full `./gradlew build` (33 unit tests) and in dev servers.

| Check | Result |
|---|---|
| Unit tests: centre line open from floor to rim for every sampled ravine, nothing opens beyond the reach cells are sized for, size runs from a round hole to the configured largest, bridges and curves stay within limits | pass |
| NeoForge boot, seed 20261003 and seed 162, no errors | yes |
| Fabric boot, seed 20261003, city generates under the 10-long, 26-wide hole (82 pieces), no errors | yes |
| In-world: the 346-long ravine (seed 20261003) is a long curve with varying width and wall shelves | seen in rendered block slices |
| In-world: the 390-long, 2-bridge ravine (seed 162) has a rock band crossing its full width | seen in rendered block slices |
| In-world: the hole's predicted, leaning centre line is air at all 10 probed heights from y=-30 to 60; the block straight above the cavern centre is rock | yes (Fabric, `execute if block`) |

Not verified:
- **Nothing was looked at in game.** The renders are 2D slices of the generated blocks, not screenshots.
- **Bridge arch shape:** a bridge crossing was detected and a rock slab is visible in a slice, but the arch profile
  (thin in the middle, thick at the walls) was not confirmed block by block.
- **Larion:** not run this round, so the new shapes have not been seen on Larion terrain.
- **Fabric** was only run on the hole seed, not on the bridged seed.
- **Frequency of extremes:** size is uniform in 0 to 1, so about half of ravines are under half size. Not tuned.
- The tiny hole can only be as deep as the terrain is high; on low ground it is a short shaft.

### Wall ledge round

Checked after a full `./gradlew build` (40 unit tests) and in dev servers on seed 162 (the 390-long ravine with 2 bridges).

| Check | Result |
|---|---|
| Unit tests: ledge is level on top, joins the wall, thins towards the lip, stops at its length and depth, turning it reaches new points, only on its own wall; a bridge is level with its two ledges; no ledge ever reaches the centre line; holes get no ledges | pass |
| NeoForge and Fabric boot, generate the whole ravine (NeoForge) and its centre (Fabric), no errors | yes |
| City intact under the new walls (81 pieces on Fabric) | yes |
| Cross-sections show stepped, notched walls with flat shelves sticking into the gap | seen in rendered slices |
| Wall elevation (position of each wall by height along the ravine) is built from flat-faced panels with level edges, roughly 20 to 30 blocks wide and 6 tall | seen in a rendered map |
| About 12% of wall samples step 4 or more blocks between adjacent heights, 5.5% step 8 or more | measured |

Not verified:
- **Nothing was looked at in game.** The renders are 2D views of generated blocks. Whether it reads as natural, and whether
  the ledges are wide and flat enough, is the owner's call.
- **Ledge count and width are not tuned.** On this seed the terrain surface is about y=65, so only about 40 blocks of wall
  stand above the cavern roof; ledges and strata compete for that space. Taller worlds (Larion) have more room and were not
  run this round.
- **Criss-crossing in plan view** was not measured; stacking at different heights is by construction, crossing relies on
  the 30 degree turn.
- No baseline measurement of the previous walls, so the step statistics have nothing to be compared to.

## 8. Next steps

1. Review the rim, mid-air and floor views; tune carve and city numbers.
2. Wall styles (Phase 2) and `BiomeInjector`.
3. Decide whether lush caves features on the city floor suit the look, or whether the city should keep its own ground.
4. Extract `ravine-core` into a shared source module when Rift starts.

## 9. Future additions (owner wishlist)

Nothing here is started. The first two items are third-party mods; the rules in `../AGENTS.md` apply, so they may only
ever be **soft, optional integrations**. Overgrown Abyss must load and work without them, and must not declare them as hard
dependencies. Everything below is the owner's description; no mod APIs, data formats or versions have been looked up yet
(treat as `[recall; verify]` and check against the real jars before building).

### Streams Reflowed

- **Why:** the owner loves the mod. Right now it looks weird with our ravine, but integrated well it could look great.
- **Likely meeting points to investigate:** how its streams and waterfalls behave where they meet the carved walls and the
  cavern edge; whether they can feed waterfalls into the ravine on purpose rather than by accident (surface water already
  pours down the walls, see section 6).
- **Open questions:** does it work with our density wrap and the Larion terrain; does it need anything on the NeoForge
  and Fabric builds separately (portability matrix); can it be tuned from a datapack.

### Epic Structures (large jungle temples)

- **Why:** its large jungle temples look great. The idea is to merge their paths and jigsaw pieces with our jungle
  reskin of the Ancient City, so the city grows temple districts and approach paths instead of only reskinned vanilla
  pieces.
- **Likely approach:** add their pieces to our `overgrown_abyss:city/*` template pools only when the mod is present,
  using the same pool-alias and processor-list setup as the vanilla city. Candidate routes are Lithostitched-style pool
  injection (already held in reserve in section 3) or a loader-conditional datapack file in each loader module. Our
  reskin processors would then need to tolerate or retheme their blocks.
- **Open questions:**
  - Are their templates jigsaw pieces with connectors we can join to ours, or standalone structures?
  - Do their paths use the same jigsaw naming (`connect_*`, `entrance_*`)? If not, we may need adapter pieces.
  - Licensing and redistribution: reference their templates by ID, never copy them (same stance as vanilla).
  - Same-mod risk as section 5, risk 5: if another mod replaces their templates, we inherit the change.

### Ledge dressing: micro-biomes and ruins (owner idea)

- **Idea:** the wide, flat ledges are natural places for small custom micro-biomes and for ruins (this is the Phase 5
  wall-ruins idea in `03-abyss.md`, with ledges as the anchors).
- **What exists:** each ravine's ledges are deterministic data in `RavineCell.ledges()` (position along the ravine, wall,
  height fraction, length, depth, thickness, turn), and `RavineShape.featureTop` turns a height fraction into a world Y for
  the level. A later structure type could read them the way the city structure reads the cell centre today.
- **What it needs:** a way to find a ledge's actual surface after wall noise (the ledge is nominal; strata and noise move
  its edge), a decision on which ledges get what, and the custom buildings from the hanging-temples item below.
- **Open questions:** micro-biomes by biome override (as the cavern does for lush caves) or by placed features only;
  whether bridge-landing ledges get ruins first, since they are the walkable ones.

### Hanging temples (built into the mod)

- **Idea:** upside-down temple buildings hanging from overhangs and the cavern roof, like the Western Air Temple in
  Avatar: The Last Airbender. Unlike the two mods above this is not an integration: it needs buildings we author
  ourselves, shipped inside Overgrown Abyss, vanilla blocks only.
- **Fits the existing plan:** this is a variant of Phase 5 (wall ruins in `03-abyss.md`: jigsaw structures anchored on
  ledges, one shared architectural vocabulary, own loot tables). The difference is that the anchor is a ceiling, not a
  floor, so pieces are built to hang and grow downwards.
- **What it needs that does not exist yet:**
  - New NBT templates, built in game and saved with structure blocks, or generated. The vanilla city only reuses vanilla
    templates, so this would be the first set of our own pieces, and the jungle palette from the city reskin is the
    obvious starting vocabulary.
  - A way to find ceilings. The cavern roof is computable from the shape (`RavineShape`, like the floor is today). Wall
    overhangs come from terraces plus wall noise, so they are less predictable and may need a surface scan at generation
    time.
  - Support for hanging pieces: chains, rope, root or vine supports so they read as hanging rather than floating.
- **Open questions:**
  - Cavern roof only, ravine wall overhangs only, or both?
  - Reachable from the city (bridges or stairs), or visible-only set dressing?
  - Loot on these, or purely visual?

### When this is picked up

1. Read each mod's real data files and loader support for 1.21.1 first; record versions in `docs/PINS.md` as optional
   test targets, not dependencies.
2. Decide per mod whether it is a pure datapack integration (preferred) or needs Java.
3. Add a check that the build and both loader servers still boot **without** the mod.
