package dev.syrval.overgrownabyss.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * A structure start in every chunk that holds the middle of a ruin on a disc. It extends the vanilla random-spread placement
 * only because {@code /locate} special-cases that class: the grid is every chunk, each its own candidate, so the search asks
 * the chunks round the player one ring after another, and the spacing and separation fields are placeholders.
 */
@SuppressWarnings("deprecation") // ExclusionZone is deprecated by Mojang but is part of the shared placement codec.
public final class DiscRuinsPlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<DiscRuinsPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i).apply(i, DiscRuinsPlacement::new));
    public static final StructurePlacementType<DiscRuinsPlacement> TYPE = () -> CODEC;

    private DiscRuinsPlacement(
            Vec3i locateOffset,
            StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<StructurePlacement.ExclusionZone> exclusionZone) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, 1, 0, RandomSpreadType.LINEAR);
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        return new ChunkPos(chunkX, chunkZ);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        var footprint = ((RavineFootprintHolder) (Object) state.randomState()).overgrownAbyss$footprint();
        return !footprint.ruinsIn(SectionPos.sectionToBlockCoord(chunkX), SectionPos.sectionToBlockCoord(chunkZ)).isEmpty();
    }

    @Override
    public StructurePlacementType<?> type() {
        return TYPE;
    }
}
