package dhxaerobridge.core;

/**
 * One vertical slice of a block column, as reported by Distant Horizons.
 * Covers the blocks from {@code bottom} (inclusive) up to {@code top} (exclusive).
 * {@code state == null} means air. This class has no Minecraft types in it on purpose,
 * so the logic around it can be tested on its own.
 */
public final class ColumnPoint {
    public final int bottom;
    public final int top;
    public final Object state;
    public final String biome;

    public ColumnPoint(int bottom, int top, Object state, String biome) {
        this.bottom = bottom;
        this.top = top;
        this.state = state;
        this.biome = biome;
    }
}
