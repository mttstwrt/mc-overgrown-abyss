package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.InclusiveRange;
import org.junit.jupiter.api.Test;

/** The owner's dials, turned on the mod's own settings with the groups of growth the mod's own tags give. */
class DialsTest {
    static {
        MinecraftBootstrap.init();
    }

    private static final String TAGS = "/data/overgrown_abyss/tags/worldgen/configured_feature/dial";
    private static final String FEATURES = "/data/overgrown_abyss/worldgen/configured_feature/";
    private static final Codec<RavineSettings> SETTINGS = RavineSettings.MAP_CODEC.codec();
    private static final String LAVA = "overgrown_abyss:disc_lava";

    private static Dials dials(String json) {
        return Dials.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static RavineSettings turned(String json) throws Exception {
        return dials(json).applyTo(DiscThemeTest.shipped(), groups()).getOrThrow();
    }

    /** The groups of growth as the mod's tag files give them: a file's name, and the ids it lists. */
    private static Map<String, Set<String>> groups() throws Exception {
        var groups = new HashMap<String, Set<String>>();
        try (var files = Files.list(Path.of(DialsTest.class.getResource(TAGS).toURI()))) {
            for (Path file : files.toList()) {
                var ids = new HashSet<String>();
                JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("values").forEach(id -> ids.add(id.getAsString()));
                groups.put(file.getFileName().toString().replaceFirst("\\.json$", ""), ids);
            }
        }
        return groups;
    }

    private static JsonElement written(RavineSettings settings) {
        return SETTINGS.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
    }

    private static boolean grows(Set<String> group, DiscTheme.Growth growth) {
        return group.contains(growth.feature().location().toString());
    }

    private static int every(int was, double times) {
        return (int) Math.max(1, Math.round(was / times));
    }

    private static InclusiveRange<Integer> times(InclusiveRange<Integer> count, int factor, int most) {
        return new InclusiveRange<>(Math.min(count.minInclusive() * factor, most), Math.min(count.maxInclusive() * factor, most));
    }

    @Test
    void theSettingsSurviveBeingWrittenOutAndReadBack() throws Exception {
        RavineSettings shipped = DiscThemeTest.shipped();
        JsonElement once = written(shipped);

        assertEquals(once, written(SETTINGS.parse(JsonOps.INSTANCE, once).getOrThrow()));
        // A dial that is turned and finds nothing to turn takes the settings out and back with nothing changed.
        assertEquals(once, written(turned("{\"growth\": {\"no_such_group\": 2}}")));
    }

    @Test
    void everyDialAtOneGivesTheSettingsThemselves() throws Exception {
        RavineSettings shipped = DiscThemeTest.shipped();

        assertSame(shipped, Dials.NEUTRAL.applyTo(shipped, groups()).getOrThrow());
        assertSame(shipped, dials("{\"roots\": 1, \"growth\": {\"lights\": 1}, \"themes\": {\"" + LAVA + "\": 1}}").applyTo(shipped, groups()).getOrThrow());
    }

    @Test
    void aFileWithNoDialsIsEveryDialAtOneAndUnknownKeysAreLeftAlone() {
        assertEquals(Dials.NEUTRAL, dials("{}"));
        assertTrue(dials("{}").isNeutral());
        assertEquals(0.5F, dials("{\"about\": \"a note\", \"roots\": 0.5}").roots());
        assertEquals(Map.of("roots", 0.5F, "growth.lights", 2F), dials("{\"roots\": 0.5, \"falls\": 1, \"growth\": {\"lights\": 2, \"rim\": 1}}").turned());
    }

    @Test
    void aDialOutsideItsRangeIsRefused() {
        for (String json : List.of("{\"holes\": 2}", "{\"roots\": 11}", "{\"falls\": -1}", "{\"springs\": \"many\"}", "{\"growth\": {\"lights\": 11}}")) {
            assertTrue(Dials.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError(), json);
        }
    }

    @Test
    void theDialsAreAllWrittenEvenAtOne() {
        var written = Dials.CODEC.encodeStart(JsonOps.INSTANCE, Dials.NEUTRAL.withNames(Set.of("lights"), Set.of(LAVA))).getOrThrow().getAsJsonObject();

        assertEquals(Set.of("holes", "roots", "falls", "springs", "ruins", "troves", "rough_ground", "haze", "growth", "themes"), written.keySet());
        assertEquals(Set.of("lights"), written.getAsJsonObject("growth").keySet());
        assertEquals(Set.of(LAVA), written.getAsJsonObject("themes").keySet());
    }

    @Test
    void holesTakesHolesAwayAndMovesNoneOfTheRest() throws Exception {
        RavineSettings shipped = DiscThemeTest.shipped();
        RavineSettings half = turned("{\"holes\": 0.5}");
        int kept = 0;
        for (int cellX = 0; cellX < 200; cellX++) {
            Optional<RavineCell> was = RavineCells.at(11L, shipped, cellX, 0);
            Optional<RavineCell> is = RavineCells.at(11L, half, cellX, 0);
            assertTrue(was.isPresent());
            if (is.isPresent()) {
                assertEquals(was, is);
                kept++;
            }
        }

        assertEquals(0.5F, half.chance());
        assertTrue(kept > 70 && kept < 130, kept + " of 200 holes kept");
        assertEquals(0F, turned("{\"holes\": 0}").chance());
    }

    @Test
    void noDialMovesTheGridTheCityIsPlacedOn() throws Exception {
        RavineSettings shipped = DiscThemeTest.shipped();
        RavineSettings all = turned("{\"holes\": 0.5, \"roots\": 3, \"falls\": 3, \"springs\": 3, \"ruins\": 3, \"troves\": 3, \"rough_ground\": 3, "
                + "\"growth\": {\"lights\": 3, \"giants\": 0, \"rim\": 0.3}, \"themes\": {\"" + LAVA + "\": 5}}");

        assertEquals(shipped.salt(), all.salt());
        assertEquals(shipped.cellSize(), all.cellSize());
        assertEquals(shipped.maxReach(), all.maxReach());
        assertEquals(shipped.floor(), all.floor());
        assertEquals(shipped.top(), all.top());
        assertEquals(shipped.cavernRadius(), all.cavernRadius());
        assertEquals(shipped.cavernHeight(), all.cavernHeight());
        assertEquals(shipped.discs(), all.discs());
        assertEquals(shipped.cone(), all.cone());
        assertEquals(shipped.wallNoise(), all.wallNoise());
    }

    @Test
    void rootsTurnsHowManyThereAreAndNotWhatTheyAre() throws Exception {
        RootSettings was = DiscThemeTest.shipped().roots().orElseThrow();
        RootSettings twice = turned("{\"roots\": 2}").roots().orElseThrow();

        assertEquals(times(was.great().count(), 2, RootSettings.Great.MOST), twice.great().count());
        assertEquals(times(was.crossing().count(), 2, RootSettings.Crossing.MOST), twice.crossing().count());
        assertEquals(was.branches().every() / 2, twice.branches().every());
        assertEquals(Math.min(was.links().chance() * 2, 1), twice.links().chance());
        assertEquals(was.great().radius(), twice.great().radius());
        assertEquals(was.branches().byHeight(), twice.branches().byHeight());
        assertEquals(was.woodReach(), twice.woodReach());
        assertEquals(was.growth(), twice.growth());
        assertTrue(turned("{\"roots\": 0}").roots().isEmpty());
    }

    @Test
    void aDialTurnedAllTheWayStopsAtWhatTheSettingsAllow() throws Exception {
        RootSettings most = turned("{\"roots\": 10}").roots().orElseThrow();
        RootSettings least = turned("{\"roots\": 0.001}").roots().orElseThrow();
        RavineSettings smooth = turned("{\"rough_ground\": 0.001}");
        RavineSettings rare = turned("{\"growth\": {\"lights\": 0.001}}");

        assertEquals(new InclusiveRange<>(RootSettings.Great.MOST, RootSettings.Great.MOST), most.great().count());
        assertEquals(new InclusiveRange<>(RootSettings.Crossing.MOST, RootSettings.Crossing.MOST), most.crossing().count());
        assertEquals(1F, most.links().chance());
        assertEquals(new InclusiveRange<>(0, 0), least.great().count());
        assertEquals(RootSettings.Branches.MOST_EVERY, least.branches().every());
        for (DiscTheme theme : smooth.discThemes()) {
            theme.ground().mounds().ifPresent(mounds -> assertEquals(DiscRelief.Mounds.LEAST_COVER, mounds.cover()));
            theme.ground().outcrops().ifPresent(outcrops -> assertEquals(DiscRelief.Outcrops.LEAST_COVER, outcrops.cover()));
        }
        Set<String> lights = groups().get("lights");
        List<DiscTheme.Growth> was = DiscThemeTest.shipped().discThemes().stream().flatMap(theme -> theme.growth().stream()).toList();
        List<DiscTheme.Growth> is = rare.discThemes().stream().flatMap(theme -> theme.growth().stream()).toList();
        int held = 0;
        for (int j = 0; j < was.size(); j++) {
            if (grows(lights, was.get(j))) {
                assertEquals(Math.min(every(was.get(j).every(), 0.001), DiscTheme.Growth.MOST_EVERY), is.get(j).every());
                held += is.get(j).every() == DiscTheme.Growth.MOST_EVERY ? 1 : 0;
            }
        }
        assertTrue(held > 0, "no light of the mod's is rare enough to reach the limit");
    }

    @Test
    void fallsAndSpringsEachTurnTheirOwnCount() throws Exception {
        FallSettings was = DiscThemeTest.shipped().falls().orElseThrow();
        FallSettings moreFalls = turned("{\"falls\": 2}").falls().orElseThrow();
        FallSettings noSprings = turned("{\"springs\": 0}").falls().orElseThrow();

        assertEquals(times(was.count(), 2, FallSettings.MOST), moreFalls.count());
        assertEquals(was.springs(), moreFalls.springs());
        assertEquals(was.count(), noSprings.count());
        assertEquals(new InclusiveRange<>(0, 0), noSprings.springs().orElseThrow().count());
        assertEquals(new InclusiveRange<>(0, 0), turned("{\"falls\": 0}").falls().orElseThrow().count());
    }

    @Test
    void ruinsComeOnMoreDiscsFirstAndThenMoreToADisc() throws Exception {
        List<DiscTheme> was = DiscThemeTest.shipped().discThemes();
        List<DiscTheme> twice = turned("{\"ruins\": 2}").discThemes();
        boolean spread = false;
        boolean thickened = false;
        for (int i = 0; i < was.size(); i++) {
            assertEquals(was.get(i).troves(), twice.get(i).troves());
            if (was.get(i).ruins().isEmpty()) {
                assertTrue(twice.get(i).ruins().isEmpty());
                continue;
            }
            DiscRuins before = was.get(i).ruins().get();
            DiscRuins after = twice.get(i).ruins().get();
            double asked = before.chance() * 2.0;
            assertEquals((float) Math.min(asked, 1), after.chance());
            assertEquals(asked > 1 ? (int) Math.round(before.every() / asked) : before.every(), after.every());
            assertEquals(before.kinds(), after.kinds());
            spread |= asked <= 1;
            thickened |= asked > 1;
        }

        assertTrue(spread && thickened, "the mod's themes no longer show both ways");
    }

    @Test
    void ruinsAndTrovesAtNoneAreTakenAwayEachByItsOwnDial() throws Exception {
        List<DiscTheme> was = DiscThemeTest.shipped().discThemes();
        List<DiscTheme> noRuins = turned("{\"ruins\": 0}").discThemes();
        List<DiscTheme> noTroves = turned("{\"troves\": 0}").discThemes();

        for (int i = 0; i < was.size(); i++) {
            assertTrue(noRuins.get(i).ruins().isEmpty());
            assertEquals(was.get(i).troves(), noRuins.get(i).troves());
            assertTrue(noTroves.get(i).troves().isEmpty());
            assertEquals(was.get(i).ruins(), noTroves.get(i).ruins());
        }
        assertTrue(was.stream().anyMatch(theme -> theme.ruins().isPresent()) && was.stream().anyMatch(theme -> theme.troves().isPresent()));
    }

    @Test
    void roughGroundTurnsHowMuchOfATopItsMoundsAndOutcropsCover() throws Exception {
        List<DiscTheme> was = DiscThemeTest.shipped().discThemes();
        List<DiscTheme> twice = turned("{\"rough_ground\": 2}").discThemes();
        for (int i = 0; i < was.size(); i++) {
            DiscRelief before = was.get(i).ground();
            DiscRelief after = twice.get(i).ground();
            assertEquals(before.mounds().map(mounds -> Math.min(mounds.cover() * 2, 1)), after.mounds().map(DiscRelief.Mounds::cover));
            assertEquals(before.mounds().map(DiscRelief.Mounds::height), after.mounds().map(DiscRelief.Mounds::height));
            assertEquals(before.outcrops().map(outcrops -> Math.min(outcrops.cover() * 2, 1)), after.outcrops().map(DiscRelief.Outcrops::cover));
        }

        assertTrue(was.stream().anyMatch(theme -> theme.ground().mounds().isPresent()));
        assertTrue(turned("{\"rough_ground\": 0}").discThemes().stream()
                .allMatch(theme -> theme.ground().mounds().isEmpty() && theme.ground().outcrops().isEmpty()));
    }

    @Test
    void hazeTurnsTheThicknessOfTheFogAndNoOtherGas() throws Exception {
        GasSettings was = DiscThemeTest.shipped().dressing().gas().orElseThrow();
        GasSettings twice = turned("{\"haze\": 2}").dressing().gas().orElseThrow();
        GasSettings.Fog before = was.fog().orElseThrow();
        GasSettings.Fog after = twice.fog().orElseThrow();
        assertEquals(before.bottomDensity() * 2, after.bottomDensity(), 1e-6);
        assertEquals(before.topDensity() * 2, after.topDensity(), 1e-6);
        assertEquals(new GasSettings.Fog(before.medium(), after.bottomDensity(), after.topDensity(), before.edge()), after);
        assertEquals(was.falls(), twice.falls());
        assertEquals(was.cavern(), twice.cavern());

        GasSettings none = turned("{\"haze\": 0}").dressing().gas().orElseThrow();
        assertTrue(none.fog().isEmpty());
        assertEquals(was.falls(), none.falls());
    }

    @Test
    void aGroupOfGrowthIsTurnedAndNothingElseThatGrows() throws Exception {
        RavineSettings shipped = DiscThemeTest.shipped();
        RavineSettings twice = turned("{\"growth\": {\"lights\": 2}}");
        Set<String> lights = groups().get("lights");
        int turned = 0;
        for (int i = 0; i < shipped.discThemes().size(); i++) {
            List<DiscTheme.Growth> was = shipped.discThemes().get(i).growth();
            List<DiscTheme.Growth> is = twice.discThemes().get(i).growth();
            assertEquals(was.size(), is.size());
            for (int j = 0; j < was.size(); j++) {
                if (grows(lights, was.get(j))) {
                    assertEquals(new DiscTheme.Growth(was.get(j).feature(), every(was.get(j).every(), 2), was.get(j).on(), was.get(j).patches(), was.get(j).rimFade()), is.get(j));
                    turned++;
                } else {
                    assertEquals(was.get(j), is.get(j));
                }
            }
        }
        List<RootSettings.Growth> under = shipped.roots().orElseThrow().growth();
        List<RootSettings.Growth> underTwice = twice.roots().orElseThrow().growth();

        assertTrue(turned >= 8, turned + " growths of lights");
        assertFalse(under.isEmpty());
        for (int j = 0; j < under.size(); j++) {
            assertTrue(lights.contains(under.get(j).feature().location().toString()));
            assertEquals(every(under.get(j).every(), 2), underTwice.get(j).every());
        }
    }

    @Test
    void aGroupAtNoneIsTakenOutAndTheRestKeepTheirOrder() throws Exception {
        RavineSettings shipped = DiscThemeTest.shipped();
        RavineSettings dark = turned("{\"growth\": {\"lights\": 0}}");
        Set<String> lights = groups().get("lights");

        for (int i = 0; i < shipped.discThemes().size(); i++) {
            assertEquals(
                    shipped.discThemes().get(i).growth().stream().filter(growth -> !grows(lights, growth)).toList(),
                    dark.discThemes().get(i).growth());
        }
        assertTrue(dark.roots().orElseThrow().growth().isEmpty());
    }

    @Test
    void aFeatureInTwoGroupsIsTurnedByBoth() throws Exception {
        Map<String, Set<String>> groups = groups();
        var inBoth = new HashSet<>(groups.get("lights"));
        inBoth.retainAll(groups.get("giants"));
        List<DiscTheme.Growth> was = DiscThemeTest.shipped().discThemes().stream().flatMap(theme -> theme.growth().stream()).toList();
        List<DiscTheme.Growth> is = turned("{\"growth\": {\"lights\": 2, \"giants\": 2}}").discThemes().stream().flatMap(theme -> theme.growth().stream()).toList();
        int both = 0;
        for (int j = 0; j < was.size(); j++) {
            if (grows(inBoth, was.get(j))) {
                assertEquals(every(was.get(j).every(), 4), is.get(j).every());
                both++;
            }
        }

        assertTrue(both > 0, "the glowing bulbs are no longer both lights and giants");
    }

    @Test
    void aThemesDialTurnsItsWeightAndNoOthers() throws Exception {
        List<DiscTheme> was = DiscThemeTest.shipped().discThemes();
        List<DiscTheme> is = turned("{\"themes\": {\"" + LAVA + "\": 3}}").discThemes();
        int lava = 0;
        for (int i = 0; i < was.size(); i++) {
            boolean isLava = was.get(i).biome().filter(biome -> biome.location().toString().equals(LAVA)).isPresent();
            assertEquals(was.get(i).weight() * (isLava ? 3 : 1), is.get(i).weight());
            lava += isLava ? 1 : 0;
        }

        assertEquals(1, lava);
        assertTrue(Dials.themeNames(DiscThemeTest.shipped()).contains(LAVA));
    }

    @Test
    void everyFeatureAGroupNamesIsOneTheModsOwnSettingsGrow() throws Exception {
        RavineSettings shipped = DiscThemeTest.shipped();
        var grown = new HashSet<String>();
        shipped.discThemes().forEach(theme -> theme.growth().forEach(growth -> grown.add(growth.feature().location().toString())));
        shipped.roots().orElseThrow().growth().forEach(growth -> grown.add(growth.feature().location().toString()));
        Map<String, Set<String>> groups = groups();

        assertEquals(Set.of("giants", "lights", "rim"), groups.keySet());
        for (var group : groups.entrySet()) {
            assertFalse(group.getValue().isEmpty(), group.getKey());
            for (String id : group.getValue()) {
                assertTrue(grown.contains(id), group.getKey() + " names " + id + ", which nothing grows");
                // A tag that names a feature the game does not have fails to load.
                assertNotNull(DialsTest.class.getResource(FEATURES + id.substring(id.indexOf(':') + 1) + ".json"), id);
            }
        }
        // The rim group is every feature the settings grow on a rim, so that the dial is the whole of what hangs there.
        var onRims = new HashSet<String>();
        shipped.discThemes().forEach(theme -> theme.growth().stream()
                .filter(growth -> growth.on() == DiscTheme.Surface.RIM).forEach(growth -> onRims.add(growth.feature().location().toString())));
        assertEquals(onRims, groups.get("rim"));
    }
}
