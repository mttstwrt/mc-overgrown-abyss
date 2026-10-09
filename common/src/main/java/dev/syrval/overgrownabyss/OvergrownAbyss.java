package dev.syrval.overgrownabyss;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.syrval.overgrownabyss.compat.DiscRuinsPlacement;
import dev.syrval.overgrownabyss.compat.DiscRuinsStructure;
import dev.syrval.overgrownabyss.compat.NestedProcessorListProcessor;
import dev.syrval.overgrownabyss.compat.RavineCentrePlacement;
import dev.syrval.overgrownabyss.compat.RavineCityStructure;
import dev.syrval.overgrownabyss.compat.SwapBlocksProcessor;
import dev.syrval.overgrownabyss.ravine.RavineCarve;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
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

        DeferredRegister<StructureType<?>> structureTypes = DeferredRegister.create(MOD_ID, Registries.STRUCTURE_TYPE);
        structureTypes.register("city", () -> RavineCityStructure.TYPE);
        structureTypes.register("disc_ruins", () -> DiscRuinsStructure.TYPE);
        structureTypes.register();

        DeferredRegister<StructurePlacementType<?>> placementTypes = DeferredRegister.create(MOD_ID, Registries.STRUCTURE_PLACEMENT);
        placementTypes.register("ravine_centre", () -> RavineCentrePlacement.TYPE);
        placementTypes.register("disc_ruins", () -> DiscRuinsPlacement.TYPE);
        placementTypes.register();
        DeferredRegister<StructureProcessorType<?>> processorTypes = DeferredRegister.create(MOD_ID, Registries.STRUCTURE_PROCESSOR);
        processorTypes.register("swap_blocks", () -> SwapBlocksProcessor.TYPE);
        processorTypes.register("processor_list", () -> NestedProcessorListProcessor.TYPE);
        processorTypes.register();

        LOGGER.info("Overgrown Abyss initialised (build {})", buildDescription());
    }

    // The jar carries the commit it was built from, so a log line shows whether a world ran the latest code.
    private static String buildDescription() {
        Properties build = new Properties();
        try (InputStream stream = OvergrownAbyss.class.getResourceAsStream("/overgrown_abyss-build.properties")) {
            if (stream != null) {
                build.load(stream);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read the build stamp", e);
        }
        return build.getProperty("commit", "unknown") + ("true".equals(build.getProperty("dirty")) ? " + uncommitted changes" : "");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
