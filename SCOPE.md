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

> Superseded by the dome-walls rework below. The code for this round is at git tag `before-dome-rework`.

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

> Superseded by the dome-walls rework below. The code for this round is at git tag `before-dome-rework`.

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

### Size and curve round

Checked with `./gradlew :common:test` and one NeoForge dedicated server boot (seed 162).

| Check | Result |
|---|---|
| Unit tests: sizes skewed small at the time (under 28% of ravines over 250 long, against 37% before); this was later reversed, see the note below the table, about three quarters of ravines bow against their lean, every ravine keeps at least half its wiggle | pass |
| NeoForge boot with the new `size_bias` field, no errors; `/locate structure overgrown_abyss:city` still finds a city (1037 blocks away) | yes |

Not verified:
- **Nothing was looked at in game**, and the new mix was not rendered. Fabric and Larion were not run this round.
- **Ancient city exclusion was tried and removed.** Vanilla's `exclusion_zone` only tests the other set's potential
  chunks, and ancient cities are spaced 24 chunks apart, so with `chunk_count` 16 it forbade our city almost everywhere
  (nearest city 42,137 blocks away instead of 1,037). The cavern biome swap probably already stops a vanilla city starting
  inside the cavern, but that was not tested, and an overlap at the edges is still possible.

Update: the owner then asked to undo this and aim for medium to large chasms. `size_bias` is now 0.5 (the square root of a
uniform draw), which gives about 61% of ravines over 250 long and about 9% under 120; the codec now accepts 0.25 to 8.

### Tiered walls experiment

> Superseded by the dome-walls rework below. The code for this round is at git tag `before-dome-rework`.

Goal from the owner's cross-section sketches: more, but not complete, blocking of the view from the bottom, with the
opening zigzagging as it rises. Tunables are in `walls.tiers` in `carve.json`, so this can be pushed further or backed off
without code changes.

What it does: the shaft above the cavern roof is split into 1 to 3 stacked levels (more on bigger ravines, none under
size 0.3). Each level slides the whole opening sideways by up to 0.9 half widths and sets its width between 0.5 and 1.1
of normal, blending over 4 blocks, so shelves and ceilings appear where levels meet. Neighbouring levels always overlap by
at least half a half width, so the shaft stays one passage. Bridges and ledges follow the shifted walls.

| Check | Result |
|---|---|
| Unit tests: levels stay within limits, neighbours overlap, small ravines get none, an upper level opens sideways while the lower one stays rock there and keeps the centre line | pass |
| Vertical sight lines (roof to rim, sampled, 980 ravines over 150 long): ravines with a fully clear vertical column 979 before, 901 after; share of columns that are clear 39% before, 15% after | measured on the shape alone, no wall noise |
| NeoForge dedicated server, seed 20261003, ravine at 2620,764 (318 long, 3 levels, 1 bridge) generated, no errors | yes |
| Cross-sections of the generated blocks at four places along it show large shelves jutting from one wall, a notch on the other, and the opening drifting sideways with height | seen in rendered slices |

Not verified:
- **Nothing was looked at in game.** The renders are 2D slices of the generated blocks.
- **Fabric and Larion were not run.** On vanilla height the shaft above the cavern roof is only about 50 blocks (roof at
  y=16, surface about y=65), so there is room for 2 or 3 levels at most. Larion's taller world should show the sketches'
  proportions better and has not been tried.
- **A straight shot still exists in about 90% of ravines**, only narrower. Raising `max_shift` or `max_count`, or lowering
  the cavern, closes it further. Whether that is the right balance is the owner's call.
- The sight-line measure is vertical lines only, not views from the cavern floor at an angle.

### Disc round (rooms, second pass)

> Superseded by the dome-walls rework below. The code for this round is at git tag `before-dome-rework`.

The first pass made discs as rock plates standing out from the walls; the owner found they did not read as discs. Discs
are now round rooms cut sideways into the wall from the chasm (`walls.discs` in `carve.json`; omit the block to turn them
off): a flat floor at a level boundary, a domed roof like the main cavern's, centred 0.4 to 0.75 of its radius inside
the wall so the mouth is wide. Radii run 24 to 70 (most modest, a few large) so ruins can be built inside; dome height is
0.45 of the radius but at least 14, and domes stop 12 blocks under the top of the ravine so they stay under the surface.
Several per boundary, floors within 2.5 blocks of the boundary so neighbours overlap at slightly different heights. A room
only opens from the side where the upper level reaches further than the lower one, so it always has rock under it.

