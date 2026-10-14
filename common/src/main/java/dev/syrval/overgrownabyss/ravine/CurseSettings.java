package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * The curse of descent: a player inside a hole has a depth they are adjusted to, which follows their real height at a fixed
 * rate, and the gap between the two becomes wither. A lift at or under the rate never opens a gap; a drop, a pearl or a push
 * does, and waiting closes it. See {@link CurseTracker}.
 *
 * @param rateDown        blocks a tick the adjusted depth follows a player going down
 * @param rateUp          blocks a tick it follows a player going up
 * @param levels          the gaps, in blocks, at which wither I, II and so on begin; a gap under the first costs nothing
 * @param duration        ticks the effect lasts once given, so it lapses a little after the gap closes
 * @param margin          blocks outside the hole's wall that still count as inside it
 * @param depthStrictness how much the gaps shrink toward the hole's floor: 0 for the same all the way, 0.5 for half at the floor
 */
public record CurseSettings(double rateDown, double rateUp, List<Double> levels, int duration, double margin, double depthStrictness) {
    public static final Codec<CurseSettings> CODEC = RecordCodecBuilder.<CurseSettings>create(i -> i.group(
            Codec.doubleRange(0.001, 4).fieldOf("rate_down").forGetter(CurseSettings::rateDown),
            Codec.doubleRange(0.001, 4).fieldOf("rate_up").forGetter(CurseSettings::rateUp),
            Codec.doubleRange(1, 512).listOf().fieldOf("levels").forGetter(CurseSettings::levels),
            Codec.intRange(1, 1200).fieldOf("duration").forGetter(CurseSettings::duration),
            Codec.doubleRange(0, 32).optionalFieldOf("margin", 4.0).forGetter(CurseSettings::margin),
            Codec.doubleRange(0, 0.9).optionalFieldOf("depth_strictness", 0.0).forGetter(CurseSettings::depthStrictness)
    ).apply(i, CurseSettings::new)).validate(CurseSettings::validate);

    public CurseSettings {
        levels = List.copyOf(levels);
    }

    private static DataResult<CurseSettings> validate(CurseSettings settings) {
        if (settings.levels.isEmpty() || settings.levels.size() > 5) {
            return DataResult.error(() -> "curse levels must list one to five gaps");
        }
        for (int at = 1; at < settings.levels.size(); at++) {
            if (settings.levels.get(at) <= settings.levels.get(at - 1)) {
                return DataResult.error(() -> "curse levels must each be a wider gap than the one before");
            }
        }
        return DataResult.success(settings);
    }
}
