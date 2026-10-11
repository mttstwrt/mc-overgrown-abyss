# Tuning the wilder discs

The levers of the round built on 2026-10-10 (SCOPE.md, "Wilder discs, part 1" to "part 5"): what hangs over a disc's rim,
where its trees stop, how discs differ, the giants, the ground, the falls and the springs. For each: where it is, what it
is set to, how far it can go, which way to turn it for which look, and what was measured.

Every number here is a first guess. None of it has been looked at in game.

Other levers are not repeated here: the hole's shape, the discs' sizes and spacing, roots, ruins and loot are in the table
of levers in SCOPE.md section 9, and `dev-datapacks/README.md` describes every setting in full.

## Where the levers are, and how to try one

- Nearly all are in one file: `common/src/main/resources/data/overgrown_abyss/worldgen/density_function/ravine/carve.json`.
  A theme's levers are in its entry of `disc_themes`; the falls and springs are in `falls` at the top of the file.
- What a single plant is made of (a curtain's length, a cap's width, a bulb's lights) is in its own file under
  `worldgen/configured_feature/disc/`.
- To try a change without building the mod: copy a folder from `dev-datapacks/` (it is `pack.mcmeta` and a whole
  `carve.json`), edit the copy, and put it in a new world's `datapacks` folder. A feature file can be overridden in the
  same pack, at the same path under `data/overgrown_abyss/`. Only chunks not yet generated use the new values.
- The log lines `Ravine settings:` and `Falls:` at world load show the values that are live. A value outside its range,
  or one of the rules below broken, fails the load with a message that names it.
- Each entry of a theme's `growth` has its own draw for every column, made from its place in the list. So lowering an
  `every`, or raising `wildness`, keeps the plants that entry had and adds more between them, and no other entry's places
  move. Adding, removing or reordering an entry moves every plant listed after it: two worlds compare plant for plant
  only if their lists have the same entries in the same order.

## By what you see

| If in game | Turn | Which way |
|---|---|---|
| Trees still end in a wall at the edge | the trees' `rim_fade` | `to` down towards 0, `width` up, `reach` up |
| Too little reaches out past the edge | `every` of `rim_bush*` and `rim_tree` | down (9 is a bush for every 9 blocks of rim) |
| The rim is so hung that the layers of its side are hidden | `every` of `rim_vines`, `rim_leaves`, `rim_roots` | up; or `cover` down in the rim bushes' `patches` |
| Trees on the very edge look wrong | the `rim_tree` entry (jungle only) | take it out |
| Small discs look bald at the edge | the trees' `rim_fade.reach` | down |
| A disc is too thick to walk through | the theme's `wildness.max` | down to 1 or under |
| Every disc of a theme looks alike | `wildness` | `min` and `max` further apart |
| Giants are never met, or are everywhere | `every` of `giant_*` and `glow_bulb*` | down for more, up for fewer |
| Monsters spawn nowhere on a disc, or a disc has no light to see by | "Light" below: the `every` of the lights, the share of vines with berries | `every` up and berries down for darker, the other way for lighter |
| The ground reads as flat | `ground.mounds` | `size` up first (a mound's height comes from its width), then `height` and `cover` |
| Outcrops are in the way | `ground.outcrops.cover` | down, or take `outcrops` out |
| Too many falls, or too few | `falls.count` | the range itself |
| Falls run on too far from disc to disc | `falls.chain`, `falls.max_steps` | down |
| Wide falls are too common, or never seen | `falls.curtain.chance`, `min_radius` | chance down or up; radius down lets smaller discs have one |
| A theme never starts a fall | that theme's `water` | it needs a pond or stream that reaches the bank; lush and crystal have no water, and lava's ponds start no falls |
| Springs are not noticed | `falls.springs.count` | up |

## Wildness: how discs of one theme differ

`wildness` on a theme. Each disc draws one number from `min` to `max` and grows that many times as much of everything in
the theme's `growth`. What the disc inherits from its parent biome (grass, ferns) is not scaled, so a disc at the low end
is a glade and not bare ground.

| Setting | Shipped | May be |
|---|---|---|
| `min`, `max` | 0.5 and 1.4 on jungle and mangrove; none on lush, crystal and lava (every disc alike) | 0 to 4, `min` not over `max` |

- `max` is the lever for whether the thickest disc can be walked through: it multiplies trees and bushes too.
- Measured, jungle ground with a block at foot or head height: 32% before this round, 33% with it; 28% on the discs that
  drew under 0.8 and 37% on those over 1.15. The mark set on 2026-10-05 was a quarter, so the jungle was over it before.
- To keep the variety and make no disc thicker than it was: `max` 1 with a lower `min`. To bring the jungle under the mark
  as well, raise `every` of `jungle_bush` (50); bushes are most of what blocks.

## Rim growth: what hangs over and stands on the edge

A `growth` entry with `"on": "rim"`. `every` is blocks of rim: one for every so many of the columns that touch the
platform's edge. Where the rim lies in the hole's wall nothing grows.

| Entry | Jungle | Mangrove | Lush | May be |
|---|---|---|---|---|
| `rim_bush`, `rim_bush_mangrove`, `rim_bush_azalea`: `every` | 9 | 12 | 14 | 1 to 100000 |
| `rim_tree`: `every` | 18 | | | |
| the bushes' and tree's `patches`: `size`, `cover` | 14, 0.5 | 14, 0.5 | 14, 0.5 | 4 to 256; 0.05 to 1 |
| `rim_leaves`: `every` | 14 | | | |
| `rim_roots`: `every` | | 10 | | |
| `rim_vines`: `every` | 12 | 14 | 16 | |

- `patches.cover` on the bushes and the tree is the share of a rim they stand along; the rest is left clear. With 0.5 about
  half a jungle rim has a clear lip. `every` still says how many there are, so a lower `cover` puts the same bushes closer
  together along less of the rim. All of them have one `size`, which is why they keep to the same stretches; one given a
  different `size` no longer does.
- A curtain of vines is a few columns wide: an `every` much under 8 closes into one unbroken curtain and hides the view
  from the rim.
- Inside `rim_vines.json`: how long a curtain is (3 to 24, most of them short) and how many columns a patch tries.
  Inside `rim_tree.json` and the bushes: height, leaves, and the block each grows from on the platform's side.
- Measured, rim columns with something hanging in the 10 blocks under the lip, before and after: jungle 19% and 46%,
  mangrove 15% and 39%, lush 16% and 37%. Leaves at the rim's own height in the first 2 blocks outside a jungle disc: 1%
  and 36%.
- The two ends: `bare-rims` (none of this) and `heavy-rims` (every `every` halved).

## The treeline: where a disc's trees stop

`rim_fade` on a `growth` entry of the top (not on one of the rim). Over the last `width` blocks before the rim the entry's
rate goes steadily from what it is elsewhere to `to` times that.

| Setting | Shipped | May be | Turning it |
|---|---|---|---|
| `width` | 10 on `jungle_tree` and `jungle_tree_giant`; 8 on `giant_pad` | 1 to 64 | up: the thinning starts further in |
| `to` | 0.15 on the trees; 4 on the pads | 0 to 8 | 0 is none at the rim; over 1 gathers the growth at the rim |
| `wander` | 0.8 on the trees | 0 to 1 | up: the width differs more round the rim (0.8 of 10 is 2 to 18 blocks) |
| `reach` | 0.25 on the trees | 0.05 to 1 | the most of a disc's radius the fade may take; down spares small discs |

- Every growth of one disc wanders alike, so its trees leave the same clearings at the rim.
- `reach` is what keeps small discs wooded. Without it (1) jungle discs under 30 in radius went from 34% of their ground
  under canopy to 25%; at 0.25 they are at 33%.
- The mangrove's trees have no fade on purpose. Their crowns reach past the rim by themselves, and thinning them there cut
  the canopy outside the rim from 21% to 15% in the first 2 blocks.
- Measured on the jungle, under canopy before and after: 10 to 4 blocks inside the rim 40% and 32%; the last 2 inside 25%
  and 25%; the first 2 outside 11% and 18%; 2 to 4 outside 2% and 9%.

## Giants

Entries at the head of a theme's `growth`, before its trees: a cap needs the air it fills.

| Entry | Jungle | Mangrove | Lush | Grown of places tried (four holes) |
|---|---|---|---|---|
| `giant_mushroom_red`: `every`; `patches` | 900; 30, 0.3 | | | 56 of 125 |
| `giant_mushroom_brown`: `every`; `patches` | | 500; 24, 0.3 | | 13 of 118 |
| `giant_pad`: `every`; `rim_fade` | 500; 8, `to` 4 | 400; 8, `to` 4 | | 51 of 355 and 28 of 215 |
| `glow_bulb`, `glow_bulb_swamp`: `every` | 450 | 450 | 300 | 53 of about 290, 19 of about 130 and 74 of about 330 |

- `every` is places tried, not giants grown. A mushroom or pad grows only on soil (not the jungle's brick floors, not
  stone, not in water) and only where the whole cap has air, so a mangrove disc's groves leave room for few. A bulb is a
  tree feature and stands on any ground, but since the light round ("Light" below) it wants 9 blocks of clear air over
  it, so under a low dome none grows.
- As shipped that comes to about three or four giants on a jungle disc, four on a mangrove disc and two or three bulbs on
  a lush one, unevenly: before the bulbs were changed 17 of 45 jungle discs had a red mushroom and 29 of 45 a bulb.
- `patches` puts the mushrooms in groves, about `size` blocks from one to the next, over `cover` of the top. `every` still
  says how many places are tried, so a lower `cover` gathers the same number into less ground.
- The pads' `to` 4 is what hangs their leaves over the edge (16 of 51 on the jungle reached past the rim). Up for more of
  that, 1 for pads spread evenly.
- Inside the feature files: `foliage_radius` is the cap's width (3 on the mushrooms, 4 on the pad, which is nine across);
  `cap_provider` and `stem_provider` are the blocks; in the bulbs the trunk's height and the shape of the lights ("Light"
  below).
- The two ends: `no-giants` and `many-giants` (every `every` a quarter).

## Ground: mounds and outcrops

`ground` on a theme, two optional parts. Both are blocks put on top of the platform; the platform, its dome and its rim do
not change.

| Setting | Jungle | Lush | Mangrove | Crystal | Lava | May be |
|---|---|---|---|---|---|---|
| `mounds`: `height`, `size`, `cover` | 3, 22, 0.3 | 2, 18, 0.25 | 1, 12, 0.25 | | 2, 16, 0.3 | 1 to 8; 4 to 128; 0.05 to 1 |
| `outcrops`: `height`, `size`, `cover` | 4, 7, 0.03 | 5, 8, 0.05 | | 6, 5, 0.08 | 5, 6, 0.05 | 1 to 12; 4 to 128; 0.01 to 1 |
| Share of a top raised, measured | 21% | 21% | 9% | 4% | not measured | |

- A mound is a block high at its edge and climbs by less than a block for each block across, so it is walked up without a
  jump. Its height comes from its width, about a block and a tenth of `size`: with `size` 22 most are 2 or 3 high, with 12
  none is over 2 whatever `height` allows. For higher mounds raise `size` first.
- A theme's palette has to give its top at least `mounds.height` blocks of material, or the load fails and says so.
- `cover` is the share raised where nothing holds the ground down. Less is raised in the world, much less on a wet disc: the
  ground stays level within 4 blocks of the rim, under each ruin and 2 blocks round it, and at water.
- Mounds do not get in a player's way: jungle ground blocked at foot or head height was 32.1% with them and 31.9% without.
  Outcrops do, by design: their sides are as steep as they come. `outcrops.block` is what they are made of.
- The two ends: `flat-discs` and `rugged-discs`.

## Falls

`falls` at the top of the file. A hole has a number of falls, not each disc a chance of one. Take the block out for none.

| Setting | Shipped | May be | Turning it |
|---|---|---|---|
| `count` | 2 to 4 | 0 to 16 | falls a hole starts; fewer where it has fewer places for one |
| `by_height`: `bottom`, `top` | 0.5, 2 | 0 to 1000 | the weight of a start on the lowest disc and on the highest; even between them |
| `min_drop` | 10 | 2 to 256 | the least open air under a lip; up leaves only long falls |
| `max_per_disc` | 1 | 1 to 4 | falls that may start on one disc |
| `width` | 1 | 1 to 2 | blocks of water at an ordinary lip |
| `pool`: `radius`, `depth` | 3, 2 | 2 to 8; 1 to 2 | the pool a fall lands in on a disc |
| `chain` | 0.6 | 0 to 1 | chance that a fall which landed in a pool goes on over that disc's rim |
| `max_steps` | 4 | 1 to 8 | the most falls one after another |
| `curtain`: `chance`, `width`, `min_radius` | 0.2, 3 to 5, 40 | 0 to 1; 2 to 9; 0 to 256 | wide falls, on discs of that radius or more |

- "Not overdoing it" is `count`. As shipped the four holes measured had 2, 4, 4 and 2 falls: 12, one of them wide, one
  going on from its pool.
- A fall starts only where a disc's own pond or stream comes up against its bank, on the side of the hole's middle. So a
  theme's `water` is a lever here too: `ponds` (0.06 jungle, 0.4 mangrove), `stream_spacing` (44 and 30; down for more
  streams) and `stream_width` (2). More water on a disc means more places for a fall, and less ground raised.
