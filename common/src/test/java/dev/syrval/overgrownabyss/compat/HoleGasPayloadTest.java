package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syrval.overgrownabyss.ravine.GasFog;
import dev.syrval.overgrownabyss.ravine.GasShape;
import dev.syrval.overgrownabyss.ravine.GasSource;
import dev.syrval.overgrownabyss.ravine.HoleGas;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** What is sent of a hole's gas comes back as it was, and the kinds of source are written once. */
class HoleGasPayloadTest {
    private static final ResourceLocation MIST = ResourceLocation.fromNamespaceAndPath("overgrown_abyss", "mist");
    private static final ResourceLocation EMBER = ResourceLocation.fromNamespaceAndPath("overgrown_abyss", "ember");
    private static final ResourceLocation HAZE = ResourceLocation.fromNamespaceAndPath("overgrown_abyss", "haze");

    private static HoleGasPayload roundTrip(HoleGasPayload payload) {
        ByteBuf buf = Unpooled.buffer();
        HoleGasPayload.STREAM_CODEC.encode(buf, payload);
        HoleGasPayload read = HoleGasPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "bytes left over");
        return read;
    }

    @Test
    void aHolesGasComesBackAsItWasSent() {
        var sources = List.of(
                new GasSource(MIST, new GasShape.Cylinder(3, 1), 1024.5, 63, -2047.5, 0.6F),
                new GasSource(EMBER, new GasShape.Sphere(1.5F), 30_000_000.5, -40.25, 12.5, 1.2F),
                new GasSource(MIST, new GasShape.Cylinder(3, 1), 1030.5, 64, -2040.5, 0.6F));
        var fogs = List.of(new GasFog(HAZE, 1024.5, -64, -2047.5, 174, 69, 344, 0.022F, 0.004F, 24));
        var payload = new HoleGasPayload(-123_456_789_012L, sources, fogs);
        assertEquals(payload, roundTrip(payload));
    }

    @Test
    void sourcesOfOneKindShareOneEntry() {
        var many = new ArrayList<GasSource>();
        for (int i = 0; i < 100; i++) {
            many.add(new GasSource(MIST, new GasShape.Cylinder(3, 1), i, 0, i, 0.6F));
        }
        ByteBuf buf = Unpooled.buffer();
        HoleGasPayload.STREAM_CODEC.encode(buf, new HoleGasPayload(1, many, List.of()));
        // An id, one kind, and for each source a one-byte index and three doubles.
        assertTrue(buf.readableBytes() < 100 * 26 + 64, "written in " + buf.readableBytes() + " bytes");
    }

    @Test
    void aHoleWithNoSourcesHasItsGasTakenAway() {
        assertEquals(HoleGasPayload.gone(7), roundTrip(HoleGasPayload.gone(7)));
        assertTrue(roundTrip(HoleGasPayload.gone(7)).isGone());
    }

    @Test
    void aCountPastTheCapIsRefusedBeforeAnythingIsMade() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_LONG.encode(buf, 1L);
        ByteBufCodecs.VAR_INT.encode(buf, HoleGas.MAX_SOURCES + 1);
        assertThrows(DecoderException.class, () -> HoleGasPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void moreSourcesThanAHoleMaySendAreRefused() {
        var many = new ArrayList<GasSource>();
        for (int i = 0; i <= HoleGas.MAX_SOURCES; i++) {
            many.add(new GasSource(MIST, new GasShape.Sphere(1), i, 0, 0, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new HoleGasPayload(1, many, List.of()));
    }
}
