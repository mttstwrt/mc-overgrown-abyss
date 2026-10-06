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

### Fewer discs and stems

Owner: cut the disc density by about 30%, and add the inverted funnel stems from the earlier idea, never thinner than a
5-block circle.

- **Density:** `spacing` 45 to 64. Rooms in the 900 sampled ravines: 19,759 to 13,868 at vanilla height (-29.8%) and 59,280
  to 41,524 at Larion height (-30.0%). The dev packs' spacings were scaled by the same factor. Row spacing is unchanged, so
  vanilla height still has 2 rows.
- **Stems** (superseded by "Stems at the disc centre" below; this is the first version): each disc gets a stem under the visible part of its
  ledge, centred on the middle of the stretch that sticks out into the shaft, so it is not hidden inside the wall. The top
  of the funnel is as wide as fits under the ledge (half the disc's reach, at least the stem radius); it narrows by one
  block of radius per `funnel_slope` blocks of height down to a column of `stem_radius` (2.5, so 5 blocks across), which
  then runs straight down. The stem is a vertical column in the world, placed using the shaft's lean at the disc's floor.
- **Where it stops:** at the underside of the highest lower disc whose footprint holds its axis, or at the cavern or
  terrain (the cavern is not subtracted from, so a stem is cut off by its dome). A stem never fills a room: rooms are
  carved after the stems, and the slabs are put back last.
- **How it is done:** the carve can only remove rock, so the stem is rock put back inside the shaft's air, like the slabs.
  It is only searched for within the carve's falloff of the shaft, which gives identical values to the full search.
- **Cost:** the density function takes about 1.2 microseconds per sample with stems against 0.9 without (a first version
  without the early skip took 2.4); measured on one ravine in a unit-test harness, not in a world generation profile.

| Check | Result |
|---|---|
| Unit tests (45): never thinner than 2.5 radius at any depth and widest at the ledge; solid in the full signed distance where no room is; ends under the first lower disc that holds its axis; all earlier tests | pass (`./gradlew build`) |
| Centre line (830 ravines): average closed 1.4% vanilla and 2.4% Larion with stems and 30% fewer discs; no level fully sealed | measured |
| NeoForge dedicated server, seed 20261003, 143 chunks around the ravine at 983,-712: 3 stem points computed from the code are solid blocks, 3 open-air controls beside them are air | yes |
| Boot, new settings in the log, no errors | yes |

Not verified:
- **Nothing was looked at in game or rendered.** The probe was six blocks in one ravine, at mid-funnel depth, not a column
  checked from top to bottom; the thinnest part of a stem (2.5 radius) was only tested in the distance function, not as
  blocks. Stems are sampled in noise cells 4 blocks wide, so thin columns may come out slightly uneven.
- A stem hangs only in the shaft's air; where terrain was already open (a cave) the carve cannot add rock, so it can be
  missing there. Top-row stems only appear where the terrain is solid.
- Fabric and Larion not run; the city and descent not checked.
- The stem axis is at the middle of the ledge, not the disc's centre; tell me if you want it elsewhere, or lily-pad
  platforms on stems.

### Stems at the disc centre

Owner: the stem belongs at the centre of the disc it supports, may run all the way down to the chasm floor, and should follow
a curve like -1/x, never thinner than a 5-block circle.

- **Axis:** the disc's own centre (not the middle of the ledge). Discs are centred inside the wall, so the thin column is
  mostly in the rock; what shows in the shaft is the flared top of the stem under a ledge.
- **Profile:** `radius = max(stem_radius, disc radius * funnel_scale / (depth + funnel_scale))`, with depth measured down from
  the underside of the slab. It starts as wide as the disc, halves `funnel_scale` (6) blocks below, narrows ever more slowly,
  and is capped at `stem_radius` 2.5 (5 across). `funnel_slope` is replaced by `funnel_scale`.
- **Reach:** the stem is not stopped by lower discs any more; it runs down to the chasm floor, cut only by rooms (a stem never
  fills a room) and the cavern (not subtracted from, so the city's cavern is untouched). The "stops at the first lower disc"
  search and the world-position helper it needed are gone.

| Check | Result |
|---|---|
| Unit tests (45): the profile starts at the disc's radius, halves at one scale, never widens, narrows fast then slowly and never goes below 2.5; every stem is at least 2.5 radius at the chasm floor even under lower discs; a flared stem is solid rock in the shaft under a ledge; all earlier tests | pass (`./gradlew build`) |
| Stems take up 1.3% of the shaft's cross-section samples at vanilla height and 2.6% at Larion height (scale 6; 0.7% / 1.4% at scale 3, 2.4% / 5.0% at scale 12) | measured |
| Centre line: average closed 1.0% vanilla and 1.9% Larion, no ravine over 20% closed, no level fully sealed | measured |
| Density function cost: about 1.2 microseconds per sample, the same as before | measured in a unit-test harness |
| NeoForge dedicated server, seed 20261003: 5 stem points found inside the shaft by the code are solid blocks, 3 open-air controls are air; boots with no errors | yes |

Not verified: nothing was looked at in game or rendered; the checks are single-block probes in one ravine; Fabric and Larion
not run; where terrain is already open the carve cannot add the rock. Because the stems sit at the disc centres, inside the
wall, they change the shaft only slightly (about 1 to 3% of its volume); a larger `funnel_scale` makes the flare bolder.

### Setback by size

Owner: keep larger discs further set back, while smaller ones may sit closer to the middle.

A disc reaches `radius * (1 - offset)` into the shaft. With the old numbers (`max_offset` 0.4, `large_offset_bonus` 0.45) the
reach was almost the same for every size (mean 17 to 19 blocks from the smallest to the largest quarter), because a bigger
disc reaches further for the same offset. Now `max_offset` is 0.1 and `large_offset_bonus` 0.8 (their sum stays under 0.95):

| Disc radius | mean offset | mean reach into the shaft (Larion height, 30x30 cells) |
|---|---|---|
| 20 to 27 | 0.14 | 19.7 blocks |
| 27 to 34 | 0.35 | 19.7 |
| 34 to 41 | 0.55 | 16.8 |
| 41 to 48 | 0.75 | 11.0 |

I first tried a square-root curve for the setback so reach would fall steadily from the smallest disc; it also pulled the small
discs back (mean reach 15 to 16) and was dropped. A reach that falls from the very smallest size needs a setback that grows
faster than a straight line, which sets small discs back too, so the small and mid-sized discs keep a plateau instead.
The overshoot limit (4 blocks past the middle) is unchanged. Closed share of the centre line: about 1.3% (down from 1.9%),
no ravine over 20% closed. `wide-bays` and `small-bays` keep their own offsets; `sparse-discs` and `dense-discs` follow the
new defaults.

| Check | Result |
|---|---|
| Unit test: the largest discs have a mean offset over 0.4 higher and a mean reach over 5 blocks lower than the smallest; all earlier tests | pass (`./gradlew build`) |
| NeoForge dedicated server, seed 20261003, 143 chunks, new settings in the log, no errors | yes |

Not verified: nothing rendered or looked at in game; Fabric and Larion not run. With big discs set deeper the stems' flared
tops show less in the shaft than before.

### Stems win over domes

Owner (with a sketch): the stems were being cut off by the domes of rooms underneath. The sketch shows T-shaped stems standing
in the rooms below, a thin cap with a curved fillet narrowing to a column that lands on the floor beneath.

- **Order of the carve:** rooms first, then the slabs and the stems are put back as rock (`max(max(shaft or dome, -slab), -stem)`).
  Before, rooms were carved after the stems, so a dome erased a stem where they met. The cavern is still not subtracted
  from, so the city's cavern is untouched.
- **Where a stem ends:** on the floor slab of the highest lower disc whose footprint holds its axis (it ends flush under that
  slab, so it merges into the floor), or at the chasm floor if there is none. This brings back the "lands on the disc
  below" stop, which I had removed one change earlier; the sketch shows stems ending on the floor below.
- **Flare:** `funnel_scale` 6 to 1.5. At 6 a stem is a wide cone, which would fill the rooms now that it is no longer cut by
  them; at 1.5 it is a short fillet (radius under 7 by 10 blocks down) that then runs as a thin column.
- The "skip the stem search" shortcut now skips only where both the shaft and the rooms are further than the carve's falloff.

Measured with the shipped numbers on 900 ravines: centre line closed 0.6% (vanilla height) and 1.2% (Larion), no level sealed,
about 1.2 microseconds per density sample (unchanged). The share of points just inside the wall that are open fell from about
56% to 46%, because stems and slabs now take rock out of the rooms; the unit test's bar for that is now 40%.

Rendered cross-sections of the generated shape (white air, black rock) show a stem passing through a lower room and ending on
its floor slab, and curved fillets under slabs. They do not look like the sketch: our rooms are flatter (dome height 0.45 of the
radius) and each stem sits at its disc's centre, which is inside the wall, so mostly only the fillet shows and the thin
column is in the rock.

| Check | Result |
|---|---|
| Unit tests (47): the profile is a short hyperbolic fillet capped at 2.5; a stem with nothing under it runs to the chasm floor; a stem is solid rock inside the dome of the room it passes through; it ends at the slab of the disc it lands on; a flared stem is solid under a ledge; earlier tests | pass (`./gradlew build`) |
| NeoForge dedicated server, seed 20261003: boots, 143 chunks generated, no errors | yes |

Not verified: nothing was looked at in game (the renders are 2D slices of the density function, not generated blocks); the
block probe of stems was not repeated for this change; Fabric and Larion not run.

### Cone experiment (branch `cone-experiment`)

Owner's idea: instead of a long ravine, a single hole like the Abyss in Made in Abyss, a large cone that ends at the radius of the
city dome, filled with large flat-topped stone structures like the discs on stems, which can stack on each other. A clear line of
sight straight down the middle is guaranteed. The ravine version is kept at git tag `ravine-discs-and-stems`.

How it is built (an optional `cone` block in `carve.json`, so cells, land check, cavern, city placement and the density hook are
reused; without the block nothing changes):

- **Cone:** `radius(y) = top_radius + (cavern_radius - top_radius) * (1 - t)^flare`, `t` the height from floor (0) to top (1), so it is as
  wide as the cavern at the floor and narrows upward (shipped test pack: top radius 45, flare 1.6). The cavern dome is kept, so the
  bottom of the cone is the dome's roof, which is the slightly curved floor in the sketch. Round holes are what a ravine of length 0 is,
  so the cone pack sets `length` to 0.
- **Structures** (`ConeStructures`): free-standing caps with stems, in layers from `base_clearance` above the cavern roof up to `ceiling_margin`
  below the top, `layer_spacing` apart, with slots round each layer's ring (`spacing` blocks of ring per structure). A cap is a flat-topped
  disc (`cap_thickness` thick, radius `min_radius` to `max_radius`, at most 0.55 of the room between the clear cylinder and the wall) on a stem
  as wide as the cap that narrows along the same hyperbola as the ravine stems, to `stem_fraction` of its radius (at least 5 blocks across). The cap
  may sit a little inside the wall. With chance `stack_chance` a structure is straight above the one in the layer below, so its stem lands on that
  cap (stems end flush under the cap they land on, otherwise on the cavern's dome).
- **Clear line:** no structure's footprint is ever inside the cylinder of `clear_radius` (8) around the axis, and the rock function is forced
  to zero inside it, so the line is guaranteed (a test samples every cell).
- **Adding rock:** the ravine's slabs and stems are put back with `min(original, carve)`, which cannot add rock where the terrain is air.
  The first world probe showed a cap with a hole where a natural cave was (`min` cannot fill it), and nothing can stand above the surface.
  So a cone has a second output, `RavineCarve.rock()` (same density function type, 1 inside structures and -1 elsewhere), and the final density
  is `max(min(original, carve), rock)`. The rock is counted only inside the cone and outside the cavern.

| Check | Result |
|---|---|
| Unit tests (53): a clear line of sight through the middle of 144 cones at every height; the cone is as wide as the cavern at the floor, narrows to the top radius and never widens upward; nothing opens beyond the reach; caps are solid out to a flat top; a stacked structure's stem lands on the cap below; the cone block's codec and validation; all earlier ravine tests | pass (`./gradlew build`) |
| NeoForge dedicated server, seed 20261003, a new world with the `cone` pack, 240 chunks around the cone at 950,-457: 36 of 36 cap probe points solid, 12 of 12 cap tops open, 3 of 3 stem points solid, 5 of 5 points on the axis open | yes |
| Before the rock function: 28 of 33 cap points solid; the 5 misses were one cap crossed by a natural cave | measured, fixed |
| Density function cost (carve plus rock) | about 0.56 microseconds per sample |
| Rendered cross-sections of the density function (not generated blocks) | stemmed caps stacking down to the dome with an open centre |

Not verified: nothing was looked at in game; Fabric and Larion not run; whether the ancient city still generates intact under the cone was not checked
(the dome and city placement are unchanged); the structure counts, sizes and layering were not tuned (vanilla height has only 2 layers); caps are
flat-bottomed, not rounded as in the sketch; a 6-thick cap lost parts to the noise cells' 8-block height, so caps are 12 thick in the pack, and the
ravine's 4-thick floor slabs may have the same weakness (not checked).

### Disc materials and hanging discs (branch `cone-experiment`)

Owner: wants full control over what a disc is made of (in principle also for a disc above ground), and stems that can go up as
well as down, so discs can hang. Later each disc is to get its own micro-biome dome, with control over vegetation colour, foliage,
ground material and structures, and rarity by where the disc sits. That is the next round; notes for it are at the end of this section.

Decided with the owner: keep the terrain's rock under a palette pass for now; a hanging disc has a root from the ceiling, wide at
the ceiling and thin at the platform; only the cone hangs discs; a palette sets blocks by part, in depth layers, with mixes and
patches; coded patterns (honeycomb cells, geode shells) wait for the micro-biome round.

This builds on the uncommitted rework that gave the ravine and the cone the same discs (`Disc`, `DiscShape`, `DiscLayout`, `Discs`;
`ConeStructures`, `RavineDiscs` and `RavineStems` are gone). That rework has no section of its own here.

- **Correction to earlier notes: the carve is evaluated per block, not per noise cell.** In 1.21.1 `NoiseChunk` wraps the final density
  in `cacheAllInCell`, which fills every block of a cell, and our carve and rock sit outside vanilla's `interpolated` marker. So the
  notes above that blame 4-wide or 8-high noise cells (uneven thin stems; the 6-thick cap that "lost parts", which is why the cone pack's
  caps went to 12) have the wrong cause. The real cause of the damaged cap was not found; cave carvers are a guess. The remark in
  `RavineSettings` that `edge_falloff` should be at least a noise cell high is probably outdated for the same reason; not tested.
- **Each disc's stem is worked out once.** Where a stem ends depends on the discs below it. That was searched on every sample near
  a stem; now a layout resolves it when it is built and each `Disc` carries its `Support`: `Standing(bottom)` or `Hanging(anchor, top)`.
  `DiscLayout` is now just `discs()`. No change to the shape.
- **Fixed on the way:** the world-load log line counted a cone's layers by building a cone layout without a cell, which throws as soon
  as a cone has one layer. Layers are now counted from the settings alone.
- **Hanging discs (cone only).** New `cone.hang_chance`: that share of discs is drawn to hang, and one hangs only if it has a ceiling:
  the underside of the lowest platform above that holds its axis (the root ends flush there), or else the cone's wall above its
  axis, or its own dome's apex if the axis is already inside the wall. A ceiling closer than `min_height` is no room to hang in, a wall
  anchor within `ceiling_margin` of the top is not trusted to have rock behind it, and a disc nearer the middle than `top_radius` has
  only sky over it; all of those stand on a stem as before. A hanging disc has no stem.
- **Root shape:** `discs.root_spread` (3) and `discs.root_scale` (4). The root is `root_spread` stem radii wide at its anchor (never wider
  than the disc), half that `root_scale` blocks away, never thinner than a stem. Against the sloping wall it narrows again above the
  anchor and ends one root radius above it, so there is no flat cut in open air. It is rock added by the same rock function as stems, so
  the "only where the carve opened the ground" and clear-cylinder rules apply to it unchanged.
- **Palettes:** optional `disc_palettes` in `carve.json` (`DiscPalette`). Each has a `weight`, `top` and `underside` layers counted in from
  the platform's faces, a `body`, and a `stem` with `surface` layers and a `core`; a root counts as stem. Each block is a vanilla
  block-state provider. A part left out keeps the terrain's rock, and with no palettes nothing is painted (the mod's own `carve.json`
  has none). A disc takes a palette by weight from its cell's hash and its index in the layout; the next round replaces that draw.
- **The paint pass** (`DiscPainter`, hooked at the end of `NoiseBasedChunkGenerator.applyCarvers` in the existing mixin class): each
  chunk overwrites the blocks of its discs.
  - Platforms are painted over their whole round footprint, including the part inside the wall under the dome, so a room's whole floor
    takes the palette and natural cave holes in it are filled.
  - Stems and roots are painted only where the terrain made them.
  - Stems go first and platforms second; where two platforms overlap, the later one in the layout's order wins.
  - Why there: carving runs for every chunk whatever its biome, it is the last step that only touches its own chunk, and every
    neighbour's features wait for it. So nothing a feature placed is ever painted over, the result does not depend on the order
    chunks generate in, and surface rules and carvers cannot undo it. Block writes at that stage update the worldgen heightmaps.
- **Limits:** a palette block that needs a block entity is placed without one. Vanilla's later steps still treat the result as terrain:
  ore and stone blobs replace palette blocks that are in their replaceable tags (seen: an andesite blob in a stone stem), and the
  surrounding biome decorates disc tops (seen: snow layers on platforms under the open cone, leaves over a mossy platform in the ravine).
  Stamping a biome over each dome, next round, is what keeps the surrounding biome's features out.
- **Seen in the probes, for the owner to judge in game:** two platforms at nearly the same height can overlap, and with different
  palettes the later one visibly cuts into the earlier (found in the cone: a concrete platform two blocks above a mossy one).

| Check | Result |
|---|---|
| Unit tests (79): a layout's discs carry where their stems end; root profile and its limits; a disc drawn to hang hangs only with a ceiling and never under open sky; none hang at chance 0; a root is solid from platform to anchor and the disc has no stem; the line of sight stays clear with every disc drawn to hang; a point's part and depth; layer choice, a part left out, weights; palette JSON round trip; the blocks of a chunk (whole platform footprint including inside the wall, stems only where the terrain has them, roots, same blocks in the same order twice, nothing without palettes or before a level is bound); every pack in `dev-datapacks/` loads with the current settings; all earlier tests | pass (`./gradlew build`, both loader jars built) |
| NeoForge dedicated server, seed 20261003, new world with the `cone` pack, 29 chunks around the cone at 956,-504: 148 points computed from the code and checked with `execute if block` (platform top, second layer and underside; stem surface and core; roots; under hanging discs; domes; the axis) | 145 hold. The 3 misses are later vanilla generation: 1 stone stem block turned to andesite, 2 snow layers on a platform top. No errors in the log |
| The same on a Fabric dedicated server (seed set to 20261003 for the run) | identical: 145 of 148, same 3 points, no errors |
| NeoForge dedicated server with `painted-ravine`, 25 chunks around the ravine at 983,-712, 106 points | 105 hold; the miss is leaves in a dome over a mossy platform. No errors |
| NeoForge dedicated server with the mod's own `carve.json` (no palettes) | boots, logs `Disc palettes: 0`, 9 chunks around the ravine at 983,-712 generate, no errors |
| Paint pass cost, unit-test harness, 841 chunks around each of 4 cones | mean 13 to 22 microseconds per chunk; the worst chunk 1.3 to 4.1 ms |

Not verified: nothing was looked at in game, so how the materials, the roots and the overlapping platforms look is open; Larion was
not run; generation time was not measured in a server, only the paint pass alone in the harness; the Fabric run used the cone pack only.
After `stop` each dev server saved and stopped but its JVM stayed up on idle thread pools that are not this mod's (it creates none), so the
test script ended the process; whether that predates this change was not checked.

**For the next round (micro-biomes), checked against the 1.21.1 jar:**

- Grass and leaf tint come from the biome stored in the chunk, so colour control needs a real biome stamped over each dome, the way
  the cavern's is (`CavernBiomeResolver`). A datapack biome JSON is enough. Not tested: a custom biome's colours on an unmodded client.
- A stamped biome keeps the surrounding biome's features out (vanilla features check the biome where they land) and sets mob spawns.
- Its own feature list will not run: `ChunkGenerator.applyBiomeDecoration` keeps only biomes the level's biome source can produce.
  Foliage therefore has to be placed by our own pass, driven by a per-disc theme.
- Structures test the biome source, not the stamped biome, so disc ruins need our own placement reading the disc layout, as the city
  reads the cell centre. A structure's pieces only generate within 8 chunks of its start chunk, so it is one structure per disc.
- Biome cells are 4x4x4 with fuzzy edges, so a tint border is soft by a few blocks while the material border is exact. Overlapping
  domes need a rule for which biome wins.
- Nothing in the paint pass assumes a disc is inside the hole except the stem rule, so a disc above ground needs a layout that puts
  one there and a rule for where its stem ends; a stem cannot look for the ground, because it crosses chunk borders.

### Cone as the default; larger, lower discs; thin stems; bowls

Owner, after trying both test packs in game ("looking really cool"): use the cone as the main default and leave the ravine for
later; now that discs are painted blocks, let them be a bit larger again and sit lower in the cone; make stems thinner, in
proportion to the disc, from 2 by 2 for the thinnest to about 6 by 6; give each disc a slight bowl shape, 1 to 4 blocks.

- **The mod's own `carve.json` is now the cone**, with the mossy palette and `hang_chance` 0.35. `dev-datapacks/cone` is the same plus
  the concrete test palette. The ravine lives on in the ravine packs (`painted-ravine` is what the mod shipped before), which keep
  the discs they had: thick stems, flat tops. The ravine's code is untouched apart from the disc code it shares; it was not looked at.
- **Larger:** `discs.max_radius` 56 to 64. The limit that actually bound was a constant in the code, 0.55 of the room between the
  clear cylinder and the wall at the disc's height; it is now `cone.max_share`, 0.65.
- **Lower:** `cone.base_clearance` may be negative and is -16, which starts the lowest layer 16 blocks under the cavern roof. A cone
  whose lowest platforms would reach the cavern floor is rejected at load. The cone's rock function now counts the cavern's airspace
  as open, so a low platform that reaches past the cone into the cavern keeps that part even without a palette.
- **What that gives at vanilla height** (7 cones on seed 20261003): 3 layers and 15 discs per cone (2 and 9 before), 6 of them below the
  cavern roof, radii 20 to 56 (about 40 at most before), 1 to 6 hanging. The lowest platform's underside is 34 to 35 blocks above the
  cavern floor; the city's tallest piece is 31.
- **Stems:** `stem_radius` is renamed `min_stem_radius` and there is a `max_stem_radius`. A stem is `stem_fraction` (0.05) of its disc's
  radius, kept between 1 and 3, so 2 to 6 blocks across. The flare under the platform is unchanged.
- **Discs are centred halfway between block coordinates** (`Disc.placed`). Blocks are sampled at whole coordinates, so without this a
  stem of radius 1 came out as anything from 1 to 4 blocks depending on where its axis fell; now it is always 2 by 2, and every
  stem is as wide one way as the other. A disc moves by at most half a block each way; the cone keeps a block of slack for it at
  the clear cylinder and in the reach that cells are sized for.
- **Bowls:** `discs.min_bowl_depth` 1 and `max_bowl_depth` 4. `floor` is now the middle of a platform's top, and the top rises to the
  rim by the bowl's depth along a parabola: the least for the smallest discs, the most for the largest. The underside stays flat,
  so a platform is thicker at its rim and nothing opens between it and the stem's flare. The dome's air starts at the bowl, and a
  palette's top layers follow it.
- **Seen in the probes, for the owner to judge in game:** the low discs lie in the cavern's lush caves biome, so lush caves features
  decorate them: clay and water pools, dripleaf, azalea, moss carpet and grass on the mossy tops.
- **Not looked into:** stems still run down through the cavern to its floor, and there are now more of them over the city. City pieces
  generate after the stems and may cut them.

| Check | Result |
|---|---|
| Unit tests (90): stems from 2 to 6 across in proportion to the disc; a placed disc's thinnest stem is 2 by 2 and its thickest 6 across both ways, wherever it was put; the bowl's top, flat underside, dome and material depths; bowl depth by size; discs below the cavern roof but clear of its floor, and another layer; no disc over its share of the room, some over the old share, radii past 48; a low platform is rock in the cavern's airspace beyond the cone; the line of sight with the shipped numbers; a cone reaching the cavern floor is rejected; a bowled platform is painted up to its rim; the mod's own file is the cone and gives every part a material; every testing pack loads; all earlier tests | pass (`./gradlew build`, both loader jars built) |
| NeoForge dedicated server, seed 20261003, new world with the `cone` pack, 74 chunks around the cone at 957,-511: 738 points computed from the code (bowl tops in the middle and at the rim and the air over them, second layer, underside, every block in and around each stem's thin part, roots, under hanging discs, the axis) | 686 hold. The misses are later vanilla generation: clay and water of lush caves pools in platform tops, grass, azalea, moss carpet, dripleaf and snow over them, 2 stone root blocks turned to andesite and diorite. Stems: 115 of 116 inside points hold. No errors in the log |
| Fabric dedicated server with no datapack, so the mod's own file (seed set to 20261003 for the run), same cone and points | 682 hold, the same kinds of miss. The log shows `Disc palettes: 1`. No errors |
| Paint pass cost, unit-test harness, 841 chunks around each of 7 cones | mean 27 to 50 microseconds per chunk (more blocks than before: 0.3 to 0.46 million per cone); the worst chunk 1 to 6.5 ms |

Not verified: nothing was looked at in game; Larion not run; the ravine packs were only loaded by a unit test, not generated; whether the
city still generates intact under the lower discs and their stems.

### Curved undersides, varied bowls, discs outside the cone

Owner: do not keep the underside flat, have it curve with the bowl; let the bowls vary so some stay flatter; and do not limit the
biggest discs by the size of the cone: they should be able to carve outside the cone and spawn more discs on top, outside where the
cone would normally be.

- **The underside follows the bowl.** A platform is `floor_thickness` deep everywhere, curved top and underside alike. The stem's
  flare counts its depth from the underside straight above each point, so it follows the curve and no slit of air is left between
  the two. Because the underside is no longer one plane, a stem that lands on a platform now ends halfway down through it, and a
  root hanging from a platform runs half a thickness up into it; nothing shows under or over the platform either way.
- **Bowls vary.** New `discs.bowl_variation` (0.75): each disc draws its own depth, between the full depth for its size and that
  depth less the variation's share, so a quarter of it here. 0 gives every disc the full depth. A ravine's discs do not vary yet
  (their packs have flat discs).
- **Disc size is no longer held to the cone.** `cone.max_share` is gone. A disc is as large as it is drawn (up to `max_radius`);
  one too large for the cone at its height sits with its inner edge at the clear cylinder and cuts its dome into the rock around
  the cone. Small discs are placed as before.
- **Riders: more discs on top, outside the cone.** A disc has places for riders on a circle halfway out, one per `cone.spacing`
  blocks of it, so larger discs have more. A place holds a rider with `cone.rider_chance` (0.6; 0 turns riders off). A rider is at
  most `cone.rider_scale` (0.6) of its host's radius, stands inside its host's dome between half and four fifths of the way up it
  with at least 5 blocks of air under it, has its stem on the host's platform, and has its centre outside the cone at its height.
  Its own dome then opens the rock above and beyond its host's. Riders carry riders of their own; a cone has at most 512 discs.
- **`cone.outer_radius` (200)** is the one hard limit: nothing of any disc lies further from the axis. It now sets the reach that
  cells are sized for, in place of the cavern radius plus the largest disc.
- **What that gives at vanilla height** (7 cones on seed 20261003): 18 to 34 discs per cone, 15 in the ring and 3 to 19 riders; 3 to
  10 hang; radii up to 64; 9 to 27 discs reach more than 20 blocks past the cone's wall; the furthest edge is 134 to 171 blocks from
  the axis; bowls 0.3 to 3.5 deep.
- **To look at in game: large domes right under the surface.** Size is no longer smaller near the top, so a top-layer disc can be
  up to 128 across with its dome's top at y=64 (`top` 80 less `ceiling_margin` 16). On the cone probed, the largest top-layer dome
  (radius 47, top at y=59) has air at 7 of 15 points sampled 2 and 5 blocks over its roof, so it is open or nearly open to the
  surface there. `ceiling_margin` is the knob. Over all domes 49 of 259 such points are air; for the lower ones that is natural
  caves next to the dome, which I did not separate from surface openings.

| Check | Result |
|---|---|
| Unit tests (96): the bowl's two faces, an even thickness and no air between underside and flare; a stem ends inside the curved platform it lands on and never shows under it; the ceiling of a root is the curved underside over its axis; bowl depths across the variation; discs as large as drawn, inner edge outside the clear cylinder, nothing past the outer radius, domes opening rock well outside the cone; riders outside the cone, in a larger disc's dome, within bounds, standing riders landed on a platform, the ring unchanged by them; nothing opens beyond the reach; the line of sight with riders; an outer radius too small for a disc is rejected; all earlier tests | pass (`./gradlew build`, both loader jars built) |
| NeoForge dedicated server, seed 20261003, new world with the `cone` pack, 121 chunks around the cone at 956,-499: 1435 points (tops, the blocks over them, undersides and the blocks under them, in the middle and at the rim, for ring discs and riders; stems; roots; domes outside the cone; the axis) | 1372 hold. Riders: tops 45 of 45, undersides 45 of 45, rim tops 45 of 45. Domes outside the cone: 25 of 26 open. The misses are later vanilla generation as before (lush caves water, clay and plants, stone blobs), plus 1 lava and 1 cave air. No errors in the log |
| Fabric dedicated server with no datapack (seed set to 20261003 for the run), same cone and points | 1373 hold, the same kinds of miss (26 of them water in mossy tops). No errors |
| Costs, unit-test harness, 7 cones: paint pass over 841 chunks; terrain function (carve and rock) over a slab of points | paint mean 51 to 124 microseconds per chunk, worst chunk 1.8 to 4.3 ms, 0.35 to 0.65 million blocks per cone; terrain function 0.22 to 0.39 microseconds per sample |

Not verified: nothing was looked at in game; Larion not run, where a taller cone has more layers and so more discs and riders; the
ravine packs were only loaded by a unit test; whether the city is intact under the lower discs and stems; how riders read from
inside a host's dome.

### Disc themes: a micro-biome for each disc (first pass)

Owner: start on the micro-biomes for each disc. From the earlier discussion: complete control over vegetation colour, foliage,
ground material and structures per disc, and rarity by position (discs near the centre more likely jungle; small discs low down
and far from the centre line more likely rare custom biomes such as a crystal geode or a honeycomb platform).

This pass builds the foundation and four starter themes. Structures on discs and coded patterns are not in it.

- **A theme per disc** (`disc_themes` in `carve.json`, `DiscTheme`; it replaces `disc_palettes`). A theme is a `biome`, a `weight`
  with three ramps, a `palette` (the block palette from before, without its own weight) and a `growth` list. Every part but the
  weight is optional. Each disc is given one theme when its cell's discs are built (`CellDiscs`); a disc no theme has weight for
  stays plain rock.
- **Rarity by position.** A theme's weight is multiplied along three ramps: `by_height` (`bottom` to `top`), `by_distance`
  (`centre` to `edge`) and `by_size` (`small` to `large`). The traits are measured among the discs of the same hole (lowest to
  highest disc, centre to outermost disc), so a ramp runs its whole length in every hole whatever the world's height. Size is
  measured from the smallest radius a disc may have to the largest. My first version measured height and distance against the
  whole hole; discs only fill part of that, the ramps barely moved, and the "rare" themes came out as a third of all discs.
- **Biome.** A theme's biome is stamped over the disc's dome and platform and 4 blocks round them, by the same resolver wrap as
  the cavern's (`FootprintBiomeResolver`, renamed from `CavernBiomeResolver`). Where the spaces of several discs overlap the
  highest disc has it, so a rider keeps its own biome inside its host's dome. A disc in the cavern's airspace keeps its own biome
  rather than the cavern's lush caves. The four biomes are datapack files in `data/overgrown_abyss/worldgen/biome/`: each sets
  `grass_color`, `foliage_color` and for two of them water colours, takes its spawn lists and sounds from vanilla's jungle or lush
  caves, and lists no features.
- **Growth.** A disc biome is not one the biome source can produce, so vanilla never runs a feature list for it (checked earlier in
  the jar). Each theme lists configured features with an `every` (one per that many blocks of surface, on average) and a surface,
  `top` or `underside`; `DiscGrower`, hooked at the tail of `ChunkGenerator.applyBiomeDecoration` (new `ChunkGeneratorMixin`),
  places them in the open block on that surface. The places are drawn from the cell's hash and each block's coordinates, so they
  do not depend on chunk order. A place is skipped if it is not open or the surface there is not a sturdy face.
- **Starter themes** (weights in the file): `disc_lush` (the mossy palette; moss patches, azalea trees, flowers, spore blossoms
  and cave vines underneath), `disc_jungle` (grass over dirt; jungle trees and bushes, bamboo, jungle grass, melons, cave vines;
  three times as likely at the centre as at the edge), and two rare ones weighted towards small, low, outlying discs:
  `disc_crystal` (amethyst with some budding amethyst over calcite, smooth basalt underneath; clusters above and below, from two
  small `simple_block` features of our own) and `disc_honeycomb` (honeycomb with honey patches; bee nests, the odd oak with a
  hive). Flowers cannot stand on honeycomb, which is why that theme grows nests instead.
- **What the numbers give** (7 cones, seed 20261003, vanilla height): 78 jungle, 72 lush, 18 crystal, 16 honeycomb, so 18% rare.
  In a unit test over 64 holes the rare share is under 12% in the middle and more than two and a half times that on small, low,
  outlying discs.
- **Side effects of the stamped biomes, as intended:** the surrounding biome's features stay off themed discs. The lush caves water
  and clay that the last round found in the tops of low discs is gone (0 of 90 top probes, 26 before).

| Check | Result |
|---|---|
| Unit tests (103): a theme's JSON and its defaults; a weight along each ramp; the draw, including themes with no weight; traits measured among a hole's own discs; the shipped themes gather where their ramps say; where a disc's biome reaches; the highest disc's biome wins, none outside discs or before a level is bound; growth places on a top and an underside at about the asked rate, the same every time; each shipped theme has a biome file, a block for every part, and growth whose own feature files exist; palettes, blocks and all earlier tests | pass (`./gradlew build`, both loader jars built) |
| NeoForge dedicated server, seed 20261003, new world with the `cone` pack (five themes), 145 chunks around the cone at 956,-499 | boots with `Disc themes: 5`, no missing biome or feature. Biome 4 blocks over the top of each disc (`execute if biome`): 82 of 85 as computed; cavern floor still lush caves. Tops 88 of 90, undersides 90 of 90 (the 2 tops are dirt under a tree) |
| Fabric dedicated server with no datapack (seed set to 20261003 for the run), 154 chunks around the same cone | `Disc themes: 4`. Biome 88 of 90; tops 88 of 90, undersides 90 of 90. No errors |
| What grew at the growth places (Fabric; NeoForge alike): 40 places per theme and feature, where nothing else of the layout is in the way | crystal: clusters at 40 of 40 on top and 40 of 40 underneath. Honeycomb: nests at 35 of 40 (the rest are on honey, which is not a sturdy face), oak logs at 24 of 29 tree places. Jungle: bamboo at 31 of 40, jungle logs at 27 (trees) and 31 (bushes) of 40, cave vines at 40 of 40, melons at 8 of 40. Lush: spore blossoms 40 of 40, cave vines 40 of 40, azalea trunks at 11 of 19, moss and flower patches around the rest |
| Generation time, dedicated server | 145 chunks in 15 s (NeoForge) and 154 in 15 s (Fabric); the last round took 14 s for 121 |

The biome misses (2 to 3 per run) are at the edge between two discs' spaces, where the game's own biome lookup blurs neighbouring
cells; they read as the neighbouring disc's biome.

Not verified: nothing was looked at in game. In particular the colours: the biomes are stamped, but tint is drawn by the client and
no client was run, modded or not. Larion not run. Mobs spawning by a disc's biome was not checked. The ravine packs were only
loaded by a unit test (`painted-ravine` now has two themes that are only palettes).

Not in this pass: structures on discs (ruins by theme), coded patterns (honeycomb cells, geode shells; the two rare themes only
approximate them with layers and noise patches), growth on stems, a theme's own mob lists (they are vanilla's), ravine discs
varying by theme position along the ravine.

### Hiding the rare discs: outer-edge riders, limits, mangrove, a wider base

Owner, after looking at the first themes in game:

- Drop the honeycomb disc; make it a structure that can spawn on lush or jungle discs later.
- Keep crystal discs to smaller discs further from the centre.
- Going straight down the centre almost every disc is in plain view. What is there looks good, but jungle and lush discs should
  screen the discs further back. Use the cone's geometry to hide the rarer biomes: a wide disc near the bottom and middle can carry
  a smaller disc near its outer edge, which cuts its way up outside the cone. With denser foliage on lush and jungle discs and a
  new mangrove biome, that hides the rare discs and the shape of the cone, so the place feels bigger without the cone being much
  bigger. The cone's base radius can also grow a bit.

What changed:

- **Honeycomb theme removed**, with its biome file and bee nest feature. A honeycomb structure for lush and jungle discs is noted
  under future additions; nothing of it is built.
- **Limits on a theme** (`only` in a theme: `min` and `max` on height, distance and size, each from 0 to 1). A ramp only makes a
  theme more or less likely; a limit rules it out. Crystal is now `"only": {"distance": {"min": 0.55}, "size": {"max": 0.4}}`:
  discs in the outer 45% of their hole's reach and in the smallest 40% of the size range.
- **Riders stand on the outer side of their host.** A rider's place is within 100 degrees either way of straight out from the
  cone's axis through its host's centre, half to nine tenths of the way out to where its stem still lands, and it must be further
  from the axis than its host's centre. Its floor is as low as headroom over the host's top allows and no higher than fits under
  the host's roof there, which is low near the rim, so the rider's own dome rises through that roof into the rock beyond the cone.
  Before, riders stood anywhere round their host, half of them on the side facing the middle.
- **`cone.base_radius`** (150): the cone's radius at the floor is its own number now, apart from the cavern's (still 136). Where the
  cone is wider than the cavern's dome it opens the ground beyond it.
- **`rider_chance` 0.6 to 0.5.** With places only on the outer side nearly every place is a valid one, so the count went up;
  0.5 gives 6 to 18 riders per cone at vanilla height.
- **Mangrove theme** (`disc_mangrove`): mud with moss and muddy root patches over packed mud and stone, mangrove roots under the
  platform and as the stem's surface; mangroves and tall mangroves, grass, cave vines underneath. Weighted to low and large discs
  (twice as likely at the bottom as elsewhere, and on the largest discs), since those are the hosts. Colours and spawns from
  vanilla's mangrove swamp.
- **Denser foliage.** Jungle: a tree every 45 blocks of top (90 before), a bush every 30 (60), bamboo every 120 (350). Lush: an
  azalea tree every 200 (700), moss patches every 110 (160), tall grass, and hanging moss under the platform.

What the numbers give (7 cones, seed 20261003, vanilla height): 21 to 33 discs per cone, 7 to 23 of them centred outside the cone;
67 jungle, 61 lush, 48 mangrove, 16 crystal (8%); two cones have no crystal disc. In a unit test over 64 holes no crystal disc is in
the inner third, all 240 stand behind a larger disc of another theme (one nearer the axis whose footprint reaches theirs), and
mangrove is half of the wide low discs against a fifth of all discs.

| Check | Result |
|---|---|
| Unit tests (105): limits rule a theme out and a missing end of a span is the end of the trait; the shipped themes gather where their ramps and limits say, every crystal disc inside its limits and behind a larger disc of another theme; riders outside the cone, further from the axis than a larger disc that has them in the outer part of its dome, and carrying riders of their own; the cone's base radius apart from the cavern's, and a base narrower than the top rejected; all earlier tests | pass (`./gradlew build`, both loader jars built) |
| NeoForge dedicated server, seed 20261003, new world with the `cone` pack, 149 chunks around the cone at 956,-499 | no errors, no missing biome or feature. The floor between the cavern's edge and the cone's is open. Biome 86 of 93, tops 97 of 99, undersides 97 of 99 |
| Fabric dedicated server with no datapack (seed set to 20261003 for the run), 157 chunks | no errors. Biome 93 of 99, tops 97 of 99, undersides 98 of 99 |
| Growth, 40 places per theme and feature (Fabric; NeoForge alike) | crystal clusters 40 of 40 above and 40 of 40 below. Jungle: trunks at 23 of 40 tree places and 29 of 40 bush places, bamboo at 29. Lush: azalea trunks at 16, spore blossoms 39 of 40, cave vines 38 of 40. Mangrove: a trunk in the column over 21 of 40 mangrove places and 8 of 40 tall mangrove places (a mangrove's trunk starts a few blocks up, on its roots; the tall ones mostly do not fit under a dome), with leaves through most of those columns |
| Generation time, dedicated server | 149 chunks in 16 s and 157 in 16 s |

After that check I raised the mangrove rate from one per 45 blocks to one per 30 and lowered tall mangroves from one per 55 to one
per 80, since the tall ones mostly fail; that change was not run in a world.

Not verified: nothing was looked at in game, so whether the rare discs are in fact hidden from the centre line, and how much, is
open; the unit test only shows that a larger disc stands between each of them and the axis. Larion not run. The ravine packs were
only loaded by a unit test.

### Biome inheritance for disc themes

Owner: each disc biome should carry the full features of the biome it inherits from, plus our additions and changes, so that
what another mod adds to that biome (foliage, animals) turns up on our discs too. For now shelve our own densities and additions,
to see what the unmodified parent looks like on a disc.

Minecraft has no inheritance between biomes; every biome file stands alone. So a theme names a parent (`inherits`) and the disc's
biome borrows the parent's content when it is used, instead of holding a copy that goes stale:

- **Spawns.** `BiomeMixin` makes an inheriting biome answer `getMobSettings()` with its parent's, read each time. That accessor
  is where every spawn list, cost and probability comes from, and on NeoForge it is already patched to return the biome as changed
  by other mods' biome modifiers (checked in the patched jar), so their additions are included. Bound at level load by
  `RavineDensityHook`, which logs what each disc biome inherits.
- **Features.** A disc biome lists no features of its own, which is what keeps the surrounding biome's features off the disc, and
  vanilla never decorates a biome its biome source cannot produce. So `DiscGrower` grows the parent's features itself: for each
  disc it reads the parent's current list for the stages asked for and runs each placed feature by its own placement rules, with
  two changes. The rules ask a `DiscPlacementContext` for the ground and get the disc's top instead of the column's (a column
  not over the disc answers with the bottom of the world, which vanilla takes as no ground). And where a rule asks whether the
  biome at the place lists the feature, the answer is whether the place belongs to this disc (`DiscPlot.owns`: in its dome or
  platform, and not in a higher disc's). So features land on the disc at vanilla's own rates, and cave-style features that scan
  for floors and ceilings fill the dome as they would a cave.
- **Tags.** Our biomes are added to the vanilla and convention biome tags their parents are in (frog variants, swamp slimes and
  fog, fire burnout, `is_jungle`, `c:is_lush`, ...), but not to the structure tags, since structures ask the biome source.
- **Stages.** `inherits.stages` is `vegetal_decoration` unless given, which leaves out lakes, geodes, monster rooms, ores and
  springs; `inherits.without` leaves out named features.

The shipped themes now inherit and add nothing: lush from `minecraft:lush_caves`, jungle from `minecraft:jungle`, mangrove from
`minecraft:mangrove_swamp`; crystal inherits nothing and is unchanged. Their growth lists are gone and their palettes are only
the ground the parent would have had, since no surface rule reaches a disc: grass over dirt for jungle, mud for mangrove, plain
stone for lush. The hand-tuned versions are kept as `dev-datapacks/tuned-themes` for comparison.

Found on the way:

- **A crash, fixed.** With a fixed height for the ground, a jungle tree could be started inside the trunk of a tree already grown
  there, which crashes vanilla's cocoa decorator and with it the chunk. The heightmaps that vanilla keeps up to date during
  decoration now follow the column up through what stands on the disc; the two worldgen heightmaps still answer with the bare top,
  as in vanilla.
- **Failures are contained.** These features were written for open ground, some by other mods. If one throws on a disc it is
  logged and left out of that chunk, since an exception there would stop the chunk generating. None failed in the runs below.
- **Lush discs need a body.** Left unpainted, the part of a platform inside the wall is the terrain's, cave holes included: 33 of
  300 sampled top blocks were air. Lush now has a plain stone body, and 0 of 300 are.

What it looks like in numbers (300 points over discs of each theme, Fabric, the mod's own file):

| Theme | Top block | On the ground | 3 blocks up |
|---|---|---|---|
| Lush | moss 96, water 92, clay 70, stone 33 | grass 55, moss carpet 25, azaleas 9, dripleaf 10, nothing 201 | nothing 293 |
| Jungle | grass 281, dirt 19 | bush and tree leaves 200, grass and ferns 42, trunks 19, vines 8, melons 5, nothing 25 | leaves and vines 66, trunks 6, nothing 227 |
| Mangrove | mud 276, muddy roots 24 | grass 38, roots 25, vines 11, nothing 226 | leaves 24, roots 14, vines 13, trunks 7, nothing 236 |

So: lush discs are over half pool (water and clay), as lush caves are on flat ground; jungle discs are thick with bushes, as
vanilla jungle is; mangrove discs are thin, because vanilla's mangrove swamp mostly grows tall mangroves and those rarely fit
under a dome, and its lily pads and seagrass have no water to grow in.

| Check | Result |
|---|---|
| Unit tests (107): the `inherits` setting, its default of vegetation only, and that it needs a biome of its own that is not the parent; an inheriting disc as a plot (its ground in each column, none beside it, its own blocks but not a higher disc's, nothing before a level is bound); the shipped themes inherit from the three biomes, add no growth, are only ground, and are tagged as overworld biomes; all earlier tests | pass (`./gradlew build`, both loader jars built) |
| NeoForge dedicated server, seed 20261003, new world with the `cone` pack and a stand-in for another mod: a datapack of NeoForge biome modifiers that adds one feature (a sponge block on the surface, 24 per chunk) and one spawn (allay) to `minecraft:jungle` | The log shows `disc_jungle inherits from minecraft:jungle: 12 features ... and 19 spawn entries` against 11 and 18 without the pack. Sponges stand on 23 of 300 points sampled over jungle discs. No inherited feature failed. Biome 145 of 150 |
| Fabric dedicated server with no datapack (seed set to 20261003 for the run) | no errors; the table above. Biome 145 of 150 |
| Generation time, dedicated server | 77 chunks in 13 s and 80 in 13 s |

Not verified: nothing was looked at in game. Mobs actually spawning on a disc was not checked, only that a disc biome's spawn
lists are its parent's; vanilla's own spawn rules still apply, so passive animals need light and will not appear under a dark dome.
A mod adding to a biome in code on Fabric was not tried (Fabric has no datapack biome modifiers; its API changes the parent's
settings in place, which the same read picks up). Larion not run.

Limits, by design or not yet done: a mod that checks for the exact id `minecraft:jungle` will not see a disc as jungle; a feature
that looks the surface up itself instead of through its placement rules acts on the real surface or does nothing; stages other
than vegetation are untried on discs; tags another mod adds to a parent biome are not picked up, only the ones listed in our files.

### A disc biome's own spawns as a change to the inherited ones

Noted after the inheritance round: an inheriting disc biome answered with its parent's spawn settings outright, so the spawn
lists in our own biome files did nothing, and there was no way to add or remove a mob on a disc. Owner: make our mob list a
modifier on top of the inherited list.

- **Merge** (`InheritedSpawns`): the parent's entries, then the disc biome's own. An entry of ours for a mob the parent has in the
  same category replaces the parent's, which is how a weight or group size is changed. Spawn costs follow the same rule; the
  chance of creatures at chunk generation is the parent's.
- **Leaving mobs out:** `inherits.without_spawns`, a list of mob ids, taken out of what is inherited in every category but not
  out of our own entries. Ids, so that a pack naming another mod's mob still loads without that mod (it logs a warning).
  `inherits.without` is now `without_features`, to tell the two apart.
- **Still live:** `BiomeMixin` reads the parent's settings and the disc biome's own on each call and remembers the merge until
  either is a different object, which is how NeoForge's changes to a biome show up. On Fabric, where a biome's settings are
  changed in place, a change made after the first merge would be missed; those changes are made before a level loads.
- **Our files start empty.** `disc_lush`, `disc_jungle` and `disc_mangrove` held copies of vanilla's lists. As modifiers those
  would have pinned every entry and put back anything another mod removed from the parent, so they now list no mobs, and a disc
  spawns exactly what its parent does until something is added. `disc_crystal` inherits nothing and keeps its list.
- `dev-datapacks/tuned-themes` now inherits spawns only (`"stages": []`), since its biomes would otherwise spawn nothing.

| Check | Result |
|---|---|
| Unit tests (111): nothing of our own gives exactly the parent's lists, probability and costs; our entries are added and replace the parent's for the same mob, costs too; mobs left out go from what is inherited in any category but not from our own; a merge is remembered until either biome hands out other settings; the settings' JSON; the shipped inheriting biomes list no mobs and the crystal one does | pass (`./gradlew build`, both loader jars built) |
| NeoForge dedicated server with a scratch datapack: `disc_jungle.json` adds a fox and re-weights the parrot, the jungle theme leaves out pig, cow and a mob that does not exist, and a NeoForge biome modifier adds an allay to `minecraft:jungle` | log: `disc_jungle` has 18 spawn entries where the parent has 19 (19 less pig and cow, plus the fox; the parrot replaced in place). Lush 12 where the parent has 12, mangrove 14 where 14. The missing mob is warned about and the pack loads |
| Fabric dedicated server with the same pack (the biome modifier is NeoForge's and is ignored) | `disc_jungle` 17 where the parent has 18; lush 12 and 12, mangrove 14 and 14 |

Not verified: mobs actually spawning on a disc in game; the server runs only show the merged lists' sizes, and the unit tests
their contents.

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

### Honeycomb as a structure on discs (owner idea)

- **Idea:** the honeycomb disc theme was removed (2026-10-05); the owner wants honeycomb back as a structure that can spawn on
  lush or jungle discs.
- **What exists:** nothing of it. The removed theme was honeycomb blocks with honey patches and bee nests placed as single blocks.
- **What it needs:** the structure-on-discs placement that ruins by theme also need (a start per disc, read from the cell's
  discs, since structures test the biome source and not a disc's stamped biome), and the honeycomb pieces themselves.

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
