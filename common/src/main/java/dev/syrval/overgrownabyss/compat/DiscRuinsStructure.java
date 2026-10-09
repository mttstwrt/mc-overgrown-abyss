package dev.syrval.overgrownabyss.compat;

import com.mojang.serialization.MapCodec;
import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import dev.syrval.overgrownabyss.ravine.RuinSite;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The ruins on a level's discs. A chunk's start holds one piece for every ruin whose middle is in that chunk. Which ruins
 * those are, where they stand and which element of which pool each is, is decided with the discs themselves (see
 * {@code DiscRuinSites}); this only turns each site into that piece, turned any of four ways, with the middle of its lowest
 * layer on the site. Nothing is joined on to a piece, so a jigsaw block in it is left as its final state.
 *
 * <p>Vanilla's jigsaw placement is not used: it turns the first piece about its corner, or about a jigsaw block that the
 * vanilla templates used here do not have, and a ruin has to stay inside the round of ground found for it.
 *
 * <p>A site may name a loot table (see {@code DiscRuins.Loot}). Once the pieces of a chunk are placed, every container of
 * such a ruin that came with a loot table is given the site's instead, and a chest with it is put where the template has a
 * data marker for one. Vanilla's ocean ruins have their chests only as such markers, which the game fills in from code of
 * its own that a template pool does not run.
 */
public final class DiscRuinsStructure extends Structure {
    // What vanilla's templates call the place of a chest that the game puts in by code.
    private static final String CHEST_MARKER = "chest";

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

    // A site names an element the level's pool was found to have when the level loaded (see DiscRuinPieces), so both are there.
    private static Optional<StructurePiece> pieceAt(RuinSite site, Registry<StructureTemplatePool> pools, StructureTemplateManager templates) {
        return pools.getHolder(site.pool()).flatMap(pool -> {
            List<DiscRuinPieces.Weighted> elements = DiscRuinPieces.elementsOf(pool.value());
            if (site.element() >= elements.size()) {
                return Optional.empty();
            }
            StructurePoolElement element = elements.get(site.element()).element();
            Rotation rotation = Rotation.getRandom(RandomSource.create(site.seed()));
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
    public void afterPlace(
            WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunk,
            PiecesContainer pieces) {
        var footprint = ((RavineFootprintHolder) (Object) level.getLevel().getChunkSource().randomState()).overgrownAbyss$footprint();
        for (StructurePiece piece : pieces.pieces()) {
            if (piece instanceof PoolElementStructurePiece ruin && ruin.getBoundingBox().intersects(box)) {
                siteOf(ruin, footprint).ifPresent(site -> site.loot().ifPresent(table -> fill(level, ruin, box, chunk, table, site.seed())));
            }
        }
    }

    // A piece saved with its chunk comes back as vanilla's own, so its site is found again by where pieceAt put it: the
    // middle of its box on the site, and its lowest layer at the site's height.
    private static Optional<RuinSite> siteOf(PoolElementStructurePiece ruin, RavineFootprint footprint) {
        BoundingBox bounds = ruin.getBoundingBox();
        BlockPos middle = bounds.getCenter();
        int minX = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(middle.getX()));
        int minZ = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(middle.getZ()));
        return footprint.ruinsIn(minX, minZ).stream()
                .filter(site -> site.x() == middle.getX() && site.z() == middle.getZ() && site.y() == bounds.minY())
                .findFirst();
    }

    // The part of a ruin that lies in the chunk being built, which is all of the level that may be touched now.
    private static void fill(WorldGenLevel level, PoolElementStructurePiece ruin, BoundingBox box, ChunkPos chunk, ResourceKey<LootTable> table, long seed) {
        for (BlockPos pos : level.getChunk(chunk.x, chunk.z).getBlockEntitiesPos()) {
            // One with no table was left empty on purpose by its template, and stays so.
            if (ruin.getBoundingBox().isInside(pos) && level.getBlockEntity(pos) instanceof RandomizableContainer container && container.getLootTable() != null) {
                container.setLootTable(table);
            }
        }
        if (!(ruin.getElement() instanceof SinglePoolElement element)) {
            return;
        }
        StructureTemplateManager templates = level.getLevel().getStructureManager();
        // Asked for as places in the level: turned and moved as the piece's own blocks were.
        for (StructureTemplate.StructureBlockInfo marker : element.getDataMarkers(templates, ruin.getPosition(), ruin.getRotation(), true)) {
            BlockPos pos = marker.pos();
            if (box.isInside(pos) && marker.nbt() != null && CHEST_MARKER.equals(marker.nbt().getString("metadata"))) {
                boolean inWater = level.getFluidState(pos).is(FluidTags.WATER);
                level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, inWater), Block.UPDATE_CLIENTS);
                // Seeded by the site and the place, so that a chest holds the same whatever else its chunk holds.
                RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(seed ^ pos.asLong()), pos, table);
            }
        }
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
