package dev.syrval.verdantdeep.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The room a source of gas fills round its anchor. These are two of the shapes of the gas library's emitters, written here in
 * the mod's own codec so that the server reads and sends them without that library. A cylinder stands on its anchor and a
 * sphere is centred on it, as the library has them.
 */
public sealed interface GasShape {
    Codec<GasShape> CODEC = Codec.STRING.partialDispatch("type", shape -> DataResult.success(shape.type()), GasShape::codecFor);

    String type();

    /** The height of the anchor at which the shape rests on a floor at {@code floorY}. */
    double standingOn(double floorY);

    /** The height of the anchor at which the shape hangs from a ceiling at {@code ceilingY}. */
    double hangingFrom(double ceilingY);

    /**
     * An upright cylinder standing on its anchor: the usual shape for gas lying on or rising off a surface. Not called a disc,
     * which in this mod is the platform the gas lies on.
     */
    record Cylinder(float radius, float height) implements GasShape {
        static final MapCodec<Cylinder> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0.25F, 128).fieldOf("radius").forGetter(Cylinder::radius),
                Codec.floatRange(0.25F, 256).optionalFieldOf("height", 1F).forGetter(Cylinder::height)
        ).apply(i, Cylinder::new));

        @Override
        public String type() {
            return "cylinder";
        }

        @Override
        public double standingOn(double floorY) {
            return floorY;
        }

        @Override
        public double hangingFrom(double ceilingY) {
            return ceilingY - height;
        }
    }

    /** A ball centred on its anchor. */
    record Sphere(float radius) implements GasShape {
        static final MapCodec<Sphere> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0.25F, 128).fieldOf("radius").forGetter(Sphere::radius)
        ).apply(i, Sphere::new));

        @Override
        public String type() {
            return "sphere";
        }

        @Override
        public double standingOn(double floorY) {
            return floorY + radius;
        }

        @Override
        public double hangingFrom(double ceilingY) {
            return ceilingY - radius;
        }
    }

    private static DataResult<MapCodec<? extends GasShape>> codecFor(String type) {
        return switch (type) {
            case "cylinder" -> DataResult.success(Cylinder.CODEC);
            case "sphere" -> DataResult.success(Sphere.CODEC);
            default -> DataResult.error(() -> "Unknown gas shape " + type);
        };
    }
}