- `pool.depth` 2 needs the discs' `floor_thickness` over 3 (it is 4); the load fails otherwise.
- A fall with no disc under it lands on the cavern floor and spreads 2 to 7 blocks, 11 down a slope. Three of the twelve
  came down on buildings of the city. No setting keeps a fall off the floor today (see the last section).
- The game runs the falling water at about 4 blocks a second the first time a chunk comes alive: a fall of 60 blocks takes
  a quarter of a minute to arrive, once.
- The ends: `no-falls`, `many-falls` (8 to 10 a hole, `chain` 0.9) and `curtain-falls` (every fall that can be is wide).

## Springs

`falls.springs`. Take the block out for none.

| Setting | Shipped | May be | Turning it |
|---|---|---|---|
| `count` | 1 to 2 | 0 to 16 | springs a hole has, where it has discs with wall behind them |
| `height` | 3 to 7 | 2 to 16 | blocks over the disc's rim; see below |
| `feature` | `overgrown_abyss:disc/wall_spring` | any configured feature | what makes the spring at the place given |

- `height` is a ceiling more than a setting. A dome leans in as it rises and vanilla's spring wants rock under it, so a
  spring is put no higher than its wall is still nearly upright. By the dome's shape that is 3 to 6 blocks over the middle
  of the platform on discs of 20 to 64 in radius, and 8 on a rider of 10, which is under what `height` asks nearly
  everywhere. The six measured came out 3 to 6 blocks over the rim's top block. So raising `height` should change little
  on all but small discs; that was not tried.