| Check | Result |
|---|---|
| Unit tests (47 total): rooms only on shelves and within radius, offset and jitter limits, none without levels; a room has a flat floor, a dome, a round footprint, a wide mouth onto the chasm and stays under the ceiling margin | pass |
| NeoForge dedicated server, seed 20261003, ravine at 993,-785 (299 long, 4 rooms on level 1) generated, no errors | yes |
| Cross-sections show wide, flat-floored horizontal rooms stacked in the left wall at the level heights, reaching 100+ blocks out | seen in rendered slices |

Not verified:
- **Nothing was looked at in game.** Whether each room reads as an independent circle is unknown: in plan slices they
  merge into amoeba-like shapes, because wall noise (up to about 20 blocks) distorts a 24-block room heavily and
  neighbours overlap.
- **Upper rooms mostly do not exist on vanilla height.** The second level's floor is at about y=50 to 62 and domes must
  stop at y=68, so rooms there are skipped when they cannot be 14 blocks tall. Larion has more room.
- **Ravines moved again**: room reach is part of the footprint, so every ravine's position in its cell changed.
- Fabric and Larion were not run for rooms. The micro-biome for the rooms, ruins inside them, support stems and lily-pad
  ends are not started.
- The old rectangular ledges are still generated next to the rooms.
- `dev-datapacks/stronger-tiers` was updated to the new `discs` fields; an older copy of that pack fails to load.

### Dome-walls rework

The owner asked to take a step back: keep the S-curve, drop everything else, and build the walls from overlapping round
disc rooms all the way down, with flat floors. Removed: wall levels (tiers), ledges, bridges, strata, terraces, wall
width wobble, overhang and fine wall noise, and the terrace warp. Their code and tests are at git tag
`before-dome-rework`. What is left: the curved, leaning, S-bowed shaft (narrowing towards the floor), the cavern, and the
disc rooms.

Disc rooms are a pure function of the ravine's hash and a grid position (side, row, slot), so nothing is stored. Rows
start at the cavern roof and go up every `row_spacing` blocks until a dome can no longer be `min_height` tall under the
ceiling margin; slots run every `spacing` blocks along the ravine; odd rows are shifted by half a slot and every room is
jittered, so neighbours overlap. Each room has a flat floor, a domed roof like the cavern's, a radius of 20 to 50 (most
modest, a few large) and its centre 0 to 0.4 of a radius inside the wall. The carve is now `clamp(open / edge_falloff)`
with no noise, so each floor is exactly flat (`edge_falloff` is 8, one noise cell high, so the density interpolation does
not bend it).

| Check | Result |
|---|---|
| Unit tests (35 total): room parameters within limits and deterministic, sizes vary, a room has a flat floor, round footprint, domed roof and stays under the ceiling margin, rooms cover most of the wall (over 60% of points just inside it were open, see Floors and ledges), centre line open for every ravine, nothing opens beyond the cell reach | pass |
| NeoForge dedicated server, seed 20261003, ravine at 983,-712 (372 long) generated, no errors | yes |
| Plan slices of the generated blocks at y=20 to 50 show clearly circular, overlapping bays along both walls | seen in rendered slices |
| Settings tried analytically on the same ravine (open area at y=36): offset 0.4 to 0.75 and radius 22 to 55 gives 79,000 blocks (one large merged cavity); offset 0 to 0.4 and radius 20 to 50 gives 63,000; radius 18 to 40, spacing 55 gives 48,000 | measured; the middle one is the default |

Not verified:
- **Nothing was looked at in game.** The first world I rendered used the deep-offset settings and read as one huge
  scalloped cavity (about 280 across), so I changed the defaults to the shallower ones afterwards; those defaults were
  checked only analytically in plan view, not generated.
- **Headroom:** on vanilla height there are 4 rows between the cavern roof (y=16) and the ceiling margin (y=68); Larion
  has more. Domes stop at `top` minus `ceiling_margin`, so above that the shaft is a plain slot.
- Fabric and Larion were not run. Wall roughness is gone, so surfaces are perfectly geometric.
- Ravine positions changed again (the footprint no longer includes wall noise). Older datapacks no longer load.
- Biome micro-domes, ruins in the rooms, trees, roots and columns are not started.

### Floors and ledges

Owner review of the dome walls: the discs cut into the walls but left no ledges, and the top of a dome cut into the disc
above it. Cause: domes are 14 to 22 tall but rows are 12 apart (and rooms in neighbouring rows overlap in plan view), and
the carve is the union of all rooms, so a lower dome broke through the floor of the room above.

