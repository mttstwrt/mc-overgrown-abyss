package dev.syrval.overgrownabyss.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.syrval.overgrownabyss.ravine.RavineBounds;
import dev.syrval.overgrownabyss.ravine.RavineCarve;
import dev.syrval.overgrownabyss.ravine.RavineCell;
import dev.syrval.overgrownabyss.ravine.RavineCells;
import dev.syrval.overgrownabyss.ravine.RavineSettings;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * A jigsaw city started at the centre of the ravine in the current cell, standing on the cavern floor. Vanilla's
 * {@link JigsawStructure} is final and picks its start from a height provider and heightmap, so this wraps
 * {@link JigsawPlacement} directly with a start height relative to the resolved ravine floor.
 */
public final class RavineCityStructure extends Structure {
    private static final int TERRAIN_ADAPTATION_REACH = 12;

    public static final MapCodec<RavineCityStructure> CODEC = RecordCodecBuilder.<RavineCityStructure>mapCodec(i -> i.group(
            settingsCodec(i),
            DensityFunction.CODEC.fieldOf("ravine").forGetter(s -> s.ravine),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
            Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.maxDepth),
            Codec.intRange(0, 256).fieldOf("start_height_above_floor").forGetter(s -> s.startHeightAboveFloor),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
            Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(s -> s.poolAliases)
    ).apply(i, RavineCityStructure::new)).validate(RavineCityStructure::validate);
    public static final StructureType<RavineCityStructure> TYPE = () -> CODEC;

    private final Holder<DensityFunction> ravine;
    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int maxDepth;
    private final int startHeightAboveFloor;
    private final int maxDistanceFromCenter;
    private final List<PoolAliasBinding> poolAliases;

    private RavineCityStructure(
            StructureSettings settings,
            Holder<DensityFunction> ravine,
            Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName,
            int maxDepth,
            int startHeightAboveFloor,
            int maxDistanceFromCenter,
            List<PoolAliasBinding> poolAliases) {
        super(settings);
        this.ravine = ravine;
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeightAboveFloor = startHeightAboveFloor;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.poolAliases = List.copyOf(poolAliases);
    }

    // Same rule as vanilla JigsawStructure: pieces plus terrain adaptation must stay within 128 blocks.
    private static DataResult<RavineCityStructure> validate(RavineCityStructure structure) {
        int reach = structure.terrainAdaptation() == TerrainAdjustment.NONE
                ? 0
                : TERRAIN_ADAPTATION_REACH;
        return structure.maxDistanceFromCenter + reach > 128
                ? DataResult.error(() -> "Structure size including terrain adaptation must not exceed 128")
                : DataResult.success(structure);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        RavineSettings settings = RavineCarve.settingsOf(ravine.value());
        var chunk = context.chunkPos();
        Optional<RavineCell> cell = RavineCells.containing(context.seed(), settings, chunk.getMinBlockX(), chunk.getMinBlockZ());
        if (cell.isEmpty()) {
            return Optional.empty();
        }
        int x = (int) Math.floor(cell.get().centreX());
        int z = (int) Math.floor(cell.get().centreZ());
        // Only levels whose noise settings were carved have a cavern to stand in.
        var footprint = ((RavineFootprintHolder) (Object) context.randomState()).overgrownAbyss$footprint();
        if (!footprint.contains(x, z)) {
            return Optional.empty();
        }
        Optional<RavineBounds> bounds = settings.resolveBounds(new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        if (bounds.isEmpty()) {
            return Optional.empty();
        }
        BlockPos start = new BlockPos(x, bounds.get().floorY() + startHeightAboveFloor, z);
        return JigsawPlacement.addPieces(
                context,
                startPool,
                startJigsawName,
                maxDepth,
                start,
                false,
                Optional.empty(),
                maxDistanceFromCenter,
                PoolAliasLookup.create(poolAliases, start, context.seed()),
                JigsawStructure.DEFAULT_DIMENSION_PADDING,
                JigsawStructure.DEFAULT_LIQUID_SETTINGS);
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
