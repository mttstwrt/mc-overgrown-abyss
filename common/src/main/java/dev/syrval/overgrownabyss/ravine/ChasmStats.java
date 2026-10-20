package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * What one cell's hole holds: its discs, how many carry each theme, its troves, and the chests, barrels, spawners and decorated pots that the mod's
 * own ruins and troves place. Nothing of other mods or of vanilla's structures is counted, since only the pieces the mod puts on
 * its discs are asked. Built from the cell's discs and a function that says what a piece holds, so it needs no level.
 *
 * @param discs      the number of discs
 * @param themes     the discs of each theme, by the theme's label (see {@link #labelOf}), in the order the settings list them
 * @param ruins      the number of ruins
 * @param troves     the number of troves
 * @param containers what the ruins and the troves hold between them
 */
public record ChasmStats(int discs, Map<String, Integer> themes, int ruins, int troves, Containers containers) {
    public ChasmStats {
        themes = Collections.unmodifiableMap(new LinkedHashMap<>(themes));
    }

    /** The containers of one piece, or of many. */
    public record Containers(int chests, int barrels, int spawners, int pots) {
        public static final Containers NONE = new Containers(0, 0, 0, 0);

        public Containers plus(Containers other) {
            return new Containers(chests + other.chests, barrels + other.barrels, spawners + other.spawners, pots + other.pots);
        }

        /**
         * The containers a template's saved form places, counted per block that uses a palette entry of that name: a palette
         * entry that no block uses counts nothing, and one that many blocks use counts each. A template with several palettes
         * has the same blocks in each, so the first is read.
         */
        public static Containers ofTemplate(CompoundTag template) {
            ListTag palette = template.contains("palettes", Tag.TAG_LIST)
                    ? template.getList("palettes", Tag.TAG_LIST).getList(0)
                    : template.getList("palette", Tag.TAG_COMPOUND);
            int chests = 0;
            int barrels = 0;
            int spawners = 0;
            int pots = 0;
            for (Tag each : template.getList("blocks", Tag.TAG_COMPOUND)) {
                switch (palette.getCompound(((CompoundTag) each).getInt("state")).getString("Name")) {
                    case "minecraft:chest" -> chests++;
                    case "minecraft:barrel" -> barrels++;
                    case "minecraft:spawner" -> spawners++;
                    case "minecraft:decorated_pot" -> pots++;
                    default -> {}
                }
            }
            return new Containers(chests, barrels, spawners, pots);
        }
    }

    static ChasmStats of(RavineSettings settings, CellDiscs cell, Function<RuinSite, Containers> held) {
        Map<String, Integer> themes = new LinkedHashMap<>();
        for (Optional<DiscTheme> theme : cell.themes()) {
            themes.merge(labelOf(settings, theme), 1, Integer::sum);
        }
        Containers containers = Containers.NONE;
        for (RuinSite site : cell.ruins()) {
            containers = containers.plus(held.apply(site));
        }
        for (RuinSite site : cell.troves()) {
            containers = containers.plus(held.apply(site));
        }
        return new ChasmStats(cell.layout().discs().size(), themes, cell.ruins().size(), cell.troves().size(), containers);
    }

    // A theme has no name of its own: its biome tells it apart, and a theme with none is known by its place in the settings.
    private static String labelOf(RavineSettings settings, Optional<DiscTheme> theme) {
        if (theme.isEmpty()) {
            return "no theme";
        }
        return theme.get().biome().map(key -> key.location().toString()).orElseGet(() -> "theme #" + settings.discThemes().indexOf(theme.get()));
    }

    /** The report as lines of chat. */
    public List<String> format() {
        List<String> lines = new ArrayList<>();
        lines.add("Discs: " + discs);
        themes.forEach((label, count) -> lines.add("  " + label + ": " + count));
        lines.add("Ruins: " + ruins + ", troves: " + troves);
        lines.add("Chests: " + containers.chests() + ", barrels: " + containers.barrels() + ", spawners: " + containers.spawners() + ", pots: " + containers.pots());
        return lines;
    }
}