Each room now also has a floor slab put back as rock after the rooms are carved: its round footprint, `floor_thickness`
(4) deep under the floor, kept to `max_lip` (0.4, always below 0.5) of the shaft's width out from the wall. It is subtracted
from the shaft and the rooms but not the cavern. So every floor stays solid under lower domes, and the part of the footprint
past the wall is a ledge sticking into the shaft. The lip is measured from the wall at the sampled point's own place along
the ravine, not the room's: on a curve those differ, and measuring from the room's wall let a wide slab close the centre
line (caught by the centre-line test).

| Check | Result |
|---|---|
| Unit tests (38 total): every room's floor is solid, every room has a solid lip, a lip stops at `max_lip`, centre line open for every ravine, nothing beyond cell reach | pass (`./gradlew build`) |
| NeoForge dedicated server, seed 20261003, 143 chunks force-loaded around the ravine at 981,-695 (372 long), new settings in the log, no errors | yes |

Not verified:
- **Nothing was looked at in game or rendered.** Whether the ledges read well is the owner's call.
- **Open wall coverage dropped:** points just inside the wall that are open went from over 60% to 56% (744 of 1330), so the
  test bar is now 50%.
- **Adjacent floors can sit only 2.4 blocks apart** (row spacing 12, jitter 0.4 either way), closer than a slab is thick,
  so an upper slab can fill a lower room where they overlap. Not tuned.
- Fabric and Larion not run. Top-row lips only appear where the terrain is solid.
- Next idea from the owner: each disc grows a funnel/column below it that runs down until it meets a disc below or terrain.

### Crossing discs and row spacing

Owner review: big discs got cut off short of the far wall, and discs sat too close vertically.

- **Lip cap:** `max_lip` now goes up to 1 (a floor reaching the opposite wall) and ships at 1. At 0.4 a disc's floor was
  cut off 40% of the way across the shaft even when the disc was bigger. Domes were never capped, so only floors changed.
- **Vertical spacing:** new `row_jitter` (fraction of `row_spacing` a floor may drift from its row) replaces the old fixed
  0.8. Neighbouring rows' floors are always at least `(1 - row_jitter) * row_spacing` apart. Shipped `row_spacing` 20 and
  `row_jitter` 0.4, so at least 12 apart (was 2.4), which leaves about 8 blocks of clear height under the next slab.
- **Density packs** in `dev-datapacks/`: `sparse-discs` (spacing 60, rows 28 apart) and `dense-discs` (spacing 36, rows 15
  apart), next to the existing two.

| Check | Result |
|---|---|
| Unit tests: a large disc's floor reaches the far wall at `max_lip` 1 and stops short at 0.4; neighbouring floors keep the minimum gap | pass (`./gradlew build`) |
| NeoForge dedicated server, seed 20261003, 143 chunks around the ravine at 981,-695, new settings in the log, no errors | yes |

Not verified:
- **Nothing was looked at in game or rendered.**
- **The centre line is no longer guaranteed open** at `max_lip` 1 (the unit tests keep their own settings at 0.4). Measured on
  900 sampled ravines with the shipped numbers: 820 have a floor across the centre column at some height, and about 6% of
  sampled heights there are closed. The chasm stays open elsewhere along its length, but a round hole (no length) has no
  other way down. Not checked against the city or descent.
- **Rows:** vanilla height now holds 3 rows instead of 4; Larion has more.
- Fabric and Larion not run.

### Through discs

Owner: let floors run all the way into the opposing wall, and have 10 to 20% of discs reach all the way through the chasm;
sealing to be dealt with by fewer large discs or deeper offsets.

- `max_lip` now goes up to 2 and ships at 2 (1 is the far wall, 2 carries on through it), so a floor is limited only by its
  own footprint.
- **Why a chance, not bigger discs:** crossing depends on the chasm's width at the disc's height, so no single radius range
  gives a steady share. Measured with the old draw: 1 to 2% of discs crossed, and even `max_radius` 70 gave only 6 to 7%.
  New `through_chance` (0.15): that fraction of discs is sized from the width at its floor, `(width + 4) / (1 - min_offset)`,
  placed at `min_offset`, so it reaches 4 blocks into the far wall. `through_max_radius` (100) is a hard limit: a disc that
  would need more stays ordinary. `reach` in the cell-fit check includes it.
- Measured with the shipped numbers on 900 sampled ravines: 16.4% of discs run through on vanilla height (floor -40) and
  16.6% on Larion height (floor -104). A unit test holds the share between 10% and 20%.

Sealing, same 900 ravines, centre column only, at some height (not the whole chasm):

