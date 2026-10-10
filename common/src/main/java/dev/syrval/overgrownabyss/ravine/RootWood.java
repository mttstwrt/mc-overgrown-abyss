package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * What a root is made of (see {@link RootSettings}). Blocks are vanilla block-state providers, as in a {@link DiscPalette}.
 * A wood with bark on every face, such as {@code jungle_wood}, needs no direction, which a root that winds has none of.
 *
 * @param bark the root's outermost block all round
 * @param core the rest of it; the bark's block where this is left out
 */
public record RootWood(BlockStateProvider bark, Optional<BlockStateProvider> core) {
    static final Codec<RootWood> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockStateProvider.CODEC.fieldOf("bark").forGetter(RootWood::bark),
            BlockStateProvider.CODEC.optionalFieldOf("core").forGetter(RootWood::core)
    ).apply(i, RootWood::new));

    // A block is bark if its centre is within this of the root's surface, which is exactly the outermost block.
    private static final double BARK = 1;

    /** The material of a block whose centre is {@code inside} blocks in from the root's surface. */
    BlockStateProvider blockAt(double inside) {
        return inside > BARK ? core.orElse(bark) : bark;
    }
}
