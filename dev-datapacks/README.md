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
- Hanging: none. `hang_chance` is 0, so every disc stands on a stem under it; hanging discs did not read well in game. Above 0
  it is the share of discs drawn to hang from a root instead, which they do where they have a ceiling. `root_spread` 3 and
  `root_scale` 4 shape such a root (3 stem radii wide where it meets the ceiling, half that 4 blocks away).
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
- `ruins`: the ruins on some of the theme's discs (see `DiscRuins`). `chance` is the share of the theme's discs that have any.
  On those there is one ruin for about every `every` blocks of the top, at least one and at most six; without `every` a disc
  has one. `by_height` (`bottom` and `top`, 1 and 1 if left out) makes the ruins more or less frequent with a disc's height
  in its hole, from the lowest disc to the highest: both `chance` (up to 1) and the number on a disc are multiplied by it.
  What a ruin can be is given by `kinds`, by `structures`, or by both:
  - `kinds`: template pools.
    - `pool`: a template pool; one of its elements is stood on the disc, turned any of four ways about its middle. Only an
      element that is one template is offered (`single_pool_element`, `legacy_single_pool_element`). Nothing is joined on
      to the piece, so jigsaw blocks in it only become their final state. Use `"projection": "rigid"`.
    - `sink` (default 0): how many of a piece's lowest layers lie in the ground. 0 for a template whose lowest layer is a
      floor laid on the ground; 1 for one whose lowest layer is the ground itself, as vanilla's ocean ruins are; more for
      one with a cellar.
    - `weight`: how often a ruin is of this kind where the kind has room.
    - `by_height` (`bottom` and `top`, 1 and 1 if left out): multiplies the weight with the disc's height in its hole, so a
      kind gathers low down or high up. A kind with no weight at a height is never on a disc there.
  - `structures`: other structures whose pieces stand as ruins, each entry `{"tag": "#namespace:path", "weight": ...,
    "by_height": ...}`. The tag is a structure tag (`data/<namespace>/tags/worldgen/structure/`). Every structure in it
    that starts on the ground from a template pool (`start_pool`, `project_start_to_heightmap`, and a `start_height` that is
    one fixed number, as vanilla's villages and outposts have) is a kind of that weight: its first piece stands
    alone, with `1 - start_height` of its layers in the ground, which is where its own file puts it. The log names each
    structure taken and says why another was not. A tag's entries may be optional (`"required": false`), so a pack can name
    the structures of mods that may not be installed, and a pack adds to a tag without replacing this file.
  - The room a piece needs is not written anywhere. It is measured from the piece's own template when a level loads, so it
    is right for whatever a pack has replaced the template with: a round of ground as wide as the template's diagonal, and
    air as high as the layers it builds over the ground. Air saved in a template is placed like any block and would empty
    whatever is there, so a piece needs room up to its highest air as well, unless its pool's processors leave air out (a
    `block_ignore` that names `minecraft:air`, as the mod's ocean ruins have). The log says what each theme's pieces came
    to (`Disc ruins of ...`).
  - For each ruin the kinds are put in an order drawn by weight, and each kind's pieces in an order drawn by their weights
    in its pool; the ruin is the first piece in that order with room at one of eight places drawn for it; if none has, there
    is no ruin. So a tall kind with a large weight stands wherever a disc has the height for it and lower kinds take the
    rest, which is why the mod's tower has the largest weight and is still the rarest.
  - A place has room when the round is inside the rim, its ground steps by at most one block (a piece stands on the lower
    ground), at most a fifth of it is water (a stream may run under a ruin, a pond may not lie under one), no stem of
    another disc comes down through it, no other ruin is within 2 blocks of the round (of its own disc, or of a
    neighbouring disc whose platform runs into this one), the air over it is open to the piece's height (under the dome's
    roof where the disc is in the rock, and under whatever disc is above), and the piece's layers in the ground lie in
    rock. A platform is `floor_thickness` thick, so one or two layers always do; a piece with more only finds rock over
    the flare of the disc's stem, where it is tried first, or where the disc lies in the hole's wall. Nothing of a piece
    shows under a disc.

The mod's own themes:

- `disc_lush` inherits from `minecraft:lush_caves` and adds nothing: a plain stone platform, which lush caves moss over itself.
- `disc_jungle` inherits from `minecraft:jungle` (three times as likely in the middle of the cone as at its edge). Its top is
  a patchwork, in patches 10 to 30 blocks across: about half of it grass, with podzol, moss, coarse dirt and mud, and here
  and there a floor of mossy stone bricks edged with mossy cobblestone. Under it are three layers, which show at the rim as
  bands. A few small ponds and streams, and its own growth: a giant jungle tree for about every 120 blocks of ground and an
  ordinary one for every 45, with bushes, clumps of bamboo, ferns, moss carpet and azaleas between and under them, and
  vines. Under the disc glow berries, tufts of leaves and vines. Ruins on three discs in five half way up a hole, on more below and fewer above (see Ruins below).
  - The patchwork is one `noise_provider` in the palette's first `top` layer. Its `states` are an order, not a mix: the noise
    is turned into a place in the list, low values to its start and high ones to its end, so blocks next to each other in
    the list lie next to each other on the ground (the stone bricks at one end, ringed by cobblestone, coarse dirt and
    podzol; the mud at the other, ringed by moss). The noise is mostly near its middle, so the middle of the list takes most
    of the ground and each end only a little however many times its block is repeated: of the 20 places, the first 6 (stone
    bricks) take 4% of the ground between them and the last 7 (mud) 9%, while each of the 3 for grass takes 16%. Give a block
    more ground by moving it towards the middle or widening its run. `firstOctave` -5 with the second amplitude the largest
    makes the patches about 16 blocks apart; a higher `firstOctave` makes them smaller.
  - Nothing of the jungle's grows on the stone, since its grass, ferns and flowers want soil, so the stone floors stay
    clear. A tree that starts on one puts dirt under its trunk as any tree does.
  - The three layers under the patchwork are one block each, which with it is the whole platform (`floor_thickness` 4).
    They are three of five blocks, always in this order from the top down: dirt, mud, clay (for silt, which vanilla does not
    have), mossy stone bricks, mossy cobblestone. Soil, what water left on it, and what was built there. Each layer is a
    `noise_provider` with the same `seed` and `noise` as the other two, so all three read the same place of their own list
    of 12, and the lists are written place for place: the first entries of the three lists are one rim (dirt, mud, clay) and
    the last another (clay, bricks, cobblestone). The six there are, by how much of the rims they take: dirt, mud, clay 15%;
    dirt, mud, bricks 16%; dirt, clay, bricks 22%; dirt, bricks, cobblestone 22%; mud, bricks, cobblestone 15%; clay, bricks,
    cobblestone 11%. So cobblestone lies under bricks, and the bottom of a disc is masonry on five rims in six.
    - `firstOctave` -8 keeps them wide: a rim shows about two of the six and changes about three times on the way round,
      and the jungle discs of one hole show four or five between them. -7 doubles the changes and -6 doubles them again.
    - To change what is in a rim, change the same place in all three lists. A layer one block lower reads the noise one
      block lower, which can be the next place along; so no block may come later in the order than the one under it at the
      same place or at the place either side. `DiscThemeTest` reads the mod's own file for that.
    - A pond's bed is the second of the three in its middle, and the first at its edges and under a stream.
- `disc_mangrove` inherits from `minecraft:mangrove_swamp` (favours the wide low discs). Mud over packed mud, about two fifths of
  it shallow water. Its trees stand in groves about 40 blocks across with clearings between: giant mangroves at the hearts of
  the groves, on mud and in water, raised 5 to 8 blocks on roots a player can walk under; tall mangroves round them; short ones
  only on the mud, out to the groves' edges. On the mud everywhere, clearings included: grass, ferns and moss carpet, big
  dripleaf and blue orchids; on the water lily pads. Under the disc, patches of hanging mangrove roots draped with vines, and
  glow berries in patches of their own; its stem is clad in roots.

Light on top of these two, which have none of the sky's under their domes:

- `glow_berries_canopy` (both): glow berries hanging under leaves and branches, from 2 to 28 blocks over the ground. A cave
  vine does not hold to a leaf by the game's own rules, so one of these stays until the leaf over it changes (the tree is cut,
  the leaf decays) and then drops as a vine does when its ceiling is broken.
- Shroomlights hang under the crowns of the jungle's trees, and froglights of all three colours under the mangroves', as a
  mangrove hangs its propagules: the `attached_to_leaves` decorator at the end of each tree's file, where `probability` is the
  chance for each leaf with air under it and the two `exclusion_radius` numbers keep two lights apart.
- `froglight_bulbs` (mangrove): froglights set into the mud in twos and threes, level with it, so they light the ground
  without standing in the way.
- `sea_pickles` (mangrove): on the beds of the ponds and streams, one to four to a block; four are as bright as a froglight.

The numbers to change are each growth's `every` (larger is fewer), and the decorators' `probability`.

Both are thinned so that a player can walk through. The numbers to change are the `every` of the bushes, bamboo and trees in
each theme's `growth` (larger is thinner) and, for the mangrove's groves, their `cover` (smaller leaves more clearing); ferns,
grass, carpet and flowers do not block and can stay thick, while an azalea and a stalk of bamboo do. Neither theme has been
measured for blocked ground since the jungle's trees were thinned and the ground cover added.
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

### Ruins