| Setting | vanilla: ravines with a closed centre / closed share of heights | Larion height |
|---|---|---|
| shipped (chance 0.15, offset 0 to 0.4) | 874 of 900 / 6.4% | 900 of 900 / 12.2% |
| chance 0.10 | 842 / 5.8% (11.7% through) | 900 / 10.9% |
| chance 0.15, offset 0.3 to 0.6 | 605 / 3.5% (8.0% through) | 854 / 7.0% |
| no through discs (chance 0) | 707 / 4.1% | 875 / 8.0% |

Most of the sealing comes from the uncapped slabs of ordinary discs, not only the through ones. Deeper offsets cut sealing
but also cut the through share below 10%, so the through share and the sealing need to be balanced together.

Not verified: nothing was looked at in game or rendered; Fabric and Larion not run; the city and descent not checked
against sealing.

### Large discs set back

Owner (after seeing it in game): the sealing is from large discs generating towards the middle of the chasm, so move the
centre of large discs back from it.

- New `large_offset_bonus` (0.45): an ordinary disc's offset (how far its centre sits inside the wall, as a fraction of its
  radius) grows with its radius, by up to the bonus for the largest. A radius 50 disc now sits at 0.45 to 0.85 instead of
  0 to 0.4, and no ordinary disc reaches more than about 31 blocks into the shaft (up to 50 before). The cell-reach check
  includes it, and `max_offset + large_offset_bonus` must stay at or below 0.95.
- **Through discs ignore it** and stay at `min_offset`. Setting a through disc back needs a larger radius to still cross
  (`radius = width / (1 - offset)`), so its floor covers a longer stretch of the centre line, not a shorter one.

Measured on 900 sampled ravines (the 830 with a length of 120 or more), walking the centre line along its length and up from
the cavern roof to the ceiling margin; "over 20% closed" counts ravines where more than a fifth of that line is solid:

| Setting | through share | vanilla height: avg closed / over 20% closed | Larion height |
|---|---|---|---|
| bonus 0 (before) | 16.4% / 16.6% | 13.4% / 30 | 17.9% / 217 |
| bonus 0.3 | 15.5% / 15.3% | 10.1% / 3 | 13.7% / 33 |
| **bonus 0.45 (shipped)** | 15.3% / 15.1% | 9.0% / 1 | 12.2% / 6 |
| bonus 0.6 | 15.3% / 15.0% | 8.4% / 0 | 11.2% / 2 |
| no through discs, bonus 0.45 | 0.1% / 0.2% | 1.6% / 0 | 2.7% / 0 |

What is still closed (about 9 to 12% of the line) is nearly all through discs, which seal where they cross by definition.
Lower `through_chance` for less of that.

| Check | Result |
|---|---|
| Unit tests: offsets grow with radius, no ordinary disc reaches past 31 blocks into the shaft, all earlier tests | pass (`./gradlew build`) |
| NeoForge dedicated server, seed 20261003, 143 chunks around the ravine at 981,-695, new settings in the log, no errors | yes |

Not verified: nothing was rendered or looked at in game by me; Fabric and Larion not run; city and descent not checked.
`wide-bays` uses a bonus of 0.2 because its max offset is 0.75.

### Discs that stay full, staggered sides

Owner (after seeing it in game): some of the largest discs still poke too far into the chasm; patches of sky should still be
visible from the bottom. A disc may cut through the middle but should only overshoot it a little, and it must stay a full
disc, not be cut off. Also: offset the layer heights on the two walls so overhangs form a natural S curve instead of meeting
in the middle. And the largest disc radius should drop to 48.

- **`max_overshoot` (4):** a disc's reach into the shaft is `radius * (1 - offset)`. If that would pass the middle of the
  chasm (half its width at the floor) by more than the overshoot, the disc's offset is raised until it does not, so it is
  set back into the wall. Radius and shape are untouched; nothing is clipped.
- **`side_stagger` (0.5):** the second wall's floors sit that fraction of `row_spacing` higher than the first's, so a row on
  one wall lies halfway between two on the other.
- **`max_radius` 48** (was 50; `wide-bays` was 55). Cell reach now allows two radii behind the wall.
- **Removed:** `max_lip`, `through_chance` and `through_max_radius`. The slab is the whole disc footprint, no cap, and discs
  that cross the chasm to the far wall no longer exist (they could not coexist with a limited overshoot). The earlier
  10 to 20% through share is gone on purpose.

Measured on the 830 sampled ravines of 120 or more length, walking the centre line along its length from the cavern roof to
the ceiling margin:

