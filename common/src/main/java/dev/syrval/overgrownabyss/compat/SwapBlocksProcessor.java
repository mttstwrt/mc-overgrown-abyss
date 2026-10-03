package dev.syrval.overgrownabyss.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces blocks by weighted alternatives while keeping every block-state property the new block shares with the old
 * one (stair facing, slab type, wall sides, log axis, hanging, waterlogged). Vanilla's rule processor replaces the
 * whole state, which would reset all of those. The pick is seeded from the block position, so a structure placed twice
 * at the same spot looks the same.
 */
public final class SwapBlocksProcessor extends StructureProcessor {
    public static final MapCodec<SwapBlocksProcessor> CODEC = RecordCodecBuilder.<SwapBlocksProcessor>mapCodec(i -> i.group(
            Swap.CODEC.listOf().fieldOf("swaps").forGetter(processor -> processor.swaps)
    ).apply(i, SwapBlocksProcessor::new)).validate(SwapBlocksProcessor::validate);
    public static final StructureProcessorType<SwapBlocksProcessor> TYPE = () -> CODEC;

    private final List<Swap> swaps;
    private final Map<Block, Swap> byBlock = new HashMap<>();

    private SwapBlocksProcessor(List<Swap> swaps) {
        this.swaps = List.copyOf(swaps);
        this.swaps.forEach(swap -> byBlock.putIfAbsent(swap.from(), swap));
    }

    private static DataResult<SwapBlocksProcessor> validate(SwapBlocksProcessor processor) {
        return processor.byBlock.size() == processor.swaps.size()
                ? DataResult.success(processor)
                : DataResult.error(() -> "Each block may only be swapped by one entry");
    }

    @Nullable
    @Override
    public StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos offset,
            BlockPos pos,
            StructureBlockInfo original,
            StructureBlockInfo current,
            StructurePlaceSettings settings) {
        Swap swap = byBlock.get(current.state().getBlock());
        if (swap == null) {
            return current;
        }
        BlockState state = withSharedProperties(current.state(), swap.pick(settings.getRandom(current.pos())).defaultBlockState());
        // The old block's data (sculk sensor listener, ...) means nothing to a block that has no block entity.
        return new StructureBlockInfo(current.pos(), state, state.hasBlockEntity() ? current.nbt() : null);
    }

    private static BlockState withSharedProperties(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property<?> property : from.getProperties()) {
            result = copy(from, result, property);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE;
    }

    private record Output(Block block, int weight) {
        static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(Output::block),
                Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(Output::weight)
        ).apply(i, Output::new));
    }

    private record Swap(Block from, List<Output> to) {
        static final Codec<Swap> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("from").forGetter(Swap::from),
                Output.CODEC.listOf().fieldOf("to").forGetter(Swap::to)
        ).apply(i, Swap::new));

        Swap {
            if (to.isEmpty()) {
                throw new IllegalArgumentException("A swap needs at least one output");
            }
            to = List.copyOf(to);
        }

        Block pick(RandomSource random) {
            int roll = random.nextInt(to.stream().mapToInt(Output::weight).sum());
            for (Output output : to) {
                roll -= output.weight();
                if (roll < 0) {
                    return output.block();
                }
            }
            throw new IllegalStateException("unreachable: roll is below the total weight");
        }
    }
}
