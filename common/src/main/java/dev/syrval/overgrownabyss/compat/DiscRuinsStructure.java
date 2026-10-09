package dev.syrval.overgrownabyss.compat;

import com.mojang.serialization.MapCodec;
import dev.syrval.overgrownabyss.ravine.RuinSite;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The ruins on a level's discs. A chunk's start holds one piece for every ruin whose middle is in that chunk. Which ruins
 * those are, where they stand and of what kind is decided with the discs themselves (see {@code DiscRuinSites}); this only
 * turns each site into a piece of its pool: one element, turned any of four ways, with the middle of its lowest layer on
 * the site. Nothing is joined on to a piece, so a jigsaw block in it is left as its final state.
 *
 * <p>Vanilla's jigsaw placement is not used: it turns the first piece about its corner, or about a jigsaw block that the
 * vanilla templates used here do not have, and a ruin has to stay inside the round of ground found for it.
 */
public final class DiscRuinsStructure extends Structure {
    public static final MapCodec<DiscRuinsStructure> CODEC = simpleCodec(DiscRuinsStructure::new);
    public static final StructureType<DiscRuinsStructure> TYPE = () -> CODEC;

    private DiscRuinsStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var footprint = ((RavineFootprintHolder) (Object) context.randomState()).overgrownAbyss$footprint();
        ChunkPos chunk = context.chunkPos();
        List<RuinSite> sites = footprint.ruinsIn(chunk.getMinBlockX(), chunk.getMinBlockZ());
        if (sites.isEmpty()) {
            return Optional.empty();
        }
        Registry<StructureTemplatePool> pools = context.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        List<StructurePiece> pieces = sites.stream()
                .flatMap(site -> pieceAt(site, pools, context.structureTemplateManager()).stream())
                .toList();
        if (pieces.isEmpty()) {
            return Optional.empty();
        }
        // The start's own position only decides which biome the structure's biome list is asked about.
        RuinSite first = sites.getFirst();
        return Optional.of(new GenerationStub(new BlockPos(first.x(), first.y(), first.z()), builder -> pieces.forEach(builder::addPiece)));
    }

    // A pool that does not exist was warned of when the level loaded (see RavineDensityHook).
    private static Optional<StructurePiece> pieceAt(RuinSite site, Registry<StructureTemplatePool> pools, StructureTemplateManager templates) {
        return pools.getHolder(site.pool()).flatMap(pool -> {
            RandomSource draws = RandomSource.create(site.seed());
            StructurePoolElement element = pool.value().getRandomTemplate(draws);
            if (element == EmptyPoolElement.INSTANCE) {
                return Optional.empty();
            }
            Rotation rotation = Rotation.getRandom(draws);
            // A template is turned about its corner, so where the turned piece lies is found first and its corner then put
            // where that brings the middle of the piece onto the site.
            BoundingBox turned = element.getBoundingBox(templates, BlockPos.ZERO, rotation);
            BlockPos middle = turned.getCenter();
            BlockPos corner = new BlockPos(site.x() - middle.getX(), site.y() - turned.minY(), site.z() - middle.getZ());
            return Optional.of(new PoolElementStructurePiece(
                    templates, element, corner, element.getGroundLevelDelta(), rotation,
                    element.getBoundingBox(templates, corner, rotation), JigsawStructure.DEFAULT_LIQUID_SETTINGS));
        });
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
