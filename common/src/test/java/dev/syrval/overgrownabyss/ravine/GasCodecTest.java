package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** How gas is written in a theme and in a hole's settings, and what the mod's own settings give off. */
class GasCodecTest {
    static {
        MinecraftBootstrap.init();
    }

    @Test
    void aThemesGasDefaultsToTheTopAndSpreadsEvenly() {
        var json = JsonParser.parseString("""
                {"medium": "overgrown_abyss:mist", "every": 80, "shape": {"type": "cylinder", "radius": 3}, "rate": 0.5}
                """);
        DiscTheme.Gas gas = DiscTheme.Gas.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(new DiscTheme.Gas(ResourceLocation.parse("overgrown_abyss:mist"), 80, DiscTheme.Surface.TOP, Optional.empty(), Optional.empty(),
                new GasShape.Cylinder(3, 1), 0.5F), gas);
        var written = DiscTheme.Gas.CODEC.encodeStart(JsonOps.INSTANCE, gas).getOrThrow();
        assertEquals(gas, DiscTheme.Gas.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow());
    }

    @Test
    void anUnknownShapeIsAnErrorNotACrash() {
        var json = JsonParser.parseString("""
                {"medium": "overgrown_abyss:mist", "every": 80, "shape": {"type": "cone", "radius": 3}, "rate": 0.5}
                """);
        assertTrue(DiscTheme.Gas.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void gasOnTheRimCannotFadeTowardsIt() {
        var json = JsonParser.parseString("""
                {"medium": "overgrown_abyss:mist", "on": "rim", "every": 8, "rim_fade": {"width": 4, "to": 0},
                 "shape": {"type": "sphere", "radius": 1}, "rate": 0.5}
                """);
        assertTrue(DiscTheme.Gas.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void aHolesGasRoundTrips() {
        var json = JsonParser.parseString("""
                {"haze": {"medium": "overgrown_abyss:mist", "spacing": 16, "layer_spacing": 12, "shape": {"type": "sphere", "radius": 3}, "rate": 0.05},
                 "falls": {"spray": {"medium": "overgrown_abyss:mist", "shape": {"type": "sphere", "radius": 3}, "rate": 1.5},
                           "curtain": {"medium": "overgrown_abyss:mist", "rate": 0.25}},
                 "cavern": [{"medium": "overgrown_abyss:mist", "every": 300, "shape": {"type": "cylinder", "radius": 4}, "rate": 0.4}]}
                """);
        GasSettings gas = GasSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1.5F, gas.falls().orElseThrow().curtain().orElseThrow().margin(), "the curtain's margin by default");
        assertTrue(gas.springs().isEmpty());
        var written = GasSettings.CODEC.encodeStart(JsonOps.INSTANCE, gas).getOrThrow();
        assertEquals(gas, GasSettings.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow());
    }

    @Test
    void everyOneOfTheModsOwnThemesAndItsHoleGiveOffGas() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        for (DiscTheme theme : settings.discThemes()) {
            assertFalse(theme.gas().isEmpty(), () -> theme.biome() + " gives off no gas");
        }
        GasSettings gas = settings.dressing().gas().orElseThrow();
        assertTrue(gas.haze().isPresent() && gas.falls().isPresent() && gas.springs().isPresent() && !gas.cavern().isEmpty());
    }
}
