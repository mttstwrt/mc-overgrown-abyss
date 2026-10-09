package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Which theme each disc of a cell has: a weighted draw from the cell's hash, with weights that follow the disc's traits. */
final class DiscThemes {
    private static final int THEME_HASH_BASE = 200_000;

    private DiscThemes() {}

    /**
     * The theme of each disc, given each disc's traits in the layout's order; empty for a disc no theme has any weight for,
     * which stays plain rock.
     */
    static List<Optional<DiscTheme>> assign(RavineSettings settings, RavineCell cell, List<DiscTraits> traits) {
        List<DiscTheme> themes = settings.discThemes();
        var assigned = new ArrayList<Optional<DiscTheme>>(traits.size());
        for (int i = 0; i < traits.size(); i++) {
            OptionalInt picked = pick(themes, traits.get(i), RavineCells.unit(cell.hash(), THEME_HASH_BASE + i));
            assigned.add(picked.isPresent() ? Optional.of(themes.get(picked.getAsInt())) : Optional.empty());
        }
        return List.copyOf(assigned);
    }

    /** Which of the themes a uniform draw in [0, 1) picks for a disc with these traits; empty if none has any weight for it. */
    static OptionalInt pick(List<DiscTheme> themes, DiscTraits traits, double unit) {
        double total = 0;
        for (DiscTheme theme : themes) {
            total += theme.weightFor(traits);
        }
        if (total <= 0) {
            return OptionalInt.empty();
        }
        double left = unit * total;
        int last = -1;
        for (int i = 0; i < themes.size(); i++) {
            double weight = themes.get(i).weightFor(traits);
            if (weight <= 0) {
                continue;
            }
            last = i;
            left -= weight;
            if (left < 0) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.of(last);
    }
}
