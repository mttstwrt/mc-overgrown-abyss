package dev.syrval.overgrownabyss.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.syrval.overgrownabyss.ravine.RavineCarve;
import dev.syrval.overgrownabyss.ravine.RavineCells;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * One structure-start chunk per ravine cell, at the ravine centre. It extends the vanilla random-spread placement
 * only because {@code /locate} and map lookups special-case that class; the grid is the ravine cell grid, not the
 * spacing and separation fields, which are placeholders. The ravine settings are read lazily because the density
 * function may not be loaded yet when this codec runs.
 */
@SuppressWarnings("deprecation") // ExclusionZone is deprecated by Mojang but is part of the shared placement codec.
public final class RavineCentrePlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<RavineCentrePlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i)
            .and(DensityFunction.CODEC.fieldOf("ravine").forGetter(placement -> placement.ravine))
            .apply(i, RavineCentrePlacement::new));
    public static final StructurePlacementType<RavineCentrePlacement> TYPE = () -> CODEC;

    private final Holder<DensityFunction> ravine;

    private RavineCentrePlacement(
            Vec3i locateOffset,
            FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<ExclusionZone> exclusionZone,
            Holder<DensityFunction> ravine) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, 2, 1, RandomSpreadType.LINEAR);
        this.ravine = ravine;
    }

    /** Chunks per ravine cell; {@code /locate} steps its search ring by this. */
    @Override
    public int spacing() {
        return RavineCarve.settingsOf(ravine.value()).cellSize() / 16;
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        return RavineCells.centreChunk(seed, RavineCarve.settingsOf(ravine.value()), chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type() {
        return TYPE;
    }
}
