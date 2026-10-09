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
| Disc ruins | Custom `overgrown_abyss:disc_ruins` structure and placement: one piece of a template pool at each place the discs' own code finds for a ruin | Sites in `common/…/ravine`, structure and placement in `common/…/compat`, pools and processors in JSON | Yes |

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
   - *Seen 2026-10-09:* the owner's pack has Dungeons and Taverns' Ancient City overhaul, which replaces all 18 city
     templates the disc ruins use and the city's own. Template lookup does follow pack priority. Since that day the disc
     ruins measure each template as the level has it (section 7, Ruins by depth), so (a) remains and a replaced piece no
     longer reaches past the room found for it. The city at the floor is as it was: not looked at with that pack.
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

### Jungle and mangrove discs: their own trees, water, and what hangs under them

Owner, after trying the inheriting themes in their modpack: the baseline method is liked, but neither jungle nor mangrove discs
grow any foliage. Wanted: jungle discs with dense underbrush and full-height jungle trees where they fit, vines and glow berries
hanging from the trees and from the bottom of the disc, and small ponds and streams no more than 2 blocks deep; mangrove discs
with water no more than 2 deep, dense mangroves and vines, and custom roots hanging out of the bottom of the disc with vines.

**Why they were bare.** On the dev servers both grew (the previous round's numbers). The owner's pack has William Wythers'
Overhauled Overworld, which replaces `minecraft:jungle` and `minecraft:mangrove_swamp`. Its trees choose their places by height
above sea level (a `surface_relative_threshold_filter` against a fixed height: surface between y 63 and 140, by tree type) and
by soil its own surface rules lay down (rooted dirt, sand, red sand two blocks down). A disc is mostly under sea level and has
plain grass or mud, so every one of those trees rules itself out; nothing fails and nothing is logged. Inheritance did what it
says, from a parent whose rules make no sense on a disc. So a disc cannot leave its trees to its parent.

**What changed.**

- **A theme's own growth comes first, in listed order.** `growth` ran after the inherited features, and the inherited grass
  took the blocks the trees start in. Now a theme's growths run one at a time in the order listed (trees, then what hangs from
  them), and the inherited features after, as vanilla lists a biome's trees before its grass.
- **The discs' own trees** (`configured_feature/disc/`): vanilla's jungle tree, giant jungle tree, bush, mangrove and tall
  mangrove under the mod's ids, so a pack that restyles vanilla's cannot change a disc. Two changes: more vines from their leaves,
  and `min_clipped_height`, so a tree under a low part of a dome grows as tall as the room allows instead of not at all. The
  parent's own tree feature is left out (`without_features`), so discs are equally wooded with and without such a pack.
- **Water** (`DiscWater`, the theme's `water`): ponds as a share of the top, streams of a given width following a winding line,
  1 deep, and 2 in the middle of a pond. It takes the place of the platform's top blocks at the paint pass, so it is never deeper
  than asked and has the palette's blocks as sides and bed. Nothing schedules it to flow, so it is only put where it is held in:
  not within `bank` of the rim, not in a column with lower ground beside it (a one-block dam wherever the bowl steps down), and
  only under open air (not under a stem, root or the wall's rock standing on the platform). `DiscPlacementContext` now follows
  the heightmaps that see through water down to the bed, so inherited features that ask for water depth get the right answer
  (lily pads and, with the overhaul, seagrass turned up in the ponds).
- **Growth in water** (`"on": "water"`): started in the lowest block of water, as vanilla starts a mangrove on the bed.
- **Under the disc:** glow berries, tufts of leaves and vines under jungle discs; clumps of mangrove roots tipped with hanging
  roots, and vines, under mangrove discs. Vines only hang from a block beside their top, so under a flat underside they cling to
  the tufts, the roots, the steps of the bowl and the flare of the stem.
- **Mangrove ground:** mud over packed mud, underside and stem clad in mangrove roots. The packed mud is needed: a mangrove's
  roots pass through mud, and in a platform of mud alone they left through the underside, never landed, and the tree was given
  up (about one tree in ten grew).

Glow berries cannot hang from leaves in Minecraft (they need a firm face over them), so "from the trees" is from the branches of
the giant trees, and otherwise from the rock of the dome and from undersides.

Measured on dedicated servers, seed 20261003, new worlds, 100 chunks; 220 places sampled over the discs of each theme:

| | NeoForge | NeoForge with the overhaul mod | Fabric |
|---|---|---|---|
| Jungle: bush or tree at ground level | 128 | 138 | 134 |
| Jungle: tree canopy 3, 5 or 8 blocks up | 82 | 84 | 79 |
| Jungle: water in the top | 29 | 29 | 29 |
| Jungle: something hanging under the disc (of 84 places) | 48 | 42 | 37 |
| Mangrove: canopy 3, 5 or 8 blocks up | 174 | 172 | 170 |
| Mangrove: open water in the top (more has roots standing in it) | 65 | 65 | 60 |
| Mangrove: something hanging under the disc (of 110 places) | 83 | 76 | 68 |
| Water columns found running, or with water in the third block down | 0 | 0 | 0 |
| Bed under 1-deep and 2-deep water (300 columns) | all ground | all ground | all ground |
| Errors from the mod; inherited features that failed | 0; 0 | 0; 0 | 0; 0 |
| Generation | 14 s | 31 s | 15 s |

The overhaul run had the mod's jar and its library copied from the owner's pack into the dev server's `mods` folder for that
run; its log shows `disc_jungle` inheriting 18 features from the replaced jungle. Lush discs are unchanged (same top blocks as
the previous round). The NeoForge column is the final build. The other two were run one setting earlier: the only change since
is glow berries being tried in the canopy three times as often.

Glow berries in the canopy stay few all the same: 6 of 536 samples taken 3, 5 and 8 blocks over jungle ground were a glow berry
vine, against 11 of 168 under the discs. There are few firm faces among the trees for them to hang from.

Unit tests (124): the smooth values; water's share, depth, bank, stream width and that no water has lower ground beside it; a
chunk's water columns; growth in water; a theme with water needing ground to hold it and a platform thick enough for a bed; the
shipped themes and that every feature file a theme names exists.

Not verified: nothing was looked at in game, so how any of it looks is the owner's to judge, and the densities are first
guesses. The owner's whole pack was not run (it also has Larion and C2ME). Fluid behaviour after a player changes a pond's
bank is vanilla's: the water then runs like any other.

Limits: a pond that crosses a step of the bowl is cut by a one-block dam; where two platforms overlap a block apart, the lower
one's water is left out under the higher one; trees can grow into the clear cylinder's edge.

### Thinning the jungle and mangrove discs, and glow berries under mangrove discs

Owner, after seeing the round above in game: the foliage is a bit too dense; reduce it so that a player could reasonably walk
through the biomes. Also add glow berries underneath mangrove discs.

What stops a player is a solid block at foot or head height: a bush's leaves, a trunk, a mangrove root, or the lowest leaves of
a short tree. Grass, ferns, vines and what hangs do not. So the measure is the share of sampled ground with such a block in
either of the two blocks a player takes up, and only those things were thinned:

- **Jungle:** bushes one for every 50 blocks of ground (was 10); jungle trees every 20 (was 16) and giants every 36 (was 30).
  The jungle tree's trunk is 5 to 12 blocks (vanilla's and before: 4 to 12), since its leaves start three blocks under its top
  and a trunk of four put them at head height. Ferns and grass of the theme's own every 25 (was 45), to keep the ground green.
- **Mangrove:** on the mud, short mangroves every 24 (was 12) and tall ones every 70 (was 40). In the water mostly tall ones,
  every 32, and short ones every 60 (was 12 and 40 the other way round): a short mangrove starts on the bed, so its leaves were
  at head height over the water, while a tall one stands three to seven blocks up on its roots. The short mangrove's trunk is
  one block taller (3 to 8).
- **Glow berries under mangrove discs:** the same hanging glow berries as under jungle discs, one for every 9 blocks of
  underside, listed before the root clumps.

Dedicated servers, seed 20261003, new worlds, 100 chunks, 220 places sampled over the discs of each theme:

| | Before | NeoForge | NeoForge with the overhaul mod | Fabric |
|---|---|---|---|---|
| Jungle: no way through at foot or head height | 128 at foot height alone | 58 (26%) | 49 (22%) | 55 (25%) |
| Jungle: tree canopy 3, 5 or 8 blocks up | 82 | 58 | 63 | 59 |
| Mangrove: no way through at foot or head height | 81 at foot height alone | 55 (25%) | 53 (24%) | 52 (24%) |
| Mangrove: tree canopy 3, 5 or 8 blocks up | 174 | 119 | 117 | 116 |
| Glow berries under mangrove discs (of 110 places, at two heights) | 0 | 13 | 13 | 12 |
| The discs' own water: columns running, or deeper than 2 | 0 | 0 | 0 | 0 |
| Errors from the mod; inherited features that failed | | 0; 0 | 0; 0 | 0; 0 |

"Before" is the previous round's final build (the mangrove figure from its run one setting earlier, with the same mangrove
settings), where head height was not sampled, so the true share was higher than shown. All
three columns after are the final build. Unit tests: 124 pass (`./gradlew build`, both loader jars); none changed, as this
round is settings and feature files only.

Seen while measuring, and not from this change: at this seed a vanilla river (y 57 to 59) lies over a jungle disc's dome and
the dome cuts into its bed. The river falls onto that disc and on down to the one under it, and spreads a few blocks where it
lands. That is vanilla water running as vanilla water does, not the discs' ponds, which were all still. The owner has said
natural fluids entering the hole are fine; `ceiling_margin` is the setting that keeps domes further under the surface.

Not verified: nothing was looked at in game. A quarter of the ground blocked is a number, not a walk: whether it feels right
is the owner's to judge.

### Mangrove discs: roots under the whole disc, groves and giant mangroves

Owner, after seeing the mangrove discs in game: the roots under a disc only appear around the thinnest segments of its rings,
usually just the outer one, and they are too thick; wanted are roots of uneven density, trees of uneven density on top, and a
preference for the largest mangroves, with roots large enough to walk under.

**Why the roots kept to the rim.** What hangs under a disc was started in the block under the platform's underside. Under a
standing disc that block is mostly rock: the stem's flare is as wide as the disc right under the platform and narrows from
there (`funnel_scale` 1.5), so it fills that block out to about 0.6 of the radius, and further out it fills it in the inner
part of each step of the bowl, where the underside lies just over a whole block. `DiscGrower` found the place taken and grew
nothing. Counted from the code for the shipped shape (placed discs at the full bowl depth for their size), the share of
columns where something could hang, by part of the radius:

| Radius | 0 to 0.6 | 0.6 to 0.7 | 0.7 to 0.8 | 0.8 to 0.9 | 0.9 to the rim |
|---|---|---|---|---|---|
| 30 | 0% | 33% | 70% | 39% | 100% |
| 48 | 0% | 0% | 73% | 55% | 100% |
| 64 | 0% | 39% | 27% | 73% | 100% |

Now it is every column but the stem itself. This applied to every theme, so jungle and crystal discs, and the
`tuned-themes` pack, also grow under the whole disc now, at the same rate per block as before at the rim.

**What changed.**

- **What hangs starts under the disc's lowest rock** (`Disc.hangBlockAt`): under the flare where there is one (the flare's
  radius `radius * funnel / (depth + funnel)` turned round for the depth), under the platform of a hanging disc, and not at
  all in the stem's own columns. A stem that lands on a lower disc is cut off there, flare and all, and so is the place.
