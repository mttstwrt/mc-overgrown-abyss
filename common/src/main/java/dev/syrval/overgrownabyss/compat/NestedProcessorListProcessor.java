package dev.syrval.overgrownabyss.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jetbrains.annotations.Nullable;

/** Runs another processor list in place, so the city's reskin table is written once and shared by every piece list. */
public final class NestedProcessorListProcessor extends StructureProcessor {
    public static final MapCodec<NestedProcessorListProcessor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StructureProcessorType.LIST_CODEC.fieldOf("list").forGetter(processor -> processor.list)
    ).apply(i, NestedProcessorListProcessor::new));
    public static final StructureProcessorType<NestedProcessorListProcessor> TYPE = () -> CODEC;

    private final Holder<StructureProcessorList> list;

    private NestedProcessorListProcessor(Holder<StructureProcessorList> list) {
        this.list = list;
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
        StructureBlockInfo result = current;
        for (StructureProcessor processor : list.value().list()) {
            result = processor.processBlock(level, offset, pos, original, result, settings);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE;
    }
}
