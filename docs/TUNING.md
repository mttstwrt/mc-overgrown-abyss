# Tuning the wilder discs

The levers of the round built on 2026-10-10 (SCOPE.md, "Wilder discs, part 1" to "part 5"): what hangs over a disc's rim,
where its trees stop, how discs differ, the giants, the ground, the falls and the springs. For each: where it is, what it
is set to, how far it can go, which way to turn it for which look, and what was measured.

Every number here is a first guess. None of it has been looked at in game.

Other levers are not repeated here: the hole's shape, the discs' sizes and spacing, roots, ruins and loot are in the table
of levers in SCOPE.md section 9, and `docs/SETTINGS.md` describes every setting in full.

## Where the levers are, and how to try one

- Nearly all are in one file: `common/src/main/resources/data/overgrown_abyss/worldgen/density_function/ravine/carve.json`.
  A theme's levers are in its entry of `disc_themes`; the falls and springs are in `falls` at the top of the file.
- What a single plant is made of (a curtain's length, a cap's width, a bulb's lights) is in its own file under
  `worldgen/configured_feature/disc/`.
- To try more or less of a thing without building the mod or making a datapack, turn its dial:
  `config/overgrown_abyss.json` in the game's folder, which the mod writes the first time a world loads (`docs/SETTINGS.md`,
  "Dials"). 0 is none, 1 is as the mod ships, 2 is twice as much. It is read when a world loads, and only chunks not yet
  generated use the new values.
- Anything a dial does not turn is a change to `carve.json` or to a feature file: in the mod, or in a datapack that holds
  a whole copy of the file at the same path under `data/overgrown_abyss/`.
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
| A disc is a black mass from across the hole, or the roots cannot be made out | "Light", the second part: lichen overhead, the pickles in pond middles, what hangs under roots | their `every` down; none of the three costs dark ground |
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
- The two ends: the dial `growth.rim` at 0 (nothing on a rim; `rim_fade` and `wildness` stay as they are) and at 2 (every
  `every` halved).

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
- The two ends: the dial `growth.giants` at 0 and at 4 (every `every` a quarter).

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
- The two ends: the dial `rough_ground` at 0 (every top the smooth bowl it was) and at 2 (mounds and outcrops over twice as
  much of each top; the dial does not make them higher).

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
- The ends: the dial `falls` at 0 and at 3 (6 to 12 a hole). Making every fall that can be a curtain is the file's
  (`curtain.chance` 1).

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
- A spring's pool is not joined to a fall. The end to look at: the dial `springs` at 5 (5 to 10 a hole).

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
- To find one: the dial `themes.overgrown_abyss:disc_lava` at 6, which is the weight 12 that made about two fifths of the
  discs low in a hole lava discs. Lava wherever a pond lies (`clear` 0 and `bank` 3) is the file's.

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
| The second setting, NeoForge and Fabric | 67%, 67% | 58%, 55% | 64%, 63% | 16% | 59%, 58% | 39%, 40% |
| Now, with what the second part below added, NeoForge | 67% | 58% | 64% | 16% | 59% | 39% |
| Now, Fabric | 66% | 58% | 63% | 15% | 59% | 39% |

