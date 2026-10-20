# Trove variety: design

Mod: mc-abyss-ruins (overgrown abyss), MC 1.21.1, NeoForge + Fabric. Branch `agent/trove-variety`.

## Intent

Troves are currently small, uniform and unrewarding: one chest or barrel, vanilla `chests/jungle_temple` loot,
only on the lush, jungle and mangrove discs. The owner wants them to be a **jackpot** worth descending for, with
the chasm dangerous enough to justify the reward. Three changes:

1. Troves with multiple chests sometimes spawn on lower discs.
2. Troves sometimes have a spawner hidden beneath them, more common on lower rings.
3. Amethyst (crystal) and lava discs get their own unique trove style.

## Goals

- Deeper means richer: more multi-chest troves and more hidden spawners (loot tables themselves stay as they are).
- A hidden spawner is an unannounced extra group of monsters guarding the trove (owner: "no warning").
  It spawns whenever a player is near, so the loot is guarded by an ambush.
- Crystal and lava discs, which have no troves today, get troves that look unlike the jungle-family ones.
- Every new chance is a config dial next to the existing `troves` dial.

## Non-goals / anti-goals

- No telegraphing of the spawner (no cracked block, no particles, no sound).
- Anti-goal: multi-chest troves that are barely more than one chest, or spawners in shallow troves often enough to feel unfair.
- No new dependencies. No changes to the volumetrics library.
- Do not alter existing ruin (non-trove) generation.

## Current state (researched, confirmed in source unless noted)

- Troves are a structure of type `disc_ruins` with `sites: troves`: `worldgen/structure/disc_troves.json`,
  `structure_set/disc_troves.json`, pools `template_pool/disc_troves/{cairn,marker}.json`, three NBT files each under
  `structure/trove/`, processor `disc_troves/weathered`. Placement: `RavineCarve.java:296-306`,
  `DiscRuinSites.java:85`.
- Each trove has 2-4 masonry blocks and exactly one chest or barrel (`DiscTroveFilesTest.java:50,89,116-117`).
- Per-theme trove config lives in `carve.json` `troves.kinds` / `troves.loot` (lines ~238-247, 841-847, 1253-1259);
  loot is the single table `minecraft:chests/jungle_temple`. Tier and depth/distance weighting code exists
  (`DiscRuins.java:49-103`) but is inactive for troves. Mod loot tables: `data/overgrown_abyss/loot_table/chests/`.