- **Patches** (`DiscTheme.Patches`, a growth's optional `patches`): a smooth value per disc and patch size is turned into its
  share of all such values (`RavineCells.shareBelow`, the inverse of the levels the ponds already used), and a growth's rate is
  multiplied by a ramp over the top `cover` of that share: 0 below it, `2 / cover` at the top, 1 on average. So a growth keeps
  its `every` over a disc while leaving `1 - cover` of it bare. Patches of the same size are the same patches, which nests the
  covers: the giant mangroves fill the middles of the groves the smaller trees spread out from. The smooth-value levels and the
  turned grid moved from `DiscWater` to `RavineCells`, now that two things use them.
- **Giant mangrove** (`disc/mangrove_giant`): the tall mangrove with its trunk raised 5 to 8 blocks on its roots (3 to 7),
  root arms that spread out further before they turn down (`max_root_width` 10, was 8) and may be 32 long, a trunk of 7 to 16
  (4 to 14) with longer branches, a crown of radius 4 (3) and 120 leaf tries (70). It needs room for 6 blocks of trunk over its
  roots, so where a dome is low it is not grown and the smaller trees have the place.
- **The tall mangrove's root arms may be 24 long** (vanilla's 15).
- **Thinner root clumps** (`disc/mangrove_roots_hanging`): 6 tries (14), and strands mostly 1 to 3 roots long, a quarter of
  them 3 to 9 (all 1 to 6 before), each still tipped with hanging roots.
- **The mangrove theme's growth.** Trees in groves 40 across: giants every 40 blocks of mud and every 36 of water at a cover
  of 0.45 and 0.5, tall mangroves every 80 of mud and 60 of water at 0.65, short ones every 60 of mud only at 0.8 (they were
  every 24 of mud and 60 of water, evenly), which leaves a fifth of the top as clearings. Under the disc: glow berries every 16
  in patches 10 across at 0.5, root clumps every 24 in patches 14 across at 0.45 (every 10 evenly), and vines every 30 in the
  same patches as the roots at 0.6, so that they hang about the roots.

**Root arms, simulated.** Mangroves are given up when a root arm needs more steps than `max_root_length` to reach the
ground. A Python model of `MangroveRootPlacer` (`[recall; verify]`: written from memory of 1.21.1, not read from the jar)
grew trees on open mud 2 deep over packed mud and asked whether a player could stand under the trunk and walk out (two
blocks clear, no squeezing between corners):

| Settings | Grown | Walk under the trunk | Clear height under the trunk |
|---|---|---|---|
| short mangrove (raised 1 to 3) | 99% | 33% | 2.0 |
| tall mangrove as before (3 to 7, arms 15) | 64% | 99% | 3.4 |
| tall mangrove now (arms 24) | 96% | 100% | 4.0 |
| giant (5 to 8, width 10, arms 32) | 99% | 100% | 5.5 |

The 64% agrees with the earlier server count that few tall mangroves grew. In a pond 2 deep, where a tree starts on the bed
of packed mud, the arms have less far to go: tall mangroves grew 91% before and 100% now, and the giants 100%, their trunks on
average 3.5 blocks over the water (tall ones 3.0), and 99% of them can be swum under at the surface (tall ones 59%).

**Verification.** This round could not be built: the session's container was refused `maven.fabricmc.net`,
`maven.architectury.dev`, `files.minecraftforge.net`, `maven.neoforged.net` and Mojang's servers by its network policy, so
Gradle could not fetch Loom or Minecraft. What was run instead: the changed `Disc` compiled on its own (with `DiscShape`
less its codec), and the new `DiscTest` checks held on it; the changed `RavineCells` methods and `Patches.weightAt`, copied
out of the edited files, compiled and gave for the new tests' numbers an average weight of 0.97 with 70.5% of the surface
bare at a cover of 0.3. Over six hashes and patch sizes 8, 14 and 40 the average was 3 to 6% under 1 (the levels table is
coarse in its last twentieth) and the bare share within 0.017 of `1 - cover`. The vanilla limits used (`max_root_width` 1 to
12, `max_root_length` 1 to 64, `base_height` 0 to 32, foliage `radius` 0 to 16, `min_clipped_height` 0 to 80) were read from
SpyglassMC's `vanilla-mcdoc`, not the jar.

Not verified: `./gradlew build` and the unit tests (three new, one moved), both dedicated servers, and anything in game. To
measure: things hanging inside 0.6 of the radius under mangrove discs; giant mangroves grown at their places and the clear
height under them; the share of the mangrove top blocked at foot or head height (a quarter before) inside and outside the
groves; how many more things now hang under jungle and crystal discs.

Limits: near the stem the flare is steep, so what hangs from it starts well under the platform (about 13 blocks down 0.1 of
the radius from the axis), below the margin of the disc's biome; vines there take the colour of the biome around the disc.

### A top for each hole, a bowl above it, and an opening

Owner, on the plan in `docs/rim-walls-drape-plan.md`: the goal is grand fantasy visuals of the chasm and a hole that blends
into the terrain; holes should not open close to sea level, and a higher top gives more room for discs. This builds the
plan's shape: the ground probe of phase 0 and phases 1 to 3. Wall noise, balconies and the drape (phases 4 to 6) and the
view-metrics test are not built.

**What changed.**

- **Each hole has a top of its own** (`RavineSites`, which was `LandGate`). For a cone with a `rim`, 24 columns round the
  mouth (`top_radius` from the axis) are read, each taken as the middle one of its own and its two neighbours' so that one
  pothole does not count, and the lip is `dip` under the lowest of them, never above `top`. A cell whose lip would be under
  `min_top` holds no hole, which joins the land check in one answer per cell: either nothing, or the heights the hole lies
  between. The carve, the painter, the cavern's biome and the city all read those (the city through the footprint, as
  before). `low_share` (0 as shipped) lets that share of the mouth's edge lie lower than the lip.
- **The ground is read from the level's own terrain** (`SurfaceProbe`, `compat/TerrainSurface`), not from the preliminary
  surface the plan named: that is 8 blocks under the real ground at the median but 30 and more in one high column in
  twenty (table below). The hook makes a second `RandomState` from the level's untouched settings, and the probe walks its
  final density down a noise cell at a time to the first rock and then up to the air. Whoever asks names the highest ground
  it cares about, so nothing is spent on a mountain above that.
- **Layers are spread evenly** from the lowest to `top_room` under the highest a dome may reach, no closer than
  `layer_spacing`. With `top_room` 24 under a top of 80 and a margin of 16 these are the old floors, and a test holds the two
  layouts equal.
- **A dome stays under the ground over it** (`HoleGround`): the ground is read at the disc's centre and on a 16-block grid
  inside its rim, shared by all the discs of a hole, and the roof keeps `ceiling_margin` under it at each of them (the roof
  comes down towards the rim, so ground there limits it less). A disc left with less than the least dome is not made. A
  root is not anchored to the wall higher than the margin under the ground over its axis.
