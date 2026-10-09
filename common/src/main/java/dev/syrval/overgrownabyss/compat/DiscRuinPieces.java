package dev.syrval.overgrownabyss.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.ravine.DiscRuins;
import dev.syrval.overgrownabyss.ravine.DiscTheme;
import dev.syrval.overgrownabyss.ravine.RuinPieces;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

/**
 * What a level's ruins are made of (see {@link RuinPieces}), found once when the level loads: the pieces of each kind's
 * template pool, each measured from its own template, and for the structures a theme borrows by tag the pool and the depth
 * their own files start them with.
 *
 * <p>Nothing here is typed into a theme, because the templates are not ours: a pack may replace vanilla's, and the structures
 * of a tag are other mods'. What the game has no accessor for is read through its own codecs, as the files would say it, and
 * not through mixins: which template an element of a pool is ({@code location}), and where a structure starts
 * ({@code start_pool}, {@code start_height}, {@code project_start_to_heightmap}), which also covers jigsaw structures of
 * types that are not vanilla's own.
 */
final class DiscRuinPieces {
    // Any fixed seed: it only has to give a pool's elements the same order every time a level loads, on either loader.
    private static final long ELEMENT_ORDER_SEED = 0;
    // What a template holds that is never placed: gaps, and the marker blocks a pool element leaves out.
    private static final Set<String> NOT_PLACED = Set.of("minecraft:structure_void", "minecraft:structure_block");
    private static final String AIR = "minecraft:air";

    /** Where a template's saved form is read from: the level's own templates in the game, files in tests. */
    @FunctionalInterface
    interface Templates {
        Optional<CompoundTag> saved(ResourceLocation id);
    }

    /** One element of a pool and its weight there. */
    record Weighted(StructurePoolElement element, int weight) {}

    /**
     * A template's footprint, and how many layers it has up to the highest that holds a block ({@code built}) and up to the
     * highest that holds air ({@code emptied}). Air saved in a template is placed like any block, emptying whatever is there,
     * so a piece needs room for it too; unless its pool's processors leave air out, as those of the mod's ocean ruins do.
     */
    record Measure(int sizeX, int sizeZ, int built, int emptied) {

        /** Read from a template's saved form; empty for one that builds nothing. */
        static Optional<Measure> of(CompoundTag template) {
            ListTag size = template.getList("size", Tag.TAG_INT);
            if (size.size() != 3) {
                return Optional.empty();
            }
            // A template with several palettes has the same blocks in the same places in each of them.
            ListTag palette = template.contains("palettes", Tag.TAG_LIST)
                    ? template.getList("palettes", Tag.TAG_LIST).getList(0)
                    : template.getList("palette", Tag.TAG_COMPOUND);
            int built = 0;
            int emptied = 0;
            for (Tag each : template.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) each;
                String name = palette.getCompound(block.getInt("state")).getString("Name");
                int layers = block.getList("pos", Tag.TAG_INT).getInt(1) + 1;
                if (name.equals(AIR)) {
                    emptied = Math.max(emptied, layers);
                } else if (!NOT_PLACED.contains(name)) {
                    built = Math.max(built, layers);
                }
            }
            return built == 0 ? Optional.empty() : Optional.of(new Measure(size.getInt(0), size.getInt(2), built, emptied));
        }

        // A piece is turned about its middle, so its furthest corner may come to lie anywhere on this round.
        double radius() {
            return Math.hypot(sizeX, sizeZ) / 2;
        }

