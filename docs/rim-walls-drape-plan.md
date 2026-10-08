# Chasm rim, walls and drape: plan (second version)

Status 2026-10-06: **the ground probe and phases 1 to 3 are built** (a top for each hole, the bowl, the opening); see
"A top for each hole, a bowl above it, and an opening" in `SCOPE.md` for what was built, measured and not verified. Phases 4
to 6 and the view-metrics test are not built, and nothing has been looked at in game. What was built differs from this plan
in these ways:

- The ground is read from the level's own final density, not from the preliminary surface (D1): the preliminary surface is
  30 blocks and more under the real ground in one high column in twenty.
- `min_top` is a plain height, not an anchor.
- Every disc's dome is held under the ground over it, on a 16-block grid, not only discs within 48 blocks of the lip (D4).
- `rim.low_share` is new: the share of the mouth's edge that may lie lower than the lip. It is 0 as shipped, which is D2.
- `cell_size` is 1024 and `chance` 1, since on vanilla terrain only 2.7% of cells pass `min_top` 96, and most of those
  are on steep ground. Section 11's "searching a cell for a good spot" is now the main open question.
- The bowl's roughness is the cell hash's smooth value, not a `SimplexNoise`.

Status 2026-10-07: **wall noise (phase 4) is built**, and the owner settled three things: no hole within 20 blocks of sea
level, the hole's own wall uneven but not the discs, and the floor left 24 over the world's bottom. See "No holes near sea
level, a taller cone, a mouth that follows the ground, and uneven walls" in `SCOPE.md`. Balconies, the drape and the
view-metrics test (phases 5 and 6, phase 0) are still not built. What was built differs from this plan and from the list above
in these ways:

- `min_top` is gone. `rim.min_above_sea` (20) is how far over the level's sea the ground has to stand all round the mouth,
  and it decides alone whether a cell holds a hole.