| Lever | Where | Shipped | May be | Turning it |
|---|---|---|---|---|
| `shroomlight_in_leaves`: `every` | the jungle's `growth` | 1 | 1 to 100000 | columns of ground for each place tried. A try takes only under a crown thick enough to hide a light, about one try in seventy: about nine lights to a disc. 1 is every column and as far as it goes; up for fewer, and for more the lever is `y_spread` below |
| `froglight_in_leaves`: `every`, `patches` | the mangrove's `growth` | 20; `size` 40, `cover` 0.5 | 1 to 100000 | the same; a mangrove's crown is thicker, so more tries take: about fifteen to a disc, gathered in the hearts of its groves (see "Groves" below) |
| in those two files: `y_spread` of `random_offset`, `max_steps` | `configured_feature/disc/` | 10, 22 | 0 to 16; 1 to 32 | the least a light is over the ground, and how much further up a crown is looked for. Up for higher and fewer lights |
| `glow_bulb`, `glow_bulb_swamp`: `every` | each theme's `growth` | 450, 450, 300 on lush | 1 to 100000 | places tried; about one in six grows, one in five on a lush disc |
| in the bulbs' files: `trunk_placer` `base_height` and `height_rand_a`; `minimum_size.min_clipped_height` | `configured_feature/disc/` | 10 and 4; 9 | as any tree | the stalk is 10 to 14 blocks, and as short as 9 where the dome is low. Under 9 blocks of room no bulb grows |
| in the bulbs' files: `foliage_placer` | | `radius` 2, `height` 0 | as any blob | the lights, which the file calls foliage: a cross of five. `height` 1 puts a ring of four to eight more under it |
| in `glow_berries_hanging.json`: the two `weight`s of the first layer's `height` | | 13 and 1 | any | how many vines get no berry and how many get one, as a block more at the top. One in fourteen (the first setting had one in forty). It is every vine the mod hangs: under discs, from trees and domes, in the lush disc's moss. The strongest lever of all: one in twenty is about 6 points darker on a jungle or lush disc and 3 on a mangrove's, one in ten as much lighter |
| `glow_berries_over_drop`: `every`, `"on": "underside"` | each theme's `growth` | 16 on jungle, 24 on mangrove (in the patches of its other vines), 20 on lush | 1 to 100000 | vines with berries under a disc where it costs the ground next to nothing: one is hung only where the 15 blocks under it are open air, and bears berries in its top block or two, on five vines in eight. Down for a brighter underside |
| `moss_ceiling`: `every`, on the top and `"on": "underside"` | the lush disc's `growth` | 55 and 55 | 1 to 100000 | the moss over a lush disc and under it. In the file `vegetation_chance` 0.08 is the share of mossy blocks a vine hangs from |
| `sea_pickles`: `every`; in its file the `weight`s of one, two and three pickles | the mangrove's `growth` | 120; 6, 3 and 1 | 1 to 100000; any | patches of pickles for each block of water, anywhere in it. One pickle is light 6, two 9, three 12; a fourth, 15, can be added. These are the dearest light there is: beside a bank they light the bank (see "What a kind of light gives") |
| `sea_pickles_midpond`: `every` | the mangrove's `growth`, `"on": "water"` | 6 | 1 to 100000 | single pickles only where every block within 4 steps is water, so their light of 6 falls short of any bank. Free. Down for fuller pond middles |
| `glow_lichen_high`: `every`, on the top (with `patches`) and `"on": "underside"` | jungle, mangrove and lush `growth` | 24 and 24; `cover` 0.7 | 1 to 100000 | glow lichen on a dome's ceiling and leaning wall and under a platform, only with 8 open blocks under it. From the ground it is looked for up to 32 blocks overhead. Each place tried is up to six patches of lichen |
| `roots.growth`: `glow_lichen_high` `every`; `glow_berries_over_drop` `every`, `min_radius` | `roots`, at the top of the file | 8; 20, 2.5 | 1 to 100000; 0 to 16 | what hangs under the roots, for so many blocks of a root's underside. The lit vine only hangs over 15 open blocks, and only under roots of that radius or more: round a thinner one its light reaches the root's own top |

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
- The two ends: the dial `growth.lights` at 0 and at 2. It turns the bulbs, the lights in leaves, the pickles, the lichen
  overhead and what hangs under the roots (the jungle's lights in leaves are at their most already). The berries on the
  mod's own hanging vines are part of `glow_berries_hanging.json` and not growths of their own, so the dial leaves them.
  Measured on NeoForge on 2026-10-11, with two settings files that also took those berries away or put one on a vine in
  five, dark standing places on jungle, mangrove and lush discs: 93%, 90% and 94% with no lights, which is as dark as the
  game's own berries and the ruins' lamps let a disc be; 53%, 45% and 44% with twice of each, which lights 52%, 53% and
  58% of what is seen.

### Seen as well as dark: the second mark

Added later on 2026-10-11 (SCOPE.md, "Readable discs and roots"). The owner, with the marks above met: "we also
sacrificed some readability, mangrove discs read as black masses and the large roots are very difficult to see outside
of the chasm center." Of the ideas offered they took a glowing rock backdrop, glowing ponds, lichen and lit vines under
the roots where they cost nothing, lights gathered in groves, and a second mark: about 55% of what is seen lit.

What is counted beside dark ground: of the blocks with an open face, the share whose open side has block light 5 or
more. For the roots, the same of their bark, outside the middle of the hole where the sky does not reach.

| Seen blocks lit 5+ | Jungle | Mangrove | Lush | Mangrove leaves | Roots outside the middle |
|---|---|---|---|---|---|
| Originally | 83% | 81% | 81% | 91% | 46%, and 23% with no light of any kind |
| After the light round | 40% | 38% | 43% | 38% | 35%, and 29% |
| Now, NeoForge | 43% | 44% | 46% | 51% | 47%, and 20% |
| Now, Fabric | 43% | 44% | 46% | 52% | 47%, and 20% |
| Now, leaving out the ground itself (NeoForge) | 53% | 52% | 58% | | |

- **The mark of 55% is not met, and cannot be for nothing.** I told the owner the free places could carry it; they
  cannot. With lichen in every place where it reaches no standing place, the three themes come to 48%, 50% and 49%.
  A fifth to a quarter of what is seen is the ground itself, which the first mark keeps dark (11 to 14% of it is lit
  5+), and a quarter of a jungle or mangrove disc is leaves, which hold no lichen. Leaving the ground out of the count,
  the themes are at 53%, 52% and 58% now. To reach 55% of everything would take about ten points of dark ground from
  each theme at what light costs (next item): jungle and lush to the mid fifties, the mangrove under 50.
- **What a kind of light gives** in seen surface for a point of dark ground, measured by putting each kind out: lichen
  three to four points, but there are only so many places for it; lights in leaves about one on the jungle and one and
  a half on the mangrove, where they light four points of leaves for each; berries about one; sea pickles beside a
  bank a quarter of a point. That last is why the mangrove's pickles anywhere are half as many as they were and its
  froglights three times as many: the same dark ground, and its leaves lit 51% where they were 38%.
- **Free means about a point.** Put out in the lab's light, the lichen (50,000 blocks in four holes) costs 0.9 to 1.3
  points of dark ground, since light also goes sideways; the pickles in pond middles (about 1,300) nothing; the lit
  vines under roots (about 1,500) 0.1 to 0.4.
- **Groves.** `patches` on a growth gathers it, and growths whose `patches` have one `size` share their groves. The
  mangrove's froglights, berry vines over the ground and lichen overhead have the `size` of its trees' own groves, 40,
  so the lights are in the groves' hearts, where the crowns are thick enough to hold them. On the jungle the berry
  vines and the lichen overhead are gathered, and on the lush disc its lichen (`size` 24). The jungle's lights in leaves are not: one
  try in seventy takes, so they are tried in every column and there is no rate left to gather. Gathering costs
  about a point of seen surface for the same dark ground, since lights that stand together light the same leaves
  twice: the mod's file with nothing gathered (no `patches` on its lights) had 44%, 45% and 47% lit on 67%, 57% and 64%
  dark. The mangrove's leaves are the other way round, 51% gathered to 49% spread.
- **Under the roots** only the lowest part of a root's round counts as its underside (`RootBlocks.UNDER`, 0.7 of the
  radius under its middle line): from a flank a light would shine onto the root's top, where a player walks. A root
  that lies on the ground, on a platform or in the wall has nothing under it.
