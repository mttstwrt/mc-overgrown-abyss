# Testing datapacks

Each folder here is a datapack that overrides `overgrown_abyss:ravine/carve` with different values, for trying shape
settings without rebuilding the mod. They are not shipped with the mod.

## Install

1. Open the world's save folder (`saves/<world name>/`) and go into its `datapacks` folder.
2. Copy the pack folder (for example `wide-bays`) there, or the zip made from it (`python3 make-zips.py`).
3. Start the world. Only chunks that have not generated yet use the new values, so use a new world, or fly to unexplored
   ground.

A pack replaces the whole `carve.json`, so when the mod's schema changes an old pack stops loading; regenerate it from
the mod's file. The log line `Ravine settings:` at world load shows which values are live.

## Packs

The mod's own hole is now the cone: a single round hole, `base_radius` 150 at the floor (a little wider than the city cavern's
136) and narrowing to `top_radius` 45, with free-standing discs in layers `layer_spacing` 20 apart around a clear cylinder of radius 8. Its numbers:

- Discs: radius 20 to 64, whatever room the cone has at the disc's height. A disc too large for the cone there sits against
  the clear cylinder and cuts its dome into the rock around the cone. `base_clearance` -16 starts the lowest layer 16 blocks
  under the cavern roof, in the cavern's airspace over the city.
- Riders: discs standing on top of other discs, outside the cone. A rider stands near its host's outer edge, on the side away
  from the middle of the cone, inside the host's dome; its own dome rises through the host's roof into the rock beyond. Each
  place on a disc (larger discs have more) holds one with `rider_chance` 0.5; a rider's radius is at most `rider_scale` 0.6 of
  its host's, and a rider may carry riders of its own. 0 keeps every disc in the ring around the cone.
- `outer_radius` 200: nothing of any disc lies further than this from the middle of the cone.
- Stems: `stem_fraction` 0.05 of the disc's radius, kept between `min_stem_radius` 1 (2 blocks across) and `max_stem_radius` 3
  (6 across). `funnel_scale` 1.5 flares the stem out to the disc's width under the platform.
- Bowls: each platform rises from its middle to its rim, top and underside alike, by `min_bowl_depth` 1 (smallest discs) to
  `max_bowl_depth` 4 (largest). `bowl_variation` 0.75 lets each disc come out anywhere from that depth down to a quarter of it,
  so some stay flatter. Depths of 0 and 0 make flat discs; a variation of 0 gives every disc the full depth for its size.
- Hanging: `hang_chance` 0.35, so about a third of the discs are drawn to hang from a root, and do so where they have a
  ceiling. `root_spread` 3 and `root_scale` 4 shape the root (3 stem radii wide where it meets the ceiling, half that 4 blocks
  away).
- Themes: four, see below.

`disc_themes` (optional, see `DiscTheme`) are the kinds of disc. Each disc is given one by a weighted draw, and a theme sets:

- `biome`: the biome stamped over the disc's dome and platform. It colours grass, leaves and water, decides what spawns, and
  keeps the surrounding biome's features off the disc. The mod's own are in `data/overgrown_abyss/worldgen/biome/`.
- `inherits`: a biome the disc's biome takes its content from, as that biome is when the level loads, with whatever other mods
  have added to it.
  - Features: the parent's are grown on the disc by its own placement rules, with the disc's top as the ground. `stages` chooses
    which stages of decoration are grown (only `vegetal_decoration` unless given, which leaves out lakes, geodes, monster rooms,
    ores and springs; an empty list grows none) and `without_features` names placed features to leave out.
  - Spawns: the parent's, changed by the disc biome's own file. A mob listed under `spawners` in
    `data/overgrown_abyss/worldgen/biome/disc_*.json` is added, and replaces the parent's entry for the same mob in the same
    category (so a new weight or group size). `without_spawns` names mobs to leave out of what is inherited. The mod's own
    files list no mobs, so by default a disc spawns exactly what its parent does.
- `weight`, and three optional ramps that multiply it by where the disc is among the discs of its own hole: `by_height`
  (`bottom` to `top`), `by_distance` (`centre` to `edge`) and `by_size` (`small` to `large`).
- `only`: optional hard limits on the same three traits, each from 0 to 1, as `min` and `max`. A disc outside them never gets
  the theme: `"only": {"distance": {"min": 0.55}, "size": {"max": 0.4}}` keeps a theme to small discs far from the centre.
- `palette`: what the disc is made of. Blocks for the platform's `top` and `underside` (layers counted in from the surface), its
  `body`, and the `stem` (`surface` layers and `core`). Each block is a vanilla block-state provider, so it can be one block, a
  weighted mix or noise patches. A part left out stays the terrain's own rock.
