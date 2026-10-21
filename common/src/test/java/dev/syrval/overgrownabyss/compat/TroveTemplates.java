package dev.syrval.overgrownabyss.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Makes the mod's trove templates, which are binary, from the layouts here: run with {@code WRITE_TROVE_TEMPLATES=1} to write
 * them into the mod's resources, and {@code DiscTroveFilesTest} holds what is written to what a trove is.
 *
 * <p>Layer convention: a template's layer {@code sink} is the trove's floor (its containers' layer), the first layer above
 * the ground, and the pools' {@code sink} in the theme's settings must be that number. A kind without a spawner has its floor
 * at layer 0. One with a spawner has it at layer 3: layers 0 and 1 are 3 by 3 of the style's stone, layer 2 is a ring of it
 * around the spawner (in the middle, 1 layer under the floor, where mobs can spawn at the floor), and a masonry cap
 * stands on the spawner at layer 3.
 */
class TroveTemplates {
    enum Style { OVERGROWN, CRYSTAL, LAVA }

    enum Kind { CAIRN, HOARD, NEST, HOARD_NEST }

    static final int VARIANTS = 2;
    /** The layer of the floor of a kind with a spawner. */
    static final int NEST_FLOOR = 3;
    static final String LOOT_TABLE = "overgrown_abyss:chests/disc/jungle_stash";

    private static final String CHEST = "minecraft:chest";

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The pool a kind of a style is drawn from, as its file names say: {@code trove/<name>_<n>}. */
    static String name(Style style, Kind kind) {
        String name = kind.name().toLowerCase();
        return style == Style.OVERGROWN ? name : style.name().toLowerCase() + "_" + name;
    }

    static boolean hasSpawner(Kind kind) {
        return kind == Kind.NEST || kind == Kind.HOARD_NEST;
    }

    /** What a style's nests breed. */
    static String mob(Style style) {
        return switch (style) {
            case OVERGROWN -> "minecraft:cave_spider";
            case CRYSTAL -> "minecraft:skeleton";
            case LAVA -> "minecraft:wither_skeleton";
        };
    }

    // The blocks of a style: three of masonry and the stone of a spawner's shell. The last of the masonry is the accent.
    private static String[] masonry(Style style) {
        return switch (style) {
            case OVERGROWN -> new String[] {"minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:stone_bricks"};
            case CRYSTAL -> new String[] {"minecraft:calcite", "minecraft:smooth_basalt", "minecraft:amethyst_block"};
            case LAVA -> new String[] {"minecraft:blackstone", "minecraft:gilded_blackstone", "minecraft:magma_block"};
        };
    }

    private static String shell(Style style) {
        return switch (style) {
            case OVERGROWN -> "minecraft:cobblestone";
            case CRYSTAL -> "minecraft:smooth_basalt";
            case LAVA -> "minecraft:blackstone";
        };
    }

    /** The one template of a kind of a style; {@code variant} is 1 or 2. */
    static CompoundTag build(Style style, Kind kind, int variant) {
        if (variant < 1 || variant > VARIANTS || style == Style.OVERGROWN && kind == Kind.CAIRN) {
            throw new IllegalArgumentException(style + " " + kind + " " + variant);
        }
        Layout layout = new Layout();
        String[] stone = masonry(style);
        // The part above the floor, as it is for a layer-0 floor; moved up for a kind with a spawner.
        int floor = hasSpawner(kind) ? NEST_FLOOR : 0;
        boolean one = variant == 1;
        switch (kind) {
            case CAIRN -> {
                if (one) {
                    layout.chest(1, 0, 0, "east");
                    layout.block(0, 0, 0, stone[0]);
                    layout.block(0, 1, 0, stone[1]);
                    layout.block(0, 2, 0, stone[2]);
                } else {
                    layout.chest(0, 0, 1, "west");
                    layout.block(0, 0, 0, stone[1]);
                    layout.block(1, 0, 0, stone[0]);
                    layout.block(1, 0, 1, stone[0]);
                    layout.block(1, 1, 1, stone[2]);
                }
            }
            case NEST -> {
                if (one) {
                    layout.chest(2, floor, 1, "east");
                    layout.block(0, floor, 1, stone[0]);
                    layout.block(0, floor + 1, 1, stone[1]);
                    layout.block(1, floor, 0, stone[2]);
                } else {
                    layout.chest(1, floor, 2, "south");
                    layout.block(1, floor, 0, stone[1]);
                    layout.block(0, floor, 1, stone[0]);
                    layout.block(1, floor + 1, 0, stone[2]);
                }
            }
            case HOARD, HOARD_NEST -> {
                if (one) {
                    layout.chest(0, floor, 1, "west");
                    layout.chest(2, floor, 1, "east");
                    layout.block(1, floor, 0, stone[0]);
                    layout.block(1, floor, 1, stone[1]);
                    layout.block(1, floor, 2, stone[0]);
                    layout.block(1, floor + 1, 1, stone[2]);
                } else {
                    layout.chest(1, floor, 0, "north");
                    layout.chest(0, floor, 2, "south");
                    layout.chest(2, floor, 2, "south");
                    layout.block(0, floor, 0, stone[1]);
                    layout.block(2, floor, 0, stone[1]);
                    layout.block(1, floor, 1, stone[0]);
                    layout.block(1, floor, 2, stone[2]);
                    layout.block(1, floor + 1, 1, stone[0]);
                }
            }
        }
        if (hasSpawner(kind)) {
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < NEST_FLOOR; y++) {
                    for (int z = 0; z < 3; z++) {
                        if (y < NEST_FLOOR - 1 || x != 1 || z != 1) {
                            layout.block(x, y, z, shell(style));
                        }
                    }
                }
            }
            layout.spawner(1, NEST_FLOOR - 1, 1, mob(style));
            // The cap over the spawner is masonry like the rest, so that it is not seen as a marker.
            layout.block(1, floor, 1, stone[1]);
        }
        return layout.save();
    }

    /** Every template, written into the mod's {@code structure/trove} folder under {@code resources}. */
    static void writeAll(Path resources) throws IOException {
        Path folder = resources.resolve("data/overgrown_abyss/structure/trove");
        Files.createDirectories(folder);
        for (Style style : Style.values()) {
            for (Kind kind : Kind.values()) {
                if (style == Style.OVERGROWN && kind == Kind.CAIRN) {
                    continue;
                }
                for (int variant = 1; variant <= VARIANTS; variant++) {
                    NbtIo.writeCompressed(build(style, kind, variant), folder.resolve(name(style, kind) + "_" + variant + ".nbt"));
                }
            }
        }
    }

    @Test
    void writesTheTemplatesWhenAsked() throws IOException {
        Assumptions.assumeTrue("1".equals(System.getenv("WRITE_TROVE_TEMPLATES")), "set WRITE_TROVE_TEMPLATES=1 to write the templates");
        writeAll(Path.of("src/main/resources"));
    }

    /** The blocks of one template, in a vanilla template's saved form. */
    private static final class Layout {
        private final Map<String, Integer> palette = new LinkedHashMap<>();
        private final List<CompoundTag> states = new ArrayList<>();
        private final Map<String, CompoundTag> blocks = new LinkedHashMap<>();

        private int state(String key, CompoundTag state) {
            return palette.computeIfAbsent(key, k -> {
                states.add(state);
                return states.size() - 1;
            });
        }

        private void put(int x, int y, int z, int state, CompoundTag nbt) {
            CompoundTag block = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(x));
            pos.add(IntTag.valueOf(y));
            pos.add(IntTag.valueOf(z));
            block.put("pos", pos);
            block.putInt("state", state);
            if (nbt != null) {
                block.put("nbt", nbt);
            }
            // A later block takes the place of an earlier one.
            blocks.put(x + "," + y + "," + z, block);
        }

        void block(int x, int y, int z, String name) {
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            put(x, y, z, state(name, state), null);
        }

        void chest(int x, int y, int z, String facing) {
            CompoundTag state = new CompoundTag();
            state.putString("Name", CHEST);
            CompoundTag properties = new CompoundTag();
            properties.putString("facing", facing);
            properties.putString("type", "single");
            properties.putString("waterlogged", "false");
            state.put("Properties", properties);
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", CHEST);
            nbt.putString("LootTable", LOOT_TABLE);
            put(x, y, z, state(CHEST + facing, state), nbt);
        }

        void spawner(int x, int y, int z, String mob) {
            String name = BuiltInRegistries.BLOCK.getKey(Blocks.SPAWNER).toString();
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(BlockEntityType.MOB_SPAWNER).toString());
            CompoundTag entity = new CompoundTag();
            entity.putString("id", mob);
            CompoundTag data = new CompoundTag();
            data.put("entity", entity);
            nbt.put("SpawnData", data);
            nbt.putShort("RequiredPlayerRange", (short) 16);
            nbt.putShort("Delay", (short) 20);
            nbt.putShort("MinSpawnDelay", (short) 200);
            nbt.putShort("MaxSpawnDelay", (short) 800);
            nbt.putShort("SpawnCount", (short) 4);
            nbt.putShort("MaxNearbyEntities", (short) 6);
            nbt.putShort("SpawnRange", (short) 4);
            put(x, y, z, state(name, state), nbt);
        }

        CompoundTag save() {
            int[] size = new int[3];
            for (CompoundTag block : blocks.values()) {
                ListTag pos = block.getList("pos", 3);
                for (int i = 0; i < 3; i++) {
                    size[i] = Math.max(size[i], pos.getInt(i) + 1);
                }
            }
            CompoundTag template = new CompoundTag();
            ListTag sizes = new ListTag();
            for (int each : size) {
                sizes.add(IntTag.valueOf(each));
            }
            template.put("size", sizes);
            template.put("entities", new ListTag());
            ListTag all = new ListTag();
            blocks.values().forEach(all::add);
            template.put("blocks", all);
            ListTag palette = new ListTag();
            states.forEach(palette::add);
            template.put("palette", palette);
            template.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            return template;
        }
    }
}
