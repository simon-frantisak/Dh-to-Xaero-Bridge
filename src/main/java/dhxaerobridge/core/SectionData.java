package dhxaerobridge.core;

import java.util.List;

/** One 16x16x16 piece of a chunk, already packed the way Minecraft's save files pack it. */
public final class SectionData {
    public final int y;
    /** Palette of block states. Index 0 is always air (represented by null). */
    public final List<Object> blockPalette;
    /** Packed block indices, or null when the palette has only one entry. */
    public final long[] blockData;
    public final List<String> biomePalette;
    /** Packed biome indices (4x4x4 cells), or null when the palette has only one entry. */
    public final long[] biomeData;

    public SectionData(int y, List<Object> blockPalette, long[] blockData,
                       List<String> biomePalette, long[] biomeData) {
        this.y = y;
        this.blockPalette = blockPalette;
        this.blockData = blockData;
        this.biomePalette = biomePalette;
        this.biomeData = biomeData;
    }
}
