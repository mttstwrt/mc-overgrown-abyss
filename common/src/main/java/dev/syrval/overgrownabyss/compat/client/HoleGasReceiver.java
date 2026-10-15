package dev.syrval.overgrownabyss.compat.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.syrval.overgrownabyss.compat.HoleGasPayload;

/**
 * The client's end of the gas the server sends (see {@code GasTicker}). Registered only where the gas library is, so a client
 * without it never says it can take the payload and is never sent one.
 */
public final class HoleGasReceiver {
    private HoleGasReceiver() {}

    /** On a client that has the gas library, once, while the mod starts. */
    public static void register() {
        GasDrawer drawer = VolumetricsGas.create();
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, HoleGasPayload.TYPE, HoleGasPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> drawer.accept(context.getPlayer().level(), payload)));
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> drawer.clear());
    }
}
