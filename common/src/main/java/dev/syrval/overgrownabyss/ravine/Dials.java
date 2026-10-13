package dev.syrval.overgrownabyss.ravine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.util.InclusiveRange;

/**
 * The owner's dials: one number for each thing a level's holes may have more or less of. 0 is none of it, 1 is as the settings
 * have it and 2 is twice as much. They are kept in a file in the game's config folder and read when a level loads (see
 * {@code compat/DialsFile}), so a look can be tried without a datapack.
 *
 * <p>A dial turns numbers of the settings a level has loaded: the settings are written out as the JSON a datapack would give,
 * the numbers are changed there, and the result is read back through the settings' own codec. So every limit and rule the
 * codec has holds for what the dials give, and with every dial at 1 nothing is written or read at all.
 *
 * <p>No dial moves the grid the holes lie on. The city's placement reads the salt, the cell size, a hole's reach, the floor
 * and the top from the registry's copy of the settings, which a level's dials never reach. So {@code holes} can only take holes
 * away, each of the rest staying where it was; more of them would need smaller cells.
 *
 * @param holes       the share of the holes that are kept, from 0 to 1
 * @param roots       how many roots a hole grows: great ones, crossing ones, branches and links
 * @param falls       how many falls a hole has
 * @param springs     how many springs
 * @param ruins       how many ruins stand on discs: on more of the discs first and, once every disc has them, more to a disc
 * @param troves      the same of troves
 * @param roughGround how much of a disc's top its mounds and outcrops cover
 * @param growth      how much grows of each group of features, by the group's name. A group is a tag of configured features
 *                    under {@code overgrown_abyss:dial/}, and a feature in two groups is turned by both
 * @param themes      how often a disc gets each theme, by the id of the theme's biome
 */