- `water`: ponds and streams lying in the platform's top (see `DiscWater`). `ponds` is the share of the top that is pond (0.1 is
  a tenth) and `pond_size` about how far apart ponds are; `stream_width` is how wide a stream is in blocks (0 for none) and
  `stream_spacing` about how far apart streams and their bends are. `depth` (1 or 2, default 2) is how deep the middle of a pond
  is; pond edges and streams are 1 deep. `bank` (default 3) keeps the water that many blocks from the rim. Water never runs:
  none is put where the ground beside it is lower, which leaves a one-block dam wherever the bowl steps down. A theme with water
  needs a palette for the ground that holds it (`depth` + 1 blocks of `top`, or a `body`).
- `growth`: configured features of the theme's own, each placed on average once per `every` blocks of where it grows: `on` is
  `top` (dry ground, the default), `water` (the bed of a pond or stream, where a mangrove starts) or `underside`. They are placed
  in the order listed, so vines listed after trees find the trees there, and before what the theme inherits, as vanilla grows a
  biome's trees before its grass.

The mod's own themes:

- `disc_lush` inherits from `minecraft:lush_caves` and adds nothing: a plain stone platform, which lush caves moss over itself.
- `disc_jungle` inherits from `minecraft:jungle` (three times as likely in the middle of the cone as at its edge). Grass over
  dirt, a few small ponds and streams, and its own growth: giant and ordinary jungle trees, bushes, ferns, vines, glow berries
  in the canopy, and under the disc glow berries, tufts of leaves and vines.
- `disc_mangrove` inherits from `minecraft:mangrove_swamp` (favours the wide low discs). Mud over packed mud, about two fifths of
  it shallow water, mangroves on the mud and (mostly the tall kind, which stands high on its roots) in the water, and under the
  disc glow berries and hanging clumps of mangrove roots with vines; its stem is clad in roots.

Both are thinned so that a player can walk through: bushes, trunks, roots and low leaves stand in about a quarter of the
ground, counting a place as blocked if either of the two blocks a player takes up is. The numbers to change are the `every`
of the bushes and trees in each theme's `growth` (larger is thinner); ferns and grass do not block and can stay thick.
- `disc_crystal` inherits nothing and is made by hand (amethyst over calcite, with clusters); it is rare and kept to small discs
  in the outer half, which the layout puts behind larger discs.

Jungle and mangrove grow their own trees because the parent's may not grow on a disc at all: an overworld overhaul such as
William Wythers' Overhauled Overworld replaces those biomes with trees that only grow at certain heights above sea level and on
soils its own terrain lays down. The disc's trees are vanilla's, kept under the mod's own ids
(`data/overgrown_abyss/worldgen/configured_feature/disc/`) so that such a pack cannot change them, with two changes: more vines
hanging from their leaves; a `min_clipped_height`, so that a tree under a low part of the dome grows as tall as there is
room instead of not at all; and a block more of trunk on the jungle tree and the short mangrove, which lifts their lowest
leaves over a player's head. Vanilla's own tree feature is left out of what is inherited (`without_features`), or a disc would be
twice as thick with trees without such a pack as with it. Everything else is still inherited: grass, flowers, melons, lily pads
and whatever other mods add. With no themes every disc is the terrain's own rock in the biome it lies in.

- `tuned-themes`: the mod's file as it was before biomes were inherited, with the hand-made palettes and growth lists for lush,
  jungle and mangrove (denser foliage, mangrove roots on stems). For comparing the two in game. It inherits spawns only
  (`"stages": []`), so its foliage is all its own.
- `cone`: the mod's own file plus a fifth theme with no biome, in plain concrete (white top, black underside, light grey body,
  orange stem surface, yellow stem core), that makes each part of a disc easy to tell apart.

### Ravine packs (set aside for now)

The long ravine with discs cut into its walls is no longer the mod's default; these packs still give it. They keep the discs they
had before the cone became the default: stems `stem_fraction` 0.2 with `min_stem_radius` 2.5 (never below 5 blocks across), flat
discs (bowl depths 0) and no hanging. Their own numbers are `spacing` (blocks of ravine per disc in a row), `row_spacing`,
`row_jitter`, `side_stagger`, `max_overshoot` and the offsets (how far a disc's centre sits inside the wall).

- `painted-ravine`: what the mod shipped before the cone (spacing 64, row spacing 20, row jitter 0.4, side stagger 0.5, max
  overshoot 4, large offset bonus 0.8, radius 20 to 48, offset 0 to 0.1), with two themes that are only palettes: a mossy one and
  the concrete one of the `cone` pack.
- `wide-bays`: spacing 57, radius 22 to 48, offset 0.4 to 0.75. Rooms reach deeper into the rock and merge into one
  large cavity with a scalloped edge.
- `small-bays`: spacing 78, row spacing 14, radius 18 to 40, offset 0 to 0.3. Fewer, smaller bays; the slot is more visible.
- `sparse-discs`: spacing 85, row spacing 28, row jitter 0.3. Few discs, each with plenty of room above and below.
- `dense-discs`: spacing 51, row spacing 15, row jitter 0.4. Many discs, overlapping heavily; floors at least 9 apart.