- In `wall_spring.json`: `rock_count` 4 and `hole_count` 1 are vanilla's own test (rock on three sides and one side
  open); `requires_block_below` is why a spring needs upright wall under it. `valid_blocks` is the tag
  `#overgrown_abyss:spring_rock`; a wall of some other mod's stone needs that stone added to the tag, or no spring takes.
- Where no place will do, the hole has the pool and no spring over it. Measured: 6 of 6 took.
- Vanilla puts springs of its own in the hole's wall as well, so a hole had the odd fall from the wall before this.
- A spring's pool is not joined to a fall. The end to look at: `many-springs` (6 to 8 a hole).

## The lava disc

Added after the round above (SCOPE.md, "A lava disc"). Its levers are in the `disc_lava` entry of `disc_themes`. Like the
rest, every number is a first guess and none of it has been looked at in game. The owner's second word on it
(2026-10-11): rarer, only low in a hole, and without its obsidian trees for now. And the third, the same day: `weight`
"around 2", and magma cubes among its spawns.

| Setting | Shipped | May be | Turning it |
|---|---|---|---|
| `weight` | 2 | 0 to 1000 | how many of the discs it may be given to are lava discs. Measured on vanilla terrain: about one disc in twenty-six, 1.2 to a hole; 1.5 gives 0.9 to a hole, 3 gives 1.7 |
| `only.height.max` | 0.3 | 0 to 1 | how high in a hole a lava disc may be, from the lowest disc (0) to the highest (1). 0.3 is the bottom row and what stands on it, two rows in a deep hole; 0.4 lets in the second row of a hole of four. Take `only` out for any height |
| `by_height` | none | 0 to 1000 each end | likelier low or high without ruling a height out, as the mangrove has. It was 3 at the bottom to 0.1 at the top until the limit took its place |
| `water.ponds` | 0.45 | 0 to 0.9 | the share of the top that is pond before the three rules below take theirs. Under about 0.4 the ponds are apart; over it they join into a lake with islands |
| `water.pond_size` | 14 | 4 to 128 | blocks from one pond to the next; larger gives fewer, wider ponds |
| `water.bank` | 6 | 2 to 32 | blocks of rock kept inside the rim. The other themes have 3; lava has more because a lower disc's trees stand right outside the rim |
| `water.clear` | 6 | 0 to 16, lava only | blocks kept from other discs' ground at about the lava's height, and from roots. 0 for lava wherever a pond lies |
| `water.depth` | 1 | 1 or 2 | 2 is safe while nothing grows in the lava. Keep at 1 where trees stand in it: a tree's foot is one block |
| `water.stream_width` | none | 0 to 8 | rivers of lava; not tried |
| `growth` | none | as on any theme | the obsidian trees are out for now. To put them back: `"growth": [{"feature": "overgrown_abyss:disc/obsidian_tree", "every": 30, "on": "lava"}]`, where `every` is blocks of lava for each place tried; about a third of the places grew a tree |
| in the tree's file, `obsidian_tree.json` (kept) | trunk 5 to 9, crown radius 2 | as any tree | `trunk_provider` and `dirt_provider` are the trunk and its foot: obsidian |
| in the tree's file: `foliage_provider` | magma 3, glowstone 1 | any weights, any blocks | the crown. The two `weight`s are how much of it glows: about one block in four is glowstone |
| `palette.top`, first layer | basalt, obsidian, smooth basalt in patches | any provider | `states` is an order, as on the jungle disc; `firstOctave` -4 makes patches about 8 blocks apart |
| in the biome's file, `worldgen/biome/disc_lava.json`: the `minecraft:magma_cube` entry of `spawners.monster` | `weight` 100, 2 to 5 at a time | as any spawn entry | vanilla's numbers for its basalt deltas. `weight` is against the 515 of the cave's monsters listed with it: down for fewer cubes, take the entry out for none |

