package dev.syrval.overgrownabyss.fabric;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import net.fabricmc.api.ModInitializer;

public final class OvergrownAbyssFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        OvergrownAbyss.init();
    }
}