- Depth: "rings" are the discs of a cone (`ConeDiscLayout.java:9-16`); `DiscTraits.java:14-23` gives `height`
  (0 to 1 across the hole's discs) and `distance`. Themes are weighted in `carve.json` `disc_themes`
  (lush ~34, jungle ~263, mangrove ~874, crystal 1284, lava 1401; weights 4/4/3/2/2). Crystal: distance >= 0.55
  and size <= 0.4. Lava: height <= 0.3.
- Blocks of a trove come entirely from its NBT templates, not the disc palette; a new style is a new theme entry
  with its own `troves.kinds`, pool files and NBT, with no new Java for style alone. NBT contents not yet inspected.
- No code places spawners (`minecraft:spawner` appears only in `ChasmStats.java:53-56` and a test).
- Mobs per disc are biome data (`worldgen/biome/disc_*.json`, `compat/InheritedSpawns.java`). Lava: magma_cube plus
  monsters; crystal: spider, zombie, skeleton, creeper, slime, enderman, witch and others.
- Dial `troves` at `Dials.java:59`, documented in `docs/SETTINGS.md:26`. Existing tests: `DiscTroveFilesTest`,
  `DiscRuinsTest:486-504`, `DialsTest`, `ChasmStatsTest`.
- Unverified: NBT contents, how `height` maps to "lower discs" in practice, whether the wither curse
  (`compat/CurseTicker.java:39`) varies with depth.

## Design

Decided by the owner: the three features above, jackpot feel, unannounced spawners.
Everything below marked **(my call)** is a default the owner has not seen.

### 1. Multi-chest troves

- New trove kinds with 2-3 chests in a larger template (**my call**: 3 template variants per theme family).
- Eligible only on lower discs; probability rises with `height` (**my call**: zero above height 0.4, ramping to
  about 35% of troves at the bottom; exposed as a dial).
- **No new loot tables (owner decision).** Every chest uses the current trove loot table
  (`minecraft:chests/jungle_temple` via `troves.loot` in `carve.json`). The jackpot comes from there being more chests
  to open, not richer contents. Crystal and lava troves, which have no `troves.loot` today, use the same table
  (**my call**; the builder may point them at an existing mod table under `loot_table/chests/` if one fits better,
  but must not author new ones).

### 2. Hidden spawners

- A spawner block buried 1-3 blocks under the trove floor (**my call**: 2), invisible from the surface, no hint.
- Probability rises with depth (**my call**: about 5% at the top disc, about 40% at the bottom; dial).
- Mob by disc theme (**my call**): lush/jungle/mangrove cave spider or spider; crystal skeleton or cave spider;
  lava blaze (magma cube as a fallback if blazes behave badly in the open air of the chasm). Deeper variants may
  use stronger mobs or higher spawn counts (**my call**, left to the builder within reason).
- Needs new code: a place in the trove placement path to add the spawner and set its entity. Must work on both
  loaders from `common/`.

### 3. Amethyst and lava trove styles

- Crystal troves (**my call**): a geode-like vault of amethyst, calcite, smooth basalt and budding amethyst around
  the chest(s). Currently crystal is limited to distance >= 0.55, size <= 0.4, so troves there are naturally rare.
- Lava troves (**my call**): a blackstone/magma forge, partly submerged or beside lava ponds, chest(s) behind
  obsidian or gilded blackstone. Lava is limited to height <= 0.3 (the lower end), so these are also the deepest
  troves and fit the multi-chest and spawner scaling.
- New `troves` blocks in the crystal and lava entries of `carve.json`, new pools and NBT templates, a weathering
  processor if needed.

### Config

New dials alongside `troves` in `Dials.java` and `docs/SETTINGS.md`: multi-chest chance scale and spawner
chance scale (both defaulting to 1.0 times the built-in curve), so the owner can tune or disable them.

### Settled after builder check (all my calls)

- **Spawner scope:** any trove kind on any theme can get a spawner, rolled per trove from `height` (shallow
  lush/jungle/mangrove troves sit near the 5% end, so they rarely get one). The spawner hook therefore lives in the
  shared placement path, not in the multi-chest kind.
- **Spawner mechanics:** placed by a post-pass at trove placement, not baked into NBT. It needs a solid pocket: replace
  the target position and a one-block shell around it with the disc's stone/deepslate. If the position below the floor
  is air, a carved wall edge, or lava-adjacent and a pocket cannot be made, skip the spawner for that trove.
  Never place a spawner that is exposed to air.
- **Depth measure:** `DiscTraits.height` (0 to 1) for both curves, absolute thresholds as written above, not
  normalized per hole.
- **Mob fallback:** blaze to magma cube is acceptable without asking; record which was used in the report.
- **Done means:** both loaders compile, unit tests for the curves and templates pass, and `/abyss stats` shows
  spawner and multi-chest counts rising with depth on a generated chasm. If the 1.21.1 harness can be published, also
  boot once on NeoForge and once on Fabric; if not, say so as unverified.

## Constraints

- Both loaders must compile; boot `@fabric` since loader-specific code is likely touched.
- Rendering and look cannot be tested here: mark the crystal and lava styles "needs a human check".
- Tests: default no new test. Worth considering a pure-logic JUnit test for the depth-to-chance curves and an
  extension of `DiscTroveFilesTest` so new templates keep its invariants (chest counts, block validity). Say in
  one line what failure each catches before writing it.
- Verify with `bin/mcg` compile, `/abyss stats` (spawner counting exists in `ChasmStats`) and one real chasm
  generation via the test server; the harness for MC 1.21.1 is currently unpublished.
- MC API for placing spawners and setting the entity must be looked up in the project's decompiled 1.21.1 sources,
  not written from memory.

## Left to the builder

Exact template layouts, the loot tables' contents, the precise probability curves, how to author NBT (generate via
code or capture from structure blocks), and the commit split.