- **Above the lip the hole is a bowl** (`ConeShape.bowlDistance`): open inside the mouth, and outside it only above
  `lip + height * (out / width) ^ profile`, uneven by `roughness` (the smooth value the ponds use, from the cell's hash).
  The surface has no upper end, so there is no headwall; nothing is opened past `outer_radius`, and `maxReach()` is as it was.
- **The clear air widens upwards** (`upper.clear_radius`, `ConeShape.clearAt`): from `clear_radius` at the floor to the
  upper one at the lip. Platforms, riders and roots are placed outside it, and the rock function still cuts off anything
  inside it.
- **Settings as shipped:** `top` 160 (was 80), `ceiling_margin` 12 (16), `rim` (`min_top` 96, `dip` 3, `low_share` 0,
  `top_room` 22, `collar` of width 30, height 34, profile 2, roughness 2 over 12), `upper.clear_radius` 20, and `cell_size`
  1024 with `chance` 1 (2048 and 0.5) because so few cells pass `min_top` (below). `min_top` is a plain height, not an
  anchor. Without `rim` and `upper` a cone is exactly what it was, which the `fixed-top` testing pack is.
- **The world-load log** gives each hole near the origin its own top and layers, and counts the cells that held none.

**Measured on vanilla terrain, outside the game.** Vanilla's overworld was built from the 1.21.1 jar
(`VanillaRegistries.createLookup()`, `RandomState.create`), and the mod's carve bound to it as the hook binds it, with the
land check taken from the biome names of `#is_ocean`, `#is_river` and `#is_beach`. These are scratch programs, not in the
repository.

*The probe against what the game builds* (density blended between cell corners; 1,500 columns, seed 20261003):

| Columns | 1% | 5% | median | 95% | 99% |
|---|---|---|---|---|---|
| all | -4 | -3 | 0 | +1 | +3 |
| ground at 90 or more | -6 | -2 | -1 | +1 | +2 |

The preliminary surface (`initial_density_without_jaggedness` against 0.390625, refined to a block) lies under the real
ground by a median of 8 blocks and by 30 to 34 at the 95th percentile where the ground is above 80, 100 at most.

*How many cells could hold a hole* (seeds 20261003, 1, 2 and 3; 2,304 cells; 60% are land), as the share of all cells whose
lip would be at least a height, and how much the ground round the mouth differs in those that pass 96:

| `low_share` | 72 | 80 | 88 | 96 | 104 | 112 | Ground round the mouth: quarter, median, 90% |
|---|---|---|---|---|---|---|---|
| 0 (lowest ground) | 8.9% | 6.4% | 4.2% | 2.7% | 1.9% | 0.9% | 21, 32, 78 |
| 0.25 | 12.8% | 9.7% | 6.4% | 4.2% | 2.9% | 1.9% | 25, 39, 79 |
| 0.5 (middle ground) | 18.9% | 14.1% | 11.0% | 7.6% | 5.4% | 3.3% | 29, 45, 81 |

So high ground in vanilla is steep ground: half the holes that pass have 32 blocks or more between the low and the high side
of their mouth. And with the old `cell_size` and `chance` only 1 cell in 50 held a hole, the nearest 9,000 blocks from the
origin on the owner's seed.

*The shipped settings on seed 20261003:* 14 holes within 12,288 blocks of the origin each way, 0.10 for each square of
2,048 (it was 0.3). The nearest:

| x | z | Lip | Ground round the mouth | Layers | Biome |
|---|---|---|---|---|---|
| -3743 | 4870 | 135 | 132 to 175 | 6 | forest |
| -7568 | -1315 | 97 | 83 to 115 | 4 | forest |
| 7716 | 4902 | 98 | 99 to 158 | 4 | grove |
| 1343 | 9938 | 98 | 93 to 154 | 4 | frozen peaks |
| 8722 | -5796 | 120 | 117 to 170 | 5 | stony peaks |
| -8845 | 5752 | 96 | 99 to 142 | 4 | plains |
| 3812 | 10625 | 98 | 101 to 173 | 4 | grove |
| -6501 | -9514 | 107 | 108 to 129 | 4 | meadow |

(A lip can be over the lowest ground of the mouth by more than the dip where that is a single column, which is not counted.)
The game runs the same code on the same density, so these should be where `/locate` leads.

*Domes and the ground,* at those eight holes, every second column out to the outer radius where the ground is under the
lip: of 134,252 columns a dome reaches the surface in 8 and is under less than 4 blocks of ground in 11. Before, 49 of 259
points probed over domes were air.

*Cost.* The carve and its rock together at one block inside a hole's reach: 384 ns before, 510 ns under a lip at 96, 588 at
128 and 747 at 160, where there are seven layers. Deciding one cell (land check and lip) takes 5 ms on average; a hole's
discs with their ground 7 to 29 ms, once.

**What the pictures showed.** Sections and views from above of real holes were drawn from the same carve.

- On a ridge (the hole at -3743, 4870) the old shape is a shaft 90 wide through 95 blocks of mountain with three layers
  under it; the new one starts under the ridge, with six layers over 175 blocks and a small bowl on the high side.
- On near-level high ground (the meadow hole) the bowl is an uneven collar 5 to 30 blocks wide and the discs frame an
  opening instead of covering the mouth.
- On a steep slope (7716, 4902: 59 blocks across the mouth) the bowl is a slope 50 blocks high and about 40 wide on the
  uphill side, a large scar. `low_share` 0.25 or 0.5 puts the lip 23 or 50 blocks higher there: the cut shrinks, the hole
  deepens, and the downhill side of the mouth is the ground itself, with the top layers left out on that side. It also
  brings the nearest holes on the owner's seed to 1,300 blocks. Which reads better is the owner's call.

**Verification.** Gradle does not run in this session's sandbox, so `./gradlew build` was not run. What was run: `common`'s
main and test sources compiled with JDK 21 `javac` against the cached jars, with no warnings under `-Xlint:all`, and its
153 unit tests pass (127 before and 26 new; two theme tests now bind a level whose top is 160). New tests: the lip from the ground (level, sloping, one
pothole, a valley, `low_share`, the level's top as the highest), once per cell; the layer counts by lip on vanilla height
and Larion's; no ring disc dropped under level ground; domes under falling ground and a cliff; roots under low ground (it
fails without the check); the bowl (open over it, solid under it, no step larger than its slope out to the outer radius,
9 to 12 blocks of cut on level ground, nothing past the outer radius or the reach); the clear air and no rock in it with
every disc hanging; the settings' limits; a bound carve giving two cells different tops; `TerrainSurface` on a made-up
density; the shipped file against the tests' cone; the testing packs.

Not verified: the Loom build and both loader modules; anything in game, on a dedicated server or on Larion; the hook's
second call of the wrapped `RandomState.create` (whether MixinExtras and other mods' wraps of the same call take a second
call well); the final density being computable at a point under C2ME or with Larion's functions; chunk generation time.
To measure in game: the probe against the F3 height at 100 columns round three holes; how much of the bowl is grass and how
much bare stone; the per-sample cost and the time of the first chunk of a hole; the views V1 to V6 of the plan.

Limits: the mouth is still an exact circle and the wall an exact surface of revolution (wall noise is phase 4). A hole's
place in its cell is drawn from the hash before the ground is known, so a cell can only be taken or left: that is why holes
are rare and mostly on slopes. Choosing the best place in a cell needs the city's placement to know the level's terrain,
which `getPotentialStructureChunk(seed, x, z)` does not give it; that is the next thing to design if rarer, steeper holes
are not what is wanted. Cells of 1024 can put two holes 420 blocks apart. The old reference hole near 956, -499 is gone.
The log lists holes within 3,072 blocks of the origin, which on vanilla is often none.

### No holes near sea level, a taller cone, a mouth that follows the ground, and uneven walls

Owner (2026-10-07): add some noise to the cavern walls, make the entrance integrate into the landscape better, no chasms at
sea level, and as much total height for the cone as possible. Their calls on the plan: "20 above sea level for now"; the
"cavern walls" are the edge of the main cut, not the discs; the floor is good at 24 over the world's bottom.

The jar the owner had been testing was built on 2026-10-06 at 17:08, before the section above was written, so the per-hole
top, the bowl and the opening had not been seen in game when this was built on them. Still nothing here has.

**What changed.**

- **A hole needs ground 20 over the sea all round its mouth** (`rim.min_above_sea`, in place of `min_top`). It is counted
  from the level's own sea level and asked of the ground at the mouth's 24 columns, each still taken as the middle one of
  itself and its neighbours, so one pothole does not count and two next to each other do. It alone decides whether a cell
  holds a hole; how high the lip is no longer does. A cell on low ground is given up after two columns of ground.
- **The lip is under the middle ground** (`rim.low_share` 0.5, and it may now go to 1). Half the mouth's edge is then lower
  than the lip: there the edge is the ground itself and the top layers of discs are left out, by the check that keeps a dome
  under the ground over it.
- **`top` is 64 under the level's top** (`below_top`: y=255 on vanilla height, 447 on Larion's) instead of 160, so a lip is
  as high as its ground. 12 layers fit under a lip at 255 on vanilla height.
- **The bowl starts from the ground on each side** (`RavineBounds.edge`, `ConeShape.bowlDistance`). A hole keeps the height
  of its mouth's edge at each of the 24 columns: `dip` under the ground there, nowhere under the lip. The bowl's surface
  rises from that, blended between columns, where it rose from the lip all round. So on the uphill side the hole's wall runs
  straight up to the ground, and only the ground right at the mouth is cut back.
- **The wall is uneven** (`wall_noise`, `WallNoise`). Each layer moves the wall along the line out from the axis by up to its
  amplitude, differently at each angle and height; the three shipped are lobes (90 blocks apart, 6), runnels (16 apart round
  the hole and four times that up it, 2.5) and a grain (6, 1): 9.5 blocks at most. It moves the cone's wall, the cavern's
  wall where that shows, and where the mouth and the bowl start, so the mouth is no longer an exact circle. Discs, domes,
  stems and roots are exact as before. The values are the cell hash's smooth values in columns that close on themselves round
  the hole, each column slid up by an amount of its own so that bulges never line up in rows; a hole's wall is its own and
  nothing is seeded or bound. Further from the wall than the unevenness and the falloff reach, it is not worked out.
  Settings that would bring the wall into the clear air at the top, or past `outer_radius` at the floor, are rejected, and so
  is `wall_noise` on a ravine.
- **What follows the wall.** A root hung from the wall is anchored where the uneven wall is over its axis
  (`ConeShape.wallOver`: the lowest rock over the column), and the cavern's biome reaches sideways as far as the wall may be
  moved out.
- **A hole's discs are looked up by column** (`DiscIndex`: squares of 16 columns, each with the discs within the falloff of
  it). A hole under a lip at 255 has over a hundred discs, and every block used to look at all of them.
- **The ground over a hole is read every 8 blocks**, not every 16 (`HoleGround.GRID`), for the domes' ceilings: with the lip
  higher than the downhill ground more domes lie under steep ground (figures below).
- **Settings as shipped:** `top` `below_top` 64; `rim.min_above_sea` 20, `rim.low_share` 0.5; `wall_noise` as above. The rest
  is as it was. Testing packs `low-lip` and `high-lip` (`low_share` 0 and 1) and `smooth-walls` (no `wall_noise`).
- **The world-load log** gives each hole the highest its mouth's edge stands, and the cone's line gives the wall's layers.

**Measured on vanilla terrain, outside the game,** as in the section above (vanilla's overworld from the 1.21.1 jar, the mod's
carve bound to it, the land check by the biomes of `#is_ocean`, `#is_river` and `#is_beach`; scratch programs, not in the
repository).

*How many cells hold a hole, and how high* (seeds 20261003, 1, 2 and 3; 2,304 cells of 1024):

| `low_share` | Cells with a hole | For each square of 2,048 | Lip: 10%, median, 90%, highest | Edge over the lip: median, 90%, most | Layers: median, 90%, most |
|---|---|---|---|---|---|
| 0 | 6.7% | 0.27 | 82, 90, 114, 136 | 27, 55, 77 | 3, 5, 6 |
| 0.5 (shipped) | 6.7% | 0.27 | 90, 108, 138, 158 | 11, 31, 48 | 4, 6, 7 |
| 1 | 6.7% | 0.27 | 101, 123, 160, 192 | 0, 0, 0 | 5, 7, 8 |

It was 2.7% and 0.11 with `min_top` 96, and 0.3 for each square before the top followed the ground. The cap of 255 is never
reached on vanilla. Deciding a cell takes 1 ms where it holds no hole and 12 ms where it does.

*The nearest holes on seed 20261003* (30 within 12,288 blocks of the origin each way, 14 before), and what the bowl takes out
of the ground over the lip, further than 56 blocks from the axis: how deep at most, and about how many blocks. The last
column is the same hole with the bowl started from the lip all round, as it was.

| x | z | From the origin | Lip | Ground round the mouth | Edge up to | Layers | Bowl: deepest, blocks | From the lip all round |
|---|---|---|---|---|---|---|---|---|
| 4878 | 380 | 4,893 | 104 | 84 to 161 | 152 | 4 | 16, 2,900 | 54, 64,800 |
| 4692 | -3851 | 6,070 | 113 | 93 to 125 | 122 | 4 | 2, 30 | 7, 750 |
| -3743 | 4870 | 6,142 | 148 | 132 to 175 | 163 | 6 | 41, 10,700 | 46, 18,200 |
| -7568 | -1315 | 7,681 | 102 | 83 to 115 | 111 | 4 | 5, 260 | 13, 2,100 |
| -6662 | -5343 | 8,540 | 93 | 83 to 110 | 105 | 3 | 0, 0 | 11, 3,000 |
| 7716 | 4902 | 9,142 | 141 | 99 to 158 | 154 | 6 | 16, 3,300 | 21, 11,100 |
| 5429 | -7486 | 9,248 | 98 | 82 to 113 | 109 | 4 | 4, 210 | 16, 3,500 |
| 6388 | -7706 | 10,010 | 94 | 92 to 107 | 102 | 4 | 4, 270 | 10, 1,600 |

The hole at -3743, 4870 is on a ridge with a peak beside its mouth, which the bowl still cuts back to its slope. The steep one
at 7716, 4902 had its lip at 98 and four layers; with the lip at 141 the low side of its mouth is 42 blocks under the lip, open
to the hillside. The nearest hole to the origin on this seed is 4,900 blocks away.

*Domes and the ground,* at those eight holes, every second column out to 200 blocks from the axis (251,336 columns):

| Ground read every | A dome opens the ground's top block | A dome under less than 4 blocks | A disc's rock over the ground | A hole's discs built in |
|---|---|---|---|---|
| 16 blocks | 21 | 18 | 40 | 13 to 52 ms |
| 8 blocks (shipped) | 11 | 7 | 13 | 44 to 164 ms |

All that is left at 8 blocks is at the steep hole at 7716, 4902.

*Cost,* in nanoseconds for the carve and its rock together at one block, taken at random inside a hole's reach from the floor to
40 over the lip (the shape alone, without finding the cell):

| Lip | Layers | Discs | Before the index | With it, even wall | With it, uneven wall |
|---|---|---|---|---|---|
| 96 | 4 | 39 | 595 | 163 | 299 |
| 128 | 5 | 43 | 639 | 169 | 301 |
| 160 | 7 | 67 | 969 | 200 | 330 |
| 200 | 9 | 85 | 1,193 | 218 | 348 |
| 255 | 12 | 114 | 1,566 | 256 | 388 |

The same through the bound functions, as the game calls them, at a hole with its lip at 147: 709 from the floor to the lip, 655
in the 40 blocks over it, 700 from there to the sky, 313 under the floor, 444 in the hole's cell past its reach, and 207 in a
cell without a hole. So more than half of what a block inside a hole costs, and all of what one outside costs, is finding the
cell and its answers twice (once for the carve, once for the rock), which this round did not touch.

