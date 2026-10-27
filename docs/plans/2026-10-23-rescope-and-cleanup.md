# Plan: Verdant Deep rescope, regression plan, code cleanup

Status: decisions taken 2026-10-23 (§2); D8 open; goals not yet queued. Evidence (agent notes in the dev kit, untracked): `.agent/notes/abyss-arch-ravine.md`, `abyss-arch-compat.md`,
`abyss-arch-basilisk-tests.md`, `abyss-multiversion.md` (surveys of `agent/G-003-basilisk-size-movement` @ 34d629d).

## 1. The new scope in one paragraph

Verdant Deep started as a standalone biome that a server could run without clients installing anything ("datapack
first", "Custom Java is limited to ..." in `SCOPE.md` §2, "Servers need no client-side install" in `AGENTS.md`). It is
now an **adventure/structure mod**: a generated abyss to descend, structures to loot (city, disc ruins, troves, temples
later), creatures to fight (basilisk, bosses later) and descent mechanics (curse, gas). It already registers entities,
renderers, sounds and a network payload, so clients must install it. The rules and docs should say so, and the code
should be organised around those four pillars instead of around "ravine logic vs compat glue".

What stays true from the old rules: worldgen thread safety and determinism, a comment justifying each mixin, both
loaders, vanilla-terrain files wrapped rather than overridden, Architectury as the loader layer. What goes: datapack
first as a principle (numbers the owner tunes by eye go in config files with an in-game command, or stay as data in the jar for worldgen), server-only, "no new Java".

## 2. Decisions (owner, 2026-10-23)

| # | Question | Decision |
|---|---|---|
| D1 | Integrate the linear branch stack (`main` ⊂ `dev` ⊂ `G-002` ⊂ `G-003`, 43 commits). | **Done 2026-10-23.** Everything was tested by hand on NeoForge. `main` fast-forwarded to `34d629d` and pushed; every goal below branches from `main`. |
| D2 | Where creature tuning lives. Datapacks are awkward to manage in Prism, and commands cannot write datapack files (they only enable, disable and `/reload` them). | **A config file, like the dials.** `config/verdant_deep/basilisk.json`, written with every key at its shipped value when missing and filled in when keys are missing (the `DialsFile` pattern, `compat/DialsFile.java`). Plus `/abyss tune basilisk <key> <value>` (op only), which sets the value live, saves the file and re-applies it to loaded basilisks, so tuning happens in game with no file editing or restart. Worldgen settings stay as data in the jar; the dials remain the owner's way of tuning them. |
| D3 | Where the regression plan and its scripts live. | **Plan committed to the mod** (`docs/REGRESSION.md`, plus this plan as `docs/plans/2026-10-23-rescope-and-cleanup.md`). **Scripts:** the kit's rule (scenarios never committed to a mod) gets in the way here, so kit proposal K1 (`.agent/proposals/2026-10-23-kit-regression-suites-and-branch-bases.md`) puts the suite, fixtures and baselines in the mod's `regression/` folder, committed and never shipped. It works today by path (`bin/mc-test run verdant-deep mods/verdant-deep/regression/<suite>.sh`); K1 makes the lookup by name. |
| D4 | The test cut. | **Approved:** cut to the keep-list in §5 (about six tests). |
| D5 | Gas library and Minecraft versions. | **Both mods support 1.21.1 (pinned, the main modding version) and the newest version going forward.** The abyss uses the library's `1.21.1` branch. The abyss was not designed for several versions the way the library was; G-011 (§7a) makes it so. The gas stays optional at run time: the owner's own measurements show volumetrics costs more than a shader pack, so players must be able to leave it out. |
| D6 | Basilisk spawning through `mc-boss-spawn-lib` (`roaming_boss` category, spike on `agent/boss-spawn-spike`) to escape the monster cap (diagnosis 2026-10-22). | **Deferred; recorded in §9.** B2 stays "may fail on the monster cap" until it is done. |
| D7 | Branches and package moves. | **All branches deleted after the merge** (local and remote); G-010 can run any time no other abyss branch is open. |

## 3. Order and why

1. **G-004 Docs and rules**: cheap, and every later session reads them first.
2. **G-005 Regression plan + suites**, *before* any refactor or test cut. Its worldgen fingerprint (W1) is the safety
   net that replaces most unit tests: a pure refactor must produce a bit-identical world on both loaders.
3. **G-006 Cut unit tests** to the keep-list, now that W1 guards worldgen behaviour.
4. **G-007 Basilisk behaviour spec + tuning in a config file**: addresses the cause of the mess (§6).
5. **G-008 Basilisk code cleanup**, guarded by the B-suite.
6. **G-009 Ravine query surface + random streams**, guarded by W1 equality.
7. **G-010 Feature-first package layout**, guarded by W1 equality and smoke on both loaders.
8. **G-011 Ready for two Minecraft lines** (§7a), once the packages show what the pure core is.

Each goal is one session. G-007 to G-010 are refactors or data moves: "Done when" for each includes "W1 fingerprint
equal to the baseline on NeoForge and Fabric" (worldgen untouched) or "B-suite passes" (basilisk).

## 4. Regression plan (G-005)

### 4.1 Principles
- Test through the real game: `bin/mc-test run verdant-deep[@fabric] <suite>`; `mc-tester` runs and judges.
- Check **properties that survive intended tuning** ("a basilisk wakes when a player comes near", "nothing generates
  past the footprint"), never tuned values ("wake radius is 12"). The one exception is the W1 fingerprint, which is
  exact on purpose and is re-baselined deliberately (4.5).
- Each check has an ID, so reports and goals can say "W1, B3 pass".
- Every check says who judges: **auto** (`expect`/`wait_for`/`check`), **tester** (screenshot or clip vs. a written
  description), **human** (feel, GPU cost, shader packs: listed, never claimed).

### 4.2 Layout and fixtures
In the mod (D3, kit proposal K1): `regression/fixtures.sh`, one `regression/<suite>.sh` per suite (`smoke`,
`worldgen`, `structures`, `basilisk`, `gas-curse`), `regression/baselines/`. None of it is in a Gradle source set.
Each suite starts with `. "$(dirname "${BASH_SOURCE[0]}")/fixtures.sh"` until K1 makes that automatic.

Move today's `.agent/scenarios/verdant-deep/fixtures.sh` there. Existing: `ABYSS_SEED=11`, `ABYSS_CITY="-3744 -20 4864"`. G-005 records, once, on seed 11: a rim point looking in
(`ABYSS_RIM`), a disc of each theme (`ABYSS_DISC_<theme>`, disc biomes are y ≥ 0), a disc ruin site (`ABYSS_RUIN`), a
trove chest (`ABYSS_TROVE`), a fall landing (`ABYSS_FALL`), a flat disc spot ≥ 6 blocks from an edge and one 2 blocks
from an edge (`ABYSS_DISC_SAFE`, `ABYSS_DISC_EDGE`), a wall the bot can stand on top of (`ABYSS_WALL_TOP`), and the
fingerprint boxes (`FP_BOXES`: rim slice, one disc, cavern floor, city corner; each ≤ 32×32×64).

### 4.3 Suites and checks

**S Smoke** (every change, both loaders, ~3 min each)

| ID | Check | Judge |
|---|---|---|
| S1 | Fresh server on `ABYSS_SEED` boots; `logscan server` clean | auto |
| S2 | Client joins; `logscan both` clean (client install is required now) | auto |
| S3 | Registries hold `verdant_deep:basilisk`, the city and disc-ruin structures, the density functions | auto (`query r registry`) |
| S4 | `/abyss stats` at `ABYSS_CITY` answers, not "Not in a chasm" | auto (`cmd` + `expect`) |

**W Worldgen** (changes in `ravine/`, `mixin/`, the density/aquifer/biome hooks, worldgen JSON)

| ID | Check | Judge |
|---|---|---|
| W1 | Fingerprint: `scan` block counts in `FP_BOXES` + structure starts (`chunk`) in a 5×5 chunk area at `ABYSS_CITY` + `/abyss stats` lines, written as one sorted JSON and compared with the baseline for this loader | auto (`check` diff) |
| W2 | NeoForge fingerprint equals Fabric fingerprint | auto (compare two runs' JSON) |
| W3 | `locate structure verdant_deep:city` from `ABYSS_CITY` ± 512 finds it | auto |
| W4 | Server log reports ≥ 1 ravine within 3072 blocks of the origin | auto (`wait_log`) |
| W5 | Shots: rim looking in, mid-air, cavern floor, each disc theme, a fall landing. Tester checks: no straight chunk-border seams or cut walls, fluids contained (no water/lava sheets down walls except falls), discs not floating detached from their roots or supports | tester |
| W6 | Look and taste of the terrain | human |

**R Structures and loot** (structure, processor, pool, loot changes)

| ID | Check | Judge |
|---|---|---|
| R1 | `structures` at `ABYSS_RUIN` lists a disc-ruin piece; at `ABYSS_CITY` the city | auto |
| R2 | The trove chest at `ABYSS_TROVE` uses an `verdant_deep:chests/*` loot table (G-005 confirms what `query block` returns; if it does not show the table, open it with `as_player` and `expect` on the inventory) | auto |
| R3 | Shots of the city entrance and a ruin: blocks reskinned (mossy/jungle palette), no structure cut by the ravine wall | tester |

**B Basilisk** (anything under `basilisk/`, its data, its spawning)

| ID | Check | Judge |
|---|---|---|
| B1 | 10 `summon`s at `ABYSS_DISC_SAFE`: every size within the configured range, health and segment count grow with size | auto |
| B2 | Natural spawn: survival bot at a disc fixture, `sprint 2400`, a basilisk within 128 blocks (today's `basilisk-spawn.sh`, moved to a disc fixture). May fail on the monster cap until D6 | auto, flaky noted |
| B3 | A naturally spawned basilisk starts asleep and coiled; with the bot at ≥ 2× wake radius for 600 ticks it stays asleep; at 0.5× it wakes within 100 ticks | auto (entity data) |
| B4 | Awake and engaged: the bot (survival, resistance) loses health within 400 ticks; the basilisk retreats after a hit (distance to bot rises within 60 ticks of the hit) | auto |
| B5 | Edge caution: summoned at `ABYSS_DISC_EDGE` with the bot across the gap, `sprint 1200`: the basilisk never goes below the disc's surface y − 3 | auto (`wait_for` polling y) |
| B6 | Climbing: bot on `ABYSS_WALL_TOP`, basilisk at its foot: within 400 ticks it is within 4 blocks of the bot | auto |
| B7 | Segments: segment entity count equals the size's segment count; after `/kill` of the head, 0 segments remain within 64 blocks | auto |
| B8 | `clip` of a chase: body follows the head, no detached or stretched segments, sagging over ledges | tester |
| B9 | Fight feel, fairness, readability of attacks | human |

**G/C Gas and curse**

| ID | Check | Judge |
|---|---|---|
| C1 | Bot descends from the rim into the hole faster than the curse allows: the curse effect appears; ascends slowly: no effect | auto (`as_player` tp steps + `expect` on effects) |
| G1 | Without the gas library: S1/S2 pass, no payload errors in either log | auto |
| G2 | With the library (`thirdparty/jars` or the owner's build): client log shows no payload or codec errors; shot from `ABYSS_RIM` shows haze in the hole | auto + tester |
| G3 | Gas density, GPU cost | human (real GPU, per memory) |

### 4.4 Which suites a change needs (the orchestrator picks; the tester runs)

| Paths changed | Suites |
|---|---|
| `ravine/**`, `mixin/**`, density/aquifer/biome hooks, `data/**/worldgen/**` | S, W (W1+W2 always), R |
| structures, processors, pools, loot, templates | S, R, W1 |
| `basilisk/**`, basilisk spawns in biome JSON, the basilisk config defaults | S, B |
| gas, curse | S, C, G1 (G2 when the library is at hand) |
| build files, loader entrypoints, `fabric.mod.json`, `neoforge.mods.toml` | S on both loaders |
| before the owner merges a goal, and before a release | all, both loaders |

### 4.5 Fingerprint baseline rules
- Baselines: `regression/baselines/fingerprint-<loader>.json` in the mod, committed, each recording the commit it was
  made at. A re-baseline is a commit of its own, so `git log regression/baselines` is the history of intended worldgen
  changes.
- A refactor goal requires W1 **equal**. A difference is a failure: find the cause; do not re-baseline.
- A goal that *intends* a worldgen change re-baselines in the same session, after W5 screenshots show the intended
  change, and its report names the commit, the new baseline and the screenshots. That deliberate step replaces "fix the
  unit tests that broke".

### 4.6 Running it with a subagent
`mc-tester` brief: "Run suites <list> from `mods/verdant-deep/docs/REGRESSION.md` on <loaders> at <commit>; report
PASS/FAIL/SKIP per check ID with result.md paths; judge tester checks against the descriptions in the plan; list human
checks as not run." One suite = one scenario file = one `bin/mc-test run verdant-deep[@fabric]
mods/verdant-deep/regression/<suite>.sh` (one sandboxed command each, as the kit requires). Client suites need the display (`! bin/mc-display start`).

## 5. Unit tests: keep a handful (G-006)

Today: 250 `@Test` in 39 classes, 19 of them boot Minecraft. Keep a test only if it guards an **invariant that no
intended tuning changes** and that **a scenario would rarely or never see**. Provisional keep-list (G-006 confirms each
by reading it; anything else it wants to keep is a question):

| Keep | Why a scenario cannot replace it |
|---|---|
| `RavineShapeTest` "nothing opens past the reach" (`:113-129`) | A violation shows only in rare cells at chunk borders; the fixtures would miss it |
| `ConeShapeTest` inverse round trip (`:229-236`) | Math with edge cases near the bounds |
| `CoilLayoutTest` (`:11-22`) | Sleeping segments overlapping only at some yaws |
| `HoleGasPayloadTest` round trip | Codec mismatch shows only with the gas library on a client |
| One merged "every mod data file and template loads" test (from `DiscFeatureFilesTest`, `RootFilesTest`, `DiscRuinFilesTest`, `DialsFileTest`, `DiscTroveFilesTest`) | Files read lazily at generation time fail only in the chasm that uses them, not at boot |
| New: random streams are disjoint (from G-009) | A collision correlates two features silently |

Everything else goes, including `ChainDroopTest` (tuned behaviour), `DialsTest`, the theme, roots, falls, ruins and
domes tests (tuned values or mirrored formulas). Also: `ChasmStatsReport` (a diagnostic printer) becomes a mention in
`docs/REGRESSION.md` of `/abyss stats`; `TroveTemplates` (writes `src/main/resources` when an env var is set) becomes a
Gradle task `writeTroveTemplates` on the common test classpath, or is deleted if the templates are now edited in game
(owner's call, a question in G-006). Remove production code that exists only for deleted tests
(`BasiliskSegmentChain` 6-arg `update`, widened `coilLayout` if its test goes). `MinecraftBootstrap` stays only if a
kept test needs it.

## 6. Basilisk: why it got messy and the fix (G-007, G-008)

Evidence: 18 commits in two days on `G-002`/`G-003`; `Basilisk.java` churned 711 lines, `BasiliskAttackGoal` 444,
and `BasiliskVenomStrikeGoal` (376) was written and replaced. Every tuning number is a Java constant or an inline
literal (`Basilisk.java:137-144, 405, 462-464, 486-487, 554`; `BasiliskAttackGoal.java:95, 235, 240, 278, 304, 340`),
called "first guesses" in `.agent/questions.md` (G-002). There is no written description of the intended behaviour, so
each playtest screenshot became a code commit, and the playtest items (climb, hit-and-run, spawn) were never checked.

**G-007: behaviour spec and tuning in a config file**
1. `docs/creatures/basilisk.md` (in the mod): the states (asleep, waking, stalking, bite, lunge, retreat, flank,
   climbing), what triggers each, and the observable promise behind each B-check. Short; the B-suite is derived from it.
2. `BasiliskTuning` record + codec, read from `config/verdant_deep/basilisk.json` (D2) the way `DialsFile` reads the
   dials (written at shipped values when missing, filled in when keys are missing, a broken file left alone and the
   shipped values used): size range, speeds, wake radius, attack timings, edge margins, spawn chance. Goals and
   controls read it from one server-side holder; attributes are set from it on spawn and size roll.
3. `/abyss tune basilisk <key> <value>` (op only, tab-completes the keys): sets the value, saves the file, re-applies
   it to loaded basilisks. `/abyss tune basilisk` lists the current values.
4. Done when: the file's values equal today's constants (behaviour unchanged); in a scenario, `/abyss tune basilisk`
   on the size range changes the sizes of new summons (B1) and the file on disk shows the new value; S and B1, B3-B7
   pass on NeoForge; S on Fabric.

**G-008: code cleanup** (behaviour-preserving)
- Pull out of `Basilisk.java` (585 lines, about 9 concerns): `BasiliskSleep` (`:288-330`), `BasiliskHitParts`
  (`:418-452`), foliage chewing (`:471-494`). What remains: wiring, sync, size application, combat helpers.
- One `BasiliskSteering` helper: `pointAround(target, bearing, radius)` (replaces `BasiliskStalkGoal.ringPoint:92` and
  `BasiliskAttackGoal.pointAround:317`) and `moveToSafely(point, speed, fallback)` (the "reject edge, moveTo, fall back"
  done three times: `AttackGoal:282-288, 306-309`, `StalkGoal:79-87`).
- One probe-ahead sweep in `TerrainProbe` used by `BasiliskMoveControl.basiliskFrontIsUnsafe:85-104` and
  `BasiliskAttackGoal.lungeLineIsClear:179-191`.
- Fix the stale comment `BasiliskNavigation.java:12` (says GroundPathNavigation, extends WallClimberNavigation).
- No creature framework: abstract only when a second creature exists (the old "two real callers" rule still holds).
- Done when: both loaders compile; B1, B3-B8 pass on NeoForge with the same tuning file; S on Fabric; `Basilisk.java`
  under ~350 lines.

## 7. Worldgen code (G-009, G-010)

**G-009: ravine query surface and random streams** (behaviour-preserving)
- `RavineCarve` is both the density function (`RavineCarve.java:32`) and the ravine's query API, which is written four
  times: `RavineFootprint` interface (`:102-138`), `NONE` (`:16-70`), the `of()` composite with one loop per method
  (`:170-290`), and `RavineCarve` (`:208-333`). Move the queries into a plain class implementing `RavineFootprint`;
  `RavineCarve` keeps `compute()` and holds it; the composite delegates through one helper. Adding a query then
  touches two places, not four.
- Random draws are numbered by ~20 hand-picked offsets in 15 files (`HASH_BASE = 1_000_000`, `700_000`, ...), with two
  exact duplicates (`DiscFalls:39`/`RootBlocks:21`, `ConeShape:22`/`DiscTheme:261`), harmless today only because
  they go through different draw functions. Put every offset in one `Streams` class next to `RavineCells`, with the
  same numbers (so W1 stays equal), and the disjointness test from §5.
- Done when: both loaders compile; W1 equal to baseline on NeoForge and Fabric; W2 equal; kept unit tests pass.

**G-010: feature-first packages** (mechanical moves, one commit per package)

```
dev.syrval.verdantdeep
  VerdantDeep           init only: one register() call per feature, reads as a table of contents
  abyss/                   pure worldgen of the chasm (today's ravine/, 74 files)
    shape/  cone, ravine shape, wall noise, cells, bounds, curvature, streams
    disc/   disc, layout, theme, palette, ground, relief, scatter, growth
    water/  falls, water, lava
    root/   roots
    ruin/   sites, ruins, ruin pieces
    settings/  RavineSettings, Dials, *Settings
    RavineFootprint + its implementation (G-009)
  worldgen/                the Minecraft side of the chasm (today's compat/: density hook, aquifer, biome resolver,
                           painter, grower, structures, processors, placements)
  creature/basilisk/       entity, ai, client, spawning, tuning
  mechanic/curse/, mechanic/gas/   ticker, payload, client (today split over ravine/ and compat/)
  command/                 /abyss stats
  mixin/
```
- "compat" today means "the Minecraft side", which misleads (compat usually means other-mod integration); the new name
  says what it is.
- Splitting `abyss/` forces deciding what is public: `RavineCells`' draw helpers are package-private today, which is
  partly why the package stayed flat. Make them a small public `abyss.shape.Draws`.
- Break the two cycles on the way (`DiscFalls`↔`DiscGround`, `DiscTheme`↔`DiscRuins`) by moving the shared piece to
  the lower package.
- Leave the 17-component `RavineSettings` threading alone unless the split shows a natural seam: regrouping it changes
  the JSON schema and the owner's datapacks.
- Done when: both loaders compile; W1 equal on both loaders; S1-S4 on both loaders; `rg 'package .*\.compat'` empty.

## 7a. Two Minecraft lines: 1.21.1 pinned and the newest (G-011, D5)

Evidence (`.agent/notes/abyss-multiversion.md`): the library keeps `main` on 26.3 and a pinned `1.21.1` branch, names
artifacts `<version>+mc<mc>` (`common/build.gradle:48-49`), lists every version-specific file in `docs/PINS.md:53-71`,
and keeps its simulation pure Java, compiled on its own by `:common:compileSimStandalone`. Its 1.21.1 → 26.3 port
touched 113 files, but only one file in `sim/`: the pure core moved for free, and everything with Minecraft in it moved.

The abyss has none of this: one line, 1.21.1 only, the library pinned to `0.1.0+mc1.21.1` in `libs/volumetrics`, and
Minecraft types spread through `ravine/` (39 of 74 files). Known changes on the way to 26.x: `ResourceLocation` is
`Identifier` (52 uses in 12 abyss files); 26.x is unobfuscated (no-remap Loom) and needs Java 25. Worldgen internals,
mixin targets, payloads and AI on 26.x are **unverified**: no 26.x sources are cached yet.

Copy what worked for the library, and no more. A full adapter layer over worldgen internals would be a speculative
abstraction over APIs we have not seen yet.

1. **Pure core, checked by the build.** After G-010, the version-independent packages (`abyss/shape`, the layout and
   walking parts of `abyss/disc` and `abyss/root`, the stream draws, `creature/basilisk` chain and size math) compile
   in a standalone source set with no Minecraft on the classpath (DFU codecs allowed), like the library's
   `compileSimStandalone`. Settings records that hold Minecraft types (block state providers, keys) stay on the
   Minecraft side. A port then leaves the pure core alone, and the build stops Minecraft from creeping back in.
2. **`docs/PINS.md` version-specific files table**, as in the library: each mixin and Minecraft-facing class, and the
   exact APIs it depends on. Re-checked on every bump; it is the port's checklist.
3. **Branches and artifacts like the library** (D8 below). The abyss jar is named `<version>+mc<mc>`, and
   `libs/volumetrics` on each line holds that line's library build. Fix the stale publish path in
   `libs/volumetrics/API.md`.
4. **The port itself is a later goal**, not this one. It starts with `bin/mc-cache sources` for the newest version so
   the 26.x worldgen and payload APIs are checked in source, not from memory.

**D8 (owner):** the branch scheme. The library uses `main` = newest and `1.21.1` = pinned. For the abyss, either
(a) the same: create `1.21.1` from `main` now, and goals for the current game target `1.21.1` until the port turns
`main` into the newest line; or (b) the reverse in both repos (`main` = 1.21.1, `<mc>` branches for newer versions),
which means renaming the library's branches. Recommendation: (a), the convention the library already has; the kit
already reads the version per branch from `gradle.properties`.

## 8. Goals to paste into GOALS.md (Active, in this order)

```
- [ ] **G-004 Abyss: scope and rules for an adventure mod**
  - Where: `mods/verdant-deep` (branch from main after D1)
  - Outcome: SCOPE.md describes the mod as it is (pillars, architecture as built, client required, dependencies);
    the history and wishlist move out; AGENTS.md drops "datapack first" and "server-only" and keeps the worldgen,
    mixin and loader rules.
  - Done when: SCOPE.md ≤ 300 lines; old §6-7 moved verbatim to docs/history.md and §9 to docs/ideas.md (git diff
    shows moved lines); 03-abyss.md moved to docs/history/; AGENTS.md has no "datapack first"/"no client-side install";
    `git diff --stat` touches only .md files.
  - Limits: docs only; also move docs/superpowers/{plans,specs} into docs/plans/. Plan: docs/plans/2026-10-23-rescope-and-cleanup.md §1, §7.

- [ ] **G-005 Abyss: regression plan and suites**
  - Where: `mods/verdant-deep` (docs/REGRESSION.md, regression/)
  - Outcome: the plan of §4 as docs/REGRESSION.md; fixtures recorded; one scenario per suite; W1 baselines for both
    loaders.
  - Done when: S, W, R and B suites run on NeoForge and S, W1, W2 on Fabric with a result.md each; every auto check
    passes or is listed with its cause (B2 may fail on the monster cap; say so); baselines saved with their commit.
  - Limits: no mod code changes. Plan §4.

- [ ] **G-006 Abyss: cut unit tests to the keep-list**
  - Where: `mods/verdant-deep`
  - Outcome: about six tests remain (plan §5); test-only production code removed.
  - Done when: `bin/mcg verdant-deep test` passes; `grep -rc '@Test'` ≤ 10; W1 equal on NeoForge.
  - Limits: deletions approved for the §5 list only; TroveTemplates' fate is a question if unclear.

- [ ] **G-007 Basilisk: behaviour spec and tuning in a config file**
  - Where: `mods/verdant-deep`
  - Outcome: docs/creatures/basilisk.md states the intended behaviour; every basilisk tuning number lives in
    config/verdant_deep/basilisk.json (codec), settable in game with /abyss tune; behaviour unchanged.
  - Done when: JSON values equal the old constants; a scenario that changes the size range and runs /reload sees the
    new range (B1); S and B1, B3-B7 pass on NeoForge; S passes on Fabric.
  - Limits: no behaviour changes; no new dependency. Plan §6.

- [ ] **G-008 Basilisk: code cleanup**
  - Where: `mods/verdant-deep`
  - Outcome: Basilisk.java split (sleep, hit parts, foliage); one steering helper and one probe sweep replace the
    duplicates; behaviour unchanged.
  - Done when: both loaders compile; B1, B3-B8 pass on NeoForge with the unchanged tuning file; S on Fabric;
    Basilisk.java under ~350 lines.
  - Limits: no creature framework, no tuning changes. Plan §6.

- [ ] **G-009 Abyss: ravine query surface and random streams**
  - Where: `mods/verdant-deep`
  - Outcome: RavineCarve only computes density; the queries live in one class implementing RavineFootprint; all random
    stream offsets in one place with the same numbers, plus a disjointness test.
  - Done when: both loaders compile; W1 equal to baseline on NeoForge and Fabric, W2 equal; kept unit tests pass.
  - Limits: no worldgen output change (no re-baseline). Plan §7.

- [ ] **G-010 Abyss: feature-first packages**
  - Where: `mods/verdant-deep`
  - Outcome: packages as in plan §7 (abyss/, worldgen/, creature/, mechanic/, command/, mixin/); compat/ gone; the two
    ravine cycles broken.
  - Done when: both loaders compile; W1 equal on both loaders; S1-S4 on both loaders; `rg 'package .*\.compat'` empty.
  - Limits: moves and visibility only, one commit per package; only when no other abyss branch is open (D7).

- [ ] **G-011 Abyss: ready for two Minecraft lines**
  - Where: `mods/verdant-deep` (after G-010 and D8)
  - Outcome: the pure core compiles standalone with no Minecraft on the classpath; docs/PINS.md lists every
    version-specific file and the APIs it depends on; jar named `<version>+mc<mc>`; branches per D8.
  - Done when: a `compileCoreStandalone` task (name as fits) passes and fails when a Minecraft import is added to a core
    file (try it once, then revert); both loaders compile; W1 equal on NeoForge; PINS.md table covers every file under
    mixin/ and every class importing net.minecraft outside the core (a grep in the report shows none missing).
  - Limits: no port to 26.x; no adapter layer beyond the core split. Plan §7a.
```

## 9. Deferred, so it is not forgotten

- **Basilisk spawning through `mc-boss-spawn-lib`** (D6). Natural basilisk spawns lose to the monster cap: about 72
  hostiles within 128 blocks after 90 s at the city fixture, and the basilisk spawns only in the `disc_*` biomes
  (worklog 2026-10-22). The spike `mods/mc-boss-spawn-lib@851e3ba` (branch `agent/boss-spawn-spike`) adds a
  `boss_spawn:roaming_boss` mob category, verified on NeoForge and Fabric server boots; Fabric needs Fabric API there.
  Next: the owner reviews the spike; a goal then moves the basilisk to that category (adding a dependency to the mod
  needs approval) and B2 drops its "may fail" note. It also suits future bosses.
- **The 26.x port of the abyss**, after G-011 (§7a.4).
- **The dials file** (`config/verdant_deep.json`) could move to `config/verdant_deep/dials.json` next to the
  basilisk file, with a one-time migration. Only if the owner wants one folder; it changes a path players know.
