package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** Containers are counted by the blocks that use a palette entry, not by the entries. */
class ChasmStatsTest {
    private static CompoundTag entry(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static CompoundTag block(int state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state", state);
        return tag;
    }

    @Test
    void countsBlocksOfEachPaletteEntry() {
        ListTag palette = new ListTag();
        palette.add(entry("minecraft:stone"));
        palette.add(entry("minecraft:chest"));
        palette.add(entry("minecraft:barrel"));
        palette.add(entry("minecraft:spawner"));
        ListTag blocks = new ListTag();
        // The chest entry is used three times, the barrel entry once and the spawner entry by no block.
        for (int state : new int[] {0, 1, 1, 0, 2, 1}) {
            blocks.add(block(state));
        }
        CompoundTag template = new CompoundTag();
        template.put("palette", palette);
        template.put("blocks", blocks);
        assertEquals(new ChasmStats.Containers(3, 1, 0), ChasmStats.Containers.ofTemplate(template));
    }
}