*Pictures.* Sections and plans of the holes at 4878, 380 and 7716, 4902 were drawn from the carve over the real terrain: the
wall on the uphill side stands from the lip to the ground with the hill behind it, the outline between the discs' domes is
uneven, the domes themselves are exact arcs, and the rock added where the terrain had air is mostly where a disc crosses a
cave.

**Verification.** `common`'s main and test sources compiled with JDK 21 `javac` against the cached jars, with no warnings
under `-Xlint:all`, and its 172 unit tests pass (153 before and 19 new); the same at each of the three commits this went in
as, as far as each had come (158, 160 and 172 tests). `./gradlew build` was not run by this session: Gradle runs in the
sandbox now, but three tries each stopped at another Gradle instance holding the lock on `~/.gradle`'s cache. New and
changed tests: the sea rule (20 over the sea is enough and 19 is not, the
level's own sea, a shore beside high ground whatever the lip, low ground two columns wide at every place round the mouth,
read once per cell and given up after two columns); the lip under the lowest, middle and highest ground; the mouth's edge on a
slope and its blending; a level's top as the highest; layers up to a lip at 255; on a hillside of one in one the wall
standing on the uphill side, the bowl starting at the edge there, and a cut of 12 blocks where the level bowl cuts 56; the
wall moved by no more than its amplitudes and by most of them somewhere, closing on itself, changing gradually, stretched
upwards, different in each hole; air turning to rock once along every line out from the axis up to the lip, and no rock of
the bowl without rock under or behind it; nothing opened past the reach, and the clear air open; the wall over a column
being its lowest rock; a root hung from the uneven wall reaching it; the cavern's biome; the settings' limits; smooth values
round a closed surface; every disc within the falloff of a column being listed for it, and the domes and the rock coming out
the same as over every disc; the shipped file against the tests' cone and layers; all eleven testing packs.

Not verified by this session: the Loom build and both loader modules (neither refers to anything changed here); anything in game, on a
dedicated server, on Fabric or on Larion; everything listed as not verified in the section above, which this is built on.
`below_top` resolving as `genDepth - 1 + minGenY - 64` was read from the 1.21.1 class. To look at in game: the approach to a
hole on level ground and to one on a slope, and from the air; the wall from the lip and from mid-air (runnels that read,
no single-block crumbs); whether the bowl's cut is grass or bare stone; where `/locate` leads, and the log's lines for the
holes near the origin, which on Larion are the first figures there will be.

Limits: a hole on a steep slope is open to the hillside on its low side, by as much as the lip stands over the ground there
(42 blocks at one of the eight); `low-lip` is the same holes without that, and shallower. Holes are still taken or left
where the hash puts them, so they are as rare as high ground: about one for every four squares of 2,048 on vanilla. The
discs' domes cut exact arcs into an uneven wall. On a cliff a dome can still come out through the ground between two
columns that were read. The cell's answers are still found twice for every block of the world (the 207 ns above); keeping
them by the cell's coordinates, or once for a column, is the next thing to do for speed.

### Standing discs only, and a jungle floor of more than dirt

Owner (2026-10-07, after the round above in game): hanging discs "don't read as well in-game, lets only go with stems
underneath each disc for now"; the jungle disc should be "more than just dirt, something like dirt, silt, mud, and mossy stone
bricks", and they are open to suggestions. They also said the bottoms of the discs look great, the mangrove's most of all, and
that the foliage on the jungle and mangrove discs does not: it is dark, and either barren or too dense. For that they asked
for suggestions, which are not in this section.

**What changed.**

- **Every disc stands on a stem.** `hang_chance` is 0. The roots and what places them are still there, behind the setting.
- **The jungle disc's top is a patchwork.** Its first `top` layer was grass. It is now one `noise_provider` over the same
  three blocks of dirt, whose list runs from mossy stone bricks through mossy cobblestone, coarse dirt and podzol to grass,
  and on through moss to mud. The noise picks a place in the list, so neighbours in the list are neighbours on the ground: a
  floor of stone bricks has a ring of cobblestone, then bare and dark earth round it, and a patch of mud has moss round it.
  Vanilla has no silt; coarse dirt and podzol stand in for it.
- The `low-lip`, `high-lip` and `smooth-walls` packs follow the mod's file in both. `cone`, `tuned-themes` and `fixed-top` are
  older pictures of it and keep their hanging discs and plain grass.

**Measured outside the game.** The provider's own noise, read through the provider over 12,000 blocks each way, lies under
-0.4 for 4.5% of blocks and over 0.3 for 10%, evenly either side of 0. From that the list of 20 was laid out, and over four
squares of 160 blocks it gave: grass 49%, podzol 14%, moss 10%, coarse dirt 10%, mud 9%, mossy cobblestone 5%, mossy stone
bricks 4%. Drawn from above, the patches are 10 to 30 blocks across.

**Verification.** `common` compiled with JDK 21 `javac` with no warnings and its 172 unit tests pass; the shipped cone the
rim's tests run on has no hanging discs now, and the tests of roots set their own chance. `./gradlew build` still did not run
in the sandbox: a live Gradle process outside it holds the lock on `~/.gradle`'s cache, and a build inside the sandbox cannot
ask it to let go.

Not verified: anything in game. To look at: whether the rings round a stone floor read as an overgrown ruin or as contour
lines, how the jungle's own grass and ferns sit on the podzol, moss and mud, and whether 10% of the ground as stone is too
much or too little.

### Light on the jungle and mangrove discs, fewer trees, and ground cover of the mod's own

Owner, the same day, on the foliage of the jungle and mangrove discs being dark and either barren or too dense: asked for
suggestions, and of those offered chose glow berries from the leaves, shroomlights as fruit on the jungle's trees, froglights
and sea pickles in the mangrove, fewer and larger jungle trees, and ground cover placed by the mod. They left out an
invisible fill light and softer mangrove groves.

**What the settings showed.** Nearly all of a disc's light was under it, which is why the undersides looked good: glow berries
every 8 blocks under a jungle disc, every 16 in patches under a mangrove's. On top the mangrove had no light at all, and the
jungle's `glow_berries_canopy` asked for a sturdy face over each vine, which a leaf does not have (checked against the 1.21.1
blocks), so it only ever placed under logs and rock. A jungle disc started a tree for every 13 blocks of ground wherever one
fitted, and nothing but ferns where the dome was too low for one. The mangrove's trees stand in groves by design, with bare
mud between.

**What changed,** all of it in data.

- **Glow berries hang from leaves** on both themes: `glow_berries_canopy` takes a leaf over the vine as well as a sturdy face,
  and reaches from 2 to 28 blocks over the ground instead of 3 to 16. The feature that places the vine does not ask whether it
  can stay, and a cave vine only asks when the block over it changes; so these hold until their leaf is cut or decays, and
  then drop.
- **Shroomlights under the jungle's crowns and froglights under the mangroves'**, hung by each tree's own
  `attached_to_leaves` decorator as a mangrove hangs its propagules: a chance for each leaf with air under it (0.05 on the
  giant jungle tree, 0.03 on the ordinary one and the two larger mangroves, 0.02 on the small), and no two within 3 blocks.
- **Froglights set into the mangrove's mud** in twos and threes, level with the ground (`froglight_bulbs`), and **sea pickles
  on the beds of its ponds and streams** (`sea_pickles`).
- **Fewer jungle trees:** a giant for every 120 blocks of ground (36) and an ordinary tree for every 45 (20), which is one
  tree for every 33 blocks where it was one for every 13. Clumps of bamboo (`bamboo_clump`), which grows as tall as its dome
  lets it, for the low parts.
- **Ground cover:** on the jungle, patches of moss carpet (on the stone floors too) and azaleas, with the ferns and grass it
  had; on the mangrove, which had none of its own, grass, ferns and moss carpet, big dripleaf, blue orchids, and lily pads on
  the water. What a disc inherits from its parent biome still grows as well, where the pack in use lets it.

**Verification.** `DiscFeatureFilesTest` is new: it loads the mod's 23 feature files beside vanilla's data with the game's own
registry loader, which is what refuses a world when a file is wrong; the same loader was seen to refuse a copy with one
number out of range.
`common` compiles with JDK 21 `javac` with no warnings and its 173 unit tests pass. Read from the 1.21.1 jar: shroomlights and
froglights give 15 light, a cave vine with berries 14, sea pickles in water 6 to 15 by their number; leaves have no sturdy
face; mud is in `#minecraft:dirt`. `./gradlew build` did not run, for the reason in the section above.

Not verified: anything in game, so none of the densities. They were set by counting tries for each block of ground, not by
looking: about one try of moss carpet for every 4 blocks of jungle ground, grass or fern or carpet for every 2 of mangrove
mud, an azalea for every 25 and a stalk of bamboo for every 17 at most, a cluster of froglights for every 200 blocks of mud.
How many of each tree's leaves take a light depends on how many have air under them, which was not counted. Blocked ground
has not been measured again since the trees were thinned; azaleas and bamboo block, the rest does not. On a pack that keeps
the parent biomes' own plants, lily pads and grass come twice over.

### Layers at the rim of a jungle disc

Owner (2026-10-08, from the game): "the edge of the jungle disc rings look bad when they are 3 layers of dirt, the mangrove
layers look really good though. Make jungle discs have dirt/mud/silt/mossy-brick/mossy-cobble layers in a logical order (discs
are generally 3 blocks thick so it would need to pick between those blocks)".

**What the settings showed.** A platform is 4 blocks (`floor_thickness`). The jungle's was its patchwork over three blocks of
dirt, and the side of a grass block is dirt as well, so its rim was four bands of one brown. The mangrove's is two of mud,
one of packed mud and one of roots.

**What changed,** all of it in data.

- **Three layers of one block each under the patchwork,** three of the owner's five, always in the order they gave from the
  top down: dirt, mud, clay, mossy stone bricks, mossy cobblestone. Read as soil, then what water left on it, then what was
  built there, with the cobblestone as the footing under the bricks.
- **Clay is the silt,** which vanilla does not have. Packed mud is nearer in name, but beside dirt it is nearly the same
  brown, which was the complaint, and it would make a jungle rim a mangrove's. Clay is the one pale band of the five. It is
  one id in three lists to change.
- **Which three is chosen by one noise, not a draw for each disc.** Each layer is a `noise_provider` with the same seed and
  noise, so all three read the same place of their own list of 12, and the lists are written place for place. There are six
  stacks: dirt, mud, clay; dirt, mud, bricks; dirt, clay, bricks; dirt, bricks, cobblestone; mud, bricks, cobblestone; clay,
  bricks, cobblestone. A theme has one palette, so a draw for each disc needed either six jungle themes with the same growth
  list or a new part of the palette's schema; this needed neither. What it costs is that a rim is not one stack all the
  way round. The noise is slow (`firstOctave` -8) to keep that to a few changes.
- A provider's noise takes the block's height too, so a layer reads nearly but not exactly what the one over it reads. The
  lists allow for it: no block comes later in the order than the one under it at the same place or the place either side.
- The `low-lip`, `high-lip` and `smooth-walls` packs follow the mod's file. `cone`, `tuned-themes` and `fixed-top` are older
  pictures of it and keep plain dirt.

**Measured outside the game,** through the providers as the painter asks them, round the rims of the 1,737 jungle discs in
64 holes of one seed (vanilla height, tops at 160):

