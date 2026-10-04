# Testing datapacks

Each folder here is a datapack that overrides `overgrown_abyss:ravine/carve` with different values, for trying shape
settings without rebuilding the mod. They are not shipped with the mod.

## Install

1. Open the world's save folder (`saves/<world name>/`) and go into its `datapacks` folder.
2. Copy the pack folder (for example `stronger-tiers`) there, or the zip made from it (`python3 make-zips.py`).
3. Start the world. Only chunks that have not generated yet use the new values, so use a new world, or fly to unexplored
   ground.

Changing `max_shift` changes how far each ravine reaches, which moves ravines within their cells, so a ravine you were
looking at will not be in the same place in a new world.

## Packs

- `stronger-tiers`: `max_count` 4 (was 3), `max_shift` 1.2 (was 0.9), `min_width` 0.4 (was 0.5), `max_width` 1.15
  (was 1.1), `ramp` 2 (was 4), `min_size` 0.25 (was 0.3). Bolder zigzags and sharper shelves.
