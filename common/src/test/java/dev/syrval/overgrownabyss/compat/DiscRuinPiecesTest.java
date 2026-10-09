package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.syrval.overgrownabyss.ravine.DiscRuins;
import dev.syrval.overgrownabyss.ravine.DiscTheme;
import dev.syrval.overgrownabyss.ravine.RuinPieces;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.junit.jupiter.api.Test;

/**
 * What a level's ruins are made of, found the way the game finds it: from vanilla's own pools, structures and templates and
 * those of a small pack of the tests' own, loaded by the game's loader outside a level. The mod's own pools name processors
 * that only the running mod registers, so these stand in for them here; {@link DiscRuinFilesTest} reads the mod's as files.
 */
class DiscRuinPiecesTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final RegistryAccess.Frozen VANILLA = vanilla();
    private static final String CITY = "minecraft:ancient_city/structures";
    private static final String CENTRES = "minecraft:village/plains/town_centers";

    private static RegistryAccess.Frozen vanilla() {
        try {
            Path folder = Path.of(DiscRuinPiecesTest.class.getResource("/ruin-test-pack").toURI());
            PackResources tests = new PathPackResources(new PackLocationInfo("tests", Component.literal("tests"), PackSource.BUILT_IN, Optional.empty()), folder);
            try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource(), tests))) {
                return RegistryDataLoader.load(
                        manager, RegistryLayer.createRegistryAccess().getAccessForLoading(RegistryLayer.WORLDGEN), RegistryDataLoader.WORLDGEN_REGISTRIES);
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResourceKey<StructureTemplatePool> pool(String id) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.parse(id));
    }

    private static Holder.Reference<StructureTemplatePool> poolHolder(String id) {
        return VANILLA.registryOrThrow(Registries.TEMPLATE_POOL).getHolderOrThrow(pool(id));
    }

    private static Holder<Structure> structure(String id) {
        return VANILLA.registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
    }

    private static final Comparator<RuinPieces.Piece> BY_ROOM = Comparator.comparingInt(RuinPieces.Piece::weight)
            .thenComparingDouble(RuinPieces.Piece::radius).thenComparingInt(RuinPieces.Piece::height);

    // Pieces without their places in their pool, in an order that does not depend on the pool's: to set those the level found
    // beside those the files come to.
    private static List<RuinPieces.Piece> sorted(List<RuinPieces.Piece> pieces) {
        return pieces.stream().map(piece -> new RuinPieces.Piece(piece.pool(), 0, piece.weight(), piece.radius(), piece.height(), piece.sink()))
                .sorted(BY_ROOM).toList();
    }

    /** What a pool's file and its templates' files come to, by the same rule but with nothing of the game's loading in between. */
    private static List<RuinPieces.Piece> filed(String poolId, int sink) {
        var pieces = new ArrayList<RuinPieces.Piece>();
        for (JsonObject entry : JarRuinPieces.elements(ResourceLocation.parse(poolId))) {
            JarRuinPieces.piece(pool(poolId), 0, entry, sink).ifPresent(pieces::add);
        }
        return sorted(pieces);
    }

    @Test
    void aPoolsElementsAreCountedWithTheirWeightsInTheSameOrderEveryTime() {
        StructureTemplatePool city = poolHolder(CITY).value();
        List<DiscRuinPieces.Weighted> elements = DiscRuinPieces.elementsOf(city);
        List<JsonObject> file = JarRuinPieces.elements(ResourceLocation.parse(CITY));
        assertEquals(20, file.size(), "vanilla's pool of city structures");
        assertEquals(file.size(), elements.size(), "each element of the file once");
        assertEquals(file.stream().map(entry -> entry.get("weight").getAsInt()).sorted().toList(),
                elements.stream().map(DiscRuinPieces.Weighted::weight).sorted().toList(), "with the weight the file gives it");
        List<DiscRuinPieces.Weighted> again = DiscRuinPieces.elementsOf(city);
        for (int i = 0; i < elements.size(); i++) {
            assertSame(elements.get(i).element(), again.get(i).element(), "the same order every time: a site names an element by its place in it");
        }
    }

    @Test
    void theLevelsPiecesAreMeasuredFromItsOwnPoolsAndTemplates() {
        DiscTheme theme = DiscTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"weight": 1, "ruins": {"chance": 0.5, "kinds": [
                  {"pool": "minecraft:ancient_city/structures", "weight": 3, "by_height": {"bottom": 2, "top": 0.5}},
                  {"pool": "overgrown_abyss:no_such_pool", "weight": 5},
                  {"pool": "minecraft:village/plains/town_centers", "weight": 1, "sink": 1},
                  {"pool": "minecraft:pillager_outpost/base_plates", "weight": 1, "sink": 1}]}}""")).getOrThrow();
        DiscRuins ruins = theme.ruins().orElseThrow();
        RuinPieces pieces = DiscRuinPieces.of(VANILLA, JarRuinPieces::template, List.of(theme, theme));
        assertEquals(Map.of(ruins, pieces.byRuins().get(ruins)).keySet(), pieces.byRuins().keySet(), "one entry for the themes that share these ruins");
        List<RuinPieces.Kind> kinds = pieces.byRuins().get(ruins);
        assertEquals(3, kinds.size(), "a pool the level does not have is no kind");

        RuinPieces.Kind city = kinds.get(0);
        assertEquals(3, city.weight());
        assertEquals(ruins.kinds().get(0).byHeight(), city.byHeight());
        // Of the pool's 20 elements one is empty and two are lists of templates.
        assertEquals(17, city.pieces().size(), "a piece for each element that is one template");
        assertEquals(filed(CITY, 0), sorted(city.pieces()), "each with its weight in the pool and the room its own template needs");
        assertTrue(city.pieces().stream().anyMatch(piece -> piece.height() == 19 && Math.abs(piece.radius() - Math.hypot(17, 17) / 2) < 1e-9),
                "among them a tall ruin, 17 by 17 and 19 high though its box is 23");

        RuinPieces.Kind centres = kinds.get(1);
        assertEquals(8, centres.pieces().size());
        assertEquals(filed(CENTRES, 1), sorted(centres.pieces()), "with a layer in the ground, a piece stands a layer lower over it");
        assertTrue(centres.pieces().stream().allMatch(piece -> piece.sink() == 1));

        // An outpost's plate builds two layers and is saved with the air of a box 30 high, which nothing leaves out.
        assertEquals(List.of(29), kinds.get(2).pieces().stream().map(RuinPieces.Piece::height).toList(), "a piece needs room for the air it places");

        // A site names a piece by its pool and the place of its element among the pool's, so that place must lead back to it.
        var level = new DiscRuinPieces(VANILLA, JarRuinPieces::template);
        List<DiscRuinPieces.Weighted> elements = DiscRuinPieces.elementsOf(poolHolder(CITY).value());
        for (RuinPieces.Piece piece : city.pieces()) {
            StructurePoolElement element = elements.get(piece.element()).element();
            DiscRuinPieces.Measure measure = level.templateOf(element).flatMap(JarRuinPieces::template).flatMap(DiscRuinPieces.Measure::of).orElseThrow();
            assertEquals(measure.radius(), piece.radius(), 1e-9, "element " + piece.element() + " of the city's pool");
            assertEquals(measure.built(), piece.height());
            assertEquals(elements.get(piece.element()).weight(), piece.weight());
        }
        assertEquals(city.pieces().size(), city.pieces().stream().map(RuinPieces.Piece::element).distinct().count(), "each piece another element");
    }

    private static List<RuinPieces.Kind> kindsOf(String ruins) {
        DiscTheme theme = DiscTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"weight\": 1, \"ruins\": " + ruins + "}")).getOrThrow();
        return DiscRuinPieces.of(VANILLA, JarRuinPieces::template, List.of(theme)).byRuins().get(theme.ruins().orElseThrow());
    }

    @Test
    void aPieceNeedsRoomForTheAirItPlacesUnlessItsProcessorsLeaveAirOut() {
        // One ocean ruin three times over: it builds 7 layers and is saved with the air of a box 16 high.
        List<RuinPieces.Piece> pieces = kindsOf("{\"chance\": 1, \"kinds\": [{\"pool\": \"ruintest:ocean\", \"weight\": 1, \"sink\": 1}]}").getFirst().pieces();
        Map<Integer, Integer> heightByWeight = pieces.stream().collect(Collectors.toMap(RuinPieces.Piece::weight, RuinPieces.Piece::height));
        assertEquals(Map.of(1, 6, 2, 6, 3, 15), heightByWeight,
                "with processors that leave air out, named or written in place, it needs what it builds; with none that do, its whole box");
    }

    @Test
    void aPieceThatBuildsNothingOverTheGroundIsNoRuin() {
        // Vanilla's trail ruins start 15 under the ground, and none of their five towers is more than 16 layers.
        String towers = "{\"chance\": 1, \"kinds\": [{\"pool\": \"minecraft:trail_ruins/tower\", \"weight\": 1, \"sink\": %d}]}";
        assertEquals(5, kindsOf(towers.formatted(0)).getFirst().pieces().size(), "stood on the ground, each is a piece");
        assertEquals(List.of(), kindsOf(towers.formatted(16)), "as deep as their structure starts them, nothing of any is left, and the kind with them");
        int tallest = kindsOf(towers.formatted(0)).getFirst().pieces().stream().mapToInt(RuinPieces.Piece::height).max().orElseThrow();
        assertEquals(1, kindsOf(towers.formatted(tallest - 1)).getFirst().pieces().stream().mapToInt(RuinPieces.Piece::height).max().orElseThrow(),
                "a layer short of the tallest, that one stands a block over the ground");
    }

    private static DiscRuinPieces.Source source(String poolId, int sink) {
        return new DiscRuinPieces.Source(pool(poolId), sink);
    }

    // A jigsaw structure that starts from the city's pool, on the ground or not, at a height.
    private static Holder<Structure> jigsaw(HeightProvider startHeight, Optional<Heightmap.Types> startOn) {
        var settings = new Structure.StructureSettings(HolderSet.direct(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        return Holder.direct(new JigsawStructure(
                settings, poolHolder(CITY), Optional.empty(), 1, startHeight, false, startOn, 80, List.of(),
                JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS));
    }

    private static Holder<Structure> onTheGroundAt(int startHeight) {
        return jigsaw(ConstantHeight.of(VerticalAnchor.absolute(startHeight)), Optional.of(Heightmap.Types.WORLD_SURFACE_WG));
    }

    @Test
    void aStructureLendsItsStartPoolAsDeepInTheGroundAsItsOwnFileStartsIt() {
        var level = new DiscRuinPieces(VANILLA, JarRuinPieces::template);
        assertEquals(Optional.of(source("minecraft:pillager_outpost/base_plates", 1)), level.startOf(structure("minecraft:pillager_outpost")),
                "a start height of 0 puts a piece's lowest layer in place of the ground's highest block");
        assertEquals(Optional.of(source(CENTRES, 1)), level.startOf(structure("minecraft:village_plains")));
        assertEquals(Optional.of(source("minecraft:trail_ruins/tower", 16)), level.startOf(structure("minecraft:trail_ruins")), "started 15 under the ground");
        assertEquals(Optional.of(source(CITY, 0)), level.startOf(onTheGroundAt(1)), "started a block up, its lowest layer lies on the ground");
        assertEquals(Optional.of(source(CITY, 5)), level.startOf(onTheGroundAt(-4)));

        assertEquals(Optional.empty(), level.startOf(onTheGroundAt(2)), "one that starts above the ground");
        assertEquals(Optional.empty(), level.startOf(structure("minecraft:ancient_city")), "one that starts at a height of the level, not on its ground");
        assertEquals(Optional.empty(), level.startOf(structure("minecraft:trial_chambers")));
        assertEquals(Optional.empty(), level.startOf(structure("minecraft:jungle_pyramid")), "one that is not built from a template pool");
        assertEquals(Optional.empty(),
                level.startOf(jigsaw(UniformHeight.of(VerticalAnchor.absolute(-30), VerticalAnchor.absolute(-25)), Optional.of(Heightmap.Types.WORLD_SURFACE_WG))),
                "one that starts at a depth drawn for each");
    }
}
