package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.ravine.GasShape;
import dev.syrval.overgrownabyss.ravine.GasSource;
import dev.syrval.overgrownabyss.ravine.HoleGas;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The gas of one hole, sent to a player who comes near it: every source, or none to take the hole's gas away again. The kinds
 * of source (a gas, a shape and a rate) are written once and each source names its kind, since a hole has thousands of
 * sources and a handful of kinds.
 *
 * @param hole the id the server gives the hole (see {@code RavineCarve.gasNear}); a later payload for it replaces this one
 */
public record HoleGasPayload(long hole, List<GasSource> sources) implements CustomPacketPayload {
    public static final Type<HoleGasPayload> TYPE = new Type<>(OvergrownAbyss.id("hole_gas"));
    public static final StreamCodec<ByteBuf, HoleGasPayload> STREAM_CODEC = StreamCodec.of(HoleGasPayload::write, HoleGasPayload::read);
    private static final byte CYLINDER = 0;
    private static final byte SPHERE = 1;

    public HoleGasPayload {
        if (sources.size() > HoleGas.MAX_SOURCES) {
            throw new IllegalArgumentException("A hole sends at most " + HoleGas.MAX_SOURCES + " sources, got " + sources.size());
        }
        sources = List.copyOf(sources);
    }

    /** The payload that takes a hole's gas away. */
    public static HoleGasPayload gone(long hole) {
        return new HoleGasPayload(hole, List.of());
    }

    @Override
    public Type<HoleGasPayload> type() {
        return TYPE;
    }

    private record Kind(ResourceLocation medium, GasShape shape, float rate) {}

    private static void write(ByteBuf buf, HoleGasPayload payload) {
        ByteBufCodecs.VAR_LONG.encode(buf, payload.hole);
        Map<Kind, Integer> kinds = new LinkedHashMap<>();
        for (GasSource source : payload.sources) {
            kinds.putIfAbsent(new Kind(source.medium(), source.shape(), source.rate()), kinds.size());
        }
        ByteBufCodecs.VAR_INT.encode(buf, kinds.size());
        for (Kind kind : kinds.keySet()) {
            ResourceLocation.STREAM_CODEC.encode(buf, kind.medium());
            writeShape(buf, kind.shape());
            buf.writeFloat(kind.rate());
        }
        ByteBufCodecs.VAR_INT.encode(buf, payload.sources.size());
        for (GasSource source : payload.sources) {
            ByteBufCodecs.VAR_INT.encode(buf, kinds.get(new Kind(source.medium(), source.shape(), source.rate())));
            buf.writeDouble(source.x());
            buf.writeDouble(source.y());
            buf.writeDouble(source.z());
        }
    }

    private static HoleGasPayload read(ByteBuf buf) {
        long hole = ByteBufCodecs.VAR_LONG.decode(buf);
        int kindCount = count(buf);
        List<Kind> kinds = new ArrayList<>(kindCount);
        for (int k = 0; k < kindCount; k++) {
            kinds.add(new Kind(ResourceLocation.STREAM_CODEC.decode(buf), readShape(buf), buf.readFloat()));
        }
        int sourceCount = count(buf);
        List<GasSource> sources = new ArrayList<>(sourceCount);
        for (int s = 0; s < sourceCount; s++) {
            int index = ByteBufCodecs.VAR_INT.decode(buf);
            if (index < 0 || index >= kinds.size()) {
                throw new DecoderException("Gas source of kind " + index + " of " + kinds.size());
            }
            Kind kind = kinds.get(index);
            sources.add(new GasSource(kind.medium(), kind.shape(), buf.readDouble(), buf.readDouble(), buf.readDouble(), kind.rate()));
        }
        return new HoleGasPayload(hole, sources);
    }

    // A count read from the wire is checked before anything is made that size.
    private static int count(ByteBuf buf) {
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        if (count < 0 || count > HoleGas.MAX_SOURCES) {
            throw new DecoderException("Gas count out of range: " + count);
        }
        return count;
    }

    private static void writeShape(ByteBuf buf, GasShape shape) {
        switch (shape) {
            case GasShape.Cylinder cylinder -> {
                buf.writeByte(CYLINDER);
                buf.writeFloat(cylinder.radius());
                buf.writeFloat(cylinder.height());
            }
            case GasShape.Sphere sphere -> {
                buf.writeByte(SPHERE);
                buf.writeFloat(sphere.radius());
            }
        }
    }

    private static GasShape readShape(ByteBuf buf) {
        byte type = buf.readByte();
        return switch (type) {
            case CYLINDER -> new GasShape.Cylinder(buf.readFloat(), buf.readFloat());
            case SPHERE -> new GasShape.Sphere(buf.readFloat());
            default -> throw new DecoderException("Unknown gas shape " + type);
        };
    }
}
