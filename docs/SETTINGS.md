# Settings

Everything the mod's holes are made from is in one file,
`common/src/main/resources/data/overgrown_abyss/worldgen/density_function/ravine/carve.json`, and this describes every
setting in it. A datapack may hold a file of its own at the same path under `data/`; it replaces the whole of the mod's, so
it has to keep up when the mod's settings change. The log line `Ravine settings:` at world load shows which values are live.

For the settings that make discs wilder (rims, treeline, wildness, giants, ground, falls, springs), `docs/TUNING.md` has
each one's limits and which way to turn it for which look.

## Dials

For more or less of a thing without touching that file there are the dials: `config/overgrown_abyss.json` in the game's
folder (in Prism, the instance's `.minecraft/config`; on a server, the `config` folder beside `server.properties`, and on
the server alone). The mod writes it the first time a world loads, with every dial at 1. 0 is none, 1 is as the settings
have it, 2 is twice as much; `holes` goes from 0 to 1 and the rest from 0 to 10. The file is read each time a world loads,
so a change is tried by leaving the world and coming back. Only chunks made after that show it: in a world that has been
explored, new ground meets old at a seam.

| Dial | What it turns |
|---|---|
| `holes` | `chance`: the share of the holes that are kept. Those kept stay where they were |
| `roots` | both ends of `roots.great.count` and `roots.crossing.count`, the branches (`roots.branches.every` is divided) and `roots.links.chance`. 0 takes `roots` out |
| `falls` | both ends of `falls.count` |
| `springs` | both ends of `falls.springs.count` |
| `ruins`, `troves` | each theme's `ruins` or `troves`: `chance` first, up to 1, and what the dial asks beyond that comes off `every`, so more discs have them before a disc has more. 0 takes the block out |
| `rough_ground` | each theme's `ground`: the `cover` of its mounds and of its outcrops, not their height. 0 takes `ground` out |
| `growth.<name>` | the `every` of each growth, on a disc or under the roots, whose feature is in the tag `#overgrown_abyss:dial/<name>` (`tags/worldgen/configured_feature/dial/`). The mod has `lights`, `giants` and `rim`; a feature in two of them, as the glowing bulbs are, is turned by both. 0 takes those growths out of their lists |
| `themes.<biome id>` | that theme's `weight` |

What a dial gives is held to the limits each setting has and then checked by the same rules as the file. If it does not
hold, the log says why and the world loads with every dial at 1. A file the mod cannot read is left as it is and logged,
with the same result. The log line `Dials:` at world load lists the dials that are not at 1, and `Ravine settings:`,
`Roots:` and `Falls:` after it show the values they gave.

Three things to know:

- `holes` cannot make more holes. The mod's `chance` is 1 already. More of them needs a smaller `cell_size`, which is a
  change to the file: the city's placement reads the cell grid from the file and not from a world's dials.
- A dial at 0 takes entries out of a growth list, and that moves the plants of the entries listed after it (see
  `docs/TUNING.md`). Any other value leaves every other plant where it was.
- New names come by themselves: a tag added under `dial/` by a datapack, or another theme, is written into the file at 1
  the next time a world loads. A name in the file that matches nothing is kept, and logged.

There is no dial for the lip's height, the layers' spacing, the wall's unevenness, how thick roots are, curtains, or what a
disc's ruins borrow. Those are the file's.

## The curse of descent

A block `curse` in the settings file. Inside a hole each player has a depth they are adjusted to, which follows their real
height at `rate_down` or `rate_up` blocks a tick; the gap between the two gives wither, level by level. A lift at or under the
rate never opens a gap. A drop, a pearl or a knock does, and waiting closes it. Without the block there is no curse.

