package dev.syrval.overgrownabyss.ravine;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** {@code VerticalAnchor} initialises vanilla registries, so settings tests need the standard game bootstrap. */
final class MinecraftBootstrap {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private MinecraftBootstrap() {}

    static void init() {}
}
