package dev.syrval.overgrownabyss;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.syrval.overgrownabyss.ravine.RavineCarve;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.slf4j.Logger;

public final class OvergrownAbyss {
    public static final String MOD_ID = "overgrown_abyss";
    public static final Logger LOGGER = LogUtils.getLogger();

    private OvergrownAbyss() {}

    /** Called once by each loader entrypoint. */
    public static void init() {
        DeferredRegister<MapCodec<? extends DensityFunction>> densityFunctionTypes =
                DeferredRegister.create(MOD_ID, Registries.DENSITY_FUNCTION_TYPE);
        densityFunctionTypes.register("ravine_carve", () -> RavineCarve.MAP_CODEC);
        densityFunctionTypes.register();
        LOGGER.info("Overgrown Abyss initialised");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
