package dev.syrval.overgrownabyss.compat.client;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.compat.HoleGasPayload;
import dev.syrval.overgrownabyss.ravine.GasFog;
import dev.syrval.overgrownabyss.ravine.GasShape;
import dev.syrval.overgrownabyss.ravine.GasSource;
import dev.syrval.volumetrics.api.EmitterShape;
import dev.syrval.volumetrics.api.Fog;
import dev.syrval.volumetrics.api.FogShape;
import dev.syrval.volumetrics.api.Medium;
import dev.syrval.volumetrics.api.VolumetricsRegistries;
import dev.syrval.volumetrics.api.client.GasEmitters;
import dev.syrval.volumetrics.api.client.GasFogs;
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
 * Gas drawn through the gas library: each source sent becomes one of its emitters from code, and each fog one of its fogs, in
 * the client's level. The only class of the mod that knows that library; it is loaded only when the library is there.
 *
 * <p>Each gas is looked up among the library's media, which the server's datapacks give the client: the mod's own are in
 * its data, so the server needs the library too. A gas the client was not given is skipped, and named once in the log.
 */
final class VolumetricsGas implements GasDrawer {
    private final Map<Long, Drawn> holes = new HashMap<>();
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
        Drawn old = holes.remove(payload.hole());
        if (old != null) {
            old.remove();
        }
        if (payload.isGone()) {
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
        GasFogs fogs = GasFogs.of(client);
        List<GasFogs.Handle> madeFogs = new ArrayList<>(payload.fogs().size());
        for (GasFog fog : payload.fogs()) {
            Optional<Medium> medium = found.computeIfAbsent(fog.medium(), id -> media.flatMap(registry -> registry.getOptional(id)));
            if (medium.isEmpty()) {
                warnOfMissing(fog.medium());
                continue;
            }
            madeFogs.add(fogs.add(new Fog(medium.get().appearance(), new FogShape.Round(fog.bottomRadius(), fog.topRadius(), fog.height()),
                    new Vec3(fog.x(), fog.y(), fog.z()), fog.bottomDensity(), fog.topDensity(), fog.edge())));
        }
        holes.put(payload.hole(), new Drawn(made, madeFogs));
    }

    @Override
    public void clear() {
        holes.clear();
        level = null;
    }

    /** What one hole has in the library: its emitters and its fogs. */
    private record Drawn(List<GasEmitters.Handle> emitters, List<GasFogs.Handle> fogs) {
        void remove() {
            emitters.forEach(GasEmitters.Handle::remove);
            fogs.forEach(GasFogs.Handle::remove);
        }
    }

    // Each kind returns on its own. Where two branches met holding two of the library's types, the dev client's class
    // transformer would have to load the library to find what they share, and the library is not on its path.
    private static EmitterShape shapeOf(GasShape shape) {
        if (shape instanceof GasShape.Cylinder cylinder) {
            return new EmitterShape.Disc(cylinder.radius(), cylinder.height());
        }
        return new EmitterShape.Sphere(((GasShape.Sphere) shape).radius());
    }

    private void warnOfMissing(ResourceLocation medium) {
        if (missing.add(medium)) {
            OvergrownAbyss.LOGGER.warn("The gas {} is not among the media this world gives the client; its sources are left out", medium);
        }
    }
}