| | `firstOctave` -6 | -7 | -8 (chosen) |
|---|---|---|---|
| Stacks that take over 5% of a rim | 4.5 | 3.3 | 2.2 |
| Changes on the way round a rim | 13.0 | 6.6 | 3.2 |
| Different main stacks among one hole's jungle discs | 5.9 | 5.5 | 4.7 |

At -8 the six take 15%, 16%, 22%, 22%, 15% and 11% of the rims, in the order above. No block lay over one that comes
before it in the order. Where bricks under clay change to bricks over cobblestone the two layers change a block apart, so
0.2% of rim blocks are dirt over two of bricks and another 0.2% dirt, clay, cobblestone: the only cobblestone not under
bricks.

**Verification.** A new unit test reads the mod's own file and asks the three layers for their blocks under nearly 400,000
points of ground: never out of order, never one block three deep, and all five found. It was seen to fail with one entry
of the middle list moved a place. `./gradlew build` ran in the sandbox: 174 unit tests pass and both loaders' jars are
built. The two sections above say Gradle did not run. When the three rounds were committed, each commit was checked out
and its unit tests run: 172, 173 and 174 pass.

Not verified: anything in game, on either loader; no server was started this round. To look at: whether a rim that changes
its stack part of the way round reads as strata or as patches (`firstOctave` -9 should give fewer changes still; it was
not measured); clay as the silt; whether masonry at the bottom of five rims in six is too much stone for a jungle; and
the beds of the jungle's ponds, which are now mud, clay or bricks where they were dirt.

### Ruins on discs

Owner (2026-10-08): "Lets add structure generation and ruins to the discs. Not every disc gets ruins, ruins should generally be
jungle themed, we might want to consider increasing the average vertical distance between rings to help accomadate structure
placement."

**What was built.**

- **`ruins` in a disc theme** (`DiscRuins`): the share of the theme's discs that have any (`chance`), how many a disc has
  (one for every `every` blocks of its top, at least one, at most six), and the `kinds` a ruin can be. A kind is a template
  pool and the room its pieces need: a round of ground (`radius`), clear air over it (`height`), and how many of a piece's
  lowest layers lie in the ground (`sink`). Jungle discs have ruins on three in five, lush on three in ten, mangrove on one in
  four, crystal none. `dev-datapacks/README.md` has the fields and the mod's ten kinds.
- **Where they stand is decided with the discs** (`DiscRuinSites`, pure code in `ravine`, kept with the cell's discs and so
  found once for a hole). For each ruin eight places are drawn on the disc and the kinds are put in an order drawn by weight;
  the ruin is of the first kind in that order with room at one of the places, and is left out if none has. A place has room
  when the round is 2 blocks inside the rim and 2 from another ruin, its ground steps by at most a block, at most a fifth of
  it is water, no stem or root of another disc comes through it, and the air over it is open to the kind's height. The air is
  asked of the carve itself, at the middle of the round and on two rings of eight columns, at least a block from any rock:
  that covers the dome's roof where the disc is in the wall, the hole's own wall, and the platform and flare of whatever disc
  is above, without a rule for each. Stems are thinner than the gaps between those columns, so they are looked for by name.
- **A weight is a preference, not a share.** A tall kind only has room on few discs, so it is given a large weight and
  stands wherever there is the height for it; where there is not, the next kind in the order takes the place. The tower has
  the largest weight on jungle discs and is still the rarest ruin.
- **A real structure** (`overgrown_abyss:disc_ruins`, `DiscRuinsStructure` and `DiscRuinsPlacement` in `compat`): a chunk's
  start holds one piece for every ruin whose middle is in the chunk, since the game makes one start of a structure in a
  chunk and discs lie over one another. Each piece is one element of its kind's pool, drawn and turned by the site's own
  seed and put with the middle of its lowest layer on the site. Vanilla's jigsaw placement is not used: it turns the first
  piece about its corner, or about a named jigsaw block the vanilla templates do not have, and a ruin has to stay in the
  round found for it. So a ruin is one piece and nothing is joined on to it. The placement extends vanilla's random-spread
  one, as the city's does and for the same reason: `/locate structure overgrown_abyss:disc_ruins` works.
- **The pieces are vanilla's own templates, by id,** as the city's are (`03-abyss.md`: do not redistribute vanilla templates).
  Two families: the free-standing pieces of the Ancient City, through the city's own reskin, which makes them outposts of
  the city below (camps, chambers, statues, pillars, rubble, and the four tall ruins as keeps and towers); and vanilla's cold
  ocean ruins as huts and houses, with their air, gravel and sand left out so that they stand in the disc's own ground.
  City chests on discs hold vanilla's jungle temple loot, not the city's. Left out: the barracks (a solid block 21 by 17),
  the sauna (29 by 37, with water) and the ice box.
- **Nothing about growth changed.** Structures are placed at `surface_structures`, before the mod's own growth at the end of
  decoration, so vines, leaves and ground cover grow round and over a ruin. A growth starts in the block over the disc's
  top, which a city piece's floor fills, so nothing of the theme's own starts on those floors. An ocean ruin's floor is level
  with the ground, and a tree may start on one.

**The distance between rings: measured, and left at 20.** With a rim the layers are already spread evenly up to the top, so
they are 21 to 25 apart in most holes. Over 24 holes outside the game (vanilla height, the mod's themes and ruins):

| Top at 160 | `layer_spacing` 20 (kept) | 24 | 28 |
|---|---|---|---|
| Layers, discs to a hole | 7, 65 | 6, 55 | 5, 43 |
| Ruins to a hole | 30.8 | 26.3 | 22.0 |
| Towers (19 high) | 0.7 | 0.6 | 1.4 |
| Keeps (13) and vaults (10) | 1.6 and 1.8 | 1.3 and 1.2 | 1.5 and 1.0 |
| Camps (5 high, 25 across) | 3.3 | 2.9 | 2.1 |

Under a top at 110 the 20 and the 24 give the same four layers. What ends the clear air over a disc is about as often its own
dome's roof as a disc above it (a round 25 across, the best of twelve places on each disc: at 20 a tenth of discs have 19
blocks clear, three in ten have 13, half have 10; at 28 a quarter, four in ten and six in ten, of a third fewer discs). So
28 doubles the towers, from 0.7 to 1.4 a hole, for 22 of the hole's 65 discs, and the lower kinds lose more discs than they
gain room. What did limit the wide ruins was water: asked for wholly dry ground, fewer than half as many camps found room
(1.4 a hole), because a round 25 across mostly meets one of the jungle's streams. A stream may now run under a ruin. The
`tall-rings` pack is the mod's file with 28, for judging the look. If larger buildings come (the temples of section 9), the
number to look at first is the dome's height (`height_ratio`, `min_height`), not the spacing.

**Verification.**

- Unit tests, 183, pass (`./gradlew build` in the sandbox, both loaders' jars built). New: `DiscRuinsTest` (the schema; the
  order of kinds; over four holes of the mod's own settings every ruin inside its rim, on ground that steps at most a block,
  apart from the others, clear of stems, and with open air at every block over its middle and its edge; the same with half
  the discs hanging from roots; the shares of discs with ruins; streams and ponds; every ruin handed to exactly the chunk
  its middle is in) and `DiscRuinFilesTest` (each of the 54 templates the mod's pools name, read from the game's jar,
  against its kind's `radius`, `height` and `sink`). Each rule was switched off in turn and seen to fail a test.
- Dedicated dev servers, in the sandbox for the first time (`runServer` works there; typed commands reach it), seed 11, the
  mod's own settings, the whole hole at x=-1572 z=2594 (top y=134, six layers) generated by `forceload` and the saved region
  files read back:

| | NeoForge 21.1.252 | Fabric 0.19.5 |
|---|---|---|
| Boots; pools, processor lists, structure and set load | no errors | no errors |
| `/locate structure overgrown_abyss:disc_ruins` from the centre | [-1552, ~, 2560], 39 blocks | the same |
| Starts and pieces in the hole | 11 and 11 | 11 and 11, the same templates, turns and places |
| Pieces with their blocks built, of those | 11 | 11 |
| Ground right under a piece, of its columns | 81% at least, 98% in the middle one | the same |
| Chests, all with `minecraft:chests/jungle_temple` | 4 | 4 |
| Deepslate, sculk, gravel, magma, sea lanterns, jigsaw blocks left | none | none |

  The eleven: four chambers, a vault, a camp, a pillar, two heaps of rubble, a house and two huts, from y=-2 to y=90. Their
  masonry is the same block for block on the two loaders (2,088 blocks); the leaves and vines round them are not, as before
  this round.
  `/locate` from the origin, with no hole within its 100 chunks, answers in under a second; `/locate` for the city still
  works.

Not verified: anything by eye, in game. To look at: whether the reskinned city pieces read as jungle ruins on a disc or as
pieces of the city out of place; ruins a block deep in the higher ground of a strongly bowled disc; a floor bridging a
stream; a pillar that just reaches the disc above it; how many ruins is right (a real hole of six layers had 11, where the
count outside the game, which has no ground to keep domes under, gives 18 for a hole of four layers and 31 for one of
seven); trees on the floors of ocean ruins.
Not run: Larion, the owner's pack, any hole but one, a client. A pack that replaces vanilla's templates changes these ruins
too (section 5, risk 5).

### Ruins by depth, measured pieces, and other mods' ruins

Owner (2026-10-09), after seeing the ruins: "This looks good so far. Today I want to explore making ruins more frequent the
towards the bottom, as well as allowing mod-added jungle-style ruins to spawn where there is space. One concern for spawning
ruins is that since the discs are only a few blocks thick we can't spawn anything that goes underground like the vanilla
jungle ruins."

**What looking first turned up.**

- **Ruins thinned toward the bottom, not the other way.** A theme's `chance` was the same at every height, and mangrove
  (0.25) and crystal (none) gather low. Over 24 holes outside the game the share of discs with ruins was 0.35 in the lowest
  third of a hole's height, 0.34 in the middle and 0.40 in the highest.
- **The owner's pack had already replaced the pieces** (section 5, risk 5, now seen). Dungeons and Taverns' Ancient City
  overhaul replaces all 18 city templates the disc ruins borrow. By yesterday's typed numbers 14 of them are taller than
  the air their kind kept, most by a layer; its small, medium and large "ruin" are buildings 10 high with a cellar of 4
  layers where vanilla's are heaps 3 high. So what the owner saw on discs was not what the dev servers and the tests
  built, and numbers typed for vanilla's templates do not hold in a pack.
- **Other mods' ruins in the pack.** Epic Structures: Jungle Temples is plain datapack jigsaw: `epic:epic_temple_ruin` is a
  pool of 14 standalone ruins, 11 to 41 blocks across and 7 to 27 high, each with a floor as its lowest layer; its two
  temples are 47 by 47 and 48 high. YUNG's Better Jungle Temples (`yungsapi:yung_jigsaw`) starts 25 to 30 blocks under the
  ground and cannot stand on a disc by any rule.
- **A jigsaw structure's own file says how deep it sits.** Read from the 1.21.1 bytecode of `JigsawPlacement.addPieces`:
  with `project_start_to_heightmap` the first piece's lowest layer is at the ground's highest block plus `start_height`
  (`getGroundLevelDelta()` is 1), so it has `1 - start_height` layers in the ground. A cellar's depth cannot be read from
  a template; it can from this.

**The owner's decisions**, asked before anything was built: the share of discs with ruins and the number on a disc both
grow toward the bottom, and grander kinds gather deeper; other mods' ruins come in through a structure tag that packs can
add to (not everything the jungle biome allows, and not named one by one in `carve.json`); a piece with layers meant to be
underground stands only where the rock is that deep (not on rock grown under it, which would change the undersides); every
template is measured, yesterday's kinds included.

**What was built.**

- **Pieces are measured when the level loads** (`DiscRuinPieces` in `compat`, handed to the pure code as `RuinPieces`). A kind
  in a theme is now a pool, a weight and a `sink`; `radius` and `height` are gone from `carve.json`. For each element of a
  kind's pool that is one template, the template is saved to its NBT form and read: its footprint gives the round of ground
  (half the diagonal, since a piece is turned about its middle), and the layers up to its highest block give the air. So a
  pool whose pieces differ in size, as Epic's 14 do, is tried piece by piece, and a replaced template is measured as it is.
  The kinds are still put in an order drawn by weight; within a kind the pieces are put in an order drawn by their weights in
  the pool, and the ruin is the first piece with room. A site names its pool and which element of it the piece is.
- **No new mixin for it.** The level's template manager is an argument the `ChunkMap` constructor already has, taken by the
  mixin that was there. What the game has no accessor for is read through its own codecs, as the files would say it: which
  template an element is (`location`), a pool's elements and weights (counted in a copy shuffled with a fixed seed, since a
  pool keeps an element once for each of its weight), and where a structure starts.