- **Not counted here and so understated:** a pond's glow. Water is not an open block to this count, so the pickles in
  pond middles add almost nothing to it, though the water over them is lit.

## Gas

Gas needs the gas library, Volumetrics, on the server and the client (`docs/SETTINGS.md`, Gas). In game,
`/volumetrics stats` shows what it costs, and `/volumetrics enabled false` turns it off to compare. The kinds are files of
their own in `data/overgrown_abyss/volumetrics/medium/`. Where they come from is in `carve.json`: a theme's `gas`, and
`gas` at the top. Restart the world after a change: the library reads its media when a world loads, and the mod works a
hole's gas out once.

| Kind | Where, as shipped | Its look, and the lever |
|---|---|---|
| `haze` | a fog through the whole hole (`gas.fog`) | grey-green, as thick as `bottom_density` 0.022 at the floor and `top_density` 0.004 at the top; `opacity` 1. Its `emissive` 0.06 keeps it from going black in the dark; `forward_scatter` 0.6 makes it glow looking up at the sun |
| `mist` | lush top in patches, under the disc and over the rim; jungle top in patches and over its water; springs | pale, sinking (`gravity` 0.5), thinning by `decay` 0.05. Up `decay` for less of it far from where it comes from; `opacity` for thicker |
| `spray` | where each fall lands, and up its drop | white, light (`gravity` 0.2), spreading fast (`dispersion` 2) and short-lived (`decay` 0.12); glows towards the sun (`forward_scatter` 0.9) |
| `cavern_mist` | the cavern floor | a heavier mist (`gravity` 1, `max_density` 0.4) that lies over the city |
| `bog` | mangrove top, ponds and underside | green and heavy (`gravity` 2): it lies on mud and drips off. `opacity` 0.55 |
| `ember` | nothing: the lava disc gives off no smoke (removed 2026-10-19), the medium is kept | rises (`gravity` -1.5) and fades fast (`decay` 0.12) |
| `shimmer` | crystal top and underside | faint violet, glowing of itself (`emissive` 0.35), short-lived (`decay` 0.15) |

