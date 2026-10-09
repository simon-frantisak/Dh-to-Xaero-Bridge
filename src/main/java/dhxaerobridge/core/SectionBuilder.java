package dhxaerobridge.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the 16x16 block columns that Distant Horizons gives us into the "sections"
 * (16x16x16 pieces) that a normal Minecraft chunk save is made of.
 * Pure Java, no Minecraft classes.
 */
public final class SectionBuilder {
    private static final String DEFAULT_BIOME = "minecraft:plains";

    private SectionBuilder() {}

    /**
     * @param cols     [x][z] -> list of column points (may contain nulls / be empty)
     * @param worldMinY lowest block Y of the world (inclusive)
     * @param worldMaxY highest block Y of the world (exclusive)
     * @return the sections, or null if Distant Horizons has nothing for this chunk
     */
    public static List<SectionData> build(ColumnPoint[][][] cols, int worldMinY, int worldMaxY) {
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                ColumnPoint[] pts = cols[x][z];
                if (pts == null) continue;
                for (ColumnPoint p : pts) {
                    if (p == null) continue;
                    int b = Math.max(p.bottom, worldMinY);
                    int t = Math.min(p.top, worldMaxY);
                    if (t <= b) continue;
                    if (b < lo) lo = b;
                    if (t > hi) hi = t;
                }
            }
        }
        if (hi <= lo) return null;

        int firstSection = Math.floorDiv(lo, 16);
        int lastSolidSection = Math.floorDiv(hi - 1, 16);
        int topAllowed = Math.floorDiv(worldMaxY - 1, 16);
        // one extra (air) section on top so biome lookups right at the surface still find data
        int lastSection = Math.min(lastSolidSection + 1, topAllowed);
        int count = lastSection - firstSection + 1;

        int[][] blocks = new int[count][];
        List<Object> palette = new ArrayList<>();
        palette.add(null); // air
        Map<Object, Integer> paletteIndex = new HashMap<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                ColumnPoint[] pts = cols[x][z];
                if (pts == null) continue;
                for (ColumnPoint p : pts) {
                    if (p == null || p.state == null) continue; // air stays as zeros
                    int b = Math.max(p.bottom, worldMinY);
                    int t = Math.min(p.top, worldMaxY);
                    if (t <= b) continue;
                    Integer idx = paletteIndex.get(p.state);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(p.state);
                        paletteIndex.put(p.state, idx);
                    }
                    for (int y = b; y < t; y++) {
                        int s = Math.floorDiv(y, 16) - firstSection;
                        int[] arr = blocks[s];
                        if (arr == null) {
                            arr = new int[4096];
                            blocks[s] = arr;
                        }
                        arr[((y & 15) << 8) | (z << 4) | x] = idx;
                    }
                }
            }
        }

        // The biome we fall back to for each column: the one at its highest point.
        String[][] topBiome = new String[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                ColumnPoint[] pts = cols[x][z];
                int best = Integer.MIN_VALUE;
                String name = DEFAULT_BIOME;
                if (pts != null) {
                    for (ColumnPoint p : pts) {
                        if (p != null && p.top > best && p.biome != null) {
                            best = p.top;
                            name = p.biome;
                        }
                    }
                }
                topBiome[x][z] = name;
            }
        }

        List<SectionData> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int sectionY = firstSection + i;
            int[] arr = blocks[i];

            // ---- blocks ----
            long[] blockData = null;
            List<Object> sectionPalette;
            if (arr == null) {
                sectionPalette = new ArrayList<>(1);
                sectionPalette.add(null);
            } else {
                // only keep the palette entries this section really uses
                Map<Integer, Integer> remap = new HashMap<>();
                sectionPalette = new ArrayList<>();
                sectionPalette.add(null);
                remap.put(0, 0);
                int[] local = new int[4096];
                for (int k = 0; k < 4096; k++) {
                    int g = arr[k];
                    Integer l = remap.get(g);
                    if (l == null) {
                        l = sectionPalette.size();
                        sectionPalette.add(palette.get(g));
                        remap.put(g, l);
                    }
                    local[k] = l;
                }
                if (sectionPalette.size() > 1) {
                    int bits = Math.max(4, ceilLog2(sectionPalette.size()));
                    blockData = pack(local, bits);
                }
            }

            // ---- biomes (4x4x4 cells) ----
            List<String> biomePalette = new ArrayList<>();
            Map<String, Integer> biomeIdx = new HashMap<>();
            int[] cells = new int[64];
            for (int qy = 0; qy < 4; qy++) {
                int y = sectionY * 16 + qy * 4 + 2;
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        int x = qx * 4 + 1;
                        int z = qz * 4 + 1;
                        String name = biomeAt(cols[x][z], y, topBiome[x][z]);
                        Integer bi = biomeIdx.get(name);
                        if (bi == null) {
                            bi = biomePalette.size();
                            biomePalette.add(name);
                            biomeIdx.put(name, bi);
                        }
                        cells[(qy << 4) | (qz << 2) | qx] = bi;
                    }
                }
            }
            long[] biomeData = null;
            if (biomePalette.size() > 1) {
                biomeData = pack(cells, Math.max(1, ceilLog2(biomePalette.size())));
            }

            out.add(new SectionData(sectionY, sectionPalette, blockData, biomePalette, biomeData));
        }
        return out;
    }

    private static String biomeAt(ColumnPoint[] pts, int y, String fallback) {
        if (pts != null) {
            for (ColumnPoint p : pts) {
                if (p != null && p.biome != null && y >= p.bottom && y < p.top) return p.biome;
            }
        }
        return fallback;
    }

    public static int ceilLog2(int n) {
        return n <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    /** Same layout as Minecraft's PackedIntegerArray: values never straddle two longs. */
    public static long[] pack(int[] values, int bits) {
        int perLong = 64 / bits;
        long[] out = new long[(values.length + perLong - 1) / perLong];
        long mask = (1L << bits) - 1L;
        for (int i = 0; i < values.length; i++) {
            out[i / perLong] |= ((long) values[i] & mask) << ((i % perLong) * bits);
        }
        return out;
    }

    public static int unpack(long[] data, int bits, int index) {
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        return (int) ((data[index / perLong] >>> ((index % perLong) * bits)) & mask);
    }
}