- **Room below is asked of the carve, like room above.** The layers of a piece that lie in the ground must lie in rock at the
  middle of its round and on the same two rings of eight columns: rock the mod puts back for a disc (its platform, the flare
  of its stem) or ground the carve never opened (where the disc lies in the hole's wall). The carve is worked out for each
  block, so this needs no margin. With a platform 4 thick, one layer always has rock under it and two nearly always; more
  only over the flare or in the wall. A piece with more than the platform holds is tried in the middle of its disc first,
  where the flare is. Nothing was added to the carve, and no underside changed.
- **`by_height` on a theme's ruins and on each kind** (`bottom` and `top`, reusing the theme's own ramp and a disc's height
  among its hole's discs). On the ruins it multiplies both the share of discs and the number on a disc; on a kind, its
  weight. The mod's numbers, all first guesses: ruins 1.6 to 0.4 on jungle and 1.8 to 0.4 on lush and mangrove; tower and
  keep 2 to 0.3, vault 1.8 to 0.5, house 1.5 to 0.7; camp 0.7 to 1.4, hut 0.6 to 1.6, rubble 0.5 to 2.
- **`structures` in a theme's ruins: a tag of other structures.** Each structure in the tag that starts on the ground from a
  template pool at one fixed depth is a kind: its first piece, alone, `1 - start_height` layers in the ground. Others are
  left out with the reason in the log. The mod's tag `#overgrown_abyss:on_discs/jungle` holds Epic's three structures, all
  optional, and is named by the jungle theme (weight 15) and the lush one (5). The pieces keep their own processors and loot.

**Two things that were not in the plan, found while building it.**

- **Ruins of neighbouring discs were not kept apart.** Discs of one layer run into one another (two of the lowest layer in the
  first test hole are 57 blocks apart with radii of 51 and 37), and yesterday's code only kept a ruin clear of the others on
  its own disc. Once the tests knew which disc each ruin stands on, they showed a house and a pillar of two such discs with
  a block and a half between their rounds, where 2 are kept between the ruins of one disc; larger pieces could have met. A
  ruin now keeps clear of every ruin of its hole whose piece shares a height with it.
- **Air in a template is placed, and empties what is there.** Vanilla's city pieces hold no air and the mod's ocean ruins
  leave theirs out, so yesterday's measure (the highest block that is not air) was right for both. It is not for other
  mods' pieces: Epic's are saved with the air of their whole box, and the plate of a pillager outpost builds two layers
  under a box of air 30 high, which stood on a dev server's disc as if it needed 1 block of room. A piece now needs room up
  to its highest air, unless its pool's processors leave air out (a `block_ignore` naming air, read from the pool's file).

**Where the plan's account was off.** Places with deep rock are not rare. In the tests, of 607 places where a small piece
found room on the ground, 404 still had room for the same piece with five layers in the ground: the middle of most discs,
and 250 places away from it, since much of many discs lies in the wall. The rule is as the owner chose it; it bites less
often than "only over the stem's flare or in the wall" sounded. And vanilla's trail ruins, put in the testing pack to be
kept off by that rule, never reach it: none of their five towers is taller than the 16 layers their structure buries, so
they are refused when measured.

**Measured outside the game**, the same 24 holes each time (seed 11, vanilla height, the mod's themes, vanilla's templates):

| | Yesterday | Measured pieces, no ramps | With the ramps (shipped) |
|---|---|---|---|
| Ruins to a hole | 35.5 | 36.9 | 43.9 |
| Share of discs with ruins: lowest, middle, highest third | 0.35, 0.34, 0.40 | 0.36, 0.35, 0.43 | 0.52, 0.35, 0.25 |
| Ruins to a hole in each third | 12.9, 11.7, 11.0 | 13.1, 12.3, 11.5 | 25.6, 12.9, 5.4 |
| Mean height in the hole (0 bottom, 1 top): tower, keep, vault | 0.45, 0.55, 0.49 | 0.46, 0.52, 0.59 | 0.26, 0.30, 0.25 |
| The same: camp, hut, rubble | 0.49, 0.43, 0.52 | 0.51, 0.44, 0.47 | 0.43, 0.33, 0.37 |

Finding a hole's discs and ruins took 12 to 15 ms in all three, no more with pieces than with kinds. Measuring alone changes
which kinds find room: houses went from 2.0 to 5.3 a hole and pillars from 2.8 to 4.3, because a kind's lower pieces are
no longer held to the room of its tallest. Every kind stands lower with the ramps, since all ruins
do; the grand kinds by more.

**Verification.**

- Unit tests, 196, pass, and both loaders' jars build (`./gradlew build` in the sandbox). `DiscRuinsTest` now has the ramps
  (share and count by thirds of a hole, against the same theme with no ramp), a kind that gathers where its weight is, a
  piece with layers in the ground seen to have rock under every sampled column, a piece far too tall leaving every place to
  another of its kind, and ruins of neighbouring discs apart. `DiscRuinPiecesTest` loads vanilla's own pools and structures
  with the game's loader and checks what the level-side code makes of them: elements and weights, measures against the
  templates' files, the place of an element leading back to it, air placed or left out (a small pack of the tests' own),
  pieces with nothing over the ground, and which structures lend a piece and how deep. `DiscRuinFilesTest` checks the
  measure itself against the jar's templates and that the mod's tag holds only optional entries.
- Each rule was switched off in turn and seen to fail a test: rock under a piece, the share by height, the count by height,
  a kind's weight by height, clear of other discs' ruins, the stem first, room for air, processors that leave air out
  (named and written in place), nothing over the ground, a structure's depth, a start on the ground, the element a site
  names. Two were not caught at first (the count by height, and the processors), and their tests were made to.
- Dedicated dev servers in the sandbox, seed 11, the whole hole at x=-1572 z=2594 generated by `forceload` and read back from
  the region files:

| Run | NeoForge 21.1.252 | Fabric 0.19.5 |
|---|---|---|
| The mod alone: pieces in the hole (yesterday 11) | 26 | 26, the same templates, turns and places |
| Of them under y 20, from y 20 to 79, above | 10, 15, 1 | the same |
| Columns of a piece's floor with air under them | none | none |
| `/locate structure overgrown_abyss:disc_ruins` from the hole's centre | 26 blocks away | the same |
| `borrowed-ruins` pack: pieces | 51 | 51, the same |
| Of them village centres, outpost plates, trail ruins | 35, 4, none | the same |
| Epic Structures: Jungle Temples 1.0.2 added for the run: pieces | 26, of them 3 Epic's | the same |
| Chests and brushable blocks in Epic's pieces | 5 and 14, with the loot tables its templates name | the same |
| Dungeons and Taverns' overhaul added for the run | measured at its own sizes (up to 20 blocks of air on jungle discs, where vanilla's need 19); 26 pieces, none over air | not run: the jar is NeoForge's only |

  With no other mod the log has no warning from the ruins, and one line for each theme: `Disc ruins of
  overgrown_abyss:disc_jungle: 10 kinds with 54 pieces, needing rounds 8 to 24 blocks across and 2 to 19 blocks of air`.

