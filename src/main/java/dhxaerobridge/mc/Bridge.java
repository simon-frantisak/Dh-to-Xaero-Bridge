package dhxaerobridge.mc;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import dhxaerobridge.core.ColumnPoint;
import dhxaerobridge.core.SectionBuilder;
import dhxaerobridge.core.SectionData;
import dhxaerobridge.dh.DhAccess;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** The middle-man: asks Distant Horizons for a chunk and hands Xaero the result as normal chunk data. */
public final class Bridge {
    public static final Logger LOG = LoggerFactory.getLogger("dh-xaero-bridge");

    /** A small private thread pool, so we never block Minecraft's own file-reading threads. */
    public static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "dh-xaero-bridge-worker");
        t.setDaemon(true);
        return t;
    });

    private static final Map<ServerWorld, IDhApiLevelWrapper> LEVELS = new WeakHashMap<>();
    private static final AtomicInteger PRODUCED = new AtomicInteger();
    private static volatile boolean loggedFailure = false;

    private Bridge() {}

    /**
     * Minecraft saves half-generated chunks around the area you explored (status "structure_starts",
     * "biomes", ...). They contain no terrain, so Xaero draws nothing for them (black gaps).
     * Returns false for those, true for chunks that Xaero can actually draw.
     */
    public static boolean isUsableByXaero(NbtCompound chunk) {
        try {
            if (chunk.contains("below_zero_retrogen")) return true; // leave special cases alone
            String status = chunk.getString("Status");
            if (status == null || status.isEmpty()) return true;
            if (status.startsWith("minecraft:")) status = status.substring("minecraft:".length());
            switch (status) {
                case "empty":
                case "structure_starts":
                case "structure_references":
                case "biomes":
                case "noise":
                    return false;
                default:
                    return true;
            }
        } catch (Throwable t) {
            return true;
        }
    }

    public static IDhApiLevelWrapper levelFor(ServerWorld world) {
        synchronized (LEVELS) {
            IDhApiLevelWrapper cached = LEVELS.get(world);
            if (cached != null) return cached;
            String full = world.getRegistryKey().getValue().toString();
            String path = world.getRegistryKey().getValue().getPath();
            IDhApiLevelWrapper found = DhAccess.findLevel(world, full, path);
            if (found != null) {
                LEVELS.put(world, found);
            }
            return found;
        }
    }

    /**
     * Builds chunk data for one chunk from Distant Horizons' stored LODs.
     * Returns null when Distant Horizons has nothing for that chunk.
     */
    public static NbtCompound chunkNbt(ServerWorld world, int chunkX, int chunkZ) {
        return chunkNbt(world, chunkX, chunkZ, chunkX, chunkZ);
    }

    /**
     * Border fix: for a half-finished chunk Distant Horizons has no data for, borrow the look of the
     * nearest neighbouring chunk that Distant Horizons does know (4 sides first, then corners).
     */
    public static NbtCompound chunkNbtFromNeighbour(ServerWorld world, int chunkX, int chunkZ) {
        final int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] d : around) {
            NbtCompound n = chunkNbt(world, chunkX, chunkZ, chunkX + d[0], chunkZ + d[1]);
            if (n != null) return n;
        }
        return null;
    }

    /** Reads the LOD data of chunk (srcX, srcZ) but labels the result as chunk (chunkX, chunkZ). */
    private static NbtCompound chunkNbt(ServerWorld world, int chunkX, int chunkZ, int srcX, int srcZ) {
        try {
            IDhApiLevelWrapper level = levelFor(world);
            if (level == null) return null;
            ColumnPoint[][][] cols = DhAccess.fetchChunk(level, srcX, srcZ);
            if (cols == null) return null;
            int minY = world.getBottomY();
            int maxY = world.getTopY();
            List<SectionData> sections = SectionBuilder.build(cols, minY, maxY);
            if (sections == null || sections.isEmpty()) return null;
            NbtCompound nbt = NbtWriter.toChunkNbt(sections, chunkX, chunkZ, minY >> 4);
            int n = PRODUCED.incrementAndGet();
            if (n == 1) {
                LOG.info("First chunk filled in from Distant Horizons: chunk {} {}", chunkX, chunkZ);
            }
            return nbt;
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                LOG.error("Could not turn Distant Horizons data into map data (this message is shown once)", t);
            }
            return null;
        }
    }
}