- Lava is what is left of the ponds: measured outside the game over 16 holes, 67% of them, which is 20% of a lava disc's
  top (58% and 17% when lava discs lay at every height). On vanilla terrain one lava disc in ten is left with under
  20 blocks of it, most of those with none: a small disc has no room for a pond inside a `bank` of 6.
- Rare means that many holes have none: 20 of 58 holes of vanilla terrain, where 2 of 58 had none before. `weight` is the
  lever.
- Magma cubes are what spawns on a lit lava disc. The game spawns one in any light, on any difficulty but peaceful
  (`MagmaCube.checkMagmaCubeSpawnRules`), where the cave's monsters of the same list want no block light at all
  (`Monster.isDarkEnoughToSpawn`), and lava lights its disc. So torches do not keep the cubes off, and a lava disc that
  came out with no lava has the cave's monsters as well. Not seen in game: nothing spawns on a server without a player.
- The level's own water gets at lava low in a hole. In the four holes looked at, one lava disc of four had a fall from a
  pocket of water in the rock over it, which had turned 143 of its 309 blocks of lava to obsidian. Nothing in the file
  keeps that off.
- Why the rules: the game's lava lights what burns within three blocks, and discs of one ring run into each other. In
  four holes, the mod's file with `clear` 0 had 2337 blocks within lava's reach that it can set alight, three quarters
  of them the hole's wooden roots; with `clear` 6 it has 52, for 54% of the lava.
