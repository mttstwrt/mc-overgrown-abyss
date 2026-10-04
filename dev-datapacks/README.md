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

The mod's own defaults are `spacing` 45, `row_spacing` 20, `row_jitter` 0.4 (neighbouring rows' floors at least 12 apart), `max_lip` 1
(a floor may reach right across the chasm), radius 20 to 50, `min_offset` 0, `max_offset` 0.4.

- `wide-bays`: spacing 40, radius 22 to 55, offset 0.4 to 0.75. Rooms reach deeper into the rock and merge into one
  large cavity with a scalloped edge.
- `small-bays`: spacing 55, row spacing 14, radius 18 to 40, offset 0 to 0.3. Fewer, smaller bays; the slot is more visible.
- `sparse-discs`: spacing 60, row spacing 28, row jitter 0.3. Few discs, each with plenty of room above and below.
- `dense-discs`: spacing 36, row spacing 15, row jitter 0.4. Many discs, overlapping heavily; floors at least 9 apart.
