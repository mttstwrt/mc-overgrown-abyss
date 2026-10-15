# Volumetrics API, for this mod

The gas library (`mc-volumetric-effects-lib`, mod id `volumetrics`) as this mod compiles against it. This folder is a
Maven repository holding one build of its API:

```
dev.syrval.volumetrics:volumetrics-common:0.1.0+mc1.21.1    jar, sources jar, POM
```

Published 2026-10-14 from the library's `1.21.1` branch at `d92fe27`. Everything below was read from that
commit's sources; where it and this file disagree, the sources jar beside the jar wins.

## How this mod takes it

- `common/build.gradle` declares this folder as a repository for the group `dev.syrval.volumetrics` only, and
  depends on the jar with plain `compileOnly`. The version is `volumetrics_version` in `gradle.properties`.
- The jar is the library's common module in Mojang's names, as this mod is written: not the one remapped to
  intermediary for Fabric. That is why it is not `modCompileOnly`: Loom would remap it as if it were intermediary, and
  Loom 1.13 (ours) refuses any mod jar built by a newer Loom (the library's is 1.17).
- Nothing of it is shipped or required. `fabric.mod.json` says `suggests`, `neoforge.mods.toml` says `optional`.
- Only a class that is loaded after `Platform.isModLoaded("volumetrics")` may name a library type
  (`compat/client/VolumetricsGas` here). Without the library, a class that names one can fail with
  `NoClassDefFoundError` as soon as it is loaded or first run.

## Publishing a new build here

After the library's API changes. From wherever the library's `1.21.1` branch is checked out (on 2026-10-14, the
worktree `mc-volumetric-effects-lib/.claude/worktrees/port-1.21.1`):

```sh
rm -rf ~/dev/my-projects/mc-mods/mc-abyss-ruins/libs/volumetrics/dev
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :common:publishToMavenLocal \
    -Dmaven.repo.local=$HOME/dev/my-projects/mc-mods/mc-abyss-ruins/libs/volumetrics
```

`-Dmaven.repo.local` sends the publish here instead of `~/.m2`; give it an absolute path. Clearing `dev/` first keeps
no file of an earlier build. Then change the commit named at the top of this file, and this file if the API changed.

For a game run with the gas, the library's loader jar goes into `<loader>/run/mods`:
`fabric/build/libs/volumetrics-fabric-0.1.0+mc1.21.1-fabric.jar` or
`neoforge/build/libs/volumetrics-neoforge-0.1.0+mc1.21.1-neoforge.jar` in the library, after `./gradlew build` there.

## The API

Package `dev.syrval.volumetrics.api`, and `api.client` for what is client only.

### `client.GasEmitters`: emitters from code

```java
Registry<Medium> media = level.registryAccess().registry(VolumetricsRegistries.MEDIUM).orElseThrow();
Medium mist = media.getOptional(ResourceLocation.fromNamespaceAndPath("overgrown_abyss", "mist")).orElseThrow();

GasEmitters.Handle handle = GasEmitters.of(level).add(mist, new EmitterShape.Disc(3f, 1f), new Vec3(x, y, z), 1.5f);
handle.moveTo(new Vec3(x + 1, y, z));   // as often as wanted
handle.setRate(0.5f);
handle.remove();
```

| Member | What it does |
|---|---|
| `static GasEmitters of(ClientLevel level)` | The level's own set of emitters. Throws `IllegalStateException` off the client thread |
| `Handle add(Medium medium, EmitterShape shape, Vec3 anchor, float rate)` | A new emitter. `rate` is the density each cell in the shape gains per second, up to the medium's `max_density`. Throws `IllegalArgumentException` for a negative or NaN rate or a bad shape size, `NullPointerException` for a null argument |
| `Handle.moveTo(Vec3 anchor)` | Puts it somewhere else, from the next simulation step on |
| `Handle.setRate(float rate)` | Same checks as `add` |
| `Handle.remove()` | Takes it away for good. A second call is harmless; `moveTo` and `setRate` after it do nothing |

- **Client thread only**, every method of both. Never from code that also runs on a dedicated server. A packet
  handler hands its work to the client thread first (Architectury's `PacketContext.queue`).
- **On this client only.** Nothing about these emitters is sent to anyone. For every player to see the same gas,
  each client makes the same emitters from something they all know: a packet of the mod's own, as this mod does.
- **They belong to the level** and go with it. A new `ClientLevel` (another dimension, a rejoin) starts empty; old
  handles then point at nothing.
- **Gas already given off stays** where it is when the emitter moves or goes, and thins by the medium's `decay`.
- The simulation runs ten steps a second. A move or a rate change counts from the next one.
- Emitters are kept while the player has gas turned off (`/volumetrics enabled false`) and feed again when it is on.

### `EmitterShape`: the region an emitter fills

A sealed interface; positions are relative to the anchor.

| Shape | Sits | JSON `type` |
|---|---|---|
| `Disc(float radius, float height)` | An upright cylinder standing on the anchor: gas lying on or rising off a surface | `volumetrics:disc`, `height` defaults to 1 |
| `Sphere(float radius)` | Centred on the anchor | `volumetrics:sphere` |
| `Box(float width, float height, float depth)` | Standing on the anchor, centred on it sideways (`width` along x, `depth` along z) | `volumetrics:box` |

A radius or height of zero or less, or a negative box side, throws `IllegalArgumentException` when the emitter is
made or moved. JSON also caps them (0.05 to 128 for a disc or sphere, to 256 for a box); code does not.

### `Medium`: one kind of gas

`record Medium(MediumParams simulation, Appearance appearance)`. Get one from the synced registry, as above, or
build one in code:

```java
Medium glow = new Medium(
        new MediumParams(-0.5f, 0.3f, 0.05f, 1f),   // gravity, dispersion, decay, max density; no drift
        new Appearance(0xFFB060, 0.3f, 1f, 0.5f, 0.2f, 0.3f, Appearance.Noise.DEFAULT));
```

- `MediumParams` is in `dev.syrval.volumetrics.sim`:
  `(gravity, dispersion, decay, maxDensity, driftX, driftZ)`, or the first four alone for no drift. Its constructor
  refuses non-finite values, a negative dispersion or decay, and a max density of zero or less.
- `Appearance(int color, float opacity, float skyLight, float blockLight, float emissive, float forwardScatter,
  Noise noise)`: `color` is `0xRRGGBB`; the ranges are those of the JSON below, and the constructor throws outside
  them. `Appearance.Noise(scale, speed, strength)`, or `Noise.DEFAULT` (0.12, 0.4, 0.5).
- A medium made in code needs nothing from the server. One from the registry needs the library on the server too:
  without it the server has no `volumetrics:medium` registry, sends none, and reads none of this mod's medium files.
- Media are compared by value (they are records), so two equal ones are one kind of gas.

### `VolumetricsRegistries`

`MEDIUM` and `EMITTER`, the keys of the two datapack registries. Both are loaded with the world and sent to clients,
so the server's datapacks decide what a client has. On 1.21.1: `registryAccess().registry(key)` gives an
`Optional<Registry<T>>`, and `Registry.getOptional(ResourceLocation)` the entry.

`EmitterDefinition`, `Placement` and `forEach(Sink)` are the registries' data and the library's loader glue; a mod
writes emitter JSON rather than using them.

## Datapack files

A gas, `data/<namespace>/volumetrics/medium/<name>.json` (this mod's are in
`data/overgrown_abyss/volumetrics/medium/`):

```json
{
  "simulation": { "gravity": 2.0, "dispersion": 0.35, "decay": 0.03 },
  "appearance": {
    "color": "#DCE6EE",
    "opacity": 0.4,
    "block_light": 0.5,
    "forward_scatter": 0.4,
    "noise": { "scale": 0.12, "speed": 0.6, "strength": 0.6 }
  }
}
```

| Field | Meaning | Range | Default |
|---|---|---|---|
| `simulation.gravity` | Blocks per second. Positive sinks, negative rises | -64 to 64 | required |
| `simulation.dispersion` | Square blocks per second: how fast it spreads from dense to thin | 0 to 64 | required |
| `simulation.decay` | Share lost per second | 0 to 20 | 0 |
| `simulation.max_density` | How much a cell holds before gas piles up beside or above it | 0.01 to 16 | 1 |
| `simulation.drift` | `{ "x": 1.5, "z": 0 }`: blocks per second carried east (`x`) and south (`z`); negative for west and north | -16 to 16 each | none |
| `appearance.color` | `#RRGGBB` | | required |
| `appearance.opacity` | Light through `n` blocks at full density is dimmed to `exp(-opacity * n)` | 0 to 16 | required |
| `appearance.sky_light`, `block_light` | How strongly sky and light sources light it; at 1, as they light a block | 0 to 1 | 1, 0.5 |
| `appearance.emissive` | Light of its own | 0 to 4 | 0 |
| `appearance.forward_scatter` | Glow when looking through it towards the sun or moon | 0 to 4 | 0.3 |
| `appearance.noise` | Wisps: `scale` per block (0 to 4), `speed` in blocks per second (0 to 16), `strength` (0 to 1) | | 0.12, 0.4, 0.5 |

Values a 2-block grid cannot carry in one step (very fast gravity, dispersion or drift) are slowed down, not refused.
The library ships `volumetrics:mist`, `volumetrics:steam` and `volumetrics:smoke`.

An emitter on blocks, `data/<namespace>/volumetrics/emitter/<name>.json`, which needs no code at all:

```json
{
  "medium": "volumetrics:smoke",
  "shape": { "type": "volumetrics:sphere", "radius": 0.6 },
  "offset": [0, 1.2, 0],
  "rate": 2.5,
  "placement": { "type": "volumetrics:block", "blocks": "#minecraft:campfires", "state": { "lit": "true" } }
}
```

`rate` is 0 to 64. `volumetrics:block` is the only placement: an emitter at every block in `blocks` (an id, a list or
a `#tag`) whose state has the properties in `state` (optional), anchored to the middle of the block's floor and moved
by `offset`. Clients find them as they read the world around the camera.

## Limits that matter here

- **Four kinds of gas at once** in the simulated windows; a fifth waits until a kind has no emitter in range and its
  last gas has thinned away. This mod has four media; where all four are in range they take every slot, and the
  library's own gases or another mod's wait. The owner wants the limit lifted in the library (this mod's `SCOPE.md`).
- **The windows end**: gas is simulated and drawn to 256 blocks from the camera each way and 128 up and down, half
  that in fine detail. An emitter beyond them costs next to nothing and holds no slot.
- **Every emitter in range is visited every step**, about 13 ns each in the library's measurements.
- **`remove()` searches a list of all the level's emitters**, so removing thousands at once, as leaving a hole does,
  costs in proportion to their number times the total. Not measured with this mod's 2,300 to 3,200 sources a hole.
- **Gas starts from nothing** when the world loads or the player teleports, and takes time to build up.
- **Gas is as dark as the blocks around it**: lit by the lightmap where it is, so nearly black in an unlit place
  without `emissive`.
- **Definitions are read when the world loads**; `/reload` does not pick up changed medium files.
- **A world datapack that adds media or emitters** makes single player ask for the experimental-settings
  confirmation when the world is opened. The library's own files do not; whether a mod's own data files do, as this
  mod's media are, has not been tried.
- **No emitters from the server yet**, and no placement by biome or structure: what this mod sends, it sends itself.

## Trying things in game

Client-side commands that go through `GasEmitters` as a mod would: `/volumetrics emit <medium> <radius> <rate>
[x y z]`, `/volumetrics move <dx> <dy> <dz>`, `/volumetrics clear` (only what `emit` made). Also
`/volumetrics stats` for the cost, `/volumetrics enabled <true|false>` and `/volumetrics quality <low|medium|high>`.
