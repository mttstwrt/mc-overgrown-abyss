package dev.syrval.overgrownabyss;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class OvergrownAbyss {
    public static final String MOD_ID = "overgrown_abyss";
    public static final Logger LOGGER = LogUtils.getLogger();

    private OvergrownAbyss() {}

    /** Called once by each loader entrypoint. */
    public static void init() {
        LOGGER.info("Overgrown Abyss initialised");
    }
}
