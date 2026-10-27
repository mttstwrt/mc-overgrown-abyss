package dev.syrval.verdantdeep.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

/** What a level's ravines do to the world around them, as seen by noise fill, biome assignment, disc materials, roots, growth and structures. */
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
        public Optional<CurseSettings> curse() {
            return Optional.empty();
        }

        @Override
        public OptionalDouble depthShareIn(int x, int y, int z, double margin) {
            return OptionalDouble.empty();
        }

        @Override
        public void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {}

        @Override
        public void forEachMoundBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {}

        @Override
        public void forEachDiscWater(int minX, int minZ, DiscWaterSink sink) {}

        @Override
        public void forEachFallWater(int minX, int minZ, DiscFallSink sink) {}

        @Override
        public void forEachSpring(int minX, int minZ, DiscSpringSink sink) {}

        @Override
        public void forEachRootBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {}

        @Override
        public void forEachGrowth(int minX, int minZ, DiscGrowthSink sink) {}

        @Override
        public void forEachRootGrowth(int minX, int minZ, int minY, int maxY, DiscGrowthSink sink) {}

        @Override
        public void forEachInheritingDisc(int minX, int minZ, DiscPlotSink sink) {}

        @Override
        public List<RuinSite> sitesIn(DiscSites which, int minX, int minZ) {
            return List.of();
        }

        @Override
        public List<GasNear> gasNear(double x, double z, double reach) {
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

    /** The curse of descent the level's holes carry, if their settings have one. */
    Optional<CurseSettings> curse();

    /** How far down a hole a point is, 0 at its mouth to 1 at its floor, or empty if it is outside every hole by more than {@code margin}. */
    OptionalDouble depthShareIn(int x, int y, int z, double margin);

    /**
     * Calls {@code sink} with every block that takes a disc's material in the chunk whose lowest corner is {@code (minX, minZ)},
     * between {@code minY} and {@code maxY} (exclusive).
     */
    void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink);

    /**
     * Calls {@code sink} with every block of ground that stands over a disc's platform in the chunk whose lowest corner is
     * {@code (minX, minZ)}, between {@code minY} and {@code maxY} (exclusive). The blocks are for open air only.
     */
    void forEachMoundBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink);

    /** Calls {@code sink} with every column where a disc holds water in the chunk whose lowest corner is {@code (minX, minZ)}. */
    void forEachDiscWater(int minX, int minZ, DiscWaterSink sink);

    /** Calls {@code sink} with every column where a fall's still water lies in the chunk whose lowest corner is {@code (minX, minZ)}. */
    void forEachFallWater(int minX, int minZ, DiscFallSink sink);

    /** Calls {@code sink} with every spring whose first place is in the chunk whose lowest corner is {@code (minX, minZ)}. */
    void forEachSpring(int minX, int minZ, DiscSpringSink sink);

    /**
     * Calls {@code sink} with every block that is one of a hole's roots in the chunk whose lowest corner is {@code (minX, minZ)},
     * between {@code minY} and {@code maxY} (exclusive).
     */
    void forEachRootBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink);

    /** Calls {@code sink} with every place where a disc's theme grows something in the chunk whose lowest corner is {@code (minX, minZ)}. */
    void forEachGrowth(int minX, int minZ, DiscGrowthSink sink);

    /**
     * Calls {@code sink} with every place under a root where a hole's roots grow something in the chunk whose lowest corner is
     * {@code (minX, minZ)}, between {@code minY} and {@code maxY} (exclusive).
     */
    void forEachRootGrowth(int minX, int minZ, int minY, int maxY, DiscGrowthSink sink);

    /**
     * Calls {@code sink} with every disc whose theme inherits a biome's features and whose space reaches into the chunk whose
     * lowest corner is {@code (minX, minZ)}.
     */
    void forEachInheritingDisc(int minX, int minZ, DiscPlotSink sink);

    /** The ruins or the troves that stand on discs with their middle in the chunk whose lowest corner is {@code (minX, minZ)}. */
    List<RuinSite> sitesIn(DiscSites which, int minX, int minZ);

    /** The gas of each hole whose middle is within {@code reach} blocks of the column {@code (x, z)}; see {@link HoleGas}. */
    List<GasNear> gasNear(double x, double z, double reach);

    /**
     * What the hole of the cell containing {@code (x, z)} holds, with what each piece holds given by {@code held}; empty where
     * the cell has no hole.
     */
    default Optional<ChasmStats> statsAt(int x, int z, Function<RuinSite, ChasmStats.Containers> held) {
        return Optional.empty();
    }

    /** The gas of one hole, the id a client knows the hole by, and the column of the hole's middle. */
    record GasNear(long hole, double x, double z, HoleGas gas) {}

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
            public Optional<ChasmStats> statsAt(int x, int z, Function<RuinSite, ChasmStats.Containers> held) {
                for (Region region : copy) {
                    Optional<ChasmStats> stats = region.carve().statsAt(x, z, held);
                    if (stats.isPresent()) {
                        return stats;
                    }
                }
                return Optional.empty();
            }

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
            public Optional<CurseSettings> curse() {
                return copy.get(0).carve().settings().curse();
            }

            @Override
            public OptionalDouble depthShareIn(int x, int y, int z, double margin) {
                for (Region region : copy) {
                    OptionalDouble share = region.carve().depthShareIn(x, y, z, margin);
                    if (share.isPresent()) {
                        return share;
                    }
                }
                return OptionalDouble.empty();
            }

            @Override
            public void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
                for (Region region : copy) {
                    region.carve().forEachDiscBlock(minX, minZ, minY, maxY, sink);
                }
            }

            @Override
            public void forEachMoundBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
                for (Region region : copy) {
                    region.carve().forEachMoundBlock(minX, minZ, minY, maxY, sink);
                }
            }

            @Override
            public void forEachDiscWater(int minX, int minZ, DiscWaterSink sink) {
                for (Region region : copy) {
                    region.carve().forEachDiscWater(minX, minZ, sink);
                }
            }

            @Override
            public void forEachFallWater(int minX, int minZ, DiscFallSink sink) {
                for (Region region : copy) {
                    region.carve().forEachFallWater(minX, minZ, sink);
                }
            }

            @Override
            public void forEachSpring(int minX, int minZ, DiscSpringSink sink) {
                for (Region region : copy) {
                    region.carve().forEachSpring(minX, minZ, sink);
                }
            }

            @Override
            public void forEachRootBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
                for (Region region : copy) {
                    region.carve().forEachRootBlock(minX, minZ, minY, maxY, sink);
                }
            }

            @Override
            public void forEachGrowth(int minX, int minZ, DiscGrowthSink sink) {
                for (Region region : copy) {
                    region.carve().forEachGrowth(minX, minZ, sink);
                }
            }

            @Override
            public void forEachRootGrowth(int minX, int minZ, int minY, int maxY, DiscGrowthSink sink) {
                for (Region region : copy) {
                    region.carve().forEachRootGrowth(minX, minZ, minY, maxY, sink);
                }
            }

            @Override
            public void forEachInheritingDisc(int minX, int minZ, DiscPlotSink sink) {
                for (Region region : copy) {
                    region.carve().forEachInheritingDisc(minX, minZ, sink);
                }
            }

            @Override
            public List<RuinSite> sitesIn(DiscSites which, int minX, int minZ) {
                var found = new ArrayList<RuinSite>();
                for (Region region : copy) {
                    found.addAll(region.carve().sitesIn(which, minX, minZ));
                }
                return found;
            }

            @Override
            public List<GasNear> gasNear(double x, double z, double reach) {
                var found = new ArrayList<GasNear>();
                for (Region region : copy) {
                    found.addAll(region.carve().gasNear(x, z, reach));
                }
                return found;
            }
        };
    }
}