Not verified by the sandbox's runs: anything by eye, in game. Not run there: Larion, the owner's whole pack, a client.
The owner ran this build in their pack later the same day (NeoForge, with Larion, Epic and Dungeons and Taverns; the log
names the build) and said: "right now it doesn't look overcrowded". That log has no warning from the ruins. Each of Epic's
three structures lends its pieces with one layer in the ground, so jungle discs have 13 kinds with 72 pieces, needing
rounds of 8 to 67 blocks across and 2 to 47 blocks of air, and the three holes near the origin have 7 to 9 layers of discs.
Still to look at in game: whether the top is too bare; whether grander-deeper reads; Epic's ruins among the outposts, and
whether 15 is enough weight to meet them (3 in the test hole); a house or hut beside a ruin of the next disc.
Known and left: with Dungeons and Taverns its buildings stand on their cellars, since a replaced template cannot say how
deep it is meant to lie; a pack that wants them buried gives those kinds a `sink` in a `carve.json` of its own. Epic's two
temples are in the tag and fit under no dome (the tallest is about 29, they are 48): making room for them is a round of its
own (the dome's height, or a disc made for a temple). Only a structure's first piece stands, so one that is nothing without
its joined pieces lends little (an outpost lends its plate). The ground the carve never opened is taken for rock, and may
hold a cave of the terrain's.

### Treasure by a disc's theme, depth and distance

Owner (2026-10-09), on what the mod is for now: "the goal is making it look cool and provide loot/resources players would
want", and "I am trying to make sure there is a reason to explore the discs instead of just heading straight to the
bottom." Then: "Can we make different loot tables for each disc biome? My general idea is that you find better loot further
down, and further from the center of the hole", and "draft the loot tables".

**What looking first turned up.**

- **The treasure pointed straight down.** The city on the hole's floor has a table of the mod's own
  (`overgrown_abyss:chests/city`, of the Ancient City's grade), and every chest of a ruin on a disc held vanilla's jungle
  temple loot.
- **Few ruins had a chest at all.** Of the city pieces the towers, keeps, the vault and the chambers have one or two; camps,
  pillars, the statue and rubble have none. Vanilla's ocean ruins have theirs only as a data marker (a structure block
  whose `metadata` is `chest`), which the game fills from code that a template pool does not run, and a pool element drops
  structure blocks before its processors see them. So huts and houses had no chest, and a mangrove disc, whose ruins are
  houses, pillars, huts and rubble, had none at all. The test hole's 26 ruins held 8 chests.
- **The owner's pack can make use of tables of the mod's own.** It has Lootr, which refills chests by loot table or by the
  table's mod id (both lists are empty there today), and Artifacts, which adds its items to vanilla's tables by name.

**What was built.**

- **`loot` in a theme's ruins**: tables from the poorest to the richest, each with the rank a disc must have to hold it
  (`from`). A disc's rank runs from 0 to 1 among the discs of its hole and rises with its depth and with its distance from
  the hole's centre, by the weights `depth` and `distance`; both are the traits a theme is already chosen by. All the chests
  of a ruin hold the last table its disc's rank reaches. The table is found with the site, in the pure code, and is part of
  the site.
- **The structure sets it** (`DiscRuinsStructure.afterPlace`), once the pieces that meet a chunk are placed in it: every
  container of the ruin that came with a loot table is given the site's instead, and a chest with it is put at each `chest`
  marker of the piece's template, where vanilla puts one. A piece read back from a saved chunk is vanilla's own class and
  remembers nothing, so its site is found again by where the piece lies.
- **Only the theme's own kinds.** A piece borrowed from another structure keeps the loot its templates name, and a theme
  without `loot` is as before: jungle temple loot in the city pieces, no chest at a marker.
- **Nine tables**, `chests/disc/jungle_1` to `_3`, `lush_1` to `_3`, `mangrove_1` to `_3`: drafts from vanilla's items for
  the owner to edit. Each has valuables, what grows or lies on that kind of disc, and a prize that is mostly not there.
  Jungle is gold, emeralds and the jungle temple's trim; lush is copper, amethyst and a trail ruins trim; mangrove is what a
  swamp and the sea leave, froglights and the shipwrecks' trim. The third of each has diamonds, enchanted diamond gear and,
  rarely, an enchanted golden apple, which is about the city's grade.

**Choices that were mine**, for the owner to judge:

- **The mod picks the table, not the table itself.** A loot table's own conditions can ask a chest's biome and its height,
  but not its distance from the centre, and the height is a number of the world's, which differs between vanilla's heights
  and Larion's.
- **Where a table begins is written, not divided evenly.** With three equal shares of the rank the richest table had half
  the chests (168 of 342 on jungle discs over 24 holes), because ruins and the kinds with chests both gather low. With
  `depth` 2, `distance` 1 and tables from 0, 0.6 and 0.85, of 550 ruins with chests in 24 holes 180 held the first table,
  265 the second and 105 the third.
- **A chest stands exactly where its marker is.** Most markers are in a template's lowest layer, which lies in the ground
  here, so most hut and house chests are set into the floor with the lid level with the ground. One of the nine in the test
  hole is under a block of grass, as vanilla's are under gravel. Standing them on the floor instead is a small change.
- **One table for all the chests of a ruin**, and no step up for a grander kind.

**Verification.**

- Unit tests, 201, pass, and both loaders' jars build. New in `DiscRuinsTest`: the schema of `loot`, the rank and the table
  it comes to, and over four holes that every ruin of a theme's own kind has its disc's table and no borrowed one has any.
  `DiscLootFilesTest` reads each table the mod's file names with the game's own codec and vanilla's enchantments and tags.
- Each rule was switched off in turn and seen to fail a test: a borrowed ruin given a table, distance not counting, depth
  not counting, the first table reached held in place of the last, tables out of order let through, a table naming an item
  the game has not, one naming a tag of enchantments it has not, and the settings naming a table that is no file.
- The ruins themselves did not move: a checksum over the place, pool, element and seed of all 995 ruins in 24 holes is the
  same as the committed code's, built from a worktree of it.
- Dedicated dev servers in the sandbox, seed 11, the hole at x=-1572 z=2594, generated by `forceload` and read back:

| | NeoForge 21.1.252 | Fabric 0.19.5 |
|---|---|---|
| Pieces in the hole | 26, the same templates, turns and boxes as the committed build's world | 26, the same |
| Chests in ruins (were 8) | 17: 8 in city pieces, 9 at markers in huts and houses | the same places, tables and seeds |
| By table | `jungle_1` 1, `jungle_2` 10, `jungle_3` 2, `lush_2` 1, `mangrove_2` 2, `mangrove_3` 1 | the same |
| `/loot spawn` of each of the nine tables | each drops items; one that does not exist is refused | the same |
| `/locate structure overgrown_abyss:disc_ruins` from the hole's centre | 26 blocks away | the same |

  `afterPlace` has no unit test; the read-back is what shows it. The first run showed its one mistake: markers were asked
  for as places in the template, not in the level, so no hut had a chest.

Not verified: nothing was looked at in game, no chest was opened, Lootr was not run, and neither the owner's pack nor a
client. Seen and not looked into: between two worlds of one seed (two builds on NeoForge, and NeoForge against Fabric)
about a tenth of the blocks inside the ruins' boxes differ, all of them vines, leaves, ground cover and moss; pieces,
masonry and chests do not. One city piece's chest in the test hole has a dripstone block of the terrain's on it.
Known and left: camps, pillars, the statue and rubble have no chest; Artifacts' items no longer reach the disc ruins'
chests through the jungle temple's table, so a pack that wants them there adds a modifier for the mod's tables.
The numbers this is balanced by are in the table of levers in section 9, with those of the ideas that are not built yet.

## 8. Next steps

1. Review the rim, mid-air and floor views; tune carve and city numbers. For the cone: look at the new top, mouth, bowl,
   opening and uneven wall in game, settle `min_above_sea`, `low_share`, the wall's layers and how often holes come, then
   balconies and the drape (phases 5 and 6 of `docs/rim-walls-drape-plan.md`).
2. Wall styles (Phase 2) and `BiomeInjector`.
3. Decide whether lush caves features on the city floor suit the look, or whether the city should keep its own ground.
4. Extract `ravine-core` into a shared source module when Rift starts.
5. Disc ruins: look at them in game (the `many-ruins` pack puts them on every disc that has room, `borrowed-ruins` shows
   borrowed ones without another mod), settle the ramps by height, how many and which kinds, and the weight of other mods'
   ruins. Then pieces of our own, built for a disc rather than borrowed from the city: the placement takes any template
   pool and measures it, so that is templates and JSON only.
6. Room for a temple: Epic's are 48 high and the tallest dome is about 29. The owner's answer (2026-10-09) is a disc made
   for ruins, section 9.
7. Treasure: the owner edits the nine draft tables; look at where the hut and house chests sit; then, in the owner's pack,
   Lootr's lists for chests that refill.
8. The owner's goals of 2026-10-09 (section 9, with the levers of each): suggested next the penalty for going up or down
   too fast, then the disc made for ruins, then fights that reset and traps. The owner has not set that order.

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

- **On discs since 2026-10-09** (section 7, Ruins by depth): its jar was read, and its structures are in the mod's tag
  `#overgrown_abyss:on_discs/jungle` as optional entries. Its 14 standalone ruins stand on jungle and lush discs where they
  fit; its temples fit under no dome. What follows is the older idea of joining its pieces to the city, which is not started.
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
- **What it needs:** the honeycomb pieces themselves, as templates in a pool. The placement exists since the ruins round
  (section 7, Ruins on discs): a kind in a theme's `ruins` naming that pool, with the room it needs, puts it on lush and
  jungle discs.

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

### Reasons to explore the discs, and to come back (owner's goals, 2026-10-09)

Of what follows only the treasure is built (section 7, "Treasure by a disc's theme, depth and distance"). The rest is one
discussion: the owner's ideas in their words, each in an item of its own below, with what was suggested in answer and the
numbers each could be balanced by. "Suggested" marks what was proposed to the owner and not agreed. Of other mods, only what
an item says was read from a jar is known; nothing of theirs was run for these ideas.

- **The goals.** Now: "the goal is making it look cool and provide loot/resources players would want". "I am trying to make
  sure there is a reason to explore the discs instead of just heading straight to the bottom." And "a repeated reason to
  come back", for which "most of the reasons I can come up with ... involve integration with other mods".
- **The reasons the owner counts today:** "shroomlights/froglights, any item that comes from mangrove, jungle, or amythist
  discs, and the treasure from the ruins". Of these only what regrows where it is comes back by itself (budding amethyst on
  crystal discs, glow berries, vines, saplings); shroomlights and froglights are placed once, and are now in the loot
  tables too, with frogspawn in the mangrove ones.
- **The pack the mod is meant for** is "focused on create aeronautics and adventure, with cool biomes, dimensions, and
  bosses to fight", with artifacts and levelling, "so players can get quite a bit stronger that in vanilla".
- **One rank for a disc (suggested).** The rank that picks a disc's loot table, higher the deeper and the further from the
  centre, could also set how hard its fights are and how many traps its ruins hold, so that deeper and further out is both
  deadlier and richer. Today only `loot` has it, with weights of its own; when a second thing needs it, the hole should
  have one rank that all of them read.
- **Order suggested:** treasure (built), then the penalty for going up or down too fast, then the disc made for ruins, then
  fights and traps, which lean on the penalty. The owner agreed to the first.

**The levers there are today.** All in `ravine/carve.json` unless it says otherwise; `dev-datapacks/README.md` describes each.

| What it balances | Setting | As shipped |
|---|---|---|
| How many discs have ruins, and how many each | a theme's `ruins`: `chance`, `every`, `by_height` | jungle 0.6, 3000, 1.6 to 0.4; lush 0.3, 4000, 1.8 to 0.4; mangrove 0.25, 4000, 1.8 to 0.4 |
| Which kinds, and where in a hole | each kind's `weight` and `by_height` | the table in `dev-datapacks/README.md` |
| Other mods' ruins among the mod's own | `weight` of a tag in `structures` | 15 on jungle, 5 on lush |
| How many chests | follows from the kinds: only towers, keeps, the vault, chambers, houses and huts hold one | about 23 ruins with a chest in a hole of vanilla's height |
| Which discs hold which table | `ruins.loot`: `depth`, `distance`, and each table's `from` | 2 and 1; from 0, 0.6 and 0.85, which gives the tables a third, a half and a fifth of the chests |
| What a chest holds | the files in `loot_table/chests/disc/`: a pool's `rolls`, an entry's `weight` and count, the levels of an enchantment, the weight of `empty` in the prize pool | 2 to 4, 3 to 5 and 4 to 7 rolls of valuables; the prize pool empty 8 or 9 times in 10, 3 in 4, and 1 in 2 |
| What spawns on a disc | a disc biome's own spawn list over its parent's (`worldgen/biome/disc_*.json`), and a theme's `inherits.without_spawns` | nothing added, nothing left out |
| What is placed on a disc | a theme's `growth`: any configured feature, `every`, `patches` | the mod's trees, bushes and lights |
| Room between discs | `cone`: `layer_spacing`, `spacing`, `stack_chance`, `rider_chance`; `discs`: `min_radius`, `max_radius` | 20, 56, 0.4, 0.5; 20 and 64 |
| Room over a disc | `discs`: `height_ratio`, `min_height` | 0.45 and 14, so 29 over the widest disc |
| The clear middle of a hole | `cone.clear_radius` at the floor, `cone.upper.clear_radius` at the top | 8 and 20: from 16 blocks across to 40 |

Levers in the owner's pack and not in the mod: Lootr's `refresh_value`, `refresh_loot_tables` and `refresh_modids`; a loot
modifier that names the mod's tables; a datapack over any of the files above.

### Chests that refill, mobs that come back, fights that reset (owner's wish; the means are suggestions)

- **Idea:** "a repeated reason to come back", such as "increasing the spawn rate of mimics (from the artifacts mod) or
  providing places they can respawn". And: "fights that reset, exploring the chasm should be dangerous and should feel
  dangerous - even as players get good equipment".
- **Chests that refill (in the pack; not tried).** The owner's pack has Lootr, whose config refills chests by loot table or
  by a table's mod id, every `refresh_value` ticks (24000, 20 minutes); all its lists are empty there. Naming
  `overgrown_abyss` in `refresh_modids` would refill the city's chests at the bottom too; naming the nine disc tables in
  `refresh_loot_tables` refills only the discs'. This is why the disc ruins have tables of the mod's own.
