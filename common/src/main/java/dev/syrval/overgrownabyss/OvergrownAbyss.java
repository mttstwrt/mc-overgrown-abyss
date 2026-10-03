package dev.syrval.overgrownabyss;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.architectury.registry.registries.DeferredRegister;
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
        structureTypes.register();

        DeferredRegister<StructurePlacementType<?>> placementTypes = DeferredRegister.create(MOD_ID, Registries.STRUCTURE_PLACEMENT);
        placementTypes.register("ravine_centre", () -> RavineCentrePlacement.TYPE);
        placementTypes.register();
        DeferredRegister<StructureProcessorType<?>> processorTypes = DeferredRegister.create(MOD_ID, Registries.STRUCTURE_PROCESSOR);
        processorTypes.register("swap_blocks", () -> SwapBlocksProcessor.TYPE);
        processorTypes.register("processor_list", () -> NestedProcessorListProcessor.TYPE);
        processorTypes.register();

        LOGGER.info("Overgrown Abyss initialised");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
