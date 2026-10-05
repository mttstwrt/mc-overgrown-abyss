package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

/** What a level's ravines do to the world around them, as seen by noise fill, biome assignment, disc materials and structures. */
public interface RavineFootprint {
    RavineFootprint NONE = new RavineFootprint() {
        @Override
        public boolean contains(int x, int z) {
            return false;
        }

        @Override
        public boolean isOpen(DensityFunction.FunctionContext context) {
            return false;
        }

        @Override
        public Optional<Holder<Biome>> cavernBiome(int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {}
    };

    /** Whether the column is touched by an active ravine, including wall noise and falloff. */
    boolean contains(int x, int z);

    /** Whether the point lies in the carved volume itself, as opposed to natural terrain next to it. */
    boolean isOpen(DensityFunction.FunctionContext context);

    /** The biome the cavern takes at this point, if it is inside a cavern that has one configured. */
    Optional<Holder<Biome>> cavernBiome(int x, int y, int z);

    /**
     * Calls {@code sink} with every block that takes a disc's material in the chunk whose lowest corner is {@code (minX, minZ)},
     * between {@code minY} and {@code maxY} (exclusive).
     */
    void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink);

    /** One bound carve and the biome (if any) resolved for its cavern. */
    record Region(RavineCarve carve, Optional<Holder<Biome>> cavernBiome) {}

    static RavineFootprint of(List<Region> regions) {
        List<Region> copy = List.copyOf(regions);
        if (copy.isEmpty()) {
            return NONE;
        }
        // These run for every open block or biome cell during generation, so avoid streams.
        return new RavineFootprint() {
            @Override
            public boolean contains(int x, int z) {
                for (Region region : copy) {
                    if (region.carve().isInFootprint(x, z)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean isOpen(DensityFunction.FunctionContext context) {
                for (Region region : copy) {
                    if (region.carve().compute(context) <= 0) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Optional<Holder<Biome>> cavernBiome(int x, int y, int z) {
                for (Region region : copy) {
                    if (region.cavernBiome().isPresent() && region.carve().isCavern(x, y, z)) {
                        return region.cavernBiome();
                    }
                }
                return Optional.empty();
            }

            @Override
            public void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
                for (Region region : copy) {
                    region.carve().forEachDiscBlock(minX, minZ, minY, maxY, sink);
                }
            }
        };
    }
}