- How much of a kind there is: a source's `rate`, and how many there are, `every`. Halving `every` doubles the sources.
- Gas is dark where the discs are dark, as blocks are. `emissive` is the lever for gas that should show in the dark. Mist
  is already lit by glow berries and lights inside leaves, through `block_light` 0.8.
- Leaves stop gas, so mist on a jungle disc lies under the canopy.
- The library holds 32 kinds at once near the camera, other mods' gas included, so a use that wants its own look gets a
  medium of its own: copy one, rename it, and point the source at it.
- The haze: `bottom_density` and `top_density` in `gas.fog`, or the dial `haze` to try. What of the floor's light reaches
  the rim is about `exp(-depth × (bottom - top) / ln(bottom / top))`: with the shipped 0.022 and 0.004, about a fifth
  over a hole 140 blocks deep and a twentieth over one of 300. From the floor, a disc 60 blocks up keeps about half its
  light and one 120 up a third; across the city floor, half is gone in about 40 blocks. `edge` softens it at the mouth
  and the floor. The fog costs no simulation; on the GPU, where a ray passes through it, up to 28 more samples at medium
  quality.
- How many sources a hole has, of the 4096 it may send: 2,300 to 3,200 in the eight holes of seed 11 counted (cells 0,0 to
  0,7, outside the game), each worked out in 30 to 70 ms. About half is mist, and up to a third bog. The mangrove's bog
  was thinned to that from 2,500 to 3,700 (ponds `every` 50 to 90, top 120 to 160) to leave room under the cap.

## Fixed in the code, so not levers yet

These are numbers of mine in Java. Each would be a small change to move into `carve.json` if it turns out to want turning.

| What | Value | Where |
|---|---|---|
| Ground stays level this far in from the rim | 4 blocks | `DiscGround.RIM_FLAT` |
| Ground stays level this far round a ruin | 2 blocks | `DiscRuinSites.MARGIN`, which also keeps ruins apart |
| Nothing of a theme grows this near a fall's water; this near where one comes down | 2.5 and 7 blocks | `DiscFalls.CLEAR`, `CLEAR_UNDER` |
| How low on a root's round a place under it must lie | 0.7 of the radius under its middle line | `RootBlocks.UNDER` |
| The share of a hole's falls first looked for among those that land on a disc | half, rounded up | `DiscFalls.Grower.grow` |
| Whether a fall may end on the floor at all, the city included | it may | `DiscFalls.Grower.landing` |
| A spring's pool: how far out from under the spring | 3 blocks | `DiscFalls.SPRING_POOL` |
| How far a wall may lean in under a spring | half a block for a block's rise | `DiscFalls.UPRIGHT` |
| How far round from straight behind a disc a spring may be | 0.6 radians either way | `DiscFalls.SPRING_ARC` |
| Top growth kept this far inside the rim | 1.5 blocks | `DiscGrowth.EDGE_CLEAR` |
| How near the lava's height another disc's top has to be for the lava to keep clear of it | 6 blocks over or under | `DiscLava.LEVEL_WITH` |
| That a disc of lava has no falls or springs | none | `DiscFalls.Grower.starts`, `pool` |
