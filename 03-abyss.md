# Project 3: Abyss

**Working title:** Abyss. **Form:** small mod (using `ravine-core`) + datapack. See `README.md` for common rules and the shared module.

## Summary

A giant ravine whose walls are varied and lush, with ledges, waterfalls, roots and small ruins, opening at the bottom onto a flat-floored cavern holding a large ancient ruined city. Dangerous by design; the way down is left to player creativity. The first iteration reskins the vanilla Ancient City; later iterations diverge.

## Goals (your own)

1. The ravine looks awesome.
2. Playable, with player creativity necessary (no guaranteed route).
3. Several wall styles, ledges with waterfalls, root areas, side-wall ruins.
4. Ancient ruins at the bottom with paths that make sense and can be walked by a player. Aesthetics first.

## Decisions already made

- First iteration: **literal reskin of the vanilla Ancient City**.
- Cavern size: **just big enough for the city** (not larger).
- Vanilla blocks only. Noise-gated style zones are acceptable (not real biomes).
- No guaranteed walking route in the ravine itself; traversability matters only inside the bottom ruins.

## Compatibility stance

Do not override any terrain file. `ravine-core` wraps the final density of the configured noise-settings with `min(original, carve)`. Primary test target: Larion. Everything else (features, structures, loot) lives in the project's own namespace.

## Design

### Layer 1: Shape
- Ravine cells (shared definition; own salt). Carve function: silhouette with wall irregularity, overhangs, and a terracing term so ledges appear where walls are steep.
- Below the ravine, a cavern volume sized to the city, with a **flat floor plateau** at a fixed Y. A flat floor lets the city sit down without terrain-adaptation tricks.
- **Fluid override** inside the footprint (see README risk). Everything below the global lava level would otherwise flood with lava.

### Layer 2: Wall dressing
- A "style" = set of vanilla placed features active in a noise zone. Zones come from an `abyss:wall_style` noise and an "inside the ravine" test.
- Features are added to all overworld biomes with a NeoForge biome modifier (not a real biome per style). **[recall; confirm it works and that the placement filter behaves on vertical walls.]**
- First styles (suggested 3): **Hanging Gardens** (vines, azalea, dripleaf, spore blossom, cave vines), **Root Cliffs** (mangrove roots, rooted dirt, hanging roots), **Cascades** (water springs at ledge lips, pools, moss). Add **Ruined Terraces** later as the wall-ruin host.
- Wall coating: wall-attaching features (vines, glow lichen via multiface growth) and banded replacement of exposed stone using the ore feature with air-exposure discard disabled. **[recall; verify parameter.]**
- Limit: no per-style fog, sound or particles.

### Layer 3a: Bottom city (iteration 1: reskin)
- Own structure type `abyss:city`: a jigsaw whose pools are **copies of the vanilla ancient-city pool JSON in your namespace**, referencing the **vanilla template files by ID** (do not redistribute vanilla templates). Extract the vanilla JSON with the game's data generator.
- Keep the vanilla jigsaw settings (size and maximum distance from centre). **[recall: size 7, max distance 116; read the real file.]** These bound the footprint and therefore the cavern size.
- **Reskin by processors** (vanilla rule processors): map deepslate bricks/tiles to stone bricks / mossy / cracked variants; remove sculk, sculk sensors and shriekers, soul fire and soul lanterns; replace lighting with vanilla alternatives (lanterns, froglights, glow lichen, candles). A starting table goes in a processor file. Optionally vary the mapping by position for weathering.
- **Loot:** use the rule processor's `append_loot` block-entity modifier to assign your own loot tables. **[recall; confirm.]**
- **Placement:** a custom structure-placement type from `ravine-core` puts the city at the ravine cell centre, at the cavern floor Y (start height absolute, projection to heightmap off). The city normally also uses terrain adaptation in the vanilla file; check and mirror what suits the flat floor.
- **Biome/ambience:** cavern ambience would ideally need a biome; defer. Evaluate overriding the biome inside the cavern volume as part of `ravine-core` later.

### Layer 3b: Wall ruins (later)
- 2 to 3 small ruin types (cliff dwelling, tower, shrine) built as jigsaw structures anchored on ledges, extending into the wall via template air. One shared architectural vocabulary. Loot via your own tables.

### Divergence after iteration 1
Terraced and vertical layout, pyramids, own road kit with building pieces authored in-house (the vanilla city is a flat grid). Decide once iteration 1 is playable.

## Phases

| Phase | Work | Gate |
|---|---|---|
| 0 | Pin versions; fixed-seed test world | Loads on a server |
| 1 | **Shape spike:** hook + carve + fluid override; plain stone | Terrain outside the footprint unchanged under Larion; **screenshots from the rim, mid-air and floor look striking**; cavern floor is lava-free |
| 2 | 3 wall styles; ledges; waterfalls | Walls look varied from three viewpoints |
| 3 | Cavern + flat floor + city placement (vanilla city, unmodified) | City generates intact on the plateau |
| 4 | Reskin processors; loot | Looks nothing like the Deep Dark; chests give correct loot |
| 5 | Wall ruins | Visible from the rim; loot works |

Stop and review after Phase 1: if the silhouette does not look striking, nothing else will fix it.

## Acceptance

A fresh world with a fixed seed produces at least one ravine whose floor holds an intact reskinned city; every door in the city opens to a path; the cavern is free of lava; terrain outside the footprint matches the same seed without the mod.

## Research

- Final-density hook and fluid-picker override (see README).
- Vanilla ancient-city JSON: pool names, jigsaw settings, terrain adaptation, start height, processor lists.
- NeoForge biome modifiers adding features across all overworld biomes; placement-filter behaviour with a noise-based gate.
- Structure-placement type from the cell centre; behaviour with structure-set spacing rules.
- Cost of the carve plus a flat-floor term at the chunk scale.

## Risks

1. Fluid override fragility (mixin into vanilla internals).
2. The reskin may still read as a Deep Dark city; that is accepted for iteration 1.
3. Aligning the city's footprint with the cavern: derive the cavern size from the real jigsaw bounds.

## Open questions

1. Cavern floor Y (depends on the target world's floor).
2. Initial wall style count (suggested 3).
3. After iteration 1: reuse the vanilla road pieces or author a road kit.