        /** The layers that need room: those the piece builds, and those it empties if its air is placed. */
        int layers(boolean placesAir) {
            return placesAir ? Math.max(built, emptied) : built;
        }
    }

    /**
     * Whether a list of processors, as its file has it, leaves a template's air out: a {@code block_ignore} that names air,
     * at any depth, since a list may hold other lists.
     */
    static boolean leavesAirOut(JsonElement processors) {
        if (processors.isJsonArray()) {
            return processors.getAsJsonArray().asList().stream().anyMatch(DiscRuinPieces::leavesAirOut);
        }
        if (!processors.isJsonObject()) {
            return false;
        }
        JsonObject file = processors.getAsJsonObject();
        if (string(file, "processor_type").filter("minecraft:block_ignore"::equals).isPresent() && file.has("blocks") && file.get("blocks").isJsonArray()) {
            return file.getAsJsonArray("blocks").asList().stream()
                    .anyMatch(block -> block.isJsonObject() && string(block.getAsJsonObject(), "Name").filter(AIR::equals).isPresent());
        }
        return file.entrySet().stream().anyMatch(entry -> leavesAirOut(entry.getValue()));
    }

    /** A pool to take pieces from and how many of their lowest layers lie in the ground. */
    record Source(ResourceKey<StructureTemplatePool> pool, int sink) {}

    private final Registry<StructureTemplatePool> pools;
    private final Registry<Structure> structures;
    private final Registry<StructureProcessorList> processorLists;
    private final DynamicOps<JsonElement> ops;
    private final Templates templates;
    // Themes share pools, and a template is measured by saving it whole.
    private final Map<Source, List<RuinPieces.Piece>> measured = new HashMap<>();
    // Themes share tags of structures as well; by the structure's name.
    private final Map<String, Optional<Source>> starts = new HashMap<>();

    DiscRuinPieces(RegistryAccess registries, Templates templates) {
        this.pools = registries.registryOrThrow(Registries.TEMPLATE_POOL);
        this.structures = registries.registryOrThrow(Registries.STRUCTURE);
        this.processorLists = registries.registryOrThrow(Registries.PROCESSOR_LIST);
        this.ops = registries.createSerializationContext(JsonOps.INSTANCE);
        this.templates = templates;
    }

    /** The pieces the ruins of {@code themes} are made of in a level with these registries and templates. */
    static RuinPieces of(RegistryAccess registries, Templates templates, List<DiscTheme> themes) {
        var level = new DiscRuinPieces(registries, templates);
        var byRuins = new HashMap<DiscRuins, List<RuinPieces.Kind>>();
        for (DiscTheme theme : themes) {
            if (theme.ruins().isPresent() && !byRuins.containsKey(theme.ruins().get())) {
                List<RuinPieces.Kind> kinds = level.kindsOf(theme.ruins().get());
                byRuins.put(theme.ruins().get(), kinds);
                logKinds(theme, kinds);
            }
        }
        return new RuinPieces(byRuins);
    }

    // Shows in a pack's log what its templates came to, which is what decides where its ruins find room.
    private static void logKinds(DiscTheme theme, List<RuinPieces.Kind> kinds) {
        List<RuinPieces.Piece> pieces = kinds.stream().flatMap(kind -> kind.pieces().stream()).toList();
        String name = theme.biome().map(biome -> biome.location().toString()).orElse("a theme with no biome");
        // Not a warning: a theme may have nothing but the structures of a mod that is not installed. A pool of its own that is
        // missing was warned of by name.
        if (pieces.isEmpty()) {
            OvergrownAbyss.LOGGER.info("Disc ruins of {}: no piece to make them of in this level, so its discs have none", name);
            return;
        }
        OvergrownAbyss.LOGGER.info("Disc ruins of {}: {} kinds with {} pieces, needing rounds {} to {} blocks across and {} to {} blocks of air",
                name, kinds.size(), pieces.size(),
                Math.round(2 * pieces.stream().mapToDouble(RuinPieces.Piece::radius).min().orElse(0)),
                Math.round(2 * pieces.stream().mapToDouble(RuinPieces.Piece::radius).max().orElse(0)),
                pieces.stream().mapToInt(RuinPieces.Piece::height).min().orElse(0),
                pieces.stream().mapToInt(RuinPieces.Piece::height).max().orElse(0));
    }

    /**
     * The distinct elements of a pool with their weights, in an order that is the same every time. A pool keeps each element
     * as many times over as its weight and hands out no more than shuffled copies of that list, so the elements are counted
     * in one shuffled with a fixed seed.
     */
    static List<Weighted> elementsOf(StructureTemplatePool pool) {
        // Elements have no equality of their own, so two entries of a pool that name the same template stay two.
        var counts = new LinkedHashMap<StructurePoolElement, Integer>();
        for (StructurePoolElement element : pool.getShuffledTemplates(RandomSource.create(ELEMENT_ORDER_SEED))) {
            counts.merge(element, 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> new Weighted(entry.getKey(), entry.getValue())).toList();
    }

    private List<RuinPieces.Kind> kindsOf(DiscRuins ruins) {
        var kinds = new ArrayList<RuinPieces.Kind>();
        for (DiscRuins.Kind kind : ruins.kinds()) {
            addKind(kinds, kind.weight(), kind.byHeight(), false, new Source(kind.pool(), kind.sink()));
        }
        for (DiscRuins.Borrowed borrowed : ruins.structures()) {
            // By name, so that the kinds do not depend on the order in which packs added to the tag.
            List<Holder<Structure>> tagged = structures.getTag(borrowed.tag()).stream().flatMap(HolderSet::stream)
                    .sorted(Comparator.comparing(Holder::getRegisteredName)).toList();
            for (Holder<Structure> structure : tagged) {
                starts.computeIfAbsent(structure.getRegisteredName(), name -> lentBy(structure, borrowed.tag()))
                        .ifPresent(start -> addKind(kinds, borrowed.weight(), borrowed.byHeight(), true, start));
            }
        }
        return List.copyOf(kinds);
    }

    // Said once for a structure, however many themes borrow it.
    private Optional<Source> lentBy(Holder<Structure> structure, TagKey<Structure> tag) {
        Optional<Source> start = startOf(structure);
        start.ifPresent(source -> OvergrownAbyss.LOGGER.info(
                "Structure {} of #{} lends disc ruins the {} pieces of {} that can stand as ruins, each with {} of its layers in the ground",
                structure.getRegisteredName(), tag.location(), measured.computeIfAbsent(source, this::piecesIn).size(), source.pool().location(), source.sink()));
        return start;
    }

    private void addKind(List<RuinPieces.Kind> kinds, float weight, DiscTheme.Ramp byHeight, boolean borrowed, Source source) {
        List<RuinPieces.Piece> pieces = measured.computeIfAbsent(source, this::piecesIn);
        if (!pieces.isEmpty()) {
            kinds.add(new RuinPieces.Kind(weight, byHeight, borrowed, pieces));
        }
    }

    private List<RuinPieces.Piece> piecesIn(Source source) {
        Optional<Holder.Reference<StructureTemplatePool>> pool = pools.getHolder(source.pool());
        if (pool.isEmpty()) {
            OvergrownAbyss.LOGGER.warn("Template pool {} does not exist; a disc theme's ruins of that kind are left out", source.pool().location());
            return List.of();
        }
        List<Weighted> elements = elementsOf(pool.get().value());
        var pieces = new ArrayList<RuinPieces.Piece>();
        for (int i = 0; i < elements.size(); i++) {
            StructurePoolElement element = elements.get(i).element();
            Optional<Measure> measure = templateOf(element).flatMap(templates::saved).flatMap(Measure::of);
            // What is left over the ground when its lowest layers are in it; a piece that builds nothing there is no ruin.
            if (measure.isPresent() && measure.get().built() > source.sink()) {
                int height = measure.get().layers(placesAir(element)) - source.sink();
                pieces.add(new RuinPieces.Piece(source.pool(), i, elements.get(i).weight(), measure.get().radius(), height, source.sink()));
            }
        }
        if (pieces.size() < elements.size()) {
            OvergrownAbyss.LOGGER.info(
                    "{} of the {} elements of template pool {} are not offered as disc ruins: not a single template, a template that is missing or"
                            + " builds nothing, or one that builds no more than its {} lowest layers",
                    elements.size() - pieces.size(), elements.size(), source.pool().location(), source.sink());
        }
        return List.copyOf(pieces);
    }

    // An element as its pool's file has it.
    private Optional<JsonObject> fileOf(StructurePoolElement element) {
        return StructurePoolElement.CODEC.encodeStart(ops, element).result().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
    }

    /** The template an element places, as its pool's file names it; empty for an element that is not one template. */
    Optional<ResourceLocation> templateOf(StructurePoolElement element) {
        return fileOf(element).flatMap(file -> string(file, "location")).map(ResourceLocation::tryParse);
    }

    /** Whether an element places the air of its template: it does unless its processors, named or written in place, leave air out. */
    boolean placesAir(StructurePoolElement element) {
        Optional<JsonElement> processors = fileOf(element).map(file -> file.get("processors"));
        Optional<JsonElement> named = processors.filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString).map(ResourceLocation::tryParse)
                .flatMap(id -> processorLists.getHolder(ResourceKey.create(Registries.PROCESSOR_LIST, id)))
                .flatMap(list -> StructureProcessorType.DIRECT_CODEC.encodeStart(ops, list.value()).result());
        return !named.or(() -> processors).map(DiscRuinPieces::leavesAirOut).orElse(false);
    }

    /**
     * The pool a structure takes its first piece from and how many of that piece's lowest layers its own file puts in the
     * ground; empty, with the reason logged, for a structure that cannot lend a piece to a disc. A jigsaw structure that
     * starts on a heightmap puts its first piece's lowest layer {@code start_height} above the ground's highest block, so a
     * start height of 0 has one layer in the ground.
     */
    Optional<Source> startOf(Holder<Structure> structure) {
        Optional<JsonObject> file = Structure.DIRECT_CODEC.encodeStart(ops, structure.value()).result()
                .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
        Optional<ResourceLocation> pool = file.flatMap(json -> string(json, "start_pool")).map(ResourceLocation::tryParse);
        if (pool.isEmpty()) {
            return notLent(structure, "it does not start from a template pool");
        }
        if (!file.get().has("project_start_to_heightmap")) {
            return notLent(structure, "it does not start on the ground");
        }
        Optional<HeightProvider> height = Optional.ofNullable(file.get().get("start_height")).flatMap(json -> HeightProvider.CODEC.parse(ops, json).result());
        if (height.isEmpty() || !(height.get() instanceof ConstantHeight constant) || !(constant.getValue() instanceof VerticalAnchor.Absolute offset)) {
            return notLent(structure, "it does not start at one fixed depth");
        }
        if (offset.y() > 1) {
            return notLent(structure, "it starts above the ground");
        }
        return Optional.of(new Source(ResourceKey.create(Registries.TEMPLATE_POOL, pool.get()), 1 - offset.y()));
    }

    private static Optional<Source> notLent(Holder<Structure> structure, String reason) {
        OvergrownAbyss.LOGGER.info("Structure {} lends nothing to disc ruins: {}", structure.getRegisteredName(), reason);
        return Optional.empty();
    }

    private static Optional<String> string(JsonObject file, String field) {
        JsonElement value = file.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? Optional.of(value.getAsString()) : Optional.empty();
    }
}
