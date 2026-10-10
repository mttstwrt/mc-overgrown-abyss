package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * The falls of a cone: water that leaves a disc over its rim, lands in a pool on a disc below, and may go on from there. A
 * hole has a number of them, not each disc a chance of one, so that they stay something to come upon. Each hole finds its own
 * from its hash (see {@link DiscFalls}), and the game runs the water.
 *
 * @param count      how many falls a hole starts; each may go on down by {@code chain}
 * @param byHeight   how much more or less likely a fall is to start on a disc with the disc's height in the hole
 * @param width      blocks of water side by side at an ordinary fall's lip
 * @param minDrop    the least a fall may drop, in blocks of open air; a place with less under it has no fall
 * @param maxPerDisc the most falls that may start on one disc
 * @param pool       the pool a fall lands in
 * @param chain      chance that a fall which landed in a pool goes on over that disc's rim
 * @param maxSteps   the most falls one after another, the first counted
 * @param curtain    a wider fall that some of the larger discs get in place of an ordinary one
 * @param springs    water that comes out of the wall behind a disc
 */
public record FallSettings(
        InclusiveRange<Integer> count, DiscTheme.Ramp byHeight, int width, int minDrop, int maxPerDisc, Pool pool, float chain, int maxSteps,
        Optional<Curtain> curtain, Optional<Springs> springs) {

    /**
     * @param radius blocks from the middle of a pool to its edge; a wide fall's pool is wider
     * @param depth  how deep its middle is; its edge is 1 deep
     */
    public record Pool(int radius, int depth) {
        static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(2, 8).fieldOf("radius").forGetter(Pool::radius),
                Codec.intRange(1, DiscWater.MAX_DEPTH).fieldOf("depth").forGetter(Pool::depth)
        ).apply(i, Pool::new));
    }

    /**
     * @param chance    chance that a fall starting on a disc large enough is a curtain
     * @param width     blocks of water side by side at its lip
     * @param minRadius the least radius of a disc that may have one
     */
    public record Curtain(float chance, InclusiveRange<Integer> width, float minRadius) {
        static final Codec<Curtain> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Curtain::chance),
                InclusiveRange.codec(Codec.intRange(2, 9)).fieldOf("width").forGetter(Curtain::width),
                Codec.floatRange(0, 256).fieldOf("min_radius").forGetter(Curtain::minRadius)
        ).apply(i, Curtain::new));
    }

    /**
     * Springs in the hole's wall where a disc lies in it: water comes out of the rock some way over the disc's ground, on its
     * side away from the hole's middle, and falls into a small pool at the foot of the wall.
     *
     * @param count   how many a hole has, where it has discs with wall behind them
     * @param height  blocks over the disc's rim that a spring comes out
     * @param feature the feature that makes one, given a block in the wall: it has to ask for rock round itself and one open
     *                side, as vanilla's spring does, since the wall is whatever the terrain left there
     */
    public record Springs(InclusiveRange<Integer> count, InclusiveRange<Integer> height, ResourceKey<ConfiguredFeature<?, ?>> feature) {
        static final Codec<Springs> CODEC = RecordCodecBuilder.create(i -> i.group(
                InclusiveRange.codec(Codec.intRange(0, 16)).fieldOf("count").forGetter(Springs::count),
                InclusiveRange.codec(Codec.intRange(2, 16)).fieldOf("height").forGetter(Springs::height),
                ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(Springs::feature)
        ).apply(i, Springs::new));
    }

    static final Codec<FallSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            InclusiveRange.codec(Codec.intRange(0, 16)).fieldOf("count").forGetter(FallSettings::count),
            DiscTheme.Ramp.BY_HEIGHT.optionalFieldOf("by_height", DiscTheme.Ramp.EVEN).forGetter(FallSettings::byHeight),
            Codec.intRange(1, 2).optionalFieldOf("width", 1).forGetter(FallSettings::width),
            Codec.intRange(2, 256).optionalFieldOf("min_drop", 10).forGetter(FallSettings::minDrop),
            Codec.intRange(1, 4).optionalFieldOf("max_per_disc", 1).forGetter(FallSettings::maxPerDisc),
            Pool.CODEC.fieldOf("pool").forGetter(FallSettings::pool),
            Codec.floatRange(0, 1).optionalFieldOf("chain", 0F).forGetter(FallSettings::chain),
            Codec.intRange(1, 8).optionalFieldOf("max_steps", 4).forGetter(FallSettings::maxSteps),
            Curtain.CODEC.optionalFieldOf("curtain").forGetter(FallSettings::curtain),
            Springs.CODEC.optionalFieldOf("springs").forGetter(FallSettings::springs)
    ).apply(i, FallSettings::new));
}
