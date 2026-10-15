package dev.syrval.overgrownabyss.compat;

import dev.architectury.networking.NetworkManager;
import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.ravine.HoleGas;
import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends each player the gas of the holes near them (see {@link HoleGas}), and takes it away again when they go. All that is
 * kept of a player is which holes they have been sent, on the server thread. Which holes are near, and their gas, are asked
 * away from that thread, since the first time anyone comes near a hole its discs may not be built yet; what comes back is
 * acted on, on the server thread, if the player is still in the same level.
 *
 * <p>A player whose client cannot take the payload, having no Overgrown Abyss, is sent nothing.
 */
public final class GasTicker {
    // Twice a second keeps well ahead of a player: a hole is sent long before any of it is in the gas library's windows.
    private static final int EVERY_TICKS = 10;
    // The gas library's far window reaches 256 blocks, and a hole's discs up to 200 from its middle.
    private static final double SEND_WITHIN = 512;
    // A little further, so that a player on the edge is not sent the same hole over and over.
    private static final double KEEP_WITHIN = SEND_WITHIN + 64;

    private final Map<UUID, Tracked> players = new HashMap<>();
    private final Set<Long> warned = new HashSet<>();

    /** The holes one player has been sent in one level, and whether an answer about the holes round them is on its way. */
    private static final class Tracked {
        private final ServerLevel level;
        private final Set<Long> holes = new HashSet<>();
        private boolean asking;

        Tracked(ServerLevel level) {
            this.level = level;
        }
    }

    public void tick(ServerPlayer player) {
        if (player.tickCount % EVERY_TICKS != 0 || !NetworkManager.canPlayerReceive(player, HoleGasPayload.TYPE)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Tracked known = players.get(player.getUUID());
        if (known == null || known.level != level) {
            known = new Tracked(level);
            players.put(player.getUUID(), known);
        }
        if (known.asking) {
            return;
        }
        known.asking = true;
        Tracked asked = known;
        double x = player.getX();
        double z = player.getZ();
        RavineFootprint footprint = RavineFootprintHolder.of(level);
        CompletableFuture.supplyAsync(() -> footprint.gasNear(x, z, KEEP_WITHIN), Util.backgroundExecutor())
                .whenCompleteAsync((near, failure) -> {
                    asked.asking = false;
                    if (failure != null) {
                        OvergrownAbyss.LOGGER.error("Could not work out the gas of the holes near {}", player.getScoreboardName(), failure);
                    } else if (!player.hasDisconnected() && players.get(player.getUUID()) == asked) {
                        update(player, asked, near, x, z);
                    }
                }, player.server);
    }

    private void update(ServerPlayer player, Tracked known, List<RavineFootprint.GasNear> near, double x, double z) {
        Set<Long> kept = new HashSet<>();
        for (RavineFootprint.GasNear hole : near) {
            kept.add(hole.hole());
            if (Math.hypot(hole.x() - x, hole.z() - z) <= SEND_WITHIN && known.holes.add(hole.hole())) {
                warnIfCut(hole);
                NetworkManager.sendToPlayer(player, new HoleGasPayload(hole.hole(), hole.gas().sources()));
            }
        }
        for (Long hole : List.copyOf(known.holes)) {
            if (!kept.contains(hole)) {
                known.holes.remove(hole);
                NetworkManager.sendToPlayer(player, HoleGasPayload.gone(hole));
            }
        }
    }

    private void warnIfCut(RavineFootprint.GasNear hole) {
        if (hole.gas().dropped() > 0 && warned.add(hole.hole())) {
            OvergrownAbyss.LOGGER.warn("A hole has {} more gas sources than the {} sent; its themes spread gas too thickly",
                    hole.gas().dropped(), HoleGas.MAX_SOURCES);
        }
    }

    /** A logout, a respawn and a change of dimension start the player over: their client has none of the gas it had. */
    public void forget(ServerPlayer player) {
        players.remove(player.getUUID());
    }

    public void clear() {
        players.clear();
        warned.clear();
    }
}
