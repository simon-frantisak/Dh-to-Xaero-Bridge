package dhxaerobridge.mc;

import dhxaerobridge.core.SectionData;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.List;

/** Builds the same kind of chunk data Minecraft stores in its region files, so Xaero can read it. */
final class NbtWriter {
    /** DataVersion of Minecraft 1.21.1. Matching it makes Minecraft's data-fixer leave our data alone. */
    private static final int DATA_VERSION = 3955;

    private NbtWriter() {}

    static NbtCompound toChunkNbt(List<SectionData> sections, int chunkX, int chunkZ, int bottomSectionY) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", DATA_VERSION);
        root.putInt("xPos", chunkX);
        root.putInt("zPos", chunkZ);
        root.putInt("yPos", bottomSectionY);
        root.putString("Status", "minecraft:full");
        root.putLong("LastUpdate", 0L);

        NbtList list = new NbtList();
        for (SectionData s : sections) {
            NbtCompound sec = new NbtCompound();
            sec.putByte("Y", (byte) s.y);

            NbtCompound blockStates = new NbtCompound();
            NbtList palette = new NbtList();
            for (Object o : s.blockPalette) {
                if (o == null) {
                    NbtCompound air = new NbtCompound();
                    air.putString("Name", "minecraft:air");
                    palette.add(air);
                } else {
                    palette.add(NbtHelper.fromBlockState((BlockState) o));
                }
            }
            blockStates.put("palette", palette);
            if (s.blockData != null) {
                blockStates.putLongArray("data", s.blockData);
            }
            sec.put("block_states", blockStates);

            NbtCompound biomes = new NbtCompound();
            NbtList biomePalette = new NbtList();
            for (String b : s.biomePalette) {
                biomePalette.add(NbtString.of(b));
            }
            biomes.put("palette", biomePalette);
            if (s.biomeData != null) {
                biomes.putLongArray("data", s.biomeData);
            }
            sec.put("biomes", biomes);

            list.add(sec);
        }
        root.put("sections", list);
        return root;
    }
}