Ruins stand on some discs, and on more of them the lower in a hole the disc is. A jungle disc has them three times in five
at the middle of the hole's height, nearly always at the bottom and one time in four at the top (`chance` 0.6, `by_height`
1.6 to 0.4), with a ruin for about every 3000 blocks of top, by the same measure more at the bottom and fewer at the top. A
lush disc has them three times in ten and a mangrove disc one time in four, both with `by_height` 1.8 to 0.4 and a ruin for
every 4000. A crystal disc has none. They are one structure, `overgrown_abyss:disc_ruins`, so
`/locate structure overgrown_abyss:disc_ruins` finds the nearest disc with one (it looks 100 chunks each way).

The mod's own pieces are vanilla's templates, named by id and not copied, in two families:

- Pieces of the Ancient City, through the same reskin as the city at the hole's floor (`processor_list/disc_ruins/outpost`:
  a tenth of the blocks rotted away, stone and mossy stone bricks for deepslate, jungle wood, moss for wool, lanterns), so
  they read as outposts of that city. Their chests hold vanilla's jungle temple loot (`minecraft:chests/jungle_temple`).
- Vanilla's cold ocean ruins (`processor_list/disc_ruins/overgrown`): their air, gravel and sand are left out, so they stand
  in the disc's own ground with whatever grows there; the three kinds of stone brick are mixed; magma, prismarine and
  cobblestone become mossy cobblestone, sea lanterns shroomlights, the planks jungle planks, the red bricks mud bricks. They
  have no chests: vanilla puts those in by code.

The kinds, each a pool in `data/overgrown_abyss/worldgen/template_pool/disc_ruins/`. The ground and air are what vanilla's
templates measure; a pack that replaces a template changes them, and the log's `Disc ruins of ...` line says what they are
in a level.

| Kind | Pieces | Round of ground, across | Air | Weight on jungle | By height, bottom to top |
|---|---|---|---|---|---|
| `tower` | `tall_ruin_1`, `tall_ruin_3` | 24 | 19 | 40 | 2 to 0.3 |
| `keep` | `tall_ruin_2`, `tall_ruin_4` | 24 | 13 | 20 | 2 to 0.3 |
| `vault` | `chamber_1` | 24 | 10 | 10 | 1.8 to 0.5 |
| `house` | the 12 large cold ocean ruins | 23 | 4 to 12 | 10 | 1.5 to 0.7 |
| `camp` | `camp_1` to `camp_3`, `large_ruin_1` | 24 | 3 or 5 | 8 | 0.7 to 1.4 |
| `chamber` | `chamber_2`, `chamber_3` | 15 or 16 | 6 | 6 | even |
| `pillar` | `medium_pillar_1`, `large_pillar_1` | 11 or 8 | 11 or 15 | 5 | even |
| `statue` | `small_statue` | 13 | 5 | 3 | even |
| `hut` | the 24 small cold ocean ruins | 9 | 2 to 6 | 3 | 0.6 to 1.6 |
| `rubble` | the two small and two medium ruins | 9 to 19 | 3 | 3 | 0.5 to 2 |

So the grand kinds gather low in a hole and the camps, huts and rubble of whoever came later high in it. Lush discs have
pillars, chambers, statues, huts and rubble; mangrove discs houses, pillars, huts and rubble, on what dry ground they have.

Jungle and lush discs also borrow the structures of the tag `#overgrown_abyss:on_discs/jungle` (weight 15 on jungle, 5 on
lush). The mod's own tag holds the three structures of Epic Structures: Jungle Temples, all optional, so without that mod it
is empty and nothing changes. With it, the 14 pieces of `epic:epic_temple_ruin` stand on discs wherever one fits; the two
temples (`epic:epic_temple`, `epic:epic_temple_large`) are 47 blocks each way and 48 high and fit under no dome as the discs
are. To lend the discs another mod's ruins, add its structure to the tag in a pack of your own.

To change how many ruins there are, change `chance`, `every` and `by_height`; to change which, the weights. A pool of your
own is a kind like the mod's: name it and say how many of its pieces' lowest layers lie in the ground.

- `many-ruins`: the mod's file with `chance` 1, `every` 1200 and no `by_height` on all three themes or on any kind: ruins on
  every disc that has room for one, up to six on a disc, every kind at every height. For looking at the kinds without
  searching for them.
- `borrowed-ruins`: the mod's file with ruins on every jungle and lush disc that has room, most of them borrowed (the tag's
  weight is 200), and a tag that lends the discs three of vanilla's structures: the centres of plains villages, the plate a
  pillager outpost stands on, and trail ruins. For seeing borrowed ruins work without another mod. The plate builds two
  layers and is saved with the air of a box 30 high, so it only stands where 29 blocks over it are clear; the trail ruins
  start 15 under the ground and none of their towers is taller than that, so the log says none of them is offered.
- `tall-rings`: the mod's file with `layer_spacing` 28 in place of 20. It was measured for the ruins and left out of the
  mod's own file: a hole 200 blocks deep goes from 7 layers and about 65 discs to 5 layers and about 43, and what it gains
  is towers, about 1.4 to a hole in place of 0.7. The lower kinds lose more discs than they gain room. For judging the look.

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
