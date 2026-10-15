package dev.syrval.overgrownabyss;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.architectury.registry.registries.DeferredRegister;
import dev.syrval.overgrownabyss.compat.CurseTicker;
import dev.syrval.overgrownabyss.compat.DiscRuinsPlacement;
import dev.syrval.overgrownabyss.compat.DiscRuinsStructure;
import dev.syrval.overgrownabyss.compat.GasTicker;
import dev.syrval.overgrownabyss.compat.HoleGasPayload;
import dev.syrval.overgrownabyss.compat.NestedProcessorListProcessor;
import dev.syrval.overgrownabyss.compat.RavineCentrePlacement;
import dev.syrval.overgrownabyss.compat.RavineCityStructure;
import dev.syrval.overgrownabyss.compat.SwapBlocksProcessor;
import dev.syrval.overgrownabyss.compat.client.HoleGasReceiver;
import dev.syrval.overgrownabyss.ravine.RavineCarve;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    // The gas library: an optional dependency, drawn on the client from media the server's data gives it.
    private static final String GAS_LIBRARY = "volumetrics";

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

        registerCurse();
        registerGas();

        LOGGER.info("Overgrown Abyss initialised (build {})", buildDescription());
    }

    // The curse of descent runs outside world generation, as the gas does; a level without a `curse` block does nothing.
    private static void registerCurse() {
        CurseTicker curse = new CurseTicker();
        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                curse.tick(serverPlayer);
            }
        });
        PlayerEvent.PLAYER_QUIT.register(curse::forget);
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) -> curse.forget(player));
        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> curse.forget(player));
        LifecycleEvent.SERVER_STOPPED.register(server -> curse.clear());
    }

    // Gas is drawn by the gas library on the client, from that library's media in the server's data, so nothing of it runs
    // where the library is not: no payload is registered there, and a client without it is never sent any.
    private static void registerGas() {
        if (!Platform.isModLoaded(GAS_LIBRARY)) {
            return;
        }
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(HoleGasPayload.TYPE, HoleGasPayload.STREAM_CODEC);
        } else {
            HoleGasReceiver.register();
        }
        GasTicker gas = new GasTicker();
        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                gas.tick(serverPlayer);
            }
        });
        PlayerEvent.PLAYER_QUIT.register(gas::forget);
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) -> gas.forget(player));
        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> gas.forget(player));
        LifecycleEvent.SERVER_STOPPED.register(server -> gas.clear());
        LOGGER.info("Overgrown Abyss gives its holes gas through {}", GAS_LIBRARY);
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