- `rim.low_share` is 0.5 and may go to 1: the lip is under the middle ground. `top` is 64 under the level's top, not 160.
- The bowl starts from the ground on each side of the mouth (the mouth's edge), not from the lip all round (D5): the wall
  runs up to the ground on the uphill side, and a slope is cut about 12 blocks deep at most instead of its whole height.
- Wall noise is drawn from the cell's hash like the bowl's roughness, not from `SimplexNoise`, in columns round the hole that
  close on themselves, so there is no `bind` and no sampler to hand round (D8, phase 4). It moves the cavern's wall and the
  bowl's start as well as the cone's. There is no `ConeWall`: `ConeShape.wallOver` finds the wall over a column for a root.
- A hole's discs are indexed by column (`DiscIndex`), since a tall hole has over a hundred.

This replaces `Improve-Geometry-Plan.md` (the first plan). It keeps that plan's sound parts, fixes the faults found in review,
and is reorganised around two goals the owner set afterwards. Figures marked *(computed)* come from the shipped settings and
the layout formulas. Vanilla facts marked `[recall; verify]` are unchecked; section 8 lists what was checked against the
1.21.1 jars.

## 1. Goal

1. **Grand fantasy visuals.** The chasm should read as a vast, layered, overgrown pit (the style notes say Made in Abyss and
   Hell's Paradise), from the lip looking down and from inside looking up.
2. **It blends into the terrain.** The rim follows the land. No drill bore through hills, no level circle cut across a slope,
   no domes breaking through the ground.
3. **Holes open in high ground, not near sea level.** A higher top also gives more height for discs.
   *My reading of the owner's note ("increase the minimum height the disc top can be"): a least height for the top of the
   hole, below which a cell holds no hole. If it meant something else, D2 and phase 1 are where it changes.*

What "grand" means here, so gates can check it:

| Quality | Seen as |
|---|---|
| Depth | At least 4 layers of discs, and from most lip positions some of the city floor |
| A framed opening | An overhanging lip with plants hanging from it; from below, a sky window that discs partly cover |
| Scale | Balconies in the walls, hanging discs, far wall detail small enough to give distance |
| A rim that belongs | A bowl falling into the hole, a cliff on the uphill side, an irregular outline |
| Light in the depths | Glow berries and glow lichen down the walls, so the depth reads at night |

Fixed viewpoints, shot at every gate on the same seed and cones:

| | Viewpoint |
|---|---|
| V1 | Lip: 2 blocks behind the drop at N, E, S and W, eye height, looking across and 40 to 50 degrees down |
| V2 | Approach: 20 blocks behind the drop, looking at the hole |
| V3 | Aerial: about 110 above the rim and 200 out |
| V4 | Up from the city floor, near the axis |
| V5 | Up from the top of a mid-height disc |
| V6 | Mid-air on the axis at mid-height, looking at a wall (the spec's Phase 1 "mid-air" shot) |

## 2. What changed from the first plan

| First plan | Now | Why |
|---|---|---|
| `top` lowered to a fixed lip at y=64 | The top is set per hole from the ground at its rim, at least `min_top` (start 96) | Goals 2 and 3. A lip at 64 is one block over sea level, and it cost the top layer its headroom |
| Terrain-aware lip last, and only if needed | The ground probe comes first; everything else is built on it | The lip, dome ceiling, layer count and drape all derive from where the ground is |
| Collar ending in a vertical headwall | A bowl that keeps rising, with no headwall | The headwall made the bore 160 across wherever ground stood over 34 above the lip |
| Layers counted up from the bottom | Layers spread evenly, the top one always `top_room` under the ceiling | With a fixed top at 64, one top-layer disc in six was dropped *(computed)*; with a varying top it would be arbitrary |
| Wall noise sampled at `radiusAt(y)` | Sampled on a cylinder of fixed radius, offset per hole | The first way lost the vertical stretch (runnels came out 1.3 to 1, not 4 to 1) and gave every hole the same wall |
| Roots and the overhang search allowed `maxDisplacement` vertically | The offset is radial; vertical allowances divide by the wall's slope | A 9.5 offset moves the wall 13 to 28 blocks vertically *(computed)* |
| Wall ring half a layer under every gap | Only where no platform is close above, and placed against the displaced wall | Two thirds of wall discs sat under a top-layer platform with 3 to 8 blocks of headroom *(estimated)* |
| Wall noise for cone and ravine, first | Cone only, after the opening | It does least for the two goals; the ravine sampling had a seam at each end |
| Prototype figures as evidence | A view-metrics test over the real layout | The prototype used stand-in discs, had no ablation and is gone |
| Curtains every 4 blocks of edge | Every 12 | A patch is 2 to 6 columns wide, so every 4 was a continuous curtain |

## 3. What is wrong today

1. **A drill bore above `top`.** `ConeShape.radiusAt` clamps its height fraction to 1, so above `top` (y=80) the hole is a
   cylinder 90 across up to build height. The land check only rejects ocean, river and beach.
2. **The top is the same everywhere.** `top` is one height for the level. Holes open in sea-level plains, a hole in hills is
   no deeper than one in a plain, and every hole has the same 3 layers on vanilla height.
3. **A lid over a bell.** The mouth is about 98 across at y=65 and the hole 300 at the floor; the top layer covers most of the
   mouth, and the guaranteed line of sight is a cylinder 16 across.
4. **Geometric surfaces.** The wall is an exact surface of revolution.
5. **Domes can break through low ground.** Dome tops reach `top - ceiling_margin` = 64 whatever the ground above them
   (SCOPE.md, "large domes right under the surface": 49 of 259 points over domes were air).

## 4. Evidence, and how gates are measured

The first plan's prototype pointed these ways. Treat them as hints to re-measure, not results:

- Keeping discs out of a cone round the axis helped both the view down and the view up most.
- A steeper upper wall did not help the view down; the bell's far wall falls away, so you see deeper. Keep the bell.
- Dense curtains at the viewer's feet hide the view. Drape needs gaps.
- Wall noise is texture: it changed neither view much.

**View metrics (new, phase 0).** A test in `common/src/test` that sphere-traces the real `ConeShape` and `ConeDiscLayout`
(carve and rock, as `max(min(original, carve), rock)` with synthetic ground from a `SurfaceProbe`) over about 40 sampled
holes, and reports through JUnit's `TestReporter`:

| Metric | From | Measures |
|---|---|---|
| Down: depth | V1 at 8 lip positions, a fan of rays | Share of the view ending below half depth; share reaching the cavern floor |
| Down: layers | the same rays | How many disc layers each take at least 2% of the view |
| Up: sky | V4 on the axis and half way to the cavern wall | Share of an upward cone of rays reaching the sky |
| Reach | V1 and V6 | Median length of a sight line |

Bands to agree with the owner before phase 3 (my proposal, from the 4 October style notes asking for more but not complete
blocking from the floor): the floor visible from at least half the lip positions, at least 3 layers in view from the lip,
and 3 to 8% sky from the floor. Once agreed, the test asserts them.

## 5. Design decisions

- **D1. The ground is known before anything is shaped.** A `SurfaceProbe` (interface in `ravine`, implemented in `compat`)
  gives the ground height at a column before any carving: the level's preliminary surface, read from the noise router's
  `initialDensityWithoutJaggedness` as vanilla's `NoiseChunk.computePreliminarySurfaceLevel` reads it, then refined to a
  block. It is installed after the `RandomState` exists, as `restrictToLand(LandCheck)` is. Tests pass synthetic ground
  (flat, a slope, a hill, a valley).
- **D2. Each hole has its own top.** For a cell, probe 24 columns round the mouth (`top_radius` from the axis) and smooth
  each with its two neighbours, so one pothole does not lower the whole lip.
  - `lip = min(lowest - dip, top)`. The setting `top` becomes the highest a lip may be.
  - A cell whose lip is under `min_top` holds no hole. This joins the land check in one per-cell verdict.
  - The cell's bounds are `(floor, lip)`. The shape and layout code already take `RavineBounds` as a parameter, so they are
    handed the cell's own instead of the level's.
- **D3. Layers are spread evenly, and the top layer always has room.** The top layer's floor is `top_room` under the dome
  ceiling (`lip - ceiling_margin`); the layers between it and the lowest are evenly spaced, never closer than
  `layer_spacing`. With `top` 80, `ceiling_margin` 16 and `top_room` 24 this gives today's floors (0, 20, 40 on vanilla
  height), which is the regression anchor.
- **D4. A dome's ceiling follows the ground over it.** A disc whose dome could reach within 48 blocks of the lip probes its
  own centre and 8 points on its rim; its dome stops `ceiling_margin` under the lowest of them. This covers discs far out
  from the rim, under a valley the rim probes never saw.
- **D5. Above the lip, a bowl.** The cylinder above the top is replaced by a bowl: outside the mouth the ground is opened
  only above `lip + height * u^profile + rough`, with `u` the distance out from the mouth over `width`. `u` has no upper
  limit, so there is never a vertical headwall: low ground gets a shallow dip, a hillside gets a cirque (a bowl backed by a
  steep slope). The bowl is clipped at `outer_radius`, which it reaches over 900 blocks above the lip *(computed)*.
- **D6. `maxReach()` does not change.** Hole positions in a cell are drawn from it, so changing it would move every hole
  and make shots from different phases incomparable. The bowl and the wall noise stay inside `outer_radius`.
- **D7. A cone of clear air for the opening.** No disc comes inside a radius that grows from `clear_radius` at the floor to
  `upper.clear_radius` at the lip. The axis cylinder stays the guaranteed line of sight. The value is chosen with the view
  metrics and the owner, not maximised.
- **D8. Noise only on the hole's own wall, as a radial heightfield.** Discs, domes, platforms, stems and roots stay exact;
  the painter, ponds, growth and biome ownership all read them from the formulas.
  - The wall's radius is `radiusAt(y) + offset(angle, y)`. The offset is in blocks out from the axis and never depends on
    depth into the rock, so along any line out from the axis air turns to rock exactly once: no floating pieces, no bubbles.
  - It is sampled at `(centreX + R cos angle, y / stretch, centreZ + R sin angle)` with `R` fixed (half way between
    `top_radius` and `base_radius`). The sample then does not slide sideways as the wall flares, so a vertical stretch stays
    a vertical stretch and reads as runnels; and the hole's own centre gives each hole its own wall.
  - Anything that needs the wall's height in a column allows `maxDisplacement() / slope` vertically, not `maxDisplacement()`.
- **D9. Balconies in the upper wall.** Smaller discs set into the wall between layers, with no stem. Each is placed against
  the displaced wall and only where no platform lies close over it.
- **D10. Drape: geometry proposes, the world decides.** The `ravine` side proposes places from the cell hash and
  coordinates, as `DiscGrowth` does; the grower settles each against the actual blocks, as `DiscGrower` does. Features are
  datapack JSON built from vanilla feature types.
- **D11. Keep the bell** (`top_radius` 45, `flare` 1.6 as shipped). A taller hole stretches the same profile.
- **D12. Cone only.** The ravine is unchanged. The drape's world-side checks do not depend on the hole's shape and can
  serve ravine rims later.

## 6. Settings sketch

Starting points. Without `rim`, every hole keeps the fixed `top` and today's layers; without `upper` and `wall_noise`,
discs and walls are today's.

```json
"top": { "absolute": 160 },
"wall_noise": [
  { "wavelength": 90, "vertical_stretch": 3, "amplitude": 6 },
  { "wavelength": 16, "vertical_stretch": 4, "amplitude": 2.5 },
  { "wavelength": 6, "vertical_stretch": 1.5, "amplitude": 1 }
],
"cone": {
  "top_radius": 45,
  "flare": 1.6,
  "ceiling_margin": 12,
  "rim": {
    "min_top": { "absolute": 96 },
    "dip": 3,
    "top_room": 22,
    "collar": { "width": 30, "height": 34, "profile": 2, "roughness": 2, "roughness_wavelength": 12 },
    "lip_band": 4,
    "overhang_depth": 24,
    "wall_depth": 24,
    "growth": [
      { "feature": "overgrown_abyss:rim/vine_curtain", "every": 12, "on": "edge" },
      { "feature": "overgrown_abyss:rim/lip_bush", "every": 40, "on": "lip" },
      { "feature": "overgrown_abyss:rim/moss_lip", "every": 30, "on": "lip" },
      { "feature": "overgrown_abyss:rim/glow_berries_overhang", "every": 12, "on": "overhang" },
      { "feature": "overgrown_abyss:rim/hanging_roots", "every": 20, "on": "overhang" },
      { "feature": "overgrown_abyss:rim/glow_lichen_wall", "every": 25, "on": "wall" }
    ]
  },
  "upper": {
    "clear_radius": 20,
    "wall_spacing": 34,
    "wall_scale": 0.5,
    "wall_from": 0.5,
    "wall_embed": 0.2
  }
}
```

Layers by lip height with these numbers *(computed, taking Larion's world bottom as -128)*; today is 3 on vanilla height
and 6 on Larion:

| Lip | 80 | 96 | 112 | 128 | 144 | 160 |
|---|---|---|---|---|---|---|
| Vanilla (lowest floor 0) | 3 | 4 | 4 | 5 | 6 | 7 |
| Larion (lowest floor -64) | 6 | 7 | 8 | 8 | 9 | 10 |

So `min_top` 96 guarantees one more layer than today, and spacing runs from 20 to under 27.

The bowl with `width` 30, `height` 34, `profile` 2 *(computed)*:

| Ground above the lip | 3 | 15 | 34 | 80 |
|---|---|---|---|---|
| Cut reaches this far out from the mouth | 9 | 20 | 30 | 46 |
| Slope at its outer edge | 34° | 56° | 66° | 74° |

Field budget: `RecordCodecBuilder` takes 16 fields. `RavineSettings` goes from 14 to 15 (`wall_noise`, rejected for a
ravine until a ravine uses it). `ConeSettings` goes from 14 to 16 (`rim`, `upper`), the limit; the next cone setting goes
in a nested object.

## 7. Phases

Each phase: one concern per commit with unit tests; a SCOPE.md section in the usual form; `docs/PINS.md` for any new
version-sensitive file; a gate shown on a dedicated server and the client, on NeoForge and Fabric, vanilla and Larion.

### Phase 0: instruments, baseline and survey

Nothing a player sees changes in this phase.

- **View metrics test** (section 4), run on today's settings. This is the baseline table.
- **`SurfaceProbe`** in `ravine`, and `compat/PreliminarySurface` implementing it: walk a column of
  `state.router().initialDensityWithoutJaggedness()` down from the level's top in cell-height steps until it exceeds
  0.390625 (vanilla's threshold), then step by single blocks. Installed in `RavineDensityHook.installLevelBindings`.
  For now it is only logged.
- **Survey, from the world-load log** (no chunks needed): for the 200 cells nearest the origin, the probe's lowest and
  highest ground round the mouth, and the biome there. From it:
  - the share of land cells that would hold a hole at `min_top` 80, 88, 96, 104 and 112;
  - how much the ground varies round one mouth;
  - which biomes rims fall in once low ground is excluded (cold and snowy ones matter for the drape).
- **Probe accuracy:** at three generated holes per terrain, compare the probe with the real surface at 100 columns each.
  Record the median and 95th percentile error. `ceiling_margin` and `dip` are set from this.
- **Baseline shots** V1 to V6 at three holes that pass `min_top` 96, chosen from the survey, one on a slope. Keep one shot
  of the old reference hole near 956,-499 on seed 20261003 as the "before", whether or not it survives the gate.
- **Gate:** the four tables written to SCOPE.md. Decisions taken from them:
  - the value of `min_top`;
  - whether `chance` (0.5) or `cell_size` (2048) must change to keep holes findable. Both together give up to 8 times the
    candidates with no new code; a search for a good spot inside a cell is only considered if that is not enough;
  - whether the probe is good enough on Larion. If Larion's router leaves `initialDensityWithoutJaggedness` meaningless,
    stop and redesign the probe before phase 1.

### Phase 1: a top for each hole

Code:
- `ConeSettings` gains `rim` (new `ravine/RimSettings.java`), holding `min_top`, `dip` and `top_room` for now.
- `LandGate` becomes a per-cell site verdict: either no hole, or the cell's `RavineBounds`. It applies the land check, then
  D2. `RavineCarve` hands the cell's bounds to `RavineShape`, `DiscBlocks` and `CellDiscs.of` in place of the level's
  (`RavineCarve.java:123`, `:210`, `:227`). `isActive` and `isInFootprint` go through the same verdict, so the city
  structure, which asks the footprint (`RavineCityStructure.java:97`), follows without a change.
- `ConeDiscLayout`: `layers` and `nominalFloor` follow D3; `domeHeight` follows D4 and takes the probe.
- Validation: `top_room` is at least `min_height` plus half the layer jitter, so no disc is dropped for want of dome room;
  `min_top` is under `top`.
- The world-load log gives each hole near the origin its lip, the ground's range round its mouth, and its layers
  (`RavineDensityHook.java:246`).

Tests:
- With flat ground at `top + dip`, `ceiling_margin` 16 and `top_room` 24, the disc lists equal today's for sampled cells.
- A cell whose lip would be under `min_top` holds no hole; one pothole among the probes does not change a lip.
- Layer counts match the table in section 6; spacing is even and never under `layer_spacing`; no disc is dropped.
- Beside a synthetic valley, no dome's top is within `ceiling_margin` of the ground anywhere over its footprint.
- `maxReach()` and the centres of sampled cells are unchanged.
- The same verdict and discs whichever chunk or thread asks first.
- Line of sight and reach hold for lips at `min_top` and at `top`.

Data: `top` 160, `rim` with `min_top` and `dip`, `ceiling_margin` 12 (or what phase 0 says). A `fixed-top` dev pack
without `rim` for side-by-side comparison; `DevPacksTest` covers it.

**Gate:**
- Every hole near the origin opens in ground at or above `min_top`; `/locate` still finds a city in reasonable time.
- Shots V1 to V6. The view metrics show at least one more layer in view than the baseline.
- Probes over every top-layer dome: none is air.
- Density cost per sample against the 0.56 µs recorded before. More layers mean more discs per sample; if it has more
  than doubled, measure where before changing anything.
- The bore above the lip is still there. That is phase 2.

### Phase 2: the bowl

Code:
- `RimSettings` gains `collar` (width, height, profile, roughness and its wavelength).
- `ConeShape`: above the lip, inside the mouth is open; outside it, D5. The distance is the vertical gap over
  `sqrt(1 + slope²)`, as `coneDistance` does. `rough` is one `SimplexNoise` over `(x, z)`.
- Nothing is added to `maxReach()` (D6).

Tests:
- A point outside the mouth and high above the lip is solid unless it is over the bowl.
- The open area grows with height and never has a vertical edge inside `outer_radius`.
- With flat ground `dip` above the lip, the cut is no wider than the first column of the table in section 6 plus `roughness`.
- Nothing opens past the reach. Domes stay under the lip. Line of sight holds.

**Gate:**
- V2 and V3 at a hole on level ground, one on a slope, and one in tall Larion terrain: no bore. Level ground shows a shallow
  dip, a slope shows a cirque on its uphill side.
- The share of the bowl's surface that is grass against bare stone. The overworld surface rule does grass only
  `above_preliminary_surface`, so deep cuts are probably stone. Steep stone is acceptable; if gentle parts are bare too,
  phase 6's moss on the `lip` band covers them. No painter change.
- **Owner review:** does the rim belong to the land?

### Phase 3: the opening

Code:
- `ConeSettings` gains `upper` (new `ravine/UpperSettings.java`) with `clear_radius` (at the lip).
- `ConeDiscLayout`: `clearAt(y)` replaces `cone.clearRadius()` at lines 139, 167, 182 and 231; `ConeShape.rockDistance`'s
  clamp (`ConeShape.java:60`) and the validation at `RavineSettings.java:124` use it too.

Tests:
- No disc, rider, stem or root comes inside `clearAt`. Stems run down into a narrowing cone, roots up into a widening one,
  so roots are the case to check.
- No placed disc loses rock to the clamp.
- Without `upper`, disc lists equal phase 1's.

Tuning, with the view metrics: `upper.clear_radius` 12, 20 and 28, and `top_radius` 45 and 56, as a table for the owner.

**Gate:** V1, V4 and V5. The metrics inside the bands agreed in section 4. **Owner review:** does the layered look survive,
and is the opening framed rather than merely open?

### Phase 4: wall noise

Code:
- New `ravine/WallNoise.java`: `record WallNoise(List<Layer> layers)`, `record Layer(float wavelength, float
  verticalStretch, float amplitude)`, codecs, `maxDisplacement()` (the sum of amplitudes, radial blocks), and
  `bind(seed, salt)` returning a `Sampler` of one `SimplexNoise` per layer, immutable after construction.
  A wavelength under 4 is rejected: the carve is computed per block.
- A small `ConeWall` (cone, the cell's bounds, the cell, the sampler) answers `radiusAt(y)`, `radiusAt(angle, y)`,
  `slopeAt(y)` and `heightAt(radius)`. `ConeShape.coneDistance`, `ConeDiscLayout` and later the wall ring and the drape
  all ask it, so the sampler is not threaded through every signature. Sampling is skipped further than
  `maxDisplacement() + 1` from the smooth wall.
- Validation: `maxDisplacement()` is under `top_radius - upper.clear_radius - 2`, and `base_radius + maxDisplacement()`
  is within `outer_radius`.
- `ConeDiscLayout.wallAbove`: a wall-anchored root's top rises by `maxDisplacement() / slopeAt(anchor)` plus its radius,
  capped at the lip. Rock is only added where the carve opened, so the extra length inside the wall costs nothing.

Tests:
- With no `wall_noise`, distances equal phase 3's at fixed points.
- Along rays out from the axis the wall term changes sign exactly once.
- The wall never moves more than `maxDisplacement()` radially.
- Up the wall at one angle, the offset changes more slowly than round it at one height, by about the layer's stretch.
- Two holes of one world have different walls; the same seed gives the same.
- Every wall-anchored root meets rock: no open block between its top and the wall, over sampled holes.
- Line of sight and reach hold.

**Gate:** V1, V3 and V6: an irregular outline, vertical runnels, no single-block pits or crumbs. Platform and stem probe
points from earlier rounds still hold. Cost per sample. **Owner review:** noise was rejected once (the walls read as
"too rounded and generated"); the off switch is deleting one field.

### Phase 5: balconies

Code:
- `UpperSettings` gains `wall_spacing`, `wall_scale`, `wall_from` and `wall_embed`.
- For each gap between layers from `wall_from` up, candidates round the wall, one per `wall_spacing`, with the floor half
  way between the layers. A candidate is kept only if:
  - no platform holds its axis within `min_height + floor_thickness` above its floor (`Discs.platformAbove`);
  - its dome fits under its ceiling (D4).
- Its centre is `ConeWall.radiusAt(angle, floor)` plus the embed, which is the larger of `wall_embed * radius` and the
  finer noise layers' amplitudes plus 2. So the displaced wall still carries it.
- New `Disc.Support.Embedded`, no stem and no root. The sealed `switch`es in `Disc.supportDistance` and `DiscBlocks`
  (lines 89 to 98) gain a case, which the compiler enforces. `Disc.java:48` and `:94` test with `instanceof` and must be
  read by hand; `hangBlockAt` already does the right thing for a disc with no stem.
- Own hash base; counted in `MAX_DISCS`; never hosts riders. Themes are assigned as for any disc.

Tests:
- Every wall disc touches rock round at least a third of its rim, with the shipped noise, over sampled holes.
- None has a platform within `min_height + floor_thickness` over its axis. None comes inside `clearAt`.
- Without the wall settings, disc lists equal phase 4's.

**Gate:** V1 and V6 show ledges with open balconies on the far wall; the world-load log counts ring, rider, wall and
hanging discs per hole; the view metrics stay inside their bands.

### Phase 6: drape

Data model, in `rim`: `growth` is a list of `{feature, every, on, biomes?}` with its own record and enum, separate from
`DiscTheme.Growth`. `on` is one of:
- `edge`: the open block on the hole side of the lip's top block. Curtains start here.
- `lip`: on the ground within `lip_band` of the edge: bushes, moss, trees whose crowns overhang.
- `overhang`: under the rock within `overhang_depth` of the edge.
- `wall`: open blocks against the wall, down to `wall_depth` below the lip.

Pure side, new `ravine/RimGrowth.java` modelled on `DiscGrowth`: for one chunk, visit the columns of the rim's annulus
(wide enough for the wall's displacement), draw each with `RavineCells.unitAt(hash, RIM_HASH_BASE + g, x, z) * every < 1`,
and emit the column and the entry. It does not run when `discThemes` is empty today (`RavineCarve.forEachGrowth`); rim
growth must not depend on that.

Compat side, new `compat/RimGrower.java` called from `DiscGrower.grow` after disc growth (same hook, no new mixin). It
settles each place against the blocks, **scanning the column** rather than trusting a computed height:
- `edge`: the column is well below a neighbour further from the axis; the place is beside that neighbour's top block, which
  must have a sturdy face towards it.
- `lip`: the block over the column's top, with a sturdy face up.
- `overhang` and `wall`: scan down from the lip through the band for an open block with rock over it (`overhang`) or
  beside it (`wall`).
- `biomes`: `level.getBiome(pos).is(...)`.

Feature files in `data/overgrown_abyss/worldgen/configured_feature/rim/`, vanilla types only:
- `vine_curtain`: the `disc/vines_hanging` pattern. Columns 6 to 28 long, most short, 2 to 6 columns a patch.
- `glow_berries_overhang`: reuse `disc/glow_berries_hanging`.
- `hanging_roots`: the mod's own, as `disc/mangrove_roots_hanging` is.
- `glow_lichen_wall`: `multiface_growth`. Vanilla's `glow_lichen` only attaches to eight kinds of stone; the rim's own file
  lists what the rim is made of.
- `lip_bush`: `disc/jungle_bush` or an azalea tree, so the crown overhangs. Leaves not held by a log need
  `persistent: true` (`[recall; verify]`).
- `moss_lip`: a `vegetation_patch` like vanilla's `moss_patch`, which also covers bare stone from phase 2.

Biomes: rims are now in high ground, so more of them are cold or snowy than before. The lush set is the default; a second
set (hanging roots and glow lichen, no vines or bushes) for cold and dry rims is added if the phase 0 survey shows more
than a few such rims. Both use tags present on both loaders (`#c:is_snowy`, `#c:is_dry`, `[recall; verify]` the names).

Tests (pure): places are deterministic and independent of chunk order; the count per entry is within 20% of `1 / every`
per column of band; none is proposed inside the clear cone.

**Gate:** V1 shows curtains down the far wall and gaps to look through at the viewer's feet. V4 shows the sky window
fringed with curtains and glow berries. Vine blocks per 100 blocks of rim. 20 curtains probed: each top vine against a
sturdy face. One cold or dry rim. The view metrics rerun with drape cannot be done (plants are not in the model); judge
the view down by eye.

### Phase 7: tuning with the owner

In order, re-shooting V1 to V6 after each and using the view metrics for the first three:
1. `min_top` and `top_radius` (how high, how wide).
2. The bowl's width and height.
3. `upper.clear_radius`, and the balconies' spacing and scale.
4. Wall noise amplitudes.
5. Drape density and lengths.

## 8. Things to verify before building on them

Checked against the cached 1.21.1 jars on 2026-10-06:
- `NoiseChunk.computePreliminarySurfaceLevel` reads `initialDensityNoJaggedness` against 0.390625.
- `NoiseRouter.initialDensityWithoutJaggedness()` and `DensityFunction.SinglePointContext(int, int, int)` exist.
- The overworld surface rule uses `above_preliminary_surface`.
- `VineBlock.canSupportAtFace` consults the block above, consistent with a hanging vine held by the vine over it.
- Vanilla configured features `spore_blossom`, `moss_patch`, `moss_vegetation`, `glow_lichen` (`multiface_growth`),
  `azalea_tree` and `vines` exist.

Still to verify:
1. How far the preliminary surface is from the real one, on vanilla and on Larion (phase 0 measures it).
2. That the router's functions from `RandomState` may be evaluated with a `SinglePointContext` from several worldgen
   threads at once, as the land check's climate sampler already is.
3. How deep below the original surface the surface rule still lays grass.
4. Which heightmaps a `WorldGenLevel` serves at the features step.
5. The JSON format of `vegetation_patch`, and `MultifaceGrowthFeature`'s search.
6. Leaf decay for leaves placed without logs; the convention tags' names.

## 9. Relation to the spec

`03-abyss.md` Layer 2 plans wall dressing through NeoForge biome modifiers. The disc themes moved away from that, because
the project grows its own features (`DiscGrower`), and the drape follows them. The same mechanism could later carry the
spec's wall styles down the walls. The spec also has holes at a fixed top; D2 changes that. Both are flagged so the spec
can be updated or the choices revisited.

## 10. Open decisions for the owner

1. **`min_top`.** Start at 96 (one more layer than today on vanilla height), settled after the phase 0 survey shows how
   many cells pass at each height. Is the reading of the note in section 1 right?
2. **Holes in very high ground.** A lip is capped at `top` (160), so a hole on a 200-high mountain sits in a crater 40 deep.
   The alternative is no hole there. Recommended: cap, and look at one at the phase 2 gate.
3. **How open the opening is.** The bands in section 4, and `upper.clear_radius` with them.
4. **How wide the mouth is.** `top_radius` 45 as shipped, or wider for a grander pit. Phase 3's table shows the cost in
   overhang.
5. **Drape by biome.** Lush on every rim, or a second set for cold and dry rims.

## 11. Not in this plan

- **Waterfalls** (the spec's Cascades). The bowl's low side is where they would run. Revisit after the phase 6 gate.
- **A notch or gully through the rim.** It read as a spout in the first prototype.
- **Wall noise and rims for the ravine shape.**
- **Searching a cell for a good spot.** Only if phase 0 shows `chance` and `cell_size` cannot keep holes findable.
