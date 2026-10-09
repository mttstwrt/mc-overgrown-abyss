package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

/** What a level's ravines do to the world around them, as seen by noise fill, biome assignment, disc materials, growth and structures. */
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
        public Optional<Holder<Biome>> biomeAt(int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {}

        @Override
        public void forEachDiscWater(int minX, int minZ, DiscWaterSink sink) {}

        @Override
        public void forEachGrowth(int minX, int minZ, DiscGrowthSink sink) {}

        @Override
        public void forEachInheritingDisc(int minX, int minZ, DiscPlotSink sink) {}

        @Override
        public List<RuinSite> ruinsIn(int minX, int minZ) {
            return List.of();
        }
    };

    /** Whether the column is touched by an active ravine, including wall noise and falloff. */
    boolean contains(int x, int z);

    /** Whether the point lies in the carved volume itself, as opposed to natural terrain next to it. */
    boolean isOpen(DensityFunction.FunctionContext context);

    /**
     * The biome a ravine gives this point in place of the level's own: that of the disc it belongs to, if the disc's theme has
     * one, or else the cavern's, if it is inside a cavern that has one configured.
     */
    Optional<Holder<Biome>> biomeAt(int x, int y, int z);

    /**
     * Calls {@code sink} with every block that takes a disc's material in the chunk whose lowest corner is {@code (minX, minZ)},
     * between {@code minY} and {@code maxY} (exclusive).
     */
    void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink);

    /** Calls {@code sink} with every column where a disc holds water in the chunk whose lowest corner is {@code (minX, minZ)}. */
    void forEachDiscWater(int minX, int minZ, DiscWaterSink sink);

    /** Calls {@code sink} with every place where a disc's theme grows something in the chunk whose lowest corner is {@code (minX, minZ)}. */
    void forEachGrowth(int minX, int minZ, DiscGrowthSink sink);

    /**
     * Calls {@code sink} with every disc whose theme inherits a biome's features and whose space reaches into the chunk whose
     * lowest corner is {@code (minX, minZ)}.
     */
    void forEachInheritingDisc(int minX, int minZ, DiscPlotSink sink);

    /** The ruins that stand on discs with their middle in the chunk whose lowest corner is {@code (minX, minZ)}. */
    List<RuinSite> ruinsIn(int minX, int minZ);

    /** One bound carve, the biome (if any) resolved for its cavern, and the biomes of its disc themes that exist in the level. */
    record Region(RavineCarve carve, Optional<Holder<Biome>> cavernBiome, Map<ResourceKey<Biome>, Holder<Biome>> discBiomes) {
        public Region {
            discBiomes = Map.copyOf(discBiomes);
        }
    }

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
            public Optional<Holder<Biome>> biomeAt(int x, int y, int z) {
                for (Region region : copy) {
                    // A disc standing in the cavern's airspace keeps its own biome there.
                    Optional<Holder<Biome>> disc = region.carve().discBiomeAt(x, y, z).map(region.discBiomes()::get);
                    if (disc.isPresent()) {
                        return disc;
                    }
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

            @Override
            public void forEachDiscWater(int minX, int minZ, DiscWaterSink sink) {
                for (Region region : copy) {
                    region.carve().forEachDiscWater(minX, minZ, sink);
                }
            }

            @Override
            public void forEachGrowth(int minX, int minZ, DiscGrowthSink sink) {
                for (Region region : copy) {
                    region.carve().forEachGrowth(minX, minZ, sink);
                }
            }

            @Override
            public void forEachInheritingDisc(int minX, int minZ, DiscPlotSink sink) {
                for (Region region : copy) {
                    region.carve().forEachInheritingDisc(minX, minZ, sink);
                }
            }

            @Override
            public List<RuinSite> ruinsIn(int minX, int minZ) {
                var found = new ArrayList<RuinSite>();
                for (Region region : copy) {
                    found.addAll(region.carve().ruinsIn(minX, minZ));
                }
                return found;
            }
        };
    }
}