public record Dials(
        float holes, float roots, float falls, float springs, float ruins, float troves, float roughGround,
        Map<String, Float> growth, Map<String, Float> themes) {

    // The most of anything a dial may ask for, in times what the settings have.
    private static final float MOST = 10;
    public static final Dials NEUTRAL = new Dials(1, 1, 1, 1, 1, 1, 1, Map.of(), Map.of());

    public static final Codec<Dials> CODEC = RecordCodecBuilder.create(i -> i.group(
            dial("holes", 1).forGetter(Dials::holes),
            dial("roots", MOST).forGetter(Dials::roots),
            dial("falls", MOST).forGetter(Dials::falls),
            dial("springs", MOST).forGetter(Dials::springs),
            dial("ruins", MOST).forGetter(Dials::ruins),
            dial("troves", MOST).forGetter(Dials::troves),
            dial("rough_ground", MOST).forGetter(Dials::roughGround),
            named("growth").forGetter(Dials::growth),
            named("themes").forGetter(Dials::themes)
    ).apply(i, Dials::new));

    // In name order, so that the file comes out the same each time it is written.
    public Dials {
        growth = Collections.unmodifiableMap(new TreeMap<>(growth));
        themes = Collections.unmodifiableMap(new TreeMap<>(themes));
    }

    // A dial left out is at 1, and one at 1 is still written: the file is where the owner finds which dials there are.
    private static MapCodec<Float> dial(String name, float most) {
        return Codec.floatRange(0, most).optionalFieldOf(name).xmap(given -> given.orElse(1F), Optional::of);
    }

    private static MapCodec<Map<String, Float>> named(String name) {
        return Codec.unboundedMap(Codec.STRING, Codec.floatRange(0, MOST)).optionalFieldOf(name).xmap(given -> given.orElse(Map.of()), Optional::of);
    }

    /** The names a level's themes go by here: the ids of their biomes. A theme without a biome has no dial. */
    public static Set<String> themeNames(RavineSettings settings) {
        return settings.discThemes().stream()
                .flatMap(theme -> theme.biome().stream())
                .map(biome -> biome.location().toString())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** These dials with one at 1 for every name given that they lack, which is how the file is filled in. */
    public Dials withNames(Set<String> growthNames, Set<String> themeNames) {
        return new Dials(holes, roots, falls, springs, ruins, troves, roughGround, filled(growth, growthNames), filled(themes, themeNames));
    }

    private static Map<String, Float> filled(Map<String, Float> given, Set<String> names) {
        var all = new TreeMap<>(given);
        names.forEach(name -> all.putIfAbsent(name, 1F));
        return all;
    }

    /** The dials that are not at 1, by the names the file has them under. */
    public Map<String, Float> turned() {
        var all = new LinkedHashMap<String, Float>();
        all.put("holes", holes);
        all.put("roots", roots);
        all.put("falls", falls);
        all.put("springs", springs);
        all.put("ruins", ruins);
        all.put("troves", troves);
        all.put("rough_ground", roughGround);
        growth.forEach((name, dial) -> all.put("growth." + name, dial));
        themes.forEach((name, dial) -> all.put("themes." + name, dial));
        all.values().removeIf(dial -> dial == 1);
        return all;
    }

    /** Whether every dial is at 1, which leaves a level's settings as they are. */
    public boolean isNeutral() {
        return turned().isEmpty();
    }

    /**
     * The settings as these dials turn them, or the codec's complaint if what they give does not hold.
     *
     * @param growthGroups the ids of the features in each group of growth, by the group's name
     */
    public DataResult<RavineSettings> applyTo(RavineSettings settings, Map<String, Set<String>> growthGroups) {
        if (isNeutral()) {
            return DataResult.success(settings);
        }
        Codec<RavineSettings> codec = RavineSettings.MAP_CODEC.codec();
        return codec.encodeStart(JsonOps.INSTANCE, settings).flatMap(json -> {
            turn(json.getAsJsonObject(), growthGroups);
            return codec.parse(JsonOps.INSTANCE, json);
        });
    }

    private void turn(JsonObject settings, Map<String, Set<String>> growthGroups) {
        times(settings, "chance", holes, 0, 1);
        turnRoots(settings, growthGroups);
        at(settings, "falls").ifPresent(found -> count(found, falls, FallSettings.MOST));
        at(settings, "falls", "springs").ifPresent(found -> count(found, springs, FallSettings.MOST));
        if (!settings.has("disc_themes")) {
            return;
        }
        for (JsonElement element : settings.getAsJsonArray("disc_themes")) {
            JsonObject theme = element.getAsJsonObject();
            if (theme.has("biome")) {
                times(theme, "weight", themes.getOrDefault(theme.get("biome").getAsString(), 1F), 0, DiscTheme.MOST_WEIGHT);
            }
            turnSites(theme, "ruins", ruins);
            turnSites(theme, "troves", troves);
            turnGround(theme);
            turnGrowth(theme, growthGroups);
        }
    }

    private void turnRoots(JsonObject settings, Map<String, Set<String>> growthGroups) {
        if (roots == 0) {
            settings.remove("roots");
            return;
        }
        at(settings, "roots", "great").ifPresent(great -> count(great, roots, RootSettings.Great.MOST));
        at(settings, "roots", "crossing").ifPresent(crossing -> count(crossing, roots, RootSettings.Crossing.MOST));
        at(settings, "roots", "branches").ifPresent(
                branches -> times(branches, "every", 1.0 / roots, RootSettings.Branches.LEAST_EVERY, RootSettings.Branches.MOST_EVERY));
        at(settings, "roots", "links").ifPresent(links -> times(links, "chance", roots, 0, 1));
        at(settings, "roots").ifPresent(found -> turnGrowth(found, growthGroups));
    }

    // On more of the theme's discs first. Once every disc has them there is no more of that to give, and the rest goes to
    // how many a disc holds.
    private static void turnSites(JsonObject theme, String name, float dial) {
        if (dial == 0) {
            theme.remove(name);
            return;
        }
        at(theme, name).ifPresent(sites -> {
            double asked = sites.get("chance").getAsDouble() * dial;
            sites.addProperty("chance", (float) Math.min(asked, 1));
            if (asked > 1) {
                every(sites, asked, DiscRuins.LEAST_EVERY, Integer.MAX_VALUE);
            }
        });
    }

    private void turnGround(JsonObject theme) {
        if (roughGround == 0) {
            theme.remove("ground");
            return;
        }
        at(theme, "ground", "mounds").ifPresent(mounds -> times(mounds, "cover", roughGround, DiscRelief.Mounds.LEAST_COVER, 1));
        at(theme, "ground", "outcrops").ifPresent(outcrops -> times(outcrops, "cover", roughGround, DiscRelief.Outcrops.LEAST_COVER, 1));
    }

    // A theme's growth and the roots' are lists of the same kind: a feature, and how many blocks there are to each one grown.
    private void turnGrowth(JsonObject owner, Map<String, Set<String>> growthGroups) {
        if (!owner.has("growth")) {
            return;
        }
        var kept = new JsonArray();
        for (JsonElement element : owner.getAsJsonArray("growth")) {
            JsonObject entry = element.getAsJsonObject();
            double factor = growthOf(entry.get("feature").getAsString(), growthGroups);
            if (factor > 0) {
                every(entry, factor, 1, DiscTheme.Growth.MOST_EVERY);
                kept.add(entry);
            }
        }
        owner.add("growth", kept);
    }

    private double growthOf(String feature, Map<String, Set<String>> growthGroups) {
        double factor = 1;
        for (var dial : growth.entrySet()) {
            if (growthGroups.getOrDefault(dial.getKey(), Set.of()).contains(feature)) {
                factor *= dial.getValue();
            }
        }
        return factor;
    }

    // The object under a path of names, if each of them is there.
    private static Optional<JsonObject> at(JsonObject from, String... path) {
        JsonObject found = from;
        for (String name : path) {
            if (!found.has(name) || !found.get(name).isJsonObject()) {
                return Optional.empty();
            }
            found = found.getAsJsonObject(name);
        }
        return Optional.of(found);
    }

    private static void times(JsonObject in, String name, double factor, double least, double most) {
        if (in.has(name)) {
            in.addProperty(name, (float) Math.clamp(in.get(name).getAsDouble() * factor, least, most));
        }
    }

    // So many times as much of something that is given as the blocks there are to each one of it.
    private static void every(JsonObject in, double factor, int least, int most) {
        if (in.has("every")) {
            in.addProperty("every", Math.clamp(Math.round(in.get("every").getAsInt() / factor), least, most));
        }
    }

    // A count is written as one number, a pair or two named ends, so it is read and written by its own codec.
    private static void count(JsonObject in, double factor, int most) {
        if (!in.has("count")) {
            return;
        }
        InclusiveRange<Integer> count = InclusiveRange.INT.parse(JsonOps.INSTANCE, in.get("count")).getOrThrow();
        var scaled = new InclusiveRange<>(scaled(count.minInclusive(), factor, most), scaled(count.maxInclusive(), factor, most));
        in.add("count", InclusiveRange.INT.encodeStart(JsonOps.INSTANCE, scaled).getOrThrow());
    }

    private static int scaled(int count, double factor, int most) {
        return (int) Math.min(Math.round(count * factor), most);
    }
}
