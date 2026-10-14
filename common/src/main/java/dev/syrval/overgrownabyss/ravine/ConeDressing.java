package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * What a cone holds besides its discs, each part optional: the roots that wind down it (see {@link RootSettings}), the
 * water that falls from disc to disc (see {@link FallSettings}) and the curse on going up or down it too fast (see
 * {@link CurseSettings}). In a file they are written beside a hole's other settings, as {@code roots}, {@code falls} and
 * {@code curse}; this only gathers them, since the settings' codec takes sixteen fields and no more.
 */
public record ConeDressing(Optional<RootSettings> roots, Optional<FallSettings> falls, Optional<CurseSettings> curse) {
    public static final ConeDressing NONE = new ConeDressing(Optional.empty(), Optional.empty());

    static final MapCodec<ConeDressing> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RootSettings.CODEC.optionalFieldOf("roots").forGetter(ConeDressing::roots),
            FallSettings.CODEC.optionalFieldOf("falls").forGetter(ConeDressing::falls),
            CurseSettings.CODEC.optionalFieldOf("curse").forGetter(ConeDressing::curse)
    ).apply(i, ConeDressing::new));

    public ConeDressing(Optional<RootSettings> roots, Optional<FallSettings> falls) {
        this(roots, falls, Optional.empty());
    }
}
