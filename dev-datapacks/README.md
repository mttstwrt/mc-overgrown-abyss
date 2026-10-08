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
136) and narrowing to `top_radius` 45, with free-standing discs in layers at least `layer_spacing` 20 apart around the clear air
in its middle. Its numbers:

- Where: a hole only opens where the ground all round its mouth stands at least `rim.min_above_sea` 20 blocks over the
  level's sea level (y=83 where the sea is at 63), so none opens in a plain or on a shore. One low column alone does not
  count; two next to each other do.
- Top: each hole has a lip of its own, `rim.dip` 3 blocks under the ground round its mouth and never above `top`, which is 64
  under the level's top (y=255 on vanilla height). `rim.low_share` 0.5 puts the lip under the middle ground: half the mouth's
  edge is lower than the lip and is simply the ground there, with the top layers of discs left out on that side. 0 puts it
  under the lowest ground (a shallower hole, level all round), 1 under the highest (the deepest).
- Above the lip the hole opens as a bowl (`rim.collar`). The hole's wall runs straight up to the ground on every side, and
  outside the mouth the ground is removed only above a surface that rises from the mouth's edge on that side, by `height` 34
  at `width` 30 blocks out and on beyond (`profile` 2 starts it level), uneven by `roughness` 2. So level ground gets a dip
  about 9 blocks wide, and a slope of one block in one is cut about 12 blocks deep at most, all round.
- Walls: `wall_noise` makes the hole's own wall uneven, from the city floor to the mouth and the bowl's start, so the hole is
  not an exact circle. Each layer moves the wall towards or away from the middle by up to `amplitude` blocks; `wavelength`
  is how far apart its bulges are round the hole (where the hole is of middling width: they are narrower at the mouth and
  wider at the floor) and `vertical_stretch` how many times further apart they are up the wall. The three layers are wide
  lobes (90, stretch 3, 6 blocks), runnels down the wall (16, stretch 4, 2.5) and a fine grain (6, stretch 1.5, 1): 9.5
  blocks at most. Take the list out for an even wall. Discs, their domes, stems and roots are not touched.
- Layers: spread evenly from the lowest up to `rim.top_room` 22 under the highest a dome may reach (`ceiling_margin` 12 under
  the lip): 4 layers under a lip at 96, 7 under one at 160, 12 under one at 255. A dome also stays 12 under the ground over
  it, so a disc under ground that falls away is lowered or left out.
