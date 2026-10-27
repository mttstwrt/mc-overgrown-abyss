# Trove Variety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multi-chest troves, hidden-spawner troves (both more common deeper) and new trove styles for the crystal and lava discs.

**Architecture:** Everything is data-driven through the existing trove "kinds". A kind is a template pool with a weight and a `by_height` ramp, so "more common deeper" is a ramp, not code. Multi-chest and spawner variants are new templates (the spawner and a stone pocket are part of the template, placed with the kind's `sink`), so no post-pass and no spawner API calls are needed. Java changes are limited to a kind `group` plus two dials, and the template-validation test.

**Tech Stack:** Java 21, MC 1.21.1, Architectury (NeoForge + Fabric), data JSON + NBT templates, JUnit.

**Spec:** `docs/superpowers/specs/2026-10-19-trove-variety-design.md`. This plan departs from it in two places, both simplifications: (a) the spawner is baked into templates, not added by a post-pass; (b) ramps are linear in `height`, so "zero above height 0.4" becomes "zero at the top disc, rising linearly to the bottom".

## Global Constraints

- Repo `mods/verdant-deep`, branch `agent/trove-variety`, MC 1.21.1. Both NeoForge and Fabric must compile (`bin/mcg verdant-deep compileJava`, then `bin/mcg verdant-deep@fabric compileJava`).
- **No new loot tables.** Every chest uses the theme's current trove table (`minecraft:chests/jungle_temple`, set in `carve.json` `troves.loot`). Crystal and lava `troves.loot` use the same table.
- Spawners get no telegraphing: no hint block, particle or sound.
- Jackpot is more chests, not richer contents. Anti-goal: spawners in shallow troves often enough to feel unfair.
- Spawner depth: 2 blocks under the trove floor, inside a solid stone pocket, never exposed to air.
- Mobs: lush/jungle/mangrove `minecraft:cave_spider`; crystal `minecraft:skeleton`; lava `minecraft:blaze` (fall back to `minecraft:magma_cube` if blazes misbehave; record which in the report).
- Chances (my calls): hoard weight ramps 0 at the top disc to about 35% of troves at the bottom; spawner (nest) weight ramps about 5% to about 40%. New dials default to 1.0.
- No new dependencies. No change to the volumetrics library or to ruins (non-trove) generation.
- Default no new test; the tests below are the only ones, each with its reason stated.
- Code style: match surrounding code, comments sparse.

## File Structure

Paths under `mods/verdant-deep/`:

- `common/src/main/java/dev/syrval/verdantdeep/ravine/DiscRuins.java`: `Kind` gains `group`.
- `common/src/main/java/dev/syrval/verdantdeep/ravine/Dials.java`: dials `hoards`, `nests`.
- `common/src/main/java/dev/syrval/verdantdeep/compat/DialsFile.java`: file order.
- `common/src/test/java/dev/syrval/verdantdeep/compat/TroveTemplates.java` (new, test sources): generator that builds trove templates as `CompoundTag`s.
- `common/src/test/.../compat/DiscTroveFilesTest.java`: actually run the template invariants.
- `common/src/main/resources/data/verdant_deep/structure/trove/*.nbt`: new templates.
- `common/src/main/resources/data/verdant_deep/worldgen/template_pool/disc_troves/*.json`: new pools.
- `common/src/main/resources/data/verdant_deep/worldgen/density_function/ravine/carve.json`: new kinds, crystal and lava `troves`.
- `docs/SETTINGS.md`: dials, crystal and lava now have troves.

Naming used throughout: pool ids `verdant_deep:disc_troves/<name>` where `<name>` is one of
`hoard`, `nest`, `hoard_nest` (overgrown style, shared by lush, jungle, mangrove), `crystal_cairn`, `crystal_hoard`,
`crystal_nest`, `crystal_hoard_nest`, `lava_cairn`, `lava_hoard`, `lava_nest`, `lava_hoard_nest`. Template names are
`trove/<name>_<n>.nbt`, `n` from 1 to 2. Kind groups: `hoard` (hoard, hoard_nest variants count under `hoard` and `nest`
both, see Task 2), `nest`.

## Review Focus

- Spawner pocket under a thin or edge disc: the pocket stone must not show on the disc underside or float over open chasm. Expected: choose `sink` from the real disc thickness, test it (Task 3).
- A hoard footprint larger than the existing 2x2 on small discs (crystal discs are size <= 0.4): expected the piece `radius` keeps it on the disc and clear of ruins and roots, not hanging off the edge (Task 3, 4).
- Every chest in a multi-chest template must get the loot table, not only the first: expected all containers filled (Task 1).
- Dial `hoards`/`nests` at 0 must leave the plain kinds alone and keep the theme valid; at max they must not overflow weight limits 0..1000 (Task 2).
- `/abyss stats` counts chests and spawners from the palette: expected rising counts with depth and a spawner count above zero in a deep chasm (Task 6).

---

### Task 1: Make trove template checks run, and generate templates

**Files:**
- Create: `common/src/test/java/dev/syrval/verdantdeep/compat/TroveTemplates.java`
- Modify: `common/src/test/java/dev/syrval/verdantdeep/compat/DiscTroveFilesTest.java:60-120`

**Interfaces:**
- Produces: `TroveTemplates.build(Style style, Kind kind, int variant) -> CompoundTag` (a vanilla structure template NBT), with `enum Style { OVERGROWN, CRYSTAL, LAVA }` and `enum Kind { CAIRN, HOARD, NEST, HOARD_NEST }`. `TroveTemplates.name(Style, Kind) -> String` returns the pool name from the naming list above. Template files are written by `TroveTemplates.writeAll(Path resources)`, run from a test only when env `WRITE_TROVE_TEMPLATES=1`.

- [ ] **Step 1:** In `DiscTroveFilesTest`, find why `files()`, `kinds()` and `assertIsATrove` (:60-118) are never called, and add a `@Test allTroveTemplatesObeyTheirInvariants` that runs `assertIsATrove` over every trove NBT. Keep the existing weathering test as is.
- [ ] **Step 2:** Run `bin/mcg verdant-deep test --tests '*DiscTroveFilesTest*'`. Expected: PASS for the six existing templates. If it fails, the existing templates violate the written invariants: fix the test's invariant, not the templates, and note it in the report.
- [ ] **Step 3:** Generalize `assertIsATrove` to take the kind: cairn/marker keep `sizeX,sizeZ <= 2`, 1 container, 2-4 masonry; HOARD allows `size <= 4`, 2-3 containers; NEST adds exactly 1 `minecraft:spawner` block entity at y below the floor with a solid block on all 6 sides and not air; HOARD_NEST both. Every container must have an `id` and the theme's loot is applied by code, so nothing to check for tables. Kind is derived from the file name.
- [ ] **Step 4:** Implement `TroveTemplates` to produce `HOARD`, `NEST`, `HOARD_NEST` for each of the three styles (cairn only for CRYSTAL and LAVA; overgrown cairn/marker already exist). Two variants each. Blocks per style: OVERGROWN reuse the existing cairn palette (mossy cobblestone, cobblestone, stone bricks; copy from an existing cairn template to match the `weathered` processor); CRYSTAL amethyst_block, calcite, smooth_basalt, budding_amethyst; LAVA blackstone, gilded_blackstone, magma_block, obsidian. Chests face outward; a NEST has its spawner `SpawnData` entity per Global Constraints, `RequiredPlayerRange` 16 and default delays, inside a full shell of the style's stone (2 blocks under the floor).
- [ ] **Step 5:** Run the generator once (`WRITE_TROVE_TEMPLATES=1 bin/mcg verdant-deep test --tests '*TroveTemplates*'`), then rerun the full `DiscTroveFilesTest`. Expected: PASS for all templates.
- [ ] **Step 6:** Commit tests, generator and generated NBT as separate commits (test wiring; generator + templates).

Why this test: it catches a template with no loot-capable container, an exposed spawner, or an out-of-spec size, none of which a compile or boot shows.

---

### Task 2: Kind `group` and the two dials

**Files:**
- Modify: `ravine/DiscRuins.java:115-120` (`Kind`, `Kind.CODEC`), `ravine/Dials.java:45-198`, `compat/DialsFile.java:32`, `docs/SETTINGS.md:26`
- Test: `ravine/DialsTest.java`, `compat/DialsFileTest.java:33`

**Interfaces:**
- Produces: `DiscRuins.Kind` gets an optional `List<String> groups` (codec field `groups`, default empty); `Dials` gets `float hoards, nests` (positional record order: after `troves`, before `roughGround`). `Dials.turn` multiplies the `weight` of every kind in a theme's `troves` whose `groups` contains `"hoard"` by `hoards` and `"nest"` by `nests` (both when both listed; clamp 0..1000; a kind scaled to 0 is removed; the plain kinds are never touched).

- [ ] **Step 1:** Write the failing test `hoardAndNestDialsScaleOnlyTheirOwnKinds` in `DialsTest`: a theme with kinds cairn (no groups, weight 1), hoard (`["hoard"]`, 2), nest (`["nest"]`, 2), hoard_nest (`["hoard","nest"]`, 4); with `hoards=0, nests=1` expect cairn 1, hoard removed, nest 2, hoard_nest removed; with `hoards=2, nests=3` expect 1, 4, 6, 24; with huge values expect weights capped at 1000.
- [ ] **Step 2:** Run it; expected FAIL (unknown dial).
- [ ] **Step 3:** Implement the `groups` field, the two dials through every place the existing `troves` dial is wired (record, `NEUTRAL`, `withNames`, `CODEC`, `turned()`, `turn()`, `DialsFile.ORDER`, `DialsFileTest:33`), and the SETTINGS.md table row.
- [ ] **Step 4:** Run `bin/mcg verdant-deep test`. Expected: all pass, including the existing dial tests (`DialsTest:113` all-dials JSON may need the two new names).
- [ ] **Step 5:** Commit.

Why this test: pure logic with edge cases (removal at 0, cap, plain kinds untouched).

---

### Task 3: Overgrown style hoards and nests (lush, jungle, mangrove)

**Files:**
- Create: `worldgen/template_pool/disc_troves/{hoard,nest,hoard_nest}.json`
- Modify: `worldgen/density_function/ravine/carve.json` lush `troves` (:231-258), jungle `troves` (:834), mangrove `troves` (:1246-1273)

**Interfaces:**
- Consumes: Task 1 templates `trove/{hoard,nest,hoard_nest}_{1,2}.nbt`, Task 2 `groups`.

- [ ] **Step 1:** Create the three pools, same shape as `template_pool/disc_troves/cairn.json` (two elements each, processor `verdant_deep:disc_troves/weathered`, rigid, fallback empty).
- [ ] **Step 2:** Add kinds to each of the three themes' `troves.kinds`: `hoard` weight 1 `groups ["hoard"]`; `nest` weight 1 `groups ["nest"]` with `sink` equal to the pocket depth plus floor; `hoard_nest` weight 1 `groups ["hoard","nest"]`; `by_height` ramps meeting Global Constraints. Before choosing ramp values, confirm which end of `height` is the bottom disc (`DiscTraits.java:11-27`, `Ramp` at `DiscTheme.java:53-68`) and state it in a comment-free commit message. Existing cairn/marker kinds unchanged. Expected values: hoard 0 at the top to about 0.5 weight at the bottom against cairn+marker total 2; nest 0.1 to 0.8; hoard_nest 0 to 0.3.
- [ ] **Step 3:** Check the disc thickness where troves stand (`ravine/DiscLayout`/`ConeDiscLayout`) and set `sink` so the pocket is inside disc rock; if discs can be thinner than the pocket, shrink the pocket in `TroveTemplates` and regenerate.
- [ ] **Step 4:** Write test `troveKindsRampWithDepthInEveryOvergrownTheme` in `DiscRuinsTest` that loads the three themes and asserts at height top: hoard and hoard_nest effective weight 0 and nest weight below 10% of the total; at bottom: hoard, nest, hoard_nest each above 10% of the total. (Catches a wrong ramp direction.)
- [ ] **Step 5:** `bin/mcg verdant-deep test`, then `compileJava` for both loaders. Expected: pass.
- [ ] **Step 6:** Commit.

---

### Task 4: Crystal troves

**Files:**
- Create: `worldgen/template_pool/disc_troves/crystal_{cairn,hoard,nest,hoard_nest}.json`
- Modify: `carve.json` crystal theme (:1284 onward): add a `troves` block.

- [ ] **Step 1:** Create the four pools (a processor is not needed unless the crystal blocks should weather; omit it).
- [ ] **Step 2:** Add `"troves"` to the crystal theme, same shape and loot as the lush one (chance 1, `every` 3333 or larger because crystal discs are small, `by_height` and loot copied from lush), kinds `crystal_cairn` (weight 1), `crystal_hoard`, `crystal_nest`, `crystal_hoard_nest` with `groups` and ramps as Task 3. Set piece `radius`/footprint so templates fit a disc of size <= 0.4 (see Review Focus); if they do not fit, make crystal hoards smaller in `TroveTemplates`.
- [ ] **Step 3:** Extend the `DiscRuinsTest` test from Task 3 to include crystal, and run a generation check via `/abyss stats` (Task 6) later.
- [ ] **Step 4:** Run tests and compile both loaders; commit.

---

### Task 5: Lava troves

Same as Task 4 for the lava theme (:1401 onward) with `lava_*` pools. Lava discs exist only at height <= 0.3, so ramp values apply at the deep end; use the bottom values of the Task 3 ramps, and ensure templates and nests do not sit in lava ponds (`DiscLava.java`): lava troves must keep clear of lava, or accept being partly submerged only if the chest stays reachable, and say which in the report. Mob blaze, with the magma cube fallback.

- [ ] **Step 1-4:** pools, theme block, extend the test, run tests and compile both loaders, commit.

---

### Task 6: Verify and document

**Files:** Modify `docs/SETTINGS.md:549` (crystal and lava now have troves), add the two dials to the dials section.

- [ ] **Step 1:** `bin/mcg verdant-deep test`, `compileJava`, and `bin/mcg verdant-deep@fabric compileJava`. Expected: all green.
- [ ] **Step 2:** Run `/abyss stats` on a generated chasm (offline seed report if the command offers it: `AbyssStatsCommand`), with a seed that has many themes. Expected: chest and spawner counts above the old baseline; spawners appear mostly in deeper troves; crystal and lava troves present. If the harness can be published (`bin/mc-harness status`), boot NeoForge and Fabric once via `mc-build-runner`; otherwise record "not booted" as unverified. Stop any server started.
- [ ] **Step 3:** Update the docs, `git status` clean in the repo, commit.
- [ ] **Step 4:** Write the report `.agent/reports/2026-10-19-trove-variety.md` (what changed with branch and commits, verification commands and results, unverified: look of crystal and lava styles, blaze behavior, real spawner activation, boot) and a worklog line in `.agent/worklog.md`. Do not push or bump the version.
