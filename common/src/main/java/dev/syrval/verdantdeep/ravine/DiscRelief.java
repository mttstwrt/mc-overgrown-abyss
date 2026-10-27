package dev.syrval.verdantdeep.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * The lie of a disc's ground, as part of its {@link DiscTheme}: mounds a player walks over and outcrops that stand in the way.
 * Both are ground put on top of the platform, which keeps its own shape under them (see {@link DiscGround}).
 *
 * @param mounds   gentle rises of the theme's own ground
 * @param outcrops steep lumps of a material of their own
 */
public record DiscRelief(Optional<Mounds> mounds, Optional<Outcrops> outcrops) {
    static final DiscRelief FLAT = new DiscRelief(Optional.empty(), Optional.empty());
    private static final int HASH_BASE = 1_300_000;

    /**
     * Rises in patches that take up {@code cover} of the top, the patches about {@code size} blocks from one to the next. A
     * mound stands a block over the ground at its edge, climbs from there by less than a block for each block across, so
     * that it is walked up without a jump, and stops at {@code height}. How high one gets before that is up to its width:
     * a block and about a tenth of {@code size} in the middle of an ordinary patch, so small patches make low mounds
     * whatever height they are allowed.
     */
    public record Mounds(int height, float size, float cover) {
        // A smooth value changes by at most 2.13 over one of its sizes; spreading a mound's rise over a little more than that
        // keeps it under a block for a block.
        private static final double GENTLE = 2.2;
        // Named because the owner's dials hold what they turn to the same limit (see Dials); so with an outcrop's.
        static final float LEAST_COVER = 0.05F;
        static final Codec<Mounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 8).fieldOf("height").forGetter(Mounds::height),
                Codec.floatRange(4, 128).fieldOf("size").forGetter(Mounds::size),
                Codec.floatRange(LEAST_COVER, 1).fieldOf("cover").forGetter(Mounds::cover)
        ).apply(i, Mounds::new));

        /** How far a mound stands over a column of the {@code index}-th disc of a hole, in blocks and parts of one; 0 where there is none. */
        double riseAt(long hash, int index, int x, int z) {
            double over = RavineCells.smoothOver(hash, HASH_BASE + 2 * index, x, z, size) - RavineCells.levelBelow(1 - cover);
            return over <= 0 ? 0 : Math.min(height, 1 + over * size / GENTLE);
        }
    }

    /**
     * Lumps of {@code block} in patches that take up {@code cover} of the top, about {@code size} blocks from one to the next,
     * a block high at their edges and {@code height} in their middles. Their sides are as steep as they come, so an outcrop
     * is something to go round or climb.
     */
    public record Outcrops(int height, float size, float cover, BlockStateProvider block) {
        // The share of the way from an outcrop's edge to the highest a smooth value goes at which it reaches its full height.
        private static final double FULL = 0.35;
        static final float LEAST_COVER = 0.01F;
        static final Codec<Outcrops> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 12).fieldOf("height").forGetter(Outcrops::height),
                Codec.floatRange(4, 128).fieldOf("size").forGetter(Outcrops::size),
                Codec.floatRange(LEAST_COVER, 1).fieldOf("cover").forGetter(Outcrops::cover),
                BlockStateProvider.CODEC.fieldOf("block").forGetter(Outcrops::block)
        ).apply(i, Outcrops::new));

        /** How far an outcrop stands over a column of the {@code index}-th disc of a hole; 0 where there is none. */
        double riseAt(long hash, int index, int x, int z) {
            double edge = RavineCells.levelBelow(1 - cover);
            double over = RavineCells.smoothOver(hash, HASH_BASE + 2 * index + 1, x, z, size) - edge;
            return over <= 0 ? 0 : 1 + (height - 1) * Math.min(1, over / (FULL * (1 - edge)));
        }
    }

    static final Codec<DiscRelief> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mounds.CODEC.optionalFieldOf("mounds").forGetter(DiscRelief::mounds),
            Outcrops.CODEC.optionalFieldOf("outcrops").forGetter(DiscRelief::outcrops)
    ).apply(i, DiscRelief::new));

    /** The most blocks the ground may stand over the platform anywhere. */
    int highest() {
        return Math.max(mounds.map(Mounds::height).orElse(0), outcrops.map(Outcrops::height).orElse(0));
    }
}
