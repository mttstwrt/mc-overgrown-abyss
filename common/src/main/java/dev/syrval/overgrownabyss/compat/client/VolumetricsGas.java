package dev.syrval.overgrownabyss.compat.client;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.compat.HoleGasPayload;
import dev.syrval.overgrownabyss.ravine.GasShape;
import dev.syrval.overgrownabyss.ravine.GasSource;
import dev.syrval.volumetrics.api.EmitterShape;
import dev.syrval.volumetrics.api.Medium;
import dev.syrval.volumetrics.api.VolumetricsRegistries;
import dev.syrval.volumetrics.api.client.GasEmitters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Gas drawn through the gas library: each source sent becomes one of its emitters from code, in the client's level. The
 * only class of the mod that knows that library; it is loaded only when the library is there.
 *
 * <p>Each gas is looked up among the library's media, which the server's datapacks give the client: the mod's own are in
 * its data, so the server needs the library too. A gas the client was not given is skipped, and named once in the log.
 */
final class VolumetricsGas implements GasDrawer {
    private final Map<Long, List<GasEmitters.Handle>> holes = new HashMap<>();
    private final Set<ResourceLocation> missing = new HashSet<>();
    // The level the emitters were made in. They go with it, so a payload for another level starts afresh.
    private @Nullable ClientLevel level;

    private VolumetricsGas() {}

    static GasDrawer create() {
        return new VolumetricsGas();
    }

    @Override
    public void accept(Level level, HoleGasPayload payload) {
        if (!(level instanceof ClientLevel client)) {
            return;
        }
        if (client != this.level) {
            holes.clear();
            this.level = client;
        }
        List<GasEmitters.Handle> old = holes.remove(payload.hole());
        if (old != null) {
            old.forEach(GasEmitters.Handle::remove);
        }
        if (payload.sources().isEmpty()) {
            return;
        }
        Optional<Registry<Medium>> media = client.registryAccess().registry(VolumetricsRegistries.MEDIUM);
        Map<ResourceLocation, Optional<Medium>> found = new HashMap<>();
        GasEmitters emitters = GasEmitters.of(client);
        List<GasEmitters.Handle> made = new ArrayList<>(payload.sources().size());
        for (GasSource source : payload.sources()) {
            Optional<Medium> medium = found.computeIfAbsent(source.medium(), id -> media.flatMap(registry -> registry.getOptional(id)));
            if (medium.isEmpty()) {
                warnOfMissing(source.medium());
                continue;
            }
            made.add(emitters.add(medium.get(), shapeOf(source.shape()), new Vec3(source.x(), source.y(), source.z()), source.rate()));
        }
        holes.put(payload.hole(), made);
    }

    @Override
    public void clear() {
        holes.clear();
        level = null;
    }

    private static EmitterShape shapeOf(GasShape shape) {
        return switch (shape) {
            case GasShape.Cylinder cylinder -> new EmitterShape.Disc(cylinder.radius(), cylinder.height());
            case GasShape.Sphere sphere -> new EmitterShape.Sphere(sphere.radius());
        };
    }

    private void warnOfMissing(ResourceLocation medium) {
        if (missing.add(medium)) {
            OvergrownAbyss.LOGGER.warn("The gas {} is not among the media this world gives the client; its sources are left out", medium);
        }
    }
}