- What the rules do not reach: what hangs from a disc higher up (vines, glow berries), and what vanilla builds in a hole,
  such as the props of a mineshaft.
- A pack that turns `doFireTick` off needs none of it, and can have `clear` 0.
- The two ends: `many-lava` (about two fifths of the discs low in a hole) and `loose-lava` (the same with `clear` 0 and
  `bank` 3).

## Light: how much of a disc is dark

Added on 2026-10-11 (SCOPE.md, "Less light on the discs, and lights only inside leaves"). The owner: shroomlights,
froglights and glow berries were "too frequent and prevent mobs from spawning most places"; a light should be "within at
least one layer of leaves, not at the edges", and lights further from the ground. Their second word the same day, on the
first setting: it "reduces light a little too much, especially underneath discs where mobs couldn't spawn anyway"; the
jungle and lush discs "somewhere between 60-70", the mangrove "a little lower" with more sea pickles, since "most mobs
can't spawn in water anyway", and more light under a disc "especially if they are far above platforms below them". "The
goal is still a fantasy look overall." Like the rest, the numbers that get there are guesses and none of it has been
looked at in game.

What is counted is standing places with no block light at all, which is what the overworld's monsters want
(`Monster.isDarkEnoughToSpawn`; the sky's light is a test of its own, and under a dome there is little of it). A standing
place is a block with a whole top under two blocks a body fits in. Four holes, read from the saved world:

| Dark standing places | Jungle | Mangrove | Lush | Crystal | Lava | The hole's own lush caves |
|---|---|---|---|---|---|---|
| Before | 9% | 6% | 14% | 5% | 28% | 39% |
| The first setting, which the owner found too dark | 79% | 72% | 79% | 17% | 66% | 40% |
| Now, NeoForge | 67% | 58% | 64% | 16% | 59% | 39% |
| Now, Fabric | 67% | 55% | 63% | 16% | 58% | 40% |

| Lever | Where | Shipped | May be | Turning it |
|---|---|---|---|---|
| `shroomlight_in_leaves`: `every` | the jungle's `growth` | 2 | 1 to 100000 | columns of ground for each place tried. A try takes only under a crown thick enough to hide a light, about one try in seventy: about six lights to a disc. Down for more; 1 is as far as it goes, and after that the lever is `y_spread` below |
| `froglight_in_leaves`: `every` | the mangrove's `growth` | 40 | 1 to 100000 | the same; a mangrove's crown is thicker, so about one try in eleven takes: four or five to a disc |
| in those two files: `y_spread` of `random_offset`, `max_steps` | `configured_feature/disc/` | 10, 22 | 0 to 16; 1 to 32 | the least a light is over the ground, and how much further up a crown is looked for. Up for higher and fewer lights |
| `glow_bulb`, `glow_bulb_swamp`: `every` | each theme's `growth` | 450, 450, 300 on lush | 1 to 100000 | places tried; about one in six grows, one in five on a lush disc |
| in the bulbs' files: `trunk_placer` `base_height` and `height_rand_a`; `minimum_size.min_clipped_height` | `configured_feature/disc/` | 10 and 4; 9 | as any tree | the stalk is 10 to 14 blocks, and as short as 9 where the dome is low. Under 9 blocks of room no bulb grows |
| in the bulbs' files: `foliage_placer` | | `radius` 2, `height` 0 | as any blob | the lights, which the file calls foliage: a cross of five. `height` 1 puts a ring of four to eight more under it |
| in `glow_berries_hanging.json`: the two `weight`s of the first layer's `height` | | 13 and 1 | any | how many vines get no berry and how many get one, as a block more at the top. One in fourteen (the first setting had one in forty). It is every vine the mod hangs: under discs, from trees and domes, in the lush disc's moss. The strongest lever of all: one in twenty is about 6 points darker on a jungle or lush disc and 3 on a mangrove's, one in ten as much lighter |
| `glow_berries_over_drop`: `every`, `"on": "underside"` | each theme's `growth` | 16 on jungle, 24 on mangrove (in the patches of its other vines), 20 on lush | 1 to 100000 | vines with berries under a disc where it costs the ground next to nothing: one is hung only where the 15 blocks under it are open air, and bears berries in its top block or two, on five vines in eight. Down for a brighter underside |
| `moss_ceiling`: `every`, on the top and `"on": "underside"` | the lush disc's `growth` | 55 and 55 | 1 to 100000 | the moss over a lush disc and under it. In the file `vegetation_chance` 0.08 is the share of mossy blocks a vine hangs from |
| `sea_pickles`: `every`; in its file the `weight`s of one, two and three pickles | the mangrove's `growth` | 60; 6, 3 and 1 | 1 to 100000; any | patches of pickles for each block of water. One pickle is light 6, two 9, three 12; a fourth, 15, can be added. How many to a block counts for more than how many patches: with four to a block as often as one, pickles alone light a quarter of a mangrove disc; these light about a seventh |

- **Height is the strongest lever.** A light gives 15 at its own block and one less for each block away, so what it lights
  on the ground is a diamond that shrinks as it rises: 265 places from 3 blocks up, where a light hung under a small
  tree, 41 from 10, 5 from 13, none from 15.
- **Inside leaves.** A light in a tree is put in place of a leaf that has a leaf or a log on all six of its sides
  (`#overgrown_abyss:hides_light` is what counts as cover; add another mod's leaves there if a pack's trees do not use
  the game's tag). Measured: of 579 such lights none had a side open. A bulb covers its own lights instead, a leaf on
  every side of every light that had air there: of the 3375 sides its lights turn outward (not to the stalk or to
  another light), 25 were not leaf, about one in a hundred. A vine, water or a cave vine was there first; where the
  light took a vine's place, the vine over and under it later fell and left air.
- **What each kind costs,** as the dark share with only that kind left (NeoForge): shroomlights and froglights 90% on the
  jungle, 91% on the mangrove, 94% on the lush disc; berries 77%, 78% and 70%; with neither 97%, 85% and 97%, and on
  the mangrove the sea pickles are what is left (over 99% without them).
- **Berries are the larger half.** Before, they alone kept three quarters of every disc lit, more than the shroomlights
  and froglights did. A berry of the common vine is now only ever in its top block, as far from the ground as the vine
  allows.
- **Under a disc.** A platform is the roof over the disc below wherever discs stand over each other, so a berry under
  it lights that disc's ground when it is within 13 blocks, and none of it otherwise. `glow_berries_over_drop` is for
  the second case: about 3,700 lit vines under the platforms of the discs' own biomes in four holes, where the first
  setting had about 900 and the mod before this round about 9,900. A block predicate may look no further than 15 blocks,
  which is why its berries are kept to the top two blocks of the vine. Light also goes sideways, to a neighbour's rim or a
  mound, so it is not quite free: about 8,500 vines with berries hang over such a drop in four holes, the game's own in
  the cavern among them, and without their berries the jungle, mangrove and lush discs would be 3, 4 and 2 points darker.
- **Not reached by any file of the mod's:**
  - The hole's own air is `minecraft:lush_caves` (`environment.cavern_biome`), so the game hangs its own glow berries,
    one block in five, on the cavern's roof, the walls and under the lowest discs: about 13,000 vines with berries in
    four holes, where the mod's own are about 4,600. That is why the last column did not move. They cost a jungle or
    lush disc two to four points and a mangrove disc, which lies low in a hole, up to ten, and they are what glows in
    the cavern and the middle of a hole. Thinning them needs the cavern to have a biome of the mod's that grows a
    chosen part of the lush caves' features, as a disc does: code, not built.
  - A crystal disc is lit by its amethyst: a cluster gives 5, and there is one for every 7 blocks. Fewer clusters, or
    buds in place of most of them (1, 2 and 4), would darken it, and change how it looks.
  - A lava disc is lit by its lava.
  - A ruin's lamps: where a borrowed piece has a sea lantern the disc ruins put a shroomlight (`disc_ruins/overgrown.json`),
    about 40 of them in four holes, bare as the piece has them.
- The two ends: `no-lights` (no bulbs, no lights in leaves, no pickles, no berries on the mod's vines) and `many-lights`
  (two to three times the lights in leaves, twice the bulbs, the pickles and the lit vines under discs, a berry on one
  vine in five). Measured on NeoForge, dark standing places on jungle, mangrove and lush discs: 93%, 89% and 94% with
  the first, which is as dark as the game's own berries and the ruins' lamps let a disc be; 51%, 40% and 44% with
  the second.

## Fixed in the code, so not levers yet

These are numbers of mine in Java. Each would be a small change to move into `carve.json` if it turns out to want turning.

| What | Value | Where |
|---|---|---|
| Ground stays level this far in from the rim | 4 blocks | `DiscGround.RIM_FLAT` |
| Ground stays level this far round a ruin | 2 blocks | `DiscRuinSites.MARGIN`, which also keeps ruins apart |
| Nothing of a theme grows this near a fall's water; this near where one comes down | 2.5 and 7 blocks | `DiscFalls.CLEAR`, `CLEAR_UNDER` |
| The share of a hole's falls first looked for among those that land on a disc | half, rounded up | `DiscFalls.Grower.grow` |
| Whether a fall may end on the floor at all, the city included | it may | `DiscFalls.Grower.landing` |
| A spring's pool: how far out from under the spring | 3 blocks | `DiscFalls.SPRING_POOL` |
| How far a wall may lean in under a spring | half a block for a block's rise | `DiscFalls.UPRIGHT` |
| How far round from straight behind a disc a spring may be | 0.6 radians either way | `DiscFalls.SPRING_ARC` |
| Top growth kept this far inside the rim | 1.5 blocks | `DiscGrowth.EDGE_CLEAR` |
| How near the lava's height another disc's top has to be for the lava to keep clear of it | 6 blocks over or under | `DiscLava.LEVEL_WITH` |
| That a disc of lava has no falls or springs | none | `DiscFalls.Grower.starts`, `pool` |
