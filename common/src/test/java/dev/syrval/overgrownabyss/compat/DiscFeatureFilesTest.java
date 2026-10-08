package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The features the disc themes grow, read the way the game reads them. A feature file the game cannot read stops every world
 * from loading, and nothing but the game's own loader knows all the rules: the fields of each feature and placement, the
 * limits on their numbers, the names of blocks and their properties.
 */
class DiscFeatureFilesTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String FEATURES = "data/overgrown_abyss/worldgen/configured_feature";

    @Test
    void theGameLoadsEveryFeatureFileOfTheMod(@TempDir Path pack) throws IOException, URISyntaxException {
        // The mod's other worldgen files need types that only the running mod registers, so the pack is its features alone.
        Path source = Path.of(DiscFeatureFilesTest.class.getResource("/" + FEATURES).toURI());
        Set<String> files;
        try (var found = Files.walk(source)) {
            files = found.filter(Files::isRegularFile)
                    .map(file -> source.relativize(file).toString().replace('\\', '/').replaceFirst("\\.json$", ""))
                    .collect(Collectors.toSet());
        }
        for (String file : files) {
            Path to = pack.resolve(FEATURES).resolve(file + ".json");
            Files.createDirectories(to.getParent());
            Files.copy(source.resolve(file + ".json"), to);
        }
        PackResources vanilla = ServerPacksSource.createVanillaPackSource();
        PackResources mod = new PathPackResources(new PackLocationInfo("mod", Component.literal("mod"), PackSource.BUILT_IN, Optional.empty()), pack);
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla, mod))) {
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            // Throws if any file fails, having logged which and why.
            RegistryAccess.Frozen loaded = RegistryDataLoader.load(
                    manager, layers.getAccessForLoading(RegistryLayer.WORLDGEN), RegistryDataLoader.WORLDGEN_REGISTRIES);
            Set<String> registered = loaded.registryOrThrow(Registries.CONFIGURED_FEATURE).keySet().stream()
                    .filter(id -> id.getNamespace().equals("overgrown_abyss"))
                    .map(id -> id.getPath())
                    .collect(Collectors.toSet());
            assertEquals(files, registered);
            assertTrue(files.size() >= 23, files.size() + " feature files");
        }
    }
}
