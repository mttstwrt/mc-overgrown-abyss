package dev.syrval.verdantdeep.compat;

import dev.syrval.verdantdeep.ravine.CurseSettings;
import dev.syrval.verdantdeep.ravine.CurseTracker;
import dev.syrval.verdantdeep.ravine.RavineFootprint;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Steps the curse of descent for each player once a tick (see {@link CurseTracker}) and gives wither by the gap. The state is
 * one number a player, kept on the server thread only; nothing is sent to the client but the effect itself.
 */
public final class CurseTicker {
    private final Map<UUID, Double> adjusted = new HashMap<>();

    public void tick(ServerPlayer player) {
        // Creative and spectating players are never cursed, and what they were adjusted to is not kept.
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            forget(player);
            return;
        }
        RavineFootprint footprint = RavineFootprintHolder.of(player.serverLevel());
        CurseSettings settings = footprint.curse().orElse(null);
        if (settings == null) {
            forget(player);
            return;
        }
        double y = player.getY();
        OptionalDouble inside = footprint.depthShareIn(player.getBlockX(), player.getBlockY(), player.getBlockZ(), settings.margin());
        Double before = adjusted.get(player.getUUID());
        CurseTracker.Step step = CurseTracker.step(before == null ? OptionalDouble.empty() : OptionalDouble.of(before), y, inside, settings);
        step.adjusted().ifPresentOrElse(value -> adjusted.put(player.getUUID(), value), () -> adjusted.remove(player.getUUID()));
        if (step.amplifier() != CurseTracker.NONE) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, settings.duration(), step.amplifier(), false, true, true));
        }
    }

    /** A death, a logout and a change of dimension start the player over: they come back in adjusted to where they stand. */
    public void forget(ServerPlayer player) {
        adjusted.remove(player.getUUID());
    }

    public void clear() {
        adjusted.clear();
    }
}