| Setting | vanilla height: avg centre line closed / levels fully sealed | Larion height |
|---|---|---|
| previous build (through discs, `max_lip` 2) | 9.0% | 12.2% |
| overshoot 4, stagger 0.5 (shipped) | 1.1% / 0 | 2.0% / 0 |
| overshoot 4, stagger 0 | 1.3% / 0 | 2.2% / 0 |
| overshoot 0, stagger 0.5 | 0.8% / 0 | 1.5% / 0 |
| overshoot 8, stagger 0.5 | 1.1% / 0 | 2.1% / 0 |

The stagger barely moves this number, because it measures the centre line, not the overlap between the two walls' discs; its
effect is on how overhangs interleave and was not measured.

| Check | Result |
|---|---|
| Unit tests (42): no disc reaches more than the overshoot past the middle and its radius is unchanged; a set-back disc is a full round floor in every direction; the second wall's rows sit higher by the stagger; every level of a 100+ long ravine has open centre line; all earlier tests | pass (`./gradlew build`) |
| NeoForge dedicated server, seed 20261003, 143 chunks around the ravine at 981,-695, new settings in the log, no errors | yes |

Not verified, and open:
- **Nothing was rendered or looked at in game by me.** Fabric and Larion not run.
- **Short ravines can still have a level fully walled off.** In 900 sampled ravines, 81 failed the "every level has open
  centre line" check; all but one were under 100 long (51 of 57 under 25 long, 18 of 42 of 25 to 50, 11 of 57 of 50 to 75),
  because one disc up to 48 across can cover a short ravine's whole length. The unit test only covers ravines of 100 or more.
- City and descent not checked against sealing.

### Minimum length 100

Owner: no ravine shorter than 100 blocks. `length.min_inclusive` is now 100 in the mod's `carve.json` and all dev packs (the
size draw still runs from the minimum to 400). The codec still accepts 0, which makes a round hole; nothing ships it.

This closes the short-ravine gap noted under "Discs that stay full": on 900 sampled ravines with lengths from 100 to 400
(shortest sampled 112), no level is fully walled off along the centre line, and the average centre line closed is 1.6% at
vanilla height and 2.8% at Larion height, up from about 1.1% and 2.0% over the earlier long-ravine sample only. NeoForge
dedicated server, seed 20261003, three ravines near the origin (379, 147 and 383 long), no errors.

Not verified: nothing rendered or looked at in game; Fabric and Larion not run. On vanilla height each wall gets only 2 rows
of discs (the server log says "2 rows"; I wrongly said 3 earlier), because the cavern roof sits at y=16 and the ceiling margin
at y=68. Larion has more.

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

### Structures that intersect the chasm (owner idea)

- **Idea:** a stronghold (or any buried structure, including ones added by mods) that cuts into the chasm currently hangs
  in open air. Wrap it so it looks like part of the chasm: a rock shell around the pieces, with ledges or bridges tying
  it to the wall. A stronghold breaking into the chasm could be a good piece of flavour if it looks right.
- **Estimate (not prototyped):** about one session for the shell alone; roughly three to four sessions with roots, ledges
  and bridges, including tuning rounds.
- **What it needs:**
  - A second density hook after vanilla's structure terrain term, because our carve can only remove rock.
  - Reading the structure starts near each chunk at noise time (vanilla's terrain adaptation already does this), limited
    to structures with a buried or beard adaptation so surface builds are left alone.
  - A root from the shell to the nearest wall, using the ravine's distance estimate, plus a ledge or bridge.
- **Testing:** `/place structure minecraft:stronghold` can force one into a ravine; both loaders and chunk generation
  time need checking.
- **Related, unresolved:** a vanilla ancient city overlapping our cavern. See the Size and curve round above.
- **Frequency:** the owner has now seen strongholds cut into the chasm three times, possibly because of stronghold
  overhaul mods, so it is more common than first thought; this raises the priority of the shell-only version.

### Stems and disc platforms (owner idea)

- **Idea:** large circular discs, overlapping and at different heights, as a main chasm feature but not covering the
  walls. Some stand on thin stems, and later vines could end in huge lily-pad-like platforms in mid-chasm. Waterfalls
  between discs (like Streams Reflowed) come after the shape is settled. Reference mood: Made in Abyss, Hell's Paradise.
- **Notes:** the Phase 1 sketch shapes (stacked tiers, fluted faces) are the first step; discs would be a new carve
  or placed-rock term alongside bridges and ledges, in the same analytic style so they stay seed-stable.

### When this is picked up

1. Read each mod's real data files and loader support for 1.21.1 first; record versions in `docs/PINS.md` as optional
   test targets, not dependencies.
2. Decide per mod whether it is a pure datapack integration (preferred) or needs Java.
3. Add a check that the build and both loader servers still boot **without** the mod.