- Opening: the clear air round the axis is `clear_radius` 8 at the floor and widens to `upper.clear_radius` 20 at the lip.
- How often: `cell_size` 1024 and `chance` 1, so every cell is tried; on vanilla terrain about 1 cell in 15 has ground high
  enough all round, which is about one hole for every four squares of 2048 blocks. `/locate structure` finds the nearest.
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
  `top` (dry ground, the default), `water` (the bed of a pond or stream, where a mangrove starts) or `underside` (under the
  disc's lowest rock, which under most of a standing disc is the flare of its stem). They are placed in the order listed, so
  vines listed after trees find the trees there, and before what the theme inherits, as vanilla grows a biome's trees before
  its grass.
  - `patches` (optional, `size` and `cover`) gathers a growth into patches about `size` blocks across that take up `cover` of
    the surface (0.4 is two fifths), thickest in their middles and thinning to nothing at their edges, with none between. The
    average over a disc stays one per `every` (a few percent under), so a smaller cover makes each patch thicker: at their
    middles `2 / cover` times the average. Growths given patches of the same `size` share them, so trees with covers of 0.4,
    0.6 and 0.8 stand in one grove, the first at its heart and the last out to its edges. Each disc has patches of its own.
    Without `patches` a growth is spread evenly.

The mod's own themes:

- `disc_lush` inherits from `minecraft:lush_caves` and adds nothing: a plain stone platform, which lush caves moss over itself.
- `disc_jungle` inherits from `minecraft:jungle` (three times as likely in the middle of the cone as at its edge). Grass over
  dirt, a few small ponds and streams, and its own growth: giant and ordinary jungle trees, bushes, ferns, vines, glow berries
  in the canopy, and under the disc glow berries, tufts of leaves and vines.
- `disc_mangrove` inherits from `minecraft:mangrove_swamp` (favours the wide low discs). Mud over packed mud, about two fifths of
  it shallow water. Its trees stand in groves about 40 blocks across with clearings between: giant mangroves at the hearts of
  the groves, on mud and in water, raised 5 to 8 blocks on roots a player can walk under; tall mangroves round them; short ones
  only on the mud, out to the groves' edges. Under the disc, patches of hanging mangrove roots draped with vines, and glow
  berries in patches of their own; its stem is clad in roots.

Both are thinned so that a player can walk through: on the jungle discs bushes, trunks, roots and low leaves stand in about a
quarter of the ground, counting a place as blocked if either of the two blocks a player takes up is. The numbers to change are
the `every` of the bushes and trees in each theme's `growth` (larger is thinner) and, for the mangrove's groves, their `cover`
(smaller leaves more clearing); ferns and grass do not block and can stay thick. The mangrove's groves have not been measured
this way yet.
- `disc_crystal` inherits nothing and is made by hand (amethyst over calcite, with clusters); it is rare and kept to small discs
  in the outer half, which the layout puts behind larger discs.

Jungle and mangrove grow their own trees because the parent's may not grow on a disc at all: an overworld overhaul such as
William Wythers' Overhauled Overworld replaces those biomes with trees that only grow at certain heights above sea level and on
soils its own terrain lays down. The disc's trees are vanilla's, kept under the mod's own ids
(`data/overgrown_abyss/worldgen/configured_feature/disc/`) so that such a pack cannot change them, with these changes: more vines
hanging from their leaves; a `min_clipped_height`, so that a tree under a low part of the dome grows as tall as there is
room instead of not at all; a block more of trunk on the jungle tree and the short mangrove, which lifts their lowest
leaves over a player's head; and root arms up to 24 blocks long on the tall mangrove (vanilla's 15 is too short for a trunk
raised 7 blocks to reach the mud, and the tree is given up). `mangrove_giant` is the mod's own: the tall mangrove with its
trunk raised 5 to 8 blocks, root arms that spread wider (`max_root_width` 10) and reach further (32), a trunk of 7 to 16 and a
larger crown. It needs room for a trunk of at least 6 over its roots, so it grows under the higher parts of a dome. Vanilla's own tree feature is left out of what is inherited (`without_features`), or a disc would be
twice as thick with trees without such a pack as with it. Everything else is still inherited: grass, flowers, melons, lily pads
and whatever other mods add. With no themes every disc is the terrain's own rock in the biome it lies in.

- `tuned-themes`: the mod's file as it was before biomes were inherited, with the hand-made palettes and growth lists for lush,
  jungle and mangrove (denser foliage, mangrove roots on stems). For comparing the two in game. It inherits spawns only
  (`"stages": []`), so its foliage is all its own.
- `cone`: the mod's own file plus a fifth theme with no biome, in plain concrete (white top, black underside, light grey body,
  orange stem surface, yellow stem core), that makes each part of a disc easy to tell apart.
- `fixed-top`: the mod's file as it was before the top followed the ground: every hole's top at y=80 whatever the ground, a
  bore above it, three layers, a clear cylinder, and a hole in half the land cells of 2048. For comparing the two in game.

- `low-lip` and `high-lip`: the mod's file with `rim.low_share` 0 and 1, the lip under the lowest and under the highest
  ground round the mouth. For choosing between them and the mod's 0.5 on the same seed: the holes are in the same places,
  only as deep as each lip makes them.
- `smooth-walls`: the mod's file without `wall_noise`, an exact round hole. For comparing with the uneven wall.

`cone` and `tuned-themes` were made from the file before that change and have no `rim`, `upper` or `wall_noise` either, so
they also keep the fixed top and the even wall. To try them with the new shape, copy the mod's `top`, `cell_size`, `chance`,
`ceiling_margin`, `wall_noise`, `rim` and `upper` into them.

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