| Setting | Meaning |
|---|---|
| `rate_down`, `rate_up` | blocks a tick the adjusted depth follows a player going down or up (0.12 is about a ladder's 2.35 a second) |
| `levels` | the gaps, widest last, at which wither I, II, ... begin; under the first costs nothing. Layers are 20 apart, so a first gap just over 20 makes one drop between discs free and two in a row not |
| `duration` | ticks each dose of wither lasts, so it lapses a little after the gap closes |
| `margin` | blocks outside the hole's open air and discs' domes that still count as inside (default 4) |
| `depth_strictness` | 0 to 0.9: how much the gaps shrink toward the floor (default 0; 0.5 is half the gaps at the floor) |

Players in creative or spectating are not cursed; coming into the hole, dying and changing dimension all start a player over
adjusted to where they stand. The log line `Curse:` at world load shows what is live. Milk and Artifacts' antidote vessel clear
wither, so they clear this too.

## The settings

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
- Roots: winding wooden roots from under the mouth to the floor, see below.

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
  needs a palette for the ground that holds it (`depth` + 1 blocks of `top`, or a `body`). `"fluid": "lava"` fills the same
  ponds and streams with lava instead (`water` is the default). A disc that holds lava has no part in the hole's falls: it
  starts none, none lands on it, and no spring comes out of the wall behind it. `clear` (0 to 16, default 0, lava only)
  leaves the lava out within that many blocks of what the game would have it set alight: the ground of any other disc
  whose top is within 6 blocks of the lava's height, unless that disc holds lava too, and any root. See the lava disc below.
- `growth`: configured features of the theme's own, each placed on average once per `every` blocks of where it grows: `on` is
  `top` (dry ground, the default), `water` (the bed of a pond or stream, where a mangrove starts), `lava` (the open block
  over a pond or stream of lava; see the lava disc below for why not its bed) or `underside` (under the
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
  - `loot` (optional): what the chests of the theme's own kinds hold. `tables` lists loot tables from the poorest to the
    richest, each `{"table": "namespace:path", "from": ...}`. A disc has a rank from 0 to 1 among the discs of its hole,
    which rises with its depth and with its distance from the hole's centre: `depth` and `distance` (1 and 1 if left out)
    say how much each counts, so the highest disc at the centre ranks 0 and the lowest at the edge 1. All the chests of a
    ruin hold the last table whose `from` (0 if left out) its disc's rank reaches; each table after the first must be from
    a higher rank than the one before. This goes for every container a piece has that came with a loot table, and for a
    chest the mod puts where a template has a data marker named `chest`, as vanilla's ocean ruins have theirs. Without
    `loot`, or on a disc whose rank reaches no table, chests keep what their pool's processors gave them and no chest is
    put at a marker. A borrowed structure's piece always keeps its own loot.
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

- `disc_lush` inherits from `minecraft:lush_caves` and adds nothing to its ground: a plain stone platform, which lush caves
  moss over itself. Azalea bushes and vines hang over its rim, and a few glowing bulbs stand on it. Its mossy ceiling and
  the vines in it are the mod's (`moss_ceiling`, over the disc and under it): two of the parent's features are left out
  (`without_features`), since their vines bear berries on one block in five and lit the whole disc.
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

Light on top of these two, which have none of the sky's under their domes. Since 2026-10-11 it is held down on purpose,
and what there is sits high and inside leaves, so that about two thirds of a jungle disc's ground and over half of a
mangrove's has no block light and monsters can spawn on it (`docs/TUNING.md`, "Light", has what was measured):

- `glow_berries_canopy` (both): cave vines hanging under leaves and branches, from 2 to 28 blocks over the ground. A cave
  vine does not hold to a leaf by the game's own rules, so one of these stays until the leaf over it changes (the tree is cut,
  the leaf decays) and then drops as a vine does when its ceiling is broken.
- `glow_berries_hanging` is the vine itself, there and under the discs and in the lush disc's moss: one vine in fourteen
  has a berry, as a block more at its top, and no other block of a vine bears one. The two `weight`s of its first layer's
  `height` are how many vines get none and how many get one.
- `glow_berries_over_drop` (jungle, mangrove and lush, under the disc): a second vine, hung only where the 15 blocks under
  it are open air, with berries in its top block or two on five vines in eight. That far up a berry lights no ground, so
  the underside of a disc glows where it stands high over the next and stays dim where it is the next one's roof.
- `shroomlight_in_leaves` (jungle) and `froglight_in_leaves` (mangrove, all three colours): a light in place of a leaf deep
  in a crown, one with a leaf or a log on all six sides, at least 10 blocks over the ground. Most places tried have no crown
  thick enough over them, which is why the jungle's `every` is 1, a try in every column. The trees hang no lights of
  their own any more, and no froglights lie in the mud.
- `sea_pickles` (mangrove): on the beds of the ponds and streams, one to three to a block and most often one, which is
  light 6, 9 and 12. Beside a bank these light the bank, so there are few.
- `sea_pickles_midpond` (mangrove): single pickles in the middles of ponds, only where every block within 4 steps holds
  water. Their light of 6 falls short of any bank, so they cost monsters nothing, and there are many.
- `glow_lichen_high` (jungle, mangrove and lush, on the top and under the disc, and under the hole's roots): glow lichen
  on whatever is overhead and has 8 open blocks under it: a dome's ceiling and leaning wall, the underside of a platform
  or a root. From the ground it looks up to 32 blocks for a place. Lichen gives 7, so from there none of it reaches the
  ground. What it will hold to is the tag `#overgrown_abyss:holds_lichen`; a pack that makes discs or roots of other
  blocks adds them there.

The numbers to change are each growth's `every` (larger is fewer), and in the two `*_in_leaves` files the `y_spread`, which
is the least a light is over the ground. Several of these have `patches`, which gathers them into groves: the mangrove's
froglights, berry vines and lichen share the groves of its trees (`size` 40), and the jungle's and the lush disc's berry
vines and lichen have groves of `size` 24.

Both are thinned so that a player can walk through. The numbers to change are the `every` of the bushes, bamboo and trees in
each theme's `growth` (larger is thinner) and, for the mangrove's groves, their `cover` (smaller leaves more clearing); ferns,
grass, carpet and flowers do not block and can stay thick, while an azalea and a stalk of bamboo do. Neither theme has been
measured for blocked ground since the jungle's trees were thinned and the ground cover added.
- `disc_crystal` inherits nothing and is made by hand (amethyst over calcite, with clusters); it is rare and kept to small discs
  in the outer half, which the layout puts behind larger discs.
- `disc_lava` inherits nothing and is made by hand: basalt and obsidian with ponds of lava. It is rare and is
  kept low in a hole (the owner, 2026-10-11): about one disc in twenty-six, none of them higher than three tenths of the
  way from the hole's lowest disc to its highest (`weight` 2, `only.height.max` 0.3). On vanilla terrain that is the
  bottom row of discs and what stands on it, and in a deep hole the bottom two rows; a hole has one or two, and one hole
  in three has none.
  - `weight` is how many: 1.5 gives a quarter fewer, 3 nearly half as many again. `only.height.max` is how high: 0.4 lets in
    the second row of a hole of four. It had `by_height` 3 to 0.1 and no limit before, which was one disc in nine at
    any height.
  - Its top is a patchwork (one `noise_provider`, as on the jungle disc, in smaller patches) of obsidian, basalt and smooth
    basalt: of the ground that is not lava, about two fifths each of the first two and a sixth of the third. Under that a layer of obsidian, which is the bed of every pond; then
    basalt; and an underside of smooth basalt and obsidian with a magma block here and there. At the rim those show as
    four bands. Its stem is basalt with some obsidian and magma.
  - `water` with `"fluid": "lava"`: ponds over 0.45 of the top before anything is taken away, no streams, 1 deep. The lava
    is still and held in on every side, as a disc's water is, and it lights the disc.
  - Nothing grows on it. The obsidian trees that stood in its lava are out for now (the owner, 2026-10-11); their file,
    `obsidian_tree.json`, is kept. To put them back, give the theme
    `"growth": [{"feature": "overgrown_abyss:disc/obsidian_tree", "every": 30, "on": "lava"}]`.
    - The tree is vanilla's acacia in other blocks: the forked trunk, 5 to 9 tall, of obsidian, and the flat crown of
      magma blocks with about one block in four glowstone (its `foliage_provider`, two `weight`s of 3 and 1). The crown
      glows, and its magma burns whoever walks on it.
    - `every` 30 was a place for one for about every 30 blocks of lava, of which about a third grew: a tree wants a
      column 5 by 5 of open air over its foot.
    - Nothing of vanilla's grows through lava, so a feature over lava is started in the open block above it. A tree
      puts its `dirt_provider` block under its trunk, which here is the lava's top block, and that is its foot: in lava
      1 deep it stands on the pond's bed. In lava 2 deep (`depth` 2) the foot would have lava under it, so keep `depth`
      at 1 where trees stand in it.
  - Mounds up to 2 over 0.3 of the top, and columns of basalt up to 5 high (`outcrops`).
  - Spawns, in its biome file: a cave's monsters and bats, and magma cubes (the owner, 2026-10-11) at the numbers
    vanilla gives its basalt deltas, `weight` 100 and 2 to 5 at a time. The game spawns a magma cube in any light and
    the others only in the dark, so on a disc lit by its lava the cubes are what spawns, torches or no.
  - What it does not have: ruins, growth of any kind, a wood of its own for roots.
  - Fire. The game's lava lights what burns within three blocks of it (the `doFireTick` rule), and discs of one ring run
    into each other, so left alone over a third of this lava lay beside the next disc's ground and an eighth by a wooden root. Three settings
    keep it apart, at the cost of lava:
    - `clear` 6: no lava within 6 blocks of another disc's ground at about its height, or of a root.
    - `bank` 6 (the others have 3): no lava within 6 blocks of its own rim, past which a lower disc's trees stand.
    - no falls or springs (in the code): running water would turn the ponds to stone.
    - What that leaves: about two thirds of the ponds, so about a fifth of a lava disc's top is lava, and about one
      lava disc in thirteen has next to none and is a disc of basalt and columns (a little over half, a sixth and one
      in seven when lava discs lay at every height). In four holes it then took the blocks lava
      could set alight from 2337 to 52. What it does not cover: what hangs from a
      disc higher up, and what vanilla builds in the hole (a mineshaft's props). SCOPE.md, "A lava disc", has the counts.
    - To have lava wherever a pond lies and take the fires with it, set `clear` to 0.

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

Giants, a few to a disc, listed first in each theme's `growth` so that they are grown before its trees (a cap needs the air
it fills, and a tree started under one grows as tall as there is room):

- `giant_mushroom_red` (jungle) and `giant_mushroom_brown` (mangrove): vanilla's two huge mushrooms with caps a block wider
  each way. In groves (`patches`).
- `giant_pad` (jungle and mangrove): a flat round leaf of moss nine blocks across on a stalk of bamboo block, 4 to 6 blocks
  up and now and then twice that. It is the brown mushroom's own feature with other blocks. Its `rim_fade` has `to` 4, so
  it grows four times as often by the rim, where its leaf hangs out over the edge.
- `glow_bulb` (jungle and lush) and `glow_bulb_swamp` (mangrove): a stalk of bamboo block 10 to 14 tall with a bud on it,
  a cross of five lights with a leaf on every side of each: shroomlights in azalea leaves, or froglights of all three
  colours in mangrove leaves. A tree feature, so it stands on any ground and puts a block of moss under itself. To the
  feature the lights are its foliage, and the leaves are six `attached_to_leaves` decorators, one for each direction; it
  needs 9 blocks of clear air, so none grows under a low dome.

For the mushrooms and the pad, `every` is places tried, and most tries come to nothing: the game grows one only on soil (grass,
dirt, podzol, moss, mud; not the jungle's brick floors, not stone, not in water) and only where every block its cap will take
is air or leaves, so not under a low part of a dome and not where a tree of the chunk next door already stands in the
way. A bulb stands on any ground but wants 9 blocks of clear air over it, and about one in six grows, one in five on a lush
disc.

A theme's `ground` is how its ground lies over the platform. The platform itself, and so the dome, the stem and the rim,
stay as they are; the ground is more blocks on top, written into open air. Both parts are optional:

- `mounds`: rises of the theme's own ground (the palette's `top` layers lie on a mound's surface, and what was the
  platform's top under it takes the layers below). `cover` is the share of the top that is raised a block or more, in
  patches about `size` blocks from one to the next. A mound stands a block high at its edge and climbs by less than a
  block for each block across, so it is walked up without a jump, up to `height`. How high one gets is up to its width,
  about a block and a tenth of `size`: with `size` 22 most are 2 or 3 high, and with `size` 12 none is over 2 whatever
  `height` says. The theme's palette has to give its top at least `height` blocks of material.
- `outcrops`: lumps of `block` (any block-state provider), a block high at their edges and `height` in their middles, with
  sides as steep as they come. They are in a player's way, so `cover` wants to stay small.

The ground stays on the platform within 4 blocks of the rim (whose side shows the palette's layers), under each ruin and 2
blocks round it, and at water and right beside it, and rises from each of those by at most a block for each block further.
So less of a top is raised than `cover` says, much less on a wet disc: measured over four holes, a fifth of a jungle or
lush disc and a tenth of a mangrove disc. What grows on a disc and what it inherits stand on the ground, mounds included.
The mod's own: jungle mounds up to 3 over 0.3 of the top with a few mossy stone outcrops up to 4; lush mounds up to 2 over
0.25 with stone and moss outcrops up to 5; mangrove hummocks 1 high between its ponds; lava mounds up to 2 over 0.3 with
columns of basalt up to 5; crystal spires of calcite and
amethyst up to 6.

Three settings keep a disc's plants from ending in a wall at its edge and from looking like the disc next to it:

- `"on": "rim"` in a `growth` entry grows the feature in the open air right beside the top block of the rim, so it must be
  one that hangs down the side, holds on to it, or stands on nothing. `every` is then blocks of rim: one for every so many
  of the columns that touch the platform's edge, which is about the rim's length round. Where the rim lies in the hole's
  wall nothing grows. The mod's own:
  - `rim_bush` and `rim_tree` (jungle), `rim_bush_mangrove` and `rim_bush_azalea` (lush): a bush, or a tree 3 to 6 blocks
    tall hung with vines, rooted on the very edge so that its leaves stand out over the drop. They are ordinary tree
    features; a tree started in open air puts its `dirt_provider` block under itself, which here is the block beside the
    platform's second layer, so each grows from a knot of wood, roots or rooted dirt that holds on to the side. They have
    `patches` of one size (14 blocks, half the rim), so on each disc they stand along the same stretches of rim and
    leave the rest of it open: a bush for every 9 blocks of rim and a tree for every 18 on the jungle, a bush for every
    12 on the mangrove and every 14 on the lush disc.
  - `rim_leaves` (jungle, every 14): tufts of leaves out and down from the lip.
  - `rim_roots` (mangrove, every 10): mangrove roots down the side, tipped with hanging roots.
  - `rim_vines`: curtains of vines 3 to 24 long, most of them short; every 12 on the jungle, 14 on the mangrove, 16 on the
    lush disc. A curtain is a few columns wide, so an `every` much under 8 closes into one unbroken curtain and hides
    the view from the rim.
- `rim_fade` in a `growth` entry changes how often it grows over the last `width` blocks before the rim: steadily from its
  usual rate to `to` times that at the rim. The jungle's trees have `to` 0.15 over `width` 10, which leaves their crowns
  ending at different distances from the edge; a `to` over 1 gathers a growth there instead, as the pads' does. The
  mangrove's trees have none: their crowns reach out past the rim by themselves, and fewer trees there was less of that. `wander` (0 to 1)
  lets the width differ round the rim, from that share less than `width` to that share more: with the trees' 0.8, 2 to 18
  blocks, so along one stretch the trees come to the edge and along another they stop well short. Every growth of a disc
  wanders alike, so its trees leave the same clearings. `reach` (0.05 to 1) is the most of the disc's radius the fade may
  take, whatever its width: the jungle's trees have 0.25, so on a disc 20 blocks in radius they are thinned over 5 blocks at most
  and on one of 64 over 16. Without it a small disc is thinned through to its middle. Not for a growth on the rim itself.
- `wildness` on a theme, `min` to `max`: each disc draws one number between them and grows that many times as much of
  everything in the theme's `growth`. What a disc inherits is not changed, so a disc at the low end is a glade in grass,
  not bare. Jungle and mangrove have 0.5 to 1.4. `max` is what decides whether the thickest disc can still be walked
  through: it multiplies the trees and bushes too.

### Roots

`roots` (optional, see `RootSettings`) are the wooden roots that wind through a hole. Without the block a hole has none. They
are blocks written over whatever is there: a root runs into the wall, through a platform or stem in its way, and across the
floor. The city and the mod's own ruins build round them (their rules leave alone the blocks of the tag
`#overgrown_abyss:ruins_cannot_replace`, which holds the two woods; a pack that makes roots of other blocks adds them there).
Measured outside the game with the mod's numbers, on level ground: a hole 150 deep has 45 to 63 roots and 124,000 to 168,000
blocks of root, one 294 deep has 92 to 126 roots and 221,000 to 310,000 blocks, over half of it in the lowest third of
the hole.

- `great`: the roots that run from the wall under the mouth to the floor. `count` 3 to 5 a hole. `radius` 4.5 where one
  leaves the wall, down to `end_radius` 3.5 at the floor (9 blocks across to 7). `fall` 0.5 is how far it comes down for
  each block it goes round the hole, about: lower winds it further round, higher drops it more steeply. With `touch_chance`
  0.5 a leg of it turns in to lie against a disc's rim, its top level with the rim's, where a disc about there has a place
  for it; otherwise the leg swings out to the wall. In the bell at the foot of the hole it keeps to the wall, lands on the
  floor and runs on along it for half to all of `floor_run` 60 blocks, sinking as it goes, before it dives.
- `crossing`: `count` 1 to 2 roots a hole that span it from wall to wall, sagging through the clear air round the axis, of
  `radius` 3.5. No other root enters the clear air. 0 to 0 keeps the middle open.
- `branches`: roots that leave other roots, for another root near by, a disc's rim, the floor or the wall. One for every
  `every` 150 blocks of root, times `by_height`: 3 at the floor and 0.5 at the top, so they gather at the bottom. A branch is
  `shrink` 0.65 of its parent's radius where it leaves it, branches may branch once more (`depth` 2), and none goes further
  than `reach` 60 for its end. One thinner than `min_radius` 1.5 is not grown.
- `links`: roots a player can walk up from the top of one disc, round the outside of the rim of a disc above, to that rim.
  `chance` 0.5 is the share of discs drawn to have one from the nearest disc under them; about half to two thirds of those
  get it, since a link is left out where it cannot make the climb in the open at `max_slope` 0.6 (three blocks up in five
  forward). `radius` 2.
- `winding` 0.6: how far a root strays to either side of the straight way to where it is going. 0 is straight.
- `wood`: what a root is made of where no disc says otherwise: `bark` for its outermost block and, if given, `core` for the
  rest. The mod's is jungle wood and mangrove wood in patches. A theme's `root_wood` (same form) is the wood of roots near
  its discs: jungle wood on the jungle theme, mangrove wood on the mangrove one, none on lush, crystal and lava. A root within
  `wood_reach` 48 blocks of such a disc takes its wood; between two different woods it is one or the other in patches along
  the root, the share shifting over `wood_blend` 16 blocks.
- `growth`: what hangs under the roots, a list as on a theme: a `feature`, `every` (blocks of a root's underside for each
  place tried) and, if wanted, `min_radius`, the thinnest root it grows under. The underside is the lowest part of a
  root's round, not its flanks, and the feature is placed in the open block beneath; a root that lies on the ground, on
  a platform or in the wall has none. The mod's: `glow_lichen_high` for every 8 blocks, and the lit vine
  `glow_berries_over_drop` for every 20 under roots of radius 2.5 or more, which hangs only over 15 open blocks. Round a
  thinner root the vine's light would reach the root's top. Without the list nothing hangs there.

### Falls

`falls` (optional, see `FallSettings`) is water that leaves a disc over its rim. Without the block a hole has none. A hole has
a number of them, not each disc a chance of one, so they stay something to come upon; and only a cone has them.

- `count` 2 to 4: how many falls a hole starts. Fewer where it has fewer places for one.
- Where one starts: where a disc's own stream or pond comes up against the bank inside its rim, on the side of the hole's
  middle (the rest of a disc's rim is in the wall). So only a theme with `water` starts falls, and a wetter theme more of
  them; a theme whose `water` is lava starts none. `by_height` (0.5 at the lowest disc, 2 at the highest) makes a start likelier high up; `max_per_disc` 1.
- What one needs: `min_drop` 10 blocks of open air under its lip, and to come down either on the hole's floor or on the top
  of a lower disc where there is room for a pool. Never on a ruin, a root, a stem or the wall, nor on a disc that holds lava. Most places have only the
  floor under them, so half of a hole's falls, rounded up, are first looked for among those that land on a disc.
- `pool`: `radius` 3, `depth` 2 in its middle and 1 at its edge, inside the disc's bank, on a disc whose palette gives its
  top 3 blocks of material (all of the mod's do; a disc with no theme has no pool, so nothing falls on it).
- `chain` 0.6: the chance that a fall which landed in a pool goes on, by a channel from the pool towards the hole's middle
  to that disc's rim and over. `max_steps` 4 is the most falls one after another.
- `width` 1 is the water at an ordinary lip. `curtain`: with `chance` 0.2 a fall that starts on a disc of `min_radius` 40 or
  more is `width` 3 to 5 wide instead, with a wider pool.

- `springs` (optional): water that comes out of the hole's wall behind a disc. `count` 1 to 2 a hole. One is over a disc
  on its side away from the hole's middle, where the disc's dome is cut into the wall, `height` 3 to 7 blocks over the
  rim, or lower: a dome leans in as it rises and a spring wants rock under it, so it is put no higher than the wall is
  still nearly upright (leaning in by half a block for a block's rise at most), and a disc whose wall leans from the foot
  has none. A pool lies on the disc at the foot of the wall for the water to come down into, 3 blocks out from under the
  spring and right up to the wall. The spring itself is `feature`, the mod's `disc/wall_spring`, which is vanilla's own
  spring: it only makes one in a block with rock over it, under it and on three sides and with one side open, of the
  blocks in the tag `#overgrown_abyss:spring_rock` (the stones of the overworld, dirt, gravel, calcite, clay, moss and
  smooth basalt). The wall is whatever the terrain left there, so the place reckoned for a spring and seven beside it are
  tried in turn, up and down first; where none will do the hole has a pool there and no spring. A spring's pool is not
  joined to any fall: a disc's top is a bowl, lowest in the middle, and still water cannot be laid across a dip. Vanilla
  puts springs of its own in the hole's wall as well, wherever the rock suits.

How the water is made. Everything but the falling is still water written with the disc: a channel through the bank at the
level of the water it leaves, to the platform's last column. Ground higher than that level is cut down to it (by 2 blocks
at most under a channel, which leaves a block of the platform as its bed); every column has ground or water of its own
level on each side. The last column has one side open, and is marked for the game to tick when the chunk comes alive, as
the game marks water that the terrain left somewhere to run. From there the game runs it: out over the edge and down, at
about 4 blocks a second, so a fall of 60 blocks takes a quarter of a minute to reach its pool the first time anyone comes
near, and is there from then on. Landing in a pool it stops. Landing on the floor it spreads as water does, up to 7
blocks and further downhill, and takes grass, flowers and vines with it.

Nothing of a theme's own growth is started within 2 blocks of a fall's water or 7 of where one comes down, and the ground
stays level there. To have none, take the block out or turn the `falls` dial to 0; for more, the dial or the block's
`count`.

### Gas

Gas is a haze through each hole, mist, swamp breath, smoke off lava and a violet shimmer, drawn by the owner's gas
library, Volumetrics
(`mc-volumetric-effects-lib`). It is optional. Without it on the server nothing is sent, and the rest of this section does
nothing. To see the gas, Volumetrics must be on the server and the client, and Overgrown Abyss on the client. The world's
blocks are never changed by it.

The kinds are the library's media, in `data/overgrown_abyss/volumetrics/medium/`: `haze`, `mist`, `bog`, `ember` and
`shimmer`. A medium is how the gas moves and looks: `gravity` (positive sinks), `dispersion`, `decay`,
`color`, `opacity`, `emissive` and the rest, all as the library's own SCOPE has them. The library holds up to 32 kinds at
once near the camera, so a use that wants a look of its own can have a medium of its own.

Where gas comes from is in `carve.json`, in two places. Every place is worked out from the hole's hashes, as growth is, and
the server sends each player the gas of the holes within 512 blocks.

- A theme's `gas` (a list): sources spread over one surface of each of its discs, as a `growth` entry is.
  - `medium`: the gas, by its id.
  - `every`: on average one source for this many blocks of the surface. Times the disc's `wildness`.
  - `on` (default `top`): `top` rests on the ground, `water` on the water, `lava` over clear lava, and `underside` hangs
    from the disc's lowest rock. `rim` rests in the open beside the rim's top block, so what sinks pours over the edge.
  - `patches`, `rim_fade`: as for growth. Patches of the same `size` as a growth's are the same patches, so mist can gather
    in the groves.
  - `shape`: `{"type": "cylinder", "radius", "height"}`, standing on the place (height 1 if left out), or
    `{"type": "sphere", "radius"}`, resting on it or hanging from it.
  - `rate`: density each cell of the shape gains per second, as the library counts it (0 to 64).
- `gas` at the top of the file, for the hole itself:
  - `fog`: the haze through the whole hole, one of the library's fogs, which are not simulated and are drawn at any
    distance. `medium` is its look (the haze's simulation plays no part). It is `bottom_density` thick at the floor and
    thins by the same share each block up to `top_density` at the top; over its outermost `edge` blocks (16 if left out)
    it fades to nothing. It fills a cone from round the hole's cone and cavern at the floor to round the mouth at the top,
    reaching an edge past the walls and below the floor so that it is at its full thickness where they are; what of it
    lies in the rock is never seen. Light through `n` blocks of it is dimmed to `exp(-opacity × density × n)`; the
    `haze` medium's opacity is 1, so at the floor (0.022 as shipped) a fifth of the light is gone in 10 blocks.
  - `falls.spray` rests on the water each fall lands in.
  - `falls.curtain` fills a cylinder up the whole drop, as wide as the fall and `margin` (1.5) blocks more each side.
  - `springs` rests on the pool where each spring's water comes down.
  - `cavern` (a list) spreads sources over the cavern floor, one for every `every` blocks, within 95% of its radius.

A hole sends at most 4096 sources. Past that the themes' last ones are left out, and the log says so once for the hole.
The hole's own come first. The `haze` dial turns the fog's two densities, and at 0 takes it away; no other dial turns gas.

### Ruins

Ruins stand on some discs, and on more of them the lower in a hole the disc is. A jungle disc has them three times in five
at the middle of the hole's height, nearly always at the bottom and one time in four at the top (`chance` 0.6, `by_height`
1.6 to 0.4), with a ruin for about every 3000 blocks of top, by the same measure more at the bottom and fewer at the top. A
lush disc has them three times in ten and a mangrove disc one time in four, both with `by_height` 1.8 to 0.4 and a ruin for
every 4000. A crystal disc has none, nor a lava disc. They are one structure, `overgrown_abyss:disc_ruins`, so
`/locate structure overgrown_abyss:disc_ruins` finds the nearest disc with one (it looks 100 chunks each way).

The mod's own pieces are vanilla's templates, named by id and not copied, in two families:

- Pieces of the Ancient City, through the same reskin as the city at the hole's floor (`processor_list/disc_ruins/outpost`:
  a tenth of the blocks rotted away, stone and mossy stone bricks for deepslate, jungle wood, moss for wool, lanterns), so
  they read as outposts of that city. Towers, keeps, the vault and the chambers have a chest or two; camps, pillars, the
  statue and rubble have none.
- Vanilla's cold ocean ruins (`processor_list/disc_ruins/overgrown`): their air, gravel and sand are left out, so they stand
  in the disc's own ground with whatever grows there; the three kinds of stone brick are mixed; magma, prismarine and
  cobblestone become mossy cobblestone, sea lanterns shroomlights, the planks jungle planks, the red bricks mud bricks.
  Vanilla puts their chests in by code, at a marker in the template, so the mod does the same: every house and all but one
  of the huts has a chest. Most markers are in a template's lowest layer, which lies in the ground here, so the chest is
  set into the floor with its lid level with the ground, and now and then under a block of it.

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

What the chests hold is a loot table for each theme and rank, in `data/overgrown_abyss/loot_table/chests/disc/`:
`jungle_1` to `jungle_3`, `lush_1` to `lush_3` and `mangrove_1` to `mangrove_3`. Each has three pools: valuables, what
grows or lies on that kind of disc, and a prize that is mostly not there. The first is the table of a theme's discs up to a
rank of 0.6, the second from there and the third from 0.85, with depth counting twice what distance does. Over 24 holes
about a third of the ruins with chests held the first table, a half the second and a fifth the third. The tables are drafts
made from vanilla's items for the owner to edit. They are the mod's own, so a pack can pick the disc ruins out by table or
by mod id (a loot modifier, or Lootr's list of tables that refill). Without `loot` the city pieces' chests hold vanilla's
jungle temple loot, which their processors give them.

To change how many ruins there are, change `chance`, `every` and `by_height`; to change which, the weights; to change what
the chests hold, the tables' files, and which discs hold which, `loot`. A pool of your own is a kind like the mod's: name it
and say how many of its pieces' lowest layers lie in the ground.

### The long ravine (set aside for now)

The long ravine with discs cut into its walls is no longer the mod's default. Its settings are still read: a file with a
`ravine` block in place of `cone` gives it. `common/src/test/resources/ravine-settings.json` is what the mod shipped before
the cone (spacing 64, row spacing 20, row jitter 0.4, side stagger 0.5, max overshoot 4, large offset bonus 0.8, radius 20
to 48, offset 0 to 0.1, with two themes that are only palettes), kept so that the tests still read one. It has the discs of
that time: stems `stem_fraction` 0.2 with `min_stem_radius` 2.5 (never below 5 blocks across), flat discs (bowl depths 0)
and no hanging. The ravine's own numbers are `spacing` (blocks of ravine per disc in a row), `row_spacing`, `row_jitter`,
`side_stagger`, `max_overshoot` and the offsets (how far a disc's centre sits inside the wall).