- **Mobs on a disc (exists, unused).** A disc biome's own spawn list is merged over its parent's (section 7, "A disc biome's
  own spawns"), so a pack raises a mob's rate on a kind of disc with an entry in that biome's file. On NeoForge a biome
  modifier was seen to reach a disc through its parent biome; one aimed at the disc biome itself was not tried. Levers: the
  entry's weight and group size, for each kind of disc.
- **Mobs that keep coming back in ruins (exists, empty).** `spawn_overrides` in `worldgen/structure/disc_ruins.json` is
  empty. Filled, its mobs spawn inside the ruins' boxes in place of the biome's, as in a witch hut or an outpost, for as long
  as the ruin stands. It is one list for every theme, since the ruins are one structure, and a pack replaces that one file
  to name mobs of its own. Levers: the mob, its weight and group size, and `piece` or `full` for the box. Not tried; whether
  Artifacts' mimic can spawn this way is not known.
- **Mimics.** Read from Artifacts' jar (13.2.5): they come from its campsite features, `artifacts:campsite` and
  `artifacts:minimalist_campsite` (one `artifacts:suspicious_chest`), which its own biome modifier places underground. A
  theme's `growth` takes any configured feature and skips one that does not exist with a warning in the log, so a pack with
  Artifacts can list one today, with `every` as its lever. Not tried: whether those features place rightly on a disc. A
  mimic found is gone, so this is a reason to explore more than one to return.
- **Fights that reset (suggested).** Vanilla's trial spawner comes back after `target_cooldown_length` (36000 ticks, 30
  minutes, unless set), pays out from `loot_tables_to_eject` each time it is beaten, and adds mobs for each player near; a
  vault pays each player once. In 1.21.1 a spawner's settings are in the block's own data (`normal_config`,
  `ominous_config`; from 1.21.2 they are files of a registry), and a processor rule can give a block data
  (`append_static`), so one could be written into a borrowed piece through its reskin, in JSON. Not tried. Levers:
  `spawn_potentials` (which mobs, with what gear and attributes, so a pack can name its own or make vanilla's stronger),
  `total_mobs`, `simultaneous_mobs`, both again `_added_per_player`, `ticks_between_spawn`, the cooldown, the ominous
  settings, the reward tables, and how many spawners a ruin has from which rank.
- **The fall (suggested).** What armour does not help against in a chasm is being knocked off a disc, so a mob that knocks
  back, such as the breeze, threatens a strong player more than one that hits harder; the penalty below makes the fall
  worse again. The owner: "adding mobs with knockback would be devious".
- **Open:** which mobs, and whether every theme's ruins share them; where a spawner goes in pieces that were not built for
  one; whether danger rises with the disc's rank.

### Traps (owner idea; the kinds are suggestions)

- **Idea:** "Something we haven't considered yet: traps." With the penalty below, "it also makes pitfall traps more
  dangerous".
- **Suggested, cheapest first:**
  1. Infested masonry: a share of a ruin's bricks let out silverfish when broken. One rule in the reskin lists that are
     there. Lever: the share.
  2. Chest traps: TNT under a trapped chest, or a pressure plate and a hidden dispenser. A processor changes one block at a
     time, so this is a small placing step of the mod's, where the loot is set now. Levers: the odds by a disc's rank, and
     what a dispenser holds, which is a loot table.
  3. False floors: big dripleaf over a hole through the disc, so that the trap is the fall. Needs holes in discs, which is
     a change to the carve and to the undersides. Lever: how many, and on which discs.
  4. Trap rooms: pieces built to be traps, which would be the mod's first templates of its own.
- **What exists:** nothing; no ruin has a trap.

### A disc made for ruins (owner idea)

- **Idea:** "a dedicated disc that would have an extra high dome and would not spawn more discs on top of it, and it would
  be filled with connected ruins, which would provide a dedicated place for larger ruin types to spawn such as Epic's
  temples". It "would use the reskinned ancient city pieces and optionally other pieces from mods like Epic's". Its part in
  the goals: "they have good looking ruins and lots of treasure to find".
- **Measured for Epic's temples** (jar read, 1.0.2): 47 by 48 across and 47 high, saved with their air, one layer in the
  ground. That is a round 34 in radius with 46 blocks of air. A dome is lower toward its rim, so the widest disc (radius 64)
  needs a dome of about 58 for it, where the tallest is 29 now. Layers are about 20 apart, so that dome takes the height of
  three of them: half or more of a hole of vanilla's height (the four near the test seed's origin have three to six layers)
  and a third of one in the owner's pack (seven to nine). The disc is 128 across where the cone, at its lowest layer, is
  about 210 across in a hole of vanilla's height and about 240 in the owner's pack.
- **What exists:** pieces are measured, so the temples already in the tag would stand on such a disc as single pieces with
  no new ruin code. The city's pieces are joined by vanilla's own assembly (`RavineCityStructure`; `size` 7,
  `max_distance_from_center` 116), all at fixed heights.
- **What it needs:** the layout to place such a disc, with a radius and a dome of its own; to leave out every disc whose
  platform or stem would enter its dome, and riders on it, which makes it the first disc that removes others; a start of
  the city on it, its reach held to a box inside the round so that nothing overhangs the rim; and a theme for it.
- **Joining Epic's pieces is the costly part.** Its large temple joins on up to seven rounds from a pool of 36, within 80
  blocks. 20 of the 36 are `terrain_matching`: they settle onto the top block of the whole column (`GravityProcessor`, read
  from the 1.21.1 bytecode), which under the overhanging wall is the ground above the hole. So only its 16 fixed-height
  pieces could join, or the mod would answer the height itself.
- **Suggested:** at most one in a hole, on the lowest layer and set into the wall, where the rock under it is deep; the disc
  first with single pieces on it, joined pieces after it has been seen; and it is the natural place to moor a craft.
- **Levers (none exist yet):** how often a hole has one; on which layer; its radius and its dome's height; how far the
  city reaches on it and for how many rounds; the weights of a temple against the city; and loot tables of its own.
- **Open:** how often, which is the owner's to choose; how many discs it removes (a wedge of the two layers above it, not
  measured).

### A penalty for going up or down too fast (owner idea)

- **Idea,** from the show the mod is drawn from: "we could add a stacking penalty for ascending or descending too fast.
  Easiest way would be to add stacking wither debuffs. This makes building elevators convinient since you can tune the
  speed to exactly how fast you can go without taking a debuff, it also makes pitfall traps more dangerous." And: "We don't
  want to risk the effect being applied to unsuspecting players outside the reaches of the chasm though."
- **Why it serves the goals (suggested reading):** it makes going straight to the bottom cost something without closing
  anything off; it gives builders a number to engineer to; and wither goes past armour, so a fall or a knock stays
  frightening to a strong player.
- **Decided so far (owner):** the limits up and down "even, but make them independantly adjustable"; "tentatively stricter
  with depth"; an ender pearl counting as travel "also tentatively yes, though creative ways of bypassing the curse might
  be fun and it would take quite a few pearls to get back up, though going down might be too easy". So not every way
  round it is to be closed.
- **Suggested shape, not agreed:** each player has a depth they are adjusted to, which follows their real height at a fixed
  rate. The penalty comes from the gap between the two and not from speed at any moment. A gap under a free band costs
  nothing, so stairs, jumps and one short drop are free; with the band just over the gap between layers, one drop from disc
  to disc is free and two in a row are not, which makes discs the places to rest. A lift at or under the rate never opens
  a gap. A wider gap is a higher level of the effect, and waiting closes it.
- **Keeping it inside the chasm (suggested):** it acts only inside a hole's own shape, which the mod can tell for any place
  (`RavineCells.containing` and the level's footprint): between the hole's floor and its mouth, within its wall at that
  height and a margin behind it, or in a disc's dome. Coming in sets the adjusted depth to where the player is, so walking
  or caving in starts nothing. A harmless stage warns before any damage. The whole thing is a block in `carve.json`, and a
  pack that leaves the block out has no penalty.
- **What it needs:** the mod's first code that is not world generation: one number kept for each player and stepped on the
  server. With vanilla's effects it still needs nothing on the client.
- **Levers (none exist yet):** the rate upward and the rate downward; the free band; the gaps at which each level begins
  and how long a level lasts once the gap has closed; how much stricter it is with depth; what counts as travel (an ender
  pearl, chorus fruit, a portal, a command, respawning); the margin behind the wall; whether there is a warning stage; and
  whether the mod deals the damage itself. To set them against: layers are 20 apart (`cone.layer_spacing`), climbing a
  ladder is about 2.35 blocks a second, and a fall passes 20 blocks a second within its first second.
- **To know:** Artifacts' antidote vessel cancels wither (its tag `artifacts:mob_effect/antidote_vessel_cancellable`, read
  from the jar), and milk clears it, so a penalty that is only the effect has counters in the owner's pack: either those
  are the intended relics, or the mod deals the damage and shows the effect as a sign. It must never act on ordinary
  movement, which only playing can show. Not looked up: how a player aboard an Aeronautics craft reports their height, and
  bubble columns.
- **Open:** the suggested shape; the three tentative answers above; whether its strictness reads the same rank as loot.

### Getting about: airships and what players build (owner's view)

- **The owner:** "I'm not sure if an airship should reach the bottom without player's contructing something. My gut
  reaction is no, because I want to provide a reason to build infrastructure around a chasm even if their base isn't
  nearby, ie. bridges, elevators, and transport systems, however, flying between discs was the original intended method of
  travel, it just shouldn't be super easy and probably require a unique smaller craft."
- **What exists:** the clear middle of a hole, which no platform or stem enters, is 16 blocks across at the hole's floor and
  widens evenly to 40 at its top, so 20 to 23 at the lowest discs. Discs of one layer have a slot for every 56 blocks round
  the ring, layers are 20 apart, and discs are 20 to 64 in radius.
- **Suggested:** the penalty above answers "should a ship reach the bottom" better than a narrower middle does, since it
  limits how fast any craft may sink or climb and leaves the way open; the disc made for ruins is the place to moor.
- **Levers:** the clear middle's two radii, `spacing` and `layer_spacing` for the gaps a small craft must pass, and the
  penalty's rates.
- **Not looked up:** how small a useful Aeronautics craft is, and `aeroportals` in the owner's pack.

### A boss at the bottom, and a dimension (owner idea, far off)

- **Idea:** "We have a long way to go before I want to add this, but I would also like to eventually add a custom boss fight
  to the bottom of the chasm. I briefly considered adding a new dimension with this mod too, the boss could guard the
  portal, though I don't know what I would want on the other side besides more discs."
- **What exists:** the city on the hole's floor, in a cavern 56 high and 136 in radius (`cavern_height`, `cavern_radius`).
- **Suggested for the boss:** first an arena at the bottom that a pack can fill with another mod's boss, lent the way the
  ruins' tag lends structures; a boss of the mod's own later. A boss of its own is the first thing here that needs the mod
  on the client (an entity and its model), where the mod is now wanted on the server alone.
- **Suggested for the dimension:** wait for an idea the hole cannot hold. One that fits the pack is the same discs with no
  walls, where a craft is the only way from one to the next. The disc code knows nothing of holes (`Disc`), which was kept
  so for the owner's earlier wish to stand a disc above ground, so this stays open at no cost.

### When this is picked up

1. Read each mod's real data files and loader support for 1.21.1 first; record versions in `docs/PINS.md` as optional
   test targets, not dependencies.
2. Decide per mod whether it is a pure datapack integration (preferred) or needs Java.
3. Add a check that the build and both loader servers still boot **without** the mod.
