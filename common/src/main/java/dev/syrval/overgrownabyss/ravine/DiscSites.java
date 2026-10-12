package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * The two lists of pieces a theme stands on its discs: its ruins, and its troves, which are counted apart from them (see
 * {@link DiscTheme}). Each is built by a structure of its own, so that either can be looked for by itself.
 */
public enum DiscSites implements StringRepresentable {
    RUINS("ruins", 800_000),
    TROVES("troves", 900_000);

    public static final Codec<DiscSites> CODEC = StringRepresentable.fromEnum(DiscSites::values);
    private final String name;
    private final int hashBase;

    DiscSites(String name, int hashBase) {
        this.name = name;
        this.hashBase = hashBase;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The first of the hash indices this list's draws are made from, kept apart from the other's. */
    int hashBase() {
        return hashBase;
    }

    /** This list as one theme has it. */
    public Optional<DiscRuins> of(DiscTheme theme) {
        return this == RUINS ? theme.ruins() : theme.troves();
    }
}
